package ai.guard2.native

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.PI

/**
 * Utility for:
 *  - Loading filters + vocabulary from filters_vocab_en.bin
 *  - Computing log-mel spectrogram (Whisper-style)
 */
class WhisperUtil {

    private val vocab = WhisperVocab()
    private val filters = WhisperFilter()
    private val mel = WhisperMel()

    val tokenTranslate: Int get() = vocab.tokenTRANSLATE
    val tokenTranscribe: Int get() = vocab.tokenTRANSCRIBE
    val tokenEOT: Int get() = vocab.tokenEOT
    val tokenSOT: Int get() = vocab.tokenSOT
    val tokenPREV: Int get() = vocab.tokenPREV
    val tokenSOLM: Int get() = vocab.tokenSOLM
    val tokenNOT: Int get() = vocab.tokenNOT
    val tokenBEG: Int get() = vocab.tokenBEG

    fun getFilters(): FloatArray = filters.data

    fun getWordFromToken(token: Int): String? = vocab.tokenToWord[token]

    /**
     * Load filters + vocab from pre-generated filters_vocab_en.bin
     */
    @Throws(IOException::class)
    fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String): Boolean {
        val bytes = Files.readAllBytes(Paths.get(vocabPath))
        val vocabBuf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        Log.d(TAG, "Vocab file size: ${vocabBuf.limit()}")

        val magic = vocabBuf.int
        if (magic != 0x5553454e) {
            Log.e(TAG, "Invalid vocab file (bad magic: $magic), $vocabPath")
            return false
        }

        // mel filters
        filters.nMel = vocabBuf.int
        filters.nFft = vocabBuf.int
        Log.d(TAG, "n_mel=${filters.nMel}, n_fft=${filters.nFft}")

        val filterDataBytes = ByteArray(filters.nMel * filters.nFft * 4)
        vocabBuf.get(filterDataBytes)
        val filterBuf = ByteBuffer.wrap(filterDataBytes).order(ByteOrder.nativeOrder())
        filters.data = FloatArray(filters.nMel * filters.nFft)
        var idx = 0
        while (filterBuf.hasRemaining()) {
            filters.data[idx++] = filterBuf.float
        }

        // base vocabulary
        val nVocab = vocabBuf.int
        Log.d(TAG, "nVocab: $nVocab")
        for (i in 0 until nVocab) {
            val len = vocabBuf.int
            val wordBytes = ByteArray(len)
            vocabBuf.get(wordBytes)
            val word = String(wordBytes)
            vocab.tokenToWord[i] = word
        }

        // extended tokens
        val nVocabAdditional = if (!multilingual) {
            vocab.nVocabEnglish
        } else {
            vocab.nVocabMultilingual.also {
                vocab.tokenEOT++
                vocab.tokenSOT++
                vocab.tokenPREV++
                vocab.tokenSOLM++
                vocab.tokenNOT++
                vocab.tokenBEG++
            }
        }

        for (i in nVocab until nVocabAdditional) {
            val w = when {
                i > vocab.tokenBEG -> "[_TT_${i - vocab.tokenBEG}]"
                i == vocab.tokenEOT -> "[_EOT_]"
                i == vocab.tokenSOT -> "[_SOT_]"
                i == vocab.tokenPREV -> "[_PREV_]"
                i == vocab.tokenNOT -> "[_NOT_]"
                i == vocab.tokenBEG -> "[_BEG_]"
                else -> "[_extra_token_$i]"
            }
            vocab.tokenToWord[i] = w
        }

        Log.d(TAG, "Filters + vocab loaded successfully")
        return true
    }

    /**
     * Compute Whisper-style log-mel spectrogram:
     *  - samples: length == nSamples (480k)
     *  - returns: FloatArray of size nMel * nLen (80 * 3000), stored as [m * nLen + t]
     */
    fun getMelSpectrogram(
        samples: FloatArray,
        nSamples: Int,
        nThreads: Int
    ): FloatArray {
        val fftSize = WHISPER_N_FFT
        val fftStep = WHISPER_HOP_LENGTH

        mel.nMel = WHISPER_N_MEL
        mel.nLen = nSamples / fftStep          // = 3000 for 480k / 160
        mel.data = FloatArray(mel.nMel * mel.nLen)

        val hann = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            hann[i] = (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
        }
        val nFft = 1 + fftSize / 2

        val threads = mutableListOf<Thread>()
        for (iw in 0 until nThreads) {
            val th = Thread {
                val fftIn = FloatArray(fftSize)
                val fftOut = FloatArray(fftSize * 2)

                var frame = iw
                while (frame < mel.nLen) {
                    val offset = frame * fftStep

                    // windowing
                    var j = 0
                    while (j < fftSize) {
                        fftIn[j] = if (offset + j < nSamples) {
                            hann[j] * samples[offset + j]
                        } else {
                            0.0f
                        }
                        j++
                    }

                    // FFT -> mag^2 (packed in-place)
                    fft(fftIn, fftOut)
                    // mag^2 per bin
                    j = 0
                    while (j < fftSize) {
                        val re = fftOut[2 * j]
                        val im = fftOut[2 * j + 1]
                        fftOut[j] = re * re + im * im
                        j++
                    }
                    // fold symmetric bins
                    j = 1
                    while (j < fftSize / 2) {
                        fftOut[j] += fftOut[fftSize - j]
                        j++
                    }

                    // mel filtering
                    for (m in 0 until mel.nMel) {
                        var sum = 0.0
                        val filterBase = m * nFft
                        for (k in 0 until nFft) {
                            sum += (fftOut[k] * filters.data[filterBase + k]).toDouble()
                        }
                        if (sum < 1e-10) sum = 1e-10
                        sum = log10(sum)
                        mel.data[m * mel.nLen + frame] = sum.toFloat()
                    }

                    frame += nThreads
                }
            }
            threads += th
            th.start()
        }

        threads.forEach {
            try {
                it.join()
            } catch (e: InterruptedException) {
                Log.e(TAG, "Mel thread interrupted", e)
            }
        }

        // clamping + normalization: same as original
        var mmax = -1e20
        for (i in mel.data.indices) {
            if (mel.data[i] > mmax) mmax = mel.data[i].toDouble()
        }
        mmax -= 8.0
        for (i in mel.data.indices) {
            if (mel.data[i] < mmax) mel.data[i] = mmax.toFloat()
            mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
        }

        return mel.data
    }

    // Cooley–Tukey FFT (float real input, complex output packed as [re0,im0,re1,im1,...])
    private fun fft(input: FloatArray, output: FloatArray) {
        val N = input.size
        if (N == 1) {
            output[0] = input[0]
            output[1] = 0f
            return
        }
        if (N % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(N / 2)
        val odd = FloatArray(N / 2)
        for (i in 0 until N) {
            if (i % 2 == 0) even[i / 2] = input[i] else odd[i / 2] = input[i]
        }

        val evenFft = FloatArray(N)
        val oddFft = FloatArray(N)
        fft(even, evenFft)
        fft(odd, oddFft)

        for (k in 0 until N / 2) {
            val theta = (2 * PI * k / N).toFloat()
            val re = cos(theta)
            val im = -kotlin.math.sin(theta)

            val reOdd = oddFft[2 * k]
            val imOdd = oddFft[2 * k + 1]

            // x[k]
            output[2 * k] = evenFft[2 * k] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd

            // x[k + N/2]
            output[2 * (k + N / 2)] = evenFft[2 * k] - re * reOdd + im * imOdd
            output[2 * (k + N / 2) + 1] =
                evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }

    private fun dft(input: FloatArray, out: FloatArray) {
        val N = input.size
        for (k in 0 until N) {
            var re = 0f
            var im = 0f
            for (n in 0 until N) {
                val angle = (2 * PI * k * n / N).toFloat()
                val c = cos(angle)
                val s = kotlin.math.sin(angle)
                re += input[n] * c
                im -= input[n] * s
            }
            out[2 * k] = re
            out[2 * k + 1] = im
        }
    }

    // Internal data holders
    private class WhisperVocab {
        // Token types
        var tokenEOT = 50256
        var tokenSOT = 50257
        var tokenPREV = 50360
        var tokenSOLM = 50361
        var tokenNOT = 50362
        var tokenBEG = 50363

        // Available tasks
        val tokenTRANSLATE = 50358
        val tokenTRANSCRIBE = 50359

        // Vocab sizes
        val nVocabEnglish = 51864
        val nVocabMultilingual = 51865

        val tokenToWord: MutableMap<Int, String> = HashMap()
    }

    private class WhisperFilter {
        var nMel: Int = 0
        var nFft: Int = 0
        lateinit var data: FloatArray
    }

    private class WhisperMel {
        var nLen: Int = 0
        var nMel: Int = 0
        lateinit var data: FloatArray
    }

    companion object {
        private const val TAG = "WhisperUtil"

        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SIZE = 30
        const val WHISPER_MEL_LEN = 3000
    }
}
