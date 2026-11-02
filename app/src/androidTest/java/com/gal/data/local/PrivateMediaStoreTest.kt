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

    @Test fun captureIsPrivateAndSurvivesReopening() {
        val before = store.list().map { it.id }.toSet()
        val pending = store.newCapture(false)
        assertEquals(before, store.list().map { it.id }.toSet())
        pending.delete()
        val id = capture()
        val file = checkNotNull(store.file(id))
        assertEquals(context.filesDir.resolve("media").canonicalFile, file.parentFile.canonicalFile)
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

    @Test fun manifestDoesNotRequestPublicMediaOrNetworkAccess() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse("android.permission.READ_MEDIA_IMAGES" in permissions)
        assertFalse("android.permission.READ_MEDIA_VIDEO" in permissions)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in permissions)
        assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in permissions)
        assertFalse("android.permission.INTERNET" in permissions)
    }
}
