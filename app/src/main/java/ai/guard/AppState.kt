package ai.guard

import ai.guard.logic.Light
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppState {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _status = MutableStateFlow("Tap the microphone to start listening")
    val status: StateFlow<String> = _status

    private val _lastText = MutableStateFlow("")
    val lastText: StateFlow<String> = _lastText

    private val _toxicity = MutableStateFlow(0f)
    val toxicity: StateFlow<Float> = _toxicity

    private val _anger = MutableStateFlow(0f)
    val anger: StateFlow<Float> = _anger

    private val _light = MutableStateFlow(Light.GREEN)
    val light: StateFlow<Light> = _light

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setListening(value: Boolean) {
        _isListening.value = value
    }

    fun setStatus(value: String) {
        _status.value = value
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    fun updateFromAnalysis(
        transcript: String,
        toxicity: Float,
        anger: Float,
        light: Light
    ) {
        _lastText.value = transcript
        _toxicity.value = toxicity.coerceIn(0f, 1f)
        _anger.value = anger.coerceIn(0f, 1f)
        _light.value = light
    }
}
