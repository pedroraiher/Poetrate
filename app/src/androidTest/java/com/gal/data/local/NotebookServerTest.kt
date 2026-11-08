package com.gal.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gal.localweb.NotebookServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NotebookServerTest {
    private lateinit var store: PrivateMediaStore
    private lateinit var server: NotebookServer
    private val created = mutableListOf<Long>()
    private var cookie: String? = null
    data class Result(val code: Int, val bytes: ByteArray, val cookie: String?) {
        fun json() = JSONObject(String(bytes))
    }
    private fun request(path: String, method: String = "GET", bytes: ByteArray? = null, origin: String = server.url, range: String? = null, mime: String = "application/json"): Result {
        return java.net.Socket("127.0.0.1", 8080).use { socket ->
            socket.soTimeout = 5000
            val headers = buildString {
                append("$method $path HTTP/1.1\r\nHost: 127.0.0.1:8080\r\nOrigin: $origin\r\nConnection: close\r\n")
                cookie?.let { append("Cookie: $it\r\n") }
                range?.let { append("Range: $it\r\n") }
                if (bytes != null) append("Content-Type: $mime\r\nContent-Length: ${bytes.size}\r\n")
                append("\r\n")
            }
            socket.getOutputStream().write(headers.toByteArray())
            bytes?.let { socket.getOutputStream().write(it) }
            socket.getOutputStream().flush()
            val input = socket.getInputStream().buffered()
            fun line(): String {
                val out = java.io.ByteArrayOutputStream()
                while (true) { val c = input.read(); if(c < 0 || c == 10) break; if(c != 13) out.write(c) }
                return out.toString("UTF-8")
            }
            val status = line().split(' ')[1].toInt()
            val fields = mutableMapOf<String,String>()
            while(true) { val h = line(); if(h.isEmpty()) break; fields[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim() }
            val output = java.io.ByteArrayOutputStream()
            if (fields["transfer-encoding"] == "chunked") {
                while(true) { val count = line().substringBefore(';').toInt(16); if(count == 0) break; val chunk=ByteArray(count); java.io.DataInputStream(input).readFully(chunk); output.write(chunk); line() }
            } else { val data=ByteArray(fields["content-length"]?.toInt() ?: 0); java.io.DataInputStream(input).readFully(data); output.write(data) }
            Result(status, output.toByteArray(), fields["set-cookie"])
        }
    }

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 37) InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.ACCESS_LOCAL_NETWORK").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        store = PrivateMediaStore(context)
        server = NotebookServer(context, store, "127.0.0.1")
        server.start(5000, true)
    }
    @After fun cleanup() { server.stop(); created.forEach { store.delete(it) }; store.close() }
    private fun login() {
        val paired = request("/api/pair", "POST", JSONObject().put("code", server.code).toString().toByteArray())
        assertEquals(200, paired.code)
        val ticket = paired.json().getString("ticket")
        assertFalse(request("/api/pair/status?ticket=$ticket").json().getBoolean("approved"))
        assertEquals(401, request("/api/media").code)
        server.approve(true)
        val response = request("/api/pair/status?ticket=$ticket")
        assertTrue(response.json().getBoolean("approved"))
        cookie = response.cookie!!.substringBefore(';')
    }
    @Test fun authorizationOriginAndStaticAssets() {
        assertEquals(200, request("/").code)
        assertEquals(200, request("/remote.js").code)
        assertEquals(401, request("/api/media").code)
        assertEquals(403, request("/api/pair", "POST", "{}".toByteArray(), "http://untrusted.example").code)
        login()
        assertEquals(200, request("/api/media").code)
        assertEquals(403, request("/api/media", origin = "http://untrusted.example").code)
    }
    @Test fun uploadThumbnailRangeRepositionAndTrash() {
        login()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        val upload = request("/api/upload?name=notebook.png", "POST", bytes, mime = "image/png")
        assertEquals(200, upload.code)
        val id = upload.json().getString("id").toLong(); created.add(id)
        assertArrayEquals(bytes, store.file(id)!!.readBytes())
        assertEquals(200, request("/api/media/$id/thumb").code)
        val part = request("/api/media/$id/original", range = "bytes=2-8")
        assertEquals(206, part.code); assertArrayEquals(bytes.copyOfRange(2,9), part.bytes)
        assertEquals(416, request("/api/media/$id/original", range = "bytes=999999-").code)
        assertEquals(200, request("/api/media/$id/position", "POST", "{\"position\":1}".toByteArray()).code)
        assertEquals(1, store.list().single { it.id == id }.position)
        assertEquals(200, request("/api/media/$id/trash", "POST").code)
        assertTrue(store.list().single { it.id == id }.isTrashed)
        assertEquals(404, request("/api/media/$id/original").code)
    }
    @Test fun badCodesAreLimited() {
        repeat(5) { assertEquals(403, request("/api/pair", "POST", "{\"code\":\"wrong\"}".toByteArray()).code) }
        assertEquals(403, request("/api/pair", "POST", JSONObject().put("code", server.code).toString().toByteArray()).code)
    }
}
