package com.gal.data.transfer

import com.gal.data.local.PrivateMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Provider-neutral boundary for future, explicitly requested album uploads.
 * No credentials, network clients or public storage paths escape the local repository.
 */
data class AlbumTransferItem(
    val mediaId: Long,
    val albumId: Long,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

interface AlbumTransferSource {
    suspend fun snapshot(albumId: Long): List<AlbumTransferItem>
    /** The caller must close the stream. Re-checks membership and trash before opening. */
    suspend fun open(item: AlbumTransferItem): InputStream
}

@Singleton
class PrivateAlbumTransferSource @Inject constructor(private val store: PrivateMediaStore) : AlbumTransferSource {
    override suspend fun snapshot(albumId: Long) = withContext(Dispatchers.IO) {
        store.list().filter { it.albumId == albumId && !it.isTrashed }.map {
            AlbumTransferItem(it.id, it.albumId, it.displayName ?: "${it.id}", it.mimeType,
                it.sizeBytes, it.dateModified.time)
        }
    }
    override suspend fun open(item: AlbumTransferItem): InputStream = withContext(Dispatchers.IO) {
        val current = store.list().firstOrNull { it.id == item.mediaId && it.albumId == item.albumId && !it.isTrashed }
            ?: error("Media was removed from this album")
        check(current.sizeBytes == item.sizeBytes && current.dateModified.time == item.modifiedAtMillis) {
            "Media changed; create a new transfer snapshot"
        }
        checkNotNull(store.file(item.mediaId)).inputStream()
    }
}
