package com.gal.localweb

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import com.gal.data.local.PrivateMediaStore
import com.gal.model.Media
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Semaphore

/** Explicit, screen-scoped LAN session. No remote services or persisted credentials. */
class NotebookServer(private val context: Context, private val store: PrivateMediaStore, val address: String, private val theme: Map<String, String> = emptyMap()) : NanoHTTPD(address, 8080) {
    val code = (100000 + SecureRandom().nextInt(900000)).toString()
    val url get() = "http://$address:8080"
    @Volatile var pendingPeer: String? = null
        private set
    @Volatile private var ticket: String? = null
    private var approved = false
    @Volatile private var token: String? = null
    private var attempts = 0
    private val expires = System.currentTimeMillis() + 60 * 60 * 1000
    private val organization by lazy { NotebookOrganizer(context, ::library) }
    @Volatile private var organizationOpened = false
    private val slots = Semaphore(4)
    private var snapshotVersion = -1L
    private var snapshot = emptyList<Media>()
    private val thumbnails = object : android.util.LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    @Synchronized fun approve(allow: Boolean) {
        approved = allow
        if (!allow) ticket = null
        pendingPeer = null
    }
    @Synchronized private fun library(): List<Media> {
        val version = PrivateMediaStore.changes.value
        if (version != snapshotVersion) {
            snapshot = store.list().filterNot { it.isTrashed }
            snapshotVersion = version
        }
        return snapshot
    }
    override fun stop() {
        token = null; ticket = null; pendingPeer = null
        super.stop()
        if (organizationOpened) organization.close()
        thumbnails.evictAll()
    }
    private fun json(value: Any, status: Response.Status = Response.Status.OK) =
        newFixedLengthResponse(status, "application/json", value.toString())
    private fun error(message: String, status: Response.Status = Response.Status.BAD_REQUEST) = json(JSONObject().put("error", message), status)
    override fun serve(session: IHTTPSession): Response {
        if (!slots.tryAcquire()) return error("Ocupado. Tente novamente.", Response.Status.SERVICE_UNAVAILABLE)
        try {
            val host = session.headers["host"]
            val origin = session.headers["origin"]
            val response = when {
                System.currentTimeMillis() > expires -> error("Sessão expirada. Reinicie no celular.", Response.Status.UNAUTHORIZED)
                host != "$address:8080" -> error("Endereço inválido.", Response.Status.FORBIDDEN)
                origin != null && origin != url -> error("Origem não autorizada.", Response.Status.FORBIDDEN)
                session.method != Method.GET && session.method != Method.HEAD && origin != url -> error("Origem obrigatória.", Response.Status.FORBIDDEN)
                else -> route(session)
            }
            response.addHeader("Cache-Control", "no-store")
            response.addHeader("X-Content-Type-Options", "nosniff")
            response.addHeader("X-Frame-Options", "DENY")
            response.addHeader("Referrer-Policy", "no-referrer")
            response.addHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
            return response
        } catch (e: IllegalArgumentException) {
            return error(e.message ?: "Pedido inválido.")
        } catch (_: Exception) {
            return error("Não foi possível concluir a operação.", Response.Status.INTERNAL_ERROR)
        } finally { slots.release() }
    }
    private fun body(s: IHTTPSession, max: Long = 4096): ByteArray {
        val length = s.headers["content-length"]?.toLongOrNull() ?: 0
        require(length in 1..max && s.headers["transfer-encoding"] == null)
        val bytes = ByteArray(length.toInt())
        java.io.DataInputStream(s.inputStream).readFully(bytes)
        return bytes
    }
    @Synchronized private fun pair(s: IHTTPSession): Response {
        if (attempts >= 5) return error("Limite de tentativas. Reinicie a conexão no celular.", Response.Status.FORBIDDEN)
        if (ticket != null || token != null) return error("Já existe um pedido ou notebook conectado.")
        attempts++
        if (JSONObject(String(body(s), Charsets.UTF_8)).optString("code") != code) return error("Código incorreto.", Response.Status.FORBIDDEN)
        ticket = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        pendingPeer = s.remoteIpAddress
        approved = false
        return json(JSONObject().put("ticket", ticket))
    }
    private fun route(s: IHTTPSession): Response {
        val path = s.uri
        if (s.method == Method.GET && path in setOf("/", "/index.html", "/styles.css", "/app.js", "/remote.js", "/organize.js", "/logo.png")) {
            val name = if (path == "/") "index.html" else path.drop(1)
            val mime = if (name.endsWith("png")) "image/png" else if (name.endsWith("css")) "text/css" else if (name.endsWith("js")) "application/javascript" else "text/html"
            return newChunkedResponse(Response.Status.OK, "$mime; charset=utf-8", context.assets.open(name))
        }
        if (path == "/api/pair" && s.method == Method.POST) return pair(s)
        if (path == "/api/pair/status" && s.method == Method.GET) return synchronized(this) {
            if (ticket == null || s.parms["ticket"] != ticket) return@synchronized error("Pedido expirado ou recusado.", Response.Status.UNAUTHORIZED)
            if (!approved) return@synchronized json(JSONObject().put("approved", false))
            token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            ticket = null
            json(JSONObject().put("approved", true)).apply { addHeader("Set-Cookie", "poetrate=$token; HttpOnly; SameSite=Strict; Path=/; Max-Age=3600") }
        }
        val cookie = s.headers["cookie"].orEmpty().split(';').map { it.trim() }
        if (token == null || "poetrate=$token" !in cookie) return error("Conecte ao celular para continuar.", Response.Status.UNAUTHORIZED)
        if (path.startsWith("/api/organize")) {
            organizationOpened = true
            if (path == "/api/organize" && s.method == Method.GET) return json(organization.snapshot())
            if (path.startsWith("/api/organize/face/") && s.method == Method.GET) {
                val file=organization.thumbnail(path.substringAfterLast('/'))
                return newFixedLengthResponse(Response.Status.OK,"image/jpeg",FileInputStream(file),file.length())
            }
            if (s.method == Method.POST && path in setOf("/api/organize/scan","/api/organize/pause","/api/organize/charging","/api/organize/rename","/api/organize/merge","/api/organize/separate")) {
                val data=JSONObject(String(body(s,1024*1024),Charsets.UTF_8))
                return json(organization.action(path.substringAfterLast('/'),data))
            }
            return error("Não encontrado.",Response.Status.NOT_FOUND)
        }
        if (path == "/api/theme" && s.method == Method.GET) return json(JSONObject(theme))
        if (path == "/api/media" && s.method == Method.GET) {
            return json(JSONArray().apply { library().forEach { m -> put(JSONObject().put("id", m.id.toString()).put("name", m.displayName).put("type", if (m.mimeType.startsWith("video/")) "video" else "image").put("size", m.sizeBytes).put("position", m.position).put("date", m.dateAdded.time)) } })
        }
        if (path == "/api/upload" && s.method == Method.POST) return upload(s)
        val match = Regex("/api/media/(\\d+)/(thumb|original|position|trash)").matchEntire(path) ?: return error("Não encontrado.", Response.Status.NOT_FOUND)
        val id = match.groupValues[1].toLong()
        val m = library().find { it.id == id } ?: return error("Mídia não encontrada.", Response.Status.NOT_FOUND)
        when (match.groupValues[2]) {
            "position" -> if (s.method == Method.POST) {
                store.reposition(id, JSONObject(String(body(s), Charsets.UTF_8)).getInt("position"))
                return json(JSONObject().put("ok", true))
            }
            "trash" -> if (s.method == Method.POST) {
                check(store.update(id, "trashed", "1"))
                return json(JSONObject().put("ok", true))
            }
            "thumb" -> if (s.method == Method.GET) {
                val key = "$id:${m.dateModified.time}"
                val bytes = thumbnails.get(key) ?: run {
                    val file = checkNotNull(store.file(id))
                    val bitmap = if (m.mimeType.startsWith("video/")) ThumbnailUtils.createVideoThumbnail(file, Size(360, 360), null) else ThumbnailUtils.createImageThumbnail(file, Size(360, 360), null)
                    ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out); bitmap.recycle(); out.toByteArray() }.also { thumbnails.put(key, it) }
                }
                return newFixedLengthResponse(Response.Status.OK, "image/jpeg", bytes.inputStream(), bytes.size.toLong())
            }
            "original" -> if (s.method == Method.GET || s.method == Method.HEAD) {
                val file = checkNotNull(store.file(id)); val size = file.length()
                val range = s.headers["range"]
                var start = 0L; var end = size - 1
                if (range != null) {
                    val parts = Regex("bytes=(\\d*)-(\\d*)").matchEntire(range)
                    if (parts == null || parts.groupValues.drop(1).all { it.isEmpty() }) return rangeError(size)
                    val a = parts.groupValues[1]; val b = parts.groupValues[2]
                    if (a.isEmpty()) { val suffix = b.toLongOrNull() ?: return rangeError(size); if (suffix <= 0) return rangeError(size); start = (size - suffix).coerceAtLeast(0) }
                    else { start = a.toLongOrNull() ?: return rangeError(size); end = if (b.isEmpty()) size - 1 else (b.toLongOrNull() ?: return rangeError(size)).coerceAtMost(size - 1) }
                    if (start !in 0 until size || end < start) return rangeError(size)
                }
                val stream = FileInputStream(file); stream.channel.position(start)
                return newFixedLengthResponse(if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT, m.mimeType, stream, end - start + 1).apply {
                    addHeader("Accept-Ranges", "bytes")
                    if (range != null) addHeader("Content-Range", "bytes $start-$end/$size")
                }
            }
        }
        return error("Método não permitido.", Response.Status.METHOD_NOT_ALLOWED)
    }
    private fun rangeError(size: Long) = error("Intervalo inválido.", Response.Status.RANGE_NOT_SATISFIABLE).apply { addHeader("Content-Range", "bytes */$size") }
    private fun upload(s: IHTTPSession): Response {
        val mime = s.headers["content-type"].orEmpty().substringBefore(';')
        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif", "video/mp4", "video/webm", "video/quicktime", "video/x-matroska")) { "Formato não suportado." }
        val length = s.headers["content-length"]?.toLongOrNull() ?: 0
        require(length in 1..(2L * 1024 * 1024 * 1024) && s.headers["transfer-encoding"] == null) { "Envie um arquivo de até 2 GB." }
        require(context.filesDir.usableSpace > length + 32L * 1024 * 1024) { "Espaço insuficiente no celular." }
        val file = store.newCapture(mime.startsWith("video/"))
        try {
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var remaining = length
                while (remaining > 0) { val count = s.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()); require(count > 0) { "Envio interrompido." }; out.write(buffer, 0, count); remaining -= count }
            }
            if (mime.startsWith("image/")) {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, options)
                require(options.outWidth > 0 && options.outHeight > 0) { "Imagem inválida." }
            } else android.media.MediaMetadataRetriever().use { it.setDataSource(file.path); require(it.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let { w -> w > 0 } == true) { "Vídeo inválido." } }
            val name = s.parms["name"].orEmpty().substringAfterLast('/').substringAfterLast('\\').take(180).ifBlank { "Notebook" }
            val id = store.commit(file, mime, name, "Notebook")
            return json(JSONObject().put("id", id.toString()))
        } finally { file.delete() }
    }
    companion object {
        fun localAddress(): String? = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap")) 0 else 1 }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }
}
