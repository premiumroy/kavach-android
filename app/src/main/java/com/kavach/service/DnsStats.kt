package com.kavach.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live counters so the UI can show what the filter is actually doing. */
object DnsStats {
    val blocked = MutableStateFlow(0)
    val forwarded = MutableStateFlow(0)
    val failed = MutableStateFlow(0)
    val upstream = MutableStateFlow<List<String>>(emptyList())

    fun reset() {
        blocked.value = 0
        forwarded.value = 0
        failed.value = 0
    }
}
