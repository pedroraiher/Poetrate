package com.gal.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
    private val helper = object : SQLiteOpenHelper(context, "private_media.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
            db.execSQL("""CREATE TABLE media (
                id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL, mime TEXT NOT NULL, album TEXT NOT NULL,
                added INTEGER NOT NULL, favourite INTEGER NOT NULL DEFAULT 0,
                trashed INTEGER NOT NULL DEFAULT 0)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    fun newCapture(video: Boolean): File = File.createTempFile("capture_", if (video) ".mp4" else ".jpg", pending)

    /** Commit only completed captures/edits. Failed writes never appear in the library. */
    fun commit(source: File, mime: String, name: String = source.name, album: String = "Capturas"): Long {
        require(source.parentFile?.canonicalFile == pending.canonicalFile)
        require(source.length() > 0) { "Empty capture" }
        val destination = File(root, "${UUID.randomUUID()}.${if (mime.startsWith("video/")) "mp4" else "jpg"}")
        check(source.renameTo(destination)) { "Could not save capture" }
        try {
            ensureAlbum(album)
            val id = helper.writableDatabase.insertOrThrow("media", null, ContentValues().apply {
                put("file", destination.name); put("name", name); put("mime", mime)
                put("album", album); put("added", System.currentTimeMillis())
            })
            notifyChanged()
            return id
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    fun list(): List<Media> = helper.readableDatabase.query("media", null, null, null, null, null, "added DESC, id DESC").use { c ->
        buildList {
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
