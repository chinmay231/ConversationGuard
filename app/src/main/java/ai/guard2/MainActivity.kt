package ai.guard2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ai.guard2.logic.LightDecider
import ai.guard2.logic.ToxicityHeuristics
import ai.guard2.native.WhisperBridge
import ai.guard2.ui.ConversationGuardScreen
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    // This flag is read/written from multiple threads (UI + audio thread).
    // Without @Volatile the audio loop may never see updates and never stop.
    @Volatile
    private var isRecording: Boolean = false

    private var audioRecord: AudioRecord? = null

    private val lightDecider = LightDecider()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                AppState.setError("Microphone permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Copy model + vocab from assets to internal storage and init whisper
        val modelPath = prepareModelFile()
        Log.d("ConversationGuard", "Model at: $modelPath")

        // New: derive vocab path (already copied by prepareModelFile)
        val vocabPath = File(filesDir, "filters_vocab_en.bin").absolutePath

        val ok = WhisperBridge.init(modelPath, vocabPath)
        if (!ok) {
            Log.e("ConversationGuard", "Failed to initialize Whisper model")
            AppState.setError("Failed to initialize speech model.")
        }

        fun logHardwareInfo(tag: String = "ConversationGuard") {
            val cores = Runtime.getRuntime().availableProcessors()
            Log.d(tag, "availableProcessors = $cores")
            Log.d(tag, "HARDWARE = ${Build.HARDWARE}")
            Log.d(tag, "BOARD = ${Build.BOARD}")
            Log.d(tag, "DEVICE = ${Build.DEVICE}, MODEL = ${Build.MODEL}")
        }

        logHardwareInfo()

        setContent {
            ConversationGuardScreen(
                onToggleListening = {
                    if (isRecording) {
                        stopListening()
                    } else {
                        checkPermissionAndStart()
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        WhisperBridge.release()
    }

    // --------------------------------------------------------------------
    // PERMISSION + ENTRY POINT
    // --------------------------------------------------------------------
    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // --------------------------------------------------------------------
    // AUDIO + WHISPER PIPELINE
    // --------------------------------------------------------------------
    @SuppressLint("MissingPermission") // we gate this via checkPermissionAndStart()
    private fun startListening() {
        if (isRecording) return

        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            AppState.setError("AudioRecord init failed (buffer size error).")
            return
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            AppState.setError("AudioRecord not initialized.")
            recorder.release()
            return
        }

        audioRecord = recorder

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            Log.e("ConversationGuard", "startRecording failed", e)
            AppState.setError("Failed to start microphone: ${e.message}")
            recorder.release()
            audioRecord = null
            return
        }

        isRecording = true
        AppState.setListening(true)
        AppState.setStatus("Listening...")

        Thread {
            Log.d("ConversationGuard", "Audio loop started")
            try {
                val buffer = ShortArray(minBuffer)
                val collected = ArrayList<Short>()

                var peak = 0
                var sumSq = 0.0
                var count = 0L

                // Loop condition reads @Volatile isRecording, so it sees updates from UI thread.
                while (isRecording) {
                    val ar = audioRecord ?: break

                    val read = ar.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val s = buffer[i]
                            collected.add(s)

                            val v = s.toInt()
                            val av = abs(v)
                            if (av > peak) peak = av
                            sumSq += v.toDouble() * v.toDouble()
                            count++
                        }
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        Log.e("ConversationGuard", "AudioRecord.read error: $read")
                        break
                    }
                }

                if (collected.isEmpty()) {
                    Log.w("ConversationGuard", "No audio captured in session")
                    AppState.setStatus("No audio captured.")
                    return@Thread
                }

                val pcm = collected.toShortArray()

                Log.d(
                    "ConversationGuard",
                    "Captured ${pcm.size} samples, peak=$peak, count=$count"
                )

                // Run Whisper once the recording is done
                val transcript = try {
                    WhisperBridge.process(pcm) ?: ""
                } catch (e: UnsatisfiedLinkError) {
                    Log.e("ConversationGuard", "JNI error while calling WhisperBridge", e)
                    AppState.setError("Speech model JNI error: ${e.message}")
                    ""
                } catch (e: Exception) {
                    Log.e("ConversationGuard", "Error during transcription", e)
                    AppState.setError("Transcription error: ${e.message}")
                    ""
                }

                val rms = if (count > 0) sqrt(sumSq / count) else 0.0

                val toxicity = ToxicityHeuristics.combinedScore(transcript, peak, rms)
                val aggression = ToxicityHeuristics.prosodyScore(peak, rms)
                val light = lightDecider.decide(toxicity, aggression)

                AppState.updateFromAnalysis(
                    transcript = transcript,
                    toxicity = toxicity,
                    anger = aggression,
                    light = light
                )

                AppState.setStatus("Listening complete")
                Log.d(
                    "ConversationGuard",
                    "Transcript='$transcript', tox=$toxicity, agg=$aggression, light=$light"
                )
            } catch (e: Exception) {
                Log.e("ConversationGuard", "Audio loop error", e)
                AppState.setError("Audio error: ${e.message}")
            } finally {
                // Make sure UI reflects that we're no longer recording
                isRecording = false
                AppState.setListening(false)
                Log.d("ConversationGuard", "Audio loop finished")
            }
        }.start()
    }

    private fun stopListening() {
        if (!isRecording && audioRecord == null) {
            return
        }

        Log.d("ConversationGuard", "stopListening called")
        isRecording = false

        // Stop and release the AudioRecord on this thread.
        audioRecord?.let { ar ->
            try {
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    ar.stop()
                }
            } catch (e: Exception) {
                Log.w("ConversationGuard", "AudioRecord.stop() failed", e)
            } finally {
                ar.release()
            }
        }
        audioRecord = null

        AppState.setListening(false)
        AppState.setStatus("Stopped")
    }

    // --------------------------------------------------------------------
    // MODEL PREPARATION FOR TFLITE + FILTER/VOCAB
    // Copies both whisper-tiny-en.tflite and filters_vocab_en.bin
    // from assets/models/ into internal storage (filesDir).
    // Returns the full path to the .tflite model.
    // --------------------------------------------------------------------
    private fun prepareModelFile(): String {
        // 1) Copy the TFLite model
        val modelFile = copyAssetToFilesDir(
            assetPath = "models/whisper-tiny-en.tflite",
            outFileName = "whisper-tiny-en.tflite"
        )

        // 2) Copy the vocab/filters file so WhisperBridge can find it
        copyAssetToFilesDir(
            assetPath = "models/filters_vocab_en.bin",
            outFileName = "filters_vocab_en.bin"
        )

        // We now also derive vocabPath in onCreate; here we just ensure it exists.
        return modelFile.absolutePath
    }

    /**
     * Helper: copy an asset (e.g. "models/whisper-tiny-en.tflite")
     * into filesDir with a given output name, and return the File.
     */
    private fun copyAssetToFilesDir(assetPath: String, outFileName: String): File {
        val outFile = File(filesDir, outFileName)
        if (!outFile.exists()) {
            outFile.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("ConversationGuard", "Copied asset $assetPath -> ${outFile.absolutePath}")
        } else {
            Log.d("ConversationGuard", "Asset already present: ${outFile.absolutePath}")
        }
        return outFile
    }
}
