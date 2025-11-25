package ai.guard.native

/**
 * Thin JNI wrapper around the Whisper C++ bindings.
 *
 * nativeInit(modelPath: String): Boolean
 * nativeProcess(pcm: ShortArray, length: Int): String?
 * nativeRelease()
 *
 * We expose nicer Kotlin helpers: init(), process(), release().
 */
object WhisperBridge {

    init {
        System.loadLibrary("conguard_jni")
    }

    @JvmStatic
    external fun nativeInit(modelPath: String): Boolean

    // JNI signature in C++ is (short[], int) – we mirror that here
    @JvmStatic
    external fun nativeProcess(pcmData: ShortArray, length: Int): String?

    @JvmStatic
    external fun nativeRelease()

    fun init(modelPath: String): Boolean = nativeInit(modelPath)

    fun process(pcmData: ShortArray): String? =
        nativeProcess(pcmData, pcmData.size)

    fun release() = nativeRelease()
}
