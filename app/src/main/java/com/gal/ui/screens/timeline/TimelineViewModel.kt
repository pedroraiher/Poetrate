package com.gal.ui.screens.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gal.data.repository.MediaRepository
import com.gal.model.DateGroup
import com.gal.model.Media
import com.gal.model.MediaType
import com.gal.util.groupedByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder(val label: String) {
    DATE_ADDED_DESC("Newest first"),
    DATE_ADDED_ASC("Oldest first"),
    DATE_MODIFIED_DESC("Recently modified"),
    SIZE_DESC("Largest first"),
    NAME_ASC("Name A–Z"),
    POSITION("Posição (1 embaixo)"),
}

enum class FilterType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    companion object {
        val KEY_GRID    = intPreferencesKey("grid_columns")
        val KEY_SORT    = intPreferencesKey("sort_order_v2")
        val KEY_FILTER  = intPreferencesKey("filter_type")
    }

    private val prefs = dataStore.data

    val gridColumns = prefs
        .map { it[KEY_GRID] ?: 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val sortOrder = prefs
        .map { SortOrder.entries.getOrElse(it[KEY_SORT] ?: SortOrder.POSITION.ordinal) { SortOrder.POSITION } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortOrder.POSITION)

    val filterType = prefs
        .map { FilterType.entries.getOrElse(it[KEY_FILTER] ?: 0) { FilterType.ALL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterType.ALL)

    val media = combine(
        repository.timeline(),
        sortOrder,
        filterType,
    ) { list, sort, filter ->
        val filtered = when (filter) {
            FilterType.ALL    -> list
            FilterType.PHOTOS -> list.filter { it.type == MediaType.IMAGE }
            FilterType.VIDEOS -> list.filter { it.type == MediaType.VIDEO }
        }
        when (sort) {
            SortOrder.POSITION          -> filtered.sortedByDescending { it.position }
            SortOrder.DATE_ADDED_DESC    -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ADDED_ASC     -> filtered.sortedBy { it.dateAdded }
            SortOrder.DATE_MODIFIED_DESC -> filtered.sortedByDescending { it.dateModified }
            SortOrder.SIZE_DESC          -> filtered.sortedByDescending { it.sizeBytes }
            SortOrder.NAME_ASC           -> filtered.sortedBy { it.displayName?.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Same data as [media] but grouped by day for header rendering. */
    val sectioned: kotlinx.coroutines.flow.StateFlow<List<DateGroup>> = combine(media, sortOrder) { items, sort ->
            if (sort == SortOrder.POSITION) listOf(DateGroup("Ordem por posição", items))
            else items.groupedByDay()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSortOrder(sort: SortOrder) = viewModelScope.launch {
        dataStore.edit { it[KEY_SORT] = sort.ordinal }
    }

    fun setFilterType(filter: FilterType) = viewModelScope.launch {
        dataStore.edit { it[KEY_FILTER] = filter.ordinal }
    }
}
