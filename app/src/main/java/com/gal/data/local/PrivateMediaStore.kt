package com.gal.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.gal.model.Album
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.gal.model.Media
import com.gal.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The database and originals live inside the app sandbox; nothing enters MediaStore. */
@Singleton
class PrivateMediaStore @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        val changes = MutableStateFlow(0L)
        fun notifyChanged() { changes.update { it + 1 } }
    }

    private val root get() = File(context.filesDir, "media").also { check(it.isDirectory || it.mkdirs()) }
    private val pending get() = File(context.filesDir, "capture_pending").also { check(it.isDirectory || it.mkdirs()) }
    private val helper = object : SQLiteOpenHelper(context, "private_media.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, cover TEXT)")
            db.execSQL("""CREATE TABLE media (
                id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL, mime TEXT NOT NULL, album TEXT NOT NULL,
                added INTEGER NOT NULL, favourite INTEGER NOT NULL DEFAULT 0,
                trashed INTEGER NOT NULL DEFAULT 0, sort_position INTEGER NOT NULL DEFAULT 0)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE albums ADD COLUMN cover TEXT")
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE media ADD COLUMN sort_position INTEGER NOT NULL DEFAULT 0")
                // Preserve the existing visual order, numbering from the bottom.
                db.rawQuery("SELECT id FROM media ORDER BY added ASC, id ASC", null).use { cursor ->
                    var position = 1L
                    while (cursor.moveToNext()) {
                        db.execSQL("UPDATE media SET sort_position=? WHERE id=?", arrayOf(position++, cursor.getLong(0)))
                    }
                }
            }
        }
    }

    fun newCapture(video: Boolean): File = File.createTempFile("capture_", if (video) ".mp4" else ".jpg", pending)

    /** Commit only completed captures/edits. Failed writes never appear in the library. */
    fun commit(source: File, mime: String, name: String = source.name, album: String = "Capturas"): Long {
        require(source.parentFile?.canonicalFile == pending.canonicalFile)
        require(source.length() > 0) { "Empty capture" }
        require(mime.startsWith("image/") || mime.startsWith("video/"))
        val destination = File(root, "${UUID.randomUUID()}.${extension(mime)}")
        check(source.renameTo(destination)) { "Could not save capture" }
        try {
            ensureAlbum(album)
            val db = helper.writableDatabase
            val id: Long
            db.beginTransaction()
            try {
                val next = db.rawQuery("SELECT COALESCE(MAX(sort_position), 0) + 1 FROM media", null).use {
                    it.moveToFirst(); it.getLong(0)
                }
                id = db.insertOrThrow("media", null, ContentValues().apply {
                    put("file", destination.name); put("name", name); put("mime", mime)
                    put("album", album); put("added", System.currentTimeMillis())
                    put("sort_position", next)
                })
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            notifyChanged()
            return id
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    fun list(): List<Media> = helper.readableDatabase.query("media", null, null, null, null, null, "sort_position DESC, id DESC").use { c ->
        val items = buildList {
            while (c.moveToNext()) {
                fun str(key: String) = c.getString(c.getColumnIndexOrThrow(key))
                fun number(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
                val file = File(root, str("file"))
                if (!file.isFile || file.length() == 0L) continue
                val mime = str("mime")
                val video = mime.startsWith("video/")
                var width = 0; var height = 0; var rotation = 0; var duration: Long? = null
                runCatching {
                    if (video) {
                        MediaMetadataRetriever().use { retriever ->
                            retriever.setDataSource(file.absolutePath)
                            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        }
                    } else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        width = options.outWidth; height = options.outHeight
                        rotation = ExifInterface(file).rotationDegrees
                    }
                }
                val album = str("album")
                add(Media(number("id"), uri(file), if (video) MediaType.VIDEO else MediaType.IMAGE,
                    mime, str("name"), albumId(album), album, Date(number("added")), Date(file.lastModified()),
                    width, height, rotation, file.length(), duration, number("favourite") == 1L, number("trashed") == 1L))
            }
        }
        var position = items.count { !it.isTrashed }
        items.map { if (it.isTrashed) it else it.copy(position = position--) }
    }

    /** Move one item within the active library, atomically shifting the intervening items. */
    fun reposition(id: Long, target: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val entries = db.rawQuery(
                "SELECT id, sort_position, file FROM media WHERE trashed=0 ORDER BY sort_position ASC, id ASC", null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val file = File(root, cursor.getString(2))
                        if (file.isFile && file.length() > 0) add(cursor.getLong(0) to cursor.getLong(1))
                    }
                }
            }
            require(target in 1..entries.size) { "Escolha uma posição de 1 a ${entries.size}." }
            val from = entries.indexOfFirst { it.first == id }
            require(from >= 0) { "Esta mídia não está mais na biblioteca." }
            val ids = entries.map { it.first }.toMutableList()
            ids.add(target - 1, ids.removeAt(from))
            for (index in minOf(from, target - 1)..maxOf(from, target - 1)) {
                db.execSQL("UPDATE media SET sort_position=? WHERE id=?", arrayOf(entries[index].second, ids[index]))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        notifyChanged()
    }

    private val covers get() = File(context.filesDir, "album_covers").also { check(it.isDirectory || it.mkdirs()) }

    private fun extension(mime: String): String = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: if (mime.startsWith("video/")) "mp4" else "jpg"

    /** Copies only explicitly selected content; never changes or deletes the source. */
    fun importUri(uri: Uri, album: String = "Importados"): Long {
        require(uri.scheme == "content") { "Selecione uma mídia pelo seletor do Android." }
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase() ?: error("Não foi possível identificar o formato.")
        require(mime.startsWith("image/") || mime.startsWith("video/")) { "Selecione uma foto ou vídeo." }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "import_${System.currentTimeMillis()}.${extension(mime)}"
        val temp = newCapture(mime.startsWith("video/"))
        return try {
            checkNotNull(resolver.openInputStream(uri)).use { input -> temp.outputStream().use { input.copyTo(it) } }
            commit(temp, mime, name, album)
        } finally { temp.delete() }
    }

    /** Pending capture paths are restored from SavedStateHandle after the camera returns. */
    fun pendingFile(name: String): File {
        require(name == File(name).name && name.startsWith("capture_"))
        return File(pending, name)
    }

    fun createAlbum(input: String, cover: Uri? = null): Long {
        val name = input.trim()
        require(name.isNotBlank() && name.length <= 100) { "Use um nome de 1 a 100 caracteres." }
        val exists = helper.readableDatabase.rawQuery("SELECT id FROM albums WHERE name = ? COLLATE NOCASE", arrayOf(name)).use { it.moveToFirst() }
        require(!exists) { "Já existe um álbum com esse nome." }
        var coverFile: File? = null
        try {
            if (cover != null) {
                require(cover.scheme == "content")
                val mime = context.contentResolver.getType(cover).orEmpty()
                require(mime.startsWith("image/")) { "A capa deve ser uma imagem." }
                coverFile = File(covers, "${UUID.randomUUID()}.${extension(mime)}")
                checkNotNull(context.contentResolver.openInputStream(cover)).use { input ->
                    coverFile.outputStream().use { input.copyTo(it) }
                }
                check(coverFile.length() > 0) { "Não foi possível copiar a capa." }
            }
            val id = helper.writableDatabase.insertOrThrow("albums", null, ContentValues().apply {
                put("name", name); put("cover", coverFile?.name)
            })
            notifyChanged()
            return id
        } catch (error: Exception) {
            coverFile?.delete()
            throw error
        }
    }

    /** Includes explicitly created albums even when they contain no media. */
    fun albums(): List<Album> {
        val items = list().filterNot { it.isTrashed }.groupBy { it.albumId }
        return helper.readableDatabase.query("albums", arrayOf("id", "name", "cover"), null, null, null, null, "id DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val media = items[id].orEmpty()
                    val cover = cursor.getString(2)?.let { File(covers, it) }?.takeIf { it.isFile }
                    add(Album(id, cursor.getString(1), cover?.let(::uri) ?: media.firstOrNull()?.uri,
                        media.size, cover == null && media.firstOrNull()?.type == MediaType.VIDEO))
                }
            }
        }
    }

    private fun ensureAlbum(name: String): Long {
        helper.writableDatabase.insertWithOnConflict("albums", null,
            ContentValues().apply { put("name", name) }, SQLiteDatabase.CONFLICT_IGNORE)
        return albumId(name)
    }
    private fun albumId(name: String): Long = helper.readableDatabase.query(
        "albums", arrayOf("id"), "name=?", arrayOf(name), null, null, null,
    ).use { check(it.moveToFirst()); it.getLong(0) }

    fun close() { helper.close() }
    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    fun file(id: Long): File? = helper.readableDatabase.query("media", arrayOf("file"), "id=?", arrayOf(id.toString()), null, null, null).use {
        if (it.moveToFirst()) File(root, it.getString(0)) else null
    }

    fun update(id: Long, column: String, value: String): Boolean {
        require(column in setOf("name", "album", "favourite", "trashed"))
        if (column == "album") ensureAlbum(value)
        val changed = helper.writableDatabase.update("media", ContentValues().apply { put(column, value) }, "id=?", arrayOf(id.toString())) > 0
        if (changed) notifyChanged()
        return changed
    }

    fun delete(id: Long): Boolean {
        val target = file(id) ?: return false
        if (target.exists() && !target.delete()) return false
        val deleted = helper.writableDatabase.delete("media", "id=?", arrayOf(id.toString())) > 0
        notifyChanged()
        return deleted
    }

    fun copy(media: Media, album: String): Boolean {
        val source = file(media.id) ?: return false
        val temp = newCapture(media.type == MediaType.VIDEO)
        return try {
            source.copyTo(temp, overwrite = true)
            commit(temp, media.mimeType, media.displayName ?: source.name, album)
            true
        } finally { temp.delete() }
    }
}
