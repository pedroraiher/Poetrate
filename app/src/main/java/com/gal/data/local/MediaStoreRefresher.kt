package com.gal.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes the private library only. Never scans public storage. */
@Singleton
class MediaStoreRefresher @Inject constructor() {
    private val counter = MutableStateFlow(0L)
    val tick = counter.asStateFlow()
    fun bump() { counter.update { it + 1 } }
}
