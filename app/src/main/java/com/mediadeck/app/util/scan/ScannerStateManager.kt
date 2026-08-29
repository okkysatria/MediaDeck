package com.mediadeck.app.util.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScannerStateManager {
    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress.asStateFlow()

    private val _isScanPaused = MutableStateFlow(value = false)
    val isScanPaused: StateFlow<Boolean> = _isScanPaused.asStateFlow()

    private val _isScanActive = MutableStateFlow(value = false)
    val isScanActive: StateFlow<Boolean> = _isScanActive.asStateFlow()

    private val _isManualRefreshing = MutableStateFlow(value = false)
    val isManualRefreshing: StateFlow<Boolean> = _isManualRefreshing.asStateFlow()

    private val _isMediaActive = MutableStateFlow(value = false)
    val isMediaActive: StateFlow<Boolean> = _isMediaActive.asStateFlow()

    fun updateProgress(progress: String?) {
        _scanProgress.value = progress
    }

    fun setScanActive(active: Boolean) {
        _isScanActive.value = active
    }

    fun setScanPaused(paused: Boolean) {
        _isScanPaused.value = paused
    }

    fun setManualRefreshing(refreshing: Boolean) {
        _isManualRefreshing.value = refreshing
    }

    fun setMediaActive(active: Boolean) {
        _isMediaActive.value = active
    }
}
