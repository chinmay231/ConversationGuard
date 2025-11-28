package ai.guard2

import ai.guard2.logic.Light
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global application state holder using StateFlows for real-time UI updates.
 */
object AppState {
    // Microphone listening status
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Status message (e.g., "Listening...", "Stopped", error info, etc.)
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    // Last transcribed text
    private val _lastText = MutableStateFlow("")
    val lastText: StateFlow<String> = _lastText.asStateFlow()

    // Last computed toxicity score [0.0 - 1.0]
    private val _toxicity = MutableStateFlow(0f)
    val toxicity: StateFlow<Float> = _toxicity.asStateFlow()

    // Last computed aggression (prosody) score [0.0 - 1.0]
    private val _anger = MutableStateFlow(0f)
    val anger: StateFlow<Float> = _anger.asStateFlow()

    // Current light signal (Green/Yellow/Red)
    private val _light = MutableStateFlow(Light.GREEN)
    val light: StateFlow<Light> = _light.asStateFlow()

    // Error message (if any)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Update listening flag (true when recording audio). */
    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    /** Update status text (UI status message). */
    fun setStatus(text: String) {
        _status.value = text
    }

    /** Set an error message (and display it in the UI). */
    fun setError(message: String) {
        _error.value = message
    }

    /** Clear any existing error message. */
    fun clearError() {
        _error.value = null
    }

    /** Update state with results from analysis (transcription & scores). */
    fun updateFromAnalysis(transcript: String, toxicity: Float, anger: Float, light: Light) {
        _lastText.value = transcript
        _toxicity.value = toxicity.coerceIn(0f, 1f)
        _anger.value = anger.coerceIn(0f, 1f)
        _light.value = light
    }
}
