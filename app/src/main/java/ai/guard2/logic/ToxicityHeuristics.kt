package ai.guard2.logic

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Very lightweight, deterministic toxicity heuristics (on-device, no cloud needed).
 *
 * - "lexical" score: based on explicit profanity/threat/identity phrases in text
 * - "prosody" score: based on short-term peak + RMS loudness of audio
 * - "combined" score: weighted fusion of both signals (range 0.0 to 1.0)
 */
object ToxicityHeuristics {
    // Extremely small example lexicon (can be expanded)
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
     * Lexical toxicity score [0,1] based on text content.
     * Counts occurrences of profanities/threats/identity slurs and accumulates score.
     */
    fun lexicalScore(text: String): Float {
        if (text.isBlank()) return 0f
        val lower = text.lowercase()
        var score = 0f

        // Additive score for each found term/phrase
        for (term in profanityTerms) {
            if (lower.contains(term)) score += 0.40f
        }
        for (phrase in threatPhrases) {
            if (lower.contains(phrase)) score += 0.20f
        }
        for (phrase in identityPhrases) {
            if (lower.contains(phrase)) score += 0.10f
        }

        // Mild boost if any word is in ALL CAPS (indicating shouting)
        val hasAllCapsWord = lower.split(Regex("\\s+")).any { token ->
            token.length >= 2 && token.all { it.isLetter() } && token == token.uppercase()
        }
        if (hasAllCapsWord) {
            score += 0.10f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Prosody-based aggression score [0,1] from audio signal.
     * Uses:
     *  - peak16: max absolute PCM amplitude (16-bit)
     *  - rms: root mean square of the PCM signal
     *
     * (Not an absolute volume in dB, just relative loudness/sharpness measure.)
     */
    fun prosodyScore(peak16: Int, rms: Double): Float {
        if (peak16 <= 0 && rms <= 0.0) return 0f

        // Normalize peak and RMS to roughly 0..1 range
        val peakNorm = (peak16 / 32768.0).coerceIn(0.0, 1.0)
        val rmsNorm = (rms / 2500.0).coerceIn(0.0, 1.0)  // scaling for ~16kHz PCM

        val loudness = 0.6 * peakNorm + 0.4 * rmsNorm

        // Logistic shaping: low loudness -> ~0, high -> ~1 (threshold around 0.4)
        val x = loudness - 0.4
        val shaped = 1.0 / (1.0 + exp(-6.0 * x))

        return shaped.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Combined toxicity score [0,1] fusing content and prosody.
     * Textual toxicity carries more weight (70%) and prosody 30%.
     */
    fun combinedScore(text: String, peak16: Int, rms: Double): Float {
        val lex = lexicalScore(text)
        val pros = prosodyScore(peak16, rms)
        return (0.7f * lex + 0.3f * pros).coerceIn(0f, 1f)
    }
}
