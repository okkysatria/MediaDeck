package com.mediadeck.app.util.scan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
object ScannerStateManager {
    private val _isMediaActive = MutableStateFlow(value = false)
    val isMediaActive: StateFlow<Boolean> = _isMediaActive.asStateFlow()
    fun setMediaActive(active: Boolean) {
        _isMediaActive.value = active
    }
}
