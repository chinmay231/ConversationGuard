package ai.guard2.native

import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File

object WhisperBridge {

    private const val TAG = "WhisperBridge"

    // From our local WhisperUtil companion object
    private const val TARGET_SAMPLE_RATE = WhisperUtil.WHISPER_SAMPLE_RATE // 16000
    private const val MEL_BANDS = WhisperUtil.WHISPER_N_MEL                // 80
    private const val TARGET_MEL_FRAMES = WhisperUtil.WHISPER_MEL_LEN      // 3000

    @Volatile
    private var interpreter: Interpreter? = null

    private val whisperUtil = WhisperUtil()

    @Volatile
    private var initialized = false

    /**
     * Initialize Whisper TFLite + filters/vocab.
     *
     * @param modelPath  absolute path to whisper-tiny-en.tflite in filesDir
     * @param vocabPath  absolute path to filters_vocab_en.bin in filesDir
     */
    @Synchronized
    fun init(modelPath: String, vocabPath: String): Boolean {
        if (initialized) {
            Log.d(TAG, "Whisper already initialized, skipping re-init")
            return true
        }

        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist: $modelPath")
                return false
            }

            val t0 = System.currentTimeMillis()

            // TFLite interpreter options
            val options = Interpreter.Options().apply {
                val cores = Runtime.getRuntime().availableProcessors()
                val threads = cores.coerceIn(1, 4)
                setNumThreads(threads)

                // Keep XNNPACK off for emulator stability; you can enable on real device if fine.
                try {
                    setUseXNNPACK(false)
                } catch (_: NoSuchMethodError) {
                    Log.w(TAG, "setUseXNNPACK not available on this TF Lite version")
                }
            }

            interpreter = Interpreter(modelFile, options)

            interpreter?.let { interp ->
                Log.d(TAG, "Whisper TFLite model loaded successfully")
                val inCount = interp.inputTensorCount
                val outCount = interp.outputTensorCount
                Log.d(TAG, "Model has $inCount input tensors:")
                for (i in 0 until inCount) {
                    val t = interp.getInputTensor(i)
                    Log.d(
                        TAG,
                        "  Input[$i]: name=${t.name()}, shape=${t.shape().contentToString()}, type=${t.dataType()}"
                    )
                }
                Log.d(TAG, "Model has $outCount output tensors:")
                for (i in 0 until outCount) {
                    val t = interp.getOutputTensor(i)
                    Log.d(
                        TAG,
                        "  Output[$i]: name=${t.name()}, shape=${t.shape().contentToString()}, type=${t.dataType()}"
                    )
                }
            }

            // Load filters + vocab using our local WhisperUtil
            val vocabOk = whisperUtil.loadFiltersAndVocab(
                multilingual = false,
                vocabPath = vocabPath
            )
            if (!vocabOk) {
                Log.e(TAG, "Failed to load filters/vocab from $vocabPath")
                interpreter?.close()
                interpreter = null
                initialized = false
                return false
            }

