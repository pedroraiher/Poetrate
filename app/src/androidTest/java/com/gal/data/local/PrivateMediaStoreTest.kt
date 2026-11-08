package com.gal.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gal.data.transfer.PrivateAlbumTransferSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateMediaStoreTest {
    private lateinit var context: Context
    private lateinit var store: PrivateMediaStore
    private val created = mutableListOf<Long>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = PrivateMediaStore(context)
    }
    @After fun cleanup() {
        created.forEach { store.delete(it) }
        store.close()
    }
    private fun capture(): Long {
        val file = store.newCapture(false)
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        bitmap.recycle()
        return store.commit(file, "image/jpeg", "test.jpg").also { created.add(it) }
    }

    @Test fun versionTwoMigrationPreservesFilesDatesAndBottomToTopOrder() {
        val files = List(3) { store.file(capture())!! }
        val databaseName = "migration_${System.nanoTime()}.db"
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getDatabasePath(name: String) = context.getDatabasePath(databaseName)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                context.openOrCreateDatabase(databaseName, mode, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?) =
                context.openOrCreateDatabase(databaseName, mode, factory, handler)
        }
        context.openOrCreateDatabase(databaseName, 0, null).use { db ->
            db.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, cover TEXT)")
            db.execSQL("INSERT INTO albums (name) VALUES ('Legacy')")
            db.execSQL("CREATE TABLE media (id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL UNIQUE, name TEXT NOT NULL, mime TEXT NOT NULL, album TEXT NOT NULL, added INTEGER NOT NULL, favourite INTEGER NOT NULL DEFAULT 0, trashed INTEGER NOT NULL DEFAULT 0)")
            files.forEachIndexed { i, file ->
                db.execSQL("INSERT INTO media (file,name,mime,album,added) VALUES (?,?, 'image/jpeg','Legacy',?)", arrayOf(file.name, "legacy$i.jpg", if (i == 1) 100L else 300L))
            }
            db.version = 2
        }
        val migrated = PrivateMediaStore(isolated)
        try {
            val items = migrated.list()
            assertEquals(listOf("legacy2.jpg", "legacy0.jpg", "legacy1.jpg"), items.map { it.displayName })
            assertEquals(listOf(3, 2, 1), items.map { it.position })
            assertEquals(listOf(300L, 300L, 100L), items.map { it.dateAdded.time })
            assertTrue(files.all { it.exists() && it.length() > 0 })
            migrated.reposition(items.first().id, 1)
            assertEquals("legacy2.jpg", migrated.list().last().displayName)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun repositionShiftsRangeAndSurvivesReopening() {
        val base = store.list().count { !it.isTrashed }
        val ids = List(5) { capture() }
        val original = store.list().filter { it.id in ids }.associateBy { it.id }
        fun order() = store.list().filter { it.id in ids }.sortedBy { it.position }.map { it.id }
        store.reposition(ids[4], base + 2)
        assertEquals(listOf(ids[0], ids[4], ids[1], ids[2], ids[3]), order())
        store.reposition(ids[4], base + 5)
        assertEquals(ids, order())
        store.reposition(ids[3], base + 1)
        store.close()
        store = PrivateMediaStore(context)
        assertEquals(listOf(ids[3], ids[0], ids[1], ids[2], ids[4]), order())
        store.list().filter { it.id in ids }.forEach {
            assertEquals(original.getValue(it.id).dateAdded, it.dateAdded)
            assertEquals(original.getValue(it.id).uri, it.uri)
        }
        assertEquals((1..(base + 5)).toList(), store.list().filterNot { it.isTrashed }.map { it.position }.reversed())
    }

    @Test fun repositionRejectsInvalidInputWithoutPartialChanges() {
        val id = capture()
        val before = store.list().map { it.id to it.position }
        for (invalid in listOf(0, -1, Int.MAX_VALUE)) {
            assertTrue(runCatching { store.reposition(id, invalid) }.isFailure)
            assertEquals(before, store.list().map { it.id to it.position })
        }
        assertTrue(runCatching { store.reposition(-1, 1) }.isFailure)
        store.reposition(id, store.list().single { it.id == id }.position!!)
        assertEquals(before, store.list().map { it.id to it.position })
    }

    @Test fun positionsStayContiguousAfterTrashRestoreDeleteAndImport() {
        val ids = List(3) { capture() }
        store.update(ids[1], "trashed", "1")
        assertNull(store.list().single { it.id == ids[1] }.position)
        assertTrue(runCatching { store.reposition(ids[1], 1) }.isFailure)
        store.reposition(ids[2], 1)
        store.update(ids[1], "trashed", "0")
        store.delete(ids[0])
        val newest = capture()
        val active = store.list().filterNot { it.isTrashed }
        assertEquals(newest, active.first().id)
        assertEquals((1..active.size).toList(), active.map { it.position }.reversed())
    }

    @Test fun captureIsPrivateAndSurvivesReopening() {
        val before = store.list().map { it.id }.toSet()
        val pending = store.newCapture(false)
        assertEquals(before, store.list().map { it.id }.toSet())
        pending.delete()
        val id = capture()
        val file = checkNotNull(store.file(id))
        assertEquals(context.filesDir.resolve("media").canonicalFile, checkNotNull(file.parentFile).canonicalFile)
        store.close()
        store = PrivateMediaStore(context)
        val item = store.list().single { it.id == id }
        assertEquals("${context.packageName}.fileprovider", item.uri.authority)
        assertEquals(4, item.width)
        assertEquals(3, item.height)
        context.contentResolver.openInputStream(item.uri).use { assertNotNull(it) }
    }

    @Test fun albumMovePreservesFileAndIdAndTrashExcludesTransfers() = runBlocking {
        val id = capture()
        val original = checkNotNull(store.file(id))
        store.update(id, "album", "Test album $id")
        val media = store.list().single { it.id == id }
        assertEquals(original, store.file(id))
        store.update(id, "name", "renamed.jpg")
        assertEquals(media.albumId, store.list().single { it.id == id }.albumId)
        val source = PrivateAlbumTransferSource(store)
        val snapshot = source.snapshot(media.albumId).single()
        source.open(snapshot).use { assertTrue(it.read() >= 0) }
        store.update(id, "favourite", "1")
        assertTrue(store.list().single { it.id == id }.isFavourite)
        store.update(id, "trashed", "1")
        assertTrue(source.snapshot(media.albumId).isEmpty())
        assertTrue(runCatching { source.open(snapshot).close() }.isFailure)
        store.update(id, "trashed", "0")
        assertEquals(1, source.snapshot(media.albumId).size)
    }

    @Test fun copyAndDeleteLeaveOriginalUntouched() {
        val id = capture()
        val original = store.list().single { it.id == id }
        val album = "Copy test $id"
        assertTrue(store.copy(original, album))
        val copied = store.list().single { it.albumName == album }
        created.add(copied.id)
        assertNotEquals(original.id, copied.id)
        assertArrayEquals(store.file(id)!!.readBytes(), store.file(copied.id)!!.readBytes())
        assertTrue(store.delete(copied.id))
        created.remove(copied.id)
        assertTrue(store.file(id)!!.exists())
    }

    @Test fun emptyAlbumAndIndependentCoverPersist() {
        val sourceId = capture()
        val source = store.list().single { it.id == sourceId }
        val name = "Empty album ${System.nanoTime()}"
        val albumId = store.createAlbum(name, source.uri)
        assertTrue(runCatching { store.createAlbum(name) }.isFailure)
        val before = store.albums().single { it.id == albumId }
        assertEquals(0, before.mediaCount)
        assertNotNull(before.coverUri)
        assertNotEquals(source.uri, before.coverUri)
        store.delete(sourceId)
        created.remove(sourceId)
        store.close()
        store = PrivateMediaStore(context)
        val after = store.albums().single { it.id == albumId }
        assertEquals(before, after)
        context.contentResolver.openInputStream(checkNotNull(after.coverUri)).use { assertTrue(checkNotNull(it).read() >= 0) }
    }

    @Test fun pngImportPreservesFormatAndSourceBytes() {
        val original = context.filesDir.resolve("media/import-test-${System.nanoTime()}.png")
        original.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(7, 5, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val bytes = original.readBytes()
            val id = store.importUri(store.uri(original)).also { created.add(it) }
            val item = store.list().single { it.id == id }
            assertEquals("image/png", item.mimeType)
            assertEquals("png", store.file(id)!!.extension)
            assertArrayEquals(bytes, original.readBytes())
            assertArrayEquals(bytes, store.file(id)!!.readBytes())
            assertEquals(7, item.width)
            assertEquals(5, item.height)
        } finally { original.delete() }
    }

    @Test fun manifestOnlyAddsNetworkForExplicitLocalSharing() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse("android.permission.READ_MEDIA_IMAGES" in permissions)
        assertFalse("android.permission.READ_MEDIA_VIDEO" in permissions)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in permissions)
        assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in permissions)
        assertTrue("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.CAMERA" in permissions)
        assertFalse("android.permission.RECORD_AUDIO" in permissions)
    }
}
