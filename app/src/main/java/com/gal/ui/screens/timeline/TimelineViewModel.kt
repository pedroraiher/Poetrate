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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder(val label: String) {
    DATE_ADDED_DESC("Mais recentes"),
    DATE_ADDED_ASC("Mais antigas"),
    DATE_MODIFIED_DESC("Modificadas recentemente"),
    SIZE_DESC("Maiores primeiro"),
    NAME_ASC("Nome A–Z"),
    POSITION("Posição (1 embaixo)"),
}

enum class FilterType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
}

enum class OrientationFilter(val label: String) { ALL("Todas as orientações"), HORIZONTAL("Horizontais"), VERTICAL("Verticais") }

internal fun matchesOrientation(media: Media, filter: OrientationFilter): Boolean {
    if (filter == OrientationFilter.ALL) return true
    if (media.width <= 0 || media.height <= 0) return false
    val rotated = ((media.orientation % 180) + 180) % 180 == 90
    val w = if (rotated) media.height else media.width
    val h = if (rotated) media.width else media.height
    return if (filter == OrientationFilter.HORIZONTAL) w >= h else h >= w
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    companion object {
        val KEY_ORIENTATION = intPreferencesKey("orientation_filter")
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

    val orientationFilter = prefs.map { OrientationFilter.entries.getOrElse(it[KEY_ORIENTATION] ?: 0) { OrientationFilter.ALL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrientationFilter.ALL)
    fun setOrientationFilter(value: OrientationFilter) = viewModelScope.launch { dataStore.edit { it[KEY_ORIENTATION] = value.ordinal } }

    val isLoading = MutableStateFlow(true)

    val media = combine(
        repository.timeline(),
        sortOrder,
        filterType,
        orientationFilter,
    ) { list, sort, filter, orientation ->
        val filtered = when (filter) {
            FilterType.ALL    -> list
            FilterType.PHOTOS -> list.filter { it.type == MediaType.IMAGE }
            FilterType.VIDEOS -> list.filter { it.type == MediaType.VIDEO }
        }
        val oriented = filtered.filter { matchesOrientation(it, orientation) }
        when (sort) {
            SortOrder.POSITION          -> oriented.sortedByDescending { it.position }
            SortOrder.DATE_ADDED_DESC    -> oriented.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ADDED_ASC     -> oriented.sortedBy { it.dateAdded }
            SortOrder.DATE_MODIFIED_DESC -> oriented.sortedByDescending { it.dateModified }
            SortOrder.SIZE_DESC          -> oriented.sortedByDescending { it.sizeBytes }
            SortOrder.NAME_ASC           -> oriented.sortedBy { it.displayName?.lowercase() }
        }
    }.flowOn(Dispatchers.Default)
        .onEach { isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Same data as [media] but grouped by day for header rendering. */
    val sectioned: kotlinx.coroutines.flow.StateFlow<List<DateGroup>> = combine(media, sortOrder) { items, sort ->
            if (sort == SortOrder.POSITION) listOf(DateGroup("", items))
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
