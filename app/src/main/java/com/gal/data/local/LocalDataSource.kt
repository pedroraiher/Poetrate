package com.gal.data.local

import com.gal.model.Album
import com.gal.model.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataSource @Inject constructor(
    private val store: PrivateMediaStore,
    private val refresher: MediaStoreRefresher,
) {
    private fun all(): Flow<List<Media>> = combine(PrivateMediaStore.changes, refresher.tick) { _, _ -> store.list() }.flowOn(Dispatchers.IO)
    fun timeline() = all().map { list -> list.filterNot { it.isTrashed } }
    fun favourites() = timeline().map { list -> list.filter { it.isFavourite } }
    fun trash() = all().map { list -> list.filter { it.isTrashed } }
    fun album(albumId: Long) = timeline().map { list -> list.filter { it.albumId == albumId } }
    fun albums(): Flow<List<Album>> = combine(PrivateMediaStore.changes, refresher.tick) { _, _ -> store.albums() }.flowOn(Dispatchers.IO)
    fun search(query: String) = timeline().map { list ->
        if (query.isBlank()) emptyList() else list.filter {
            it.displayName.orEmpty().contains(query, ignoreCase = true) || it.albumName.contains(query, ignoreCase = true)
        }
    }
}