            val dt = System.currentTimeMillis() - t0
            Log.d(TAG, "Whisper init complete in ${dt} ms")
            initialized = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper", e)
            interpreter?.close()
            interpreter = null
            initialized = false
            return false
        }
    }

    @Synchronized
    fun release() {
        try {
            interpreter?.close()
        } catch (_: Exception) {
        } finally {
            interpreter = null
            initialized = false
        }
    }

    /**
     * End-to-end pipeline:
     *  1) short PCM16 -> float [-1,1]
     *  2) mel spectrogram via WhisperUtil
     *  3) pad/trim to [80, 3000]
     *  4) run TFLite
     *  5) decode tokens using vocab
     */
    @Synchronized
    fun process(pcmShort: ShortArray): String? {
        val interp = interpreter
        if (!initialized || interp == null) {
            Log.e(TAG, "Whisper not initialized, cannot process")
            return null
        }
        if (pcmShort.isEmpty()) {
            Log.w(TAG, "process called with empty PCM")
            return ""
        }

        val tStart = System.currentTimeMillis()

        // 1) PCM16 -> float [-1,1]
        val tPcm0 = System.currentTimeMillis()
        val nSamples = pcmShort.size
        val pcmFloat = FloatArray(nSamples)
        for (i in 0 until nSamples) {
            pcmFloat[i] = pcmShort[i] / 32768.0f
        }
        val tPcm1 = System.currentTimeMillis()
        Log.d(TAG, "PCM conversion: samples=$nSamples, dt=${tPcm1 - tPcm0} ms")

        // 2) Mel spectrogram using YOUR WhisperUtil implementation
        val tMel0 = System.currentTimeMillis()
        val melThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val melData = whisperUtil.getMelSpectrogram(
            samples = pcmFloat,
            nSamples = nSamples,
            nThreads = melThreads
        )
        val tMel1 = System.currentTimeMillis()

        val melFramesRaw = melData.size / MEL_BANDS
        Log.d(
            TAG,
            "Mel spectrogram: nMel=$MEL_BANDS, nLen=$melFramesRaw, total=${melData.size}, dt=${tMel1 - tMel0} ms"
        )

        if (melFramesRaw <= 0) {
            Log.e(TAG, "Mel spectrogram has no frames, aborting")
            return ""
        }

        // 3) Pad/trim mel to exactly 80 x 3000 (model expects [1, 80, 3000])
        val tPrep0 = System.currentTimeMillis()
        val targetFrames = TARGET_MEL_FRAMES
        val usedFrames = melFramesRaw.coerceAtMost(targetFrames)

        // Flattened buffer [mel * targetFrames + frame]
        val inputMel = FloatArray(MEL_BANDS * targetFrames)

        for (m in 0 until MEL_BANDS) {
            val inOffset = m * melFramesRaw
            val outOffset = m * targetFrames

            var f = 0
            while (f < usedFrames) {
                inputMel[outOffset + f] = melData[inOffset + f]
                f++
            }
            while (f < targetFrames) {
                inputMel[outOffset + f] = 0f
                f++
            }
        }
        val tPrep1 = System.currentTimeMillis()
        Log.d(
            TAG,
            "Mel pad/trim: usedFrames=$usedFrames, targetFrames=$targetFrames, dt=${tPrep1 - tPrep0} ms"
        )

        // 4) Prepare TFLite input/output as plain arrays (avoids ByteBuffer dtype issues)
        val tInfer0 = System.currentTimeMillis()

        val inShape = interp.getInputTensor(0).shape() // [1, 80, 3000]
        if (inShape.size != 3 || inShape[0] != 1 || inShape[1] != MEL_BANDS || inShape[2] != targetFrames) {
            Log.w(
                TAG,
                "Input shape mismatch: model=${inShape.contentToString()}, expected=[1,$MEL_BANDS,$targetFrames]"
            )
        }

        // Build [1, 80, 3000] float array from flattened mel
        val inputArray = Array(1) { Array(MEL_BANDS) { FloatArray(targetFrames) } }
        for (m in 0 until MEL_BANDS) {
            for (t in 0 until targetFrames) {
                inputArray[0][m][t] = inputMel[m * targetFrames + t]
            }
        }

        val outShape = interp.getOutputTensor(0).shape() // e.g. [1, 448]
        if (outShape.size != 2 || outShape[0] != 1) {
            Log.w(TAG, "Unexpected output shape: ${outShape.contentToString()}")
        }
        val outLen = if (outShape.size >= 2) outShape[1] else 0
        val outputArray = Array(1) { IntArray(outLen) }

        // 5) Run TFLite
        try {
            interp.run(inputArray, outputArray)
        } catch (e: Exception) {
            Log.e(TAG, "Whisper inference error", e)
            return ""
        }
        val tInfer1 = System.currentTimeMillis()

        val tokens = outputArray[0]

        // 6) Decode tokens with your vocab
        val tDec0 = System.currentTimeMillis()
        val sb = StringBuilder()
        for (token in tokens) {
            if (token == whisperUtil.tokenEOT) break

            if (token < whisperUtil.tokenEOT) {
                val word = whisperUtil.getWordFromToken(token)
                if (word != null) {
                    Log.d(TAG, "Adding token: $token, word: $word")
                    sb.append(word)
                }
            } else {
                // Skip special/extra tokens, just log
                val word = whisperUtil.getWordFromToken(token)
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }
        val tDec1 = System.currentTimeMillis()

        val tEnd = System.currentTimeMillis()
        Log.d(
            TAG,
            "Whisper timings: total=${tEnd - tStart} ms, pcm=${tPcm1 - tPcm0} ms, " +
                    "mel=${tMel1 - tMel0} ms, prep=${tPrep1 - tPrep0} ms, " +
                    "infer=${tInfer1 - tInfer0} ms, decode=${tDec1 - tDec0} ms"
        )

        return sb.toString()
    }
}
