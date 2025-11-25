package ai.guard.logic

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Very lightweight, deterministic toxicity heuristics designed to be
 * runnable entirely on-device, without any cloud model.
 *
 * - "lexical" score: based on explicit profanity / identity phrases
 * - "prosody" score: based on short-term peak + RMS loudness
 * - "combined" score: weighted fusion of both signals (0..1)
 */
object ToxicityHeuristics {

    // Extremely small example lexicon, you can expand this.
    private val profanityTerms = listOf(
        "fuck", "fucking", "stupid", "bitch", "bastard", "asshole", "dick"
    )

    private val threatPhrases = listOf(
        "kill you", "beat you", "hurt you", "smack you", "punch you"
    )

    private val identityPhrases = listOf(
        "you people", "your kind"
    )

    /**
     * Lexical toxicity score in [0,1].
     * Counts profanities / threats and saturates.
     */
    fun lexicalScore(text: String): Float {
        if (text.isBlank()) return 0f

        val lower = text.lowercase()
        var score = 0f

        // crude counts
        for (term in profanityTerms) {
            if (lower.contains(term)) score += 0.40f
        }
        for (term in threatPhrases) {
            if (lower.contains(term)) score += 0.2f
        }
        for (term in identityPhrases) {
            if (lower.contains(term)) score += 0.1f
        }

        // presence of ALL CAPS words -> mild boost
        val hasAllCapsWord = lower.split(Regex("\\s+"))
            .any { token ->
                token.length >= 4 && token.all { it.isLetter() } && token == token.uppercase()
            }
        if (hasAllCapsWord) {
            score += 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Prosody-based score in [0,1].
     * Uses:
     * - peak16: max absolute PCM16 amplitude
     * - rms: root-mean-square of the signal
     *
     * We don't try to estimate absolute SPL, just relative loudness / sharpness.
     */
    fun prosodyScore(peak16: Int, rms: Double): Float {
        if (peak16 <= 0 && rms <= 0.0) return 0f

        // Normalize roughly from 0..1
        val peakNorm = (peak16 / 32768.0).coerceIn(0.0, 1.0)
        val rmsNorm = (rms / 2500.0).coerceIn(0.0, 1.0) // arbitrary scaling for 16k PCM

        val loudness = 0.6 * peakNorm + 0.4 * rmsNorm

        // Logistic shaping: low -> ~0, high -> ~1
        val x = loudness - 0.4  // center around moderate loudness
        val shaped = 1.0 / (1.0 + exp(-6.0 * x))

        return shaped.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Combined toxicity score:
     *  - lexical carries more weight (0.7)
     *  - prosody modulates it (0.3)
     */
    fun combinedScore(text: String, peak16: Int, rms: Double): Float {
        val lex = lexicalScore(text)
        val pros = prosodyScore(peak16, rms)
        return (0.7f * lex + 0.3f * pros).coerceIn(0f, 1f)
    }
}
