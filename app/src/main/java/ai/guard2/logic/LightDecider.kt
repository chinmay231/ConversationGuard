package ai.guard2.logic

/**
 * Traffic-light signal enumeration for conversation tone.
 */
enum class Light {
    GREEN,
    YELLOW,
    RED
}

/**
 * Determines the Light (Green/Yellow/Red) from continuous toxicity/aggression scores.
 * You can adjust thresholds for stricter or looser classification.
 */
class LightDecider {
    /**
     * Decide the light color based on toxicity (text content) and anger (prosody) scores.
     * @param toxicity Textual toxicity score [0,1]
     * @param anger Prosody-based aggression score [0,1]
     * @return Light enum (GREEN, YELLOW, or RED)
     */
    fun decide(toxicity: Float, anger: Float): Light {
        val t = toxicity.coerceIn(0f, 1f)
        val a = anger.coerceIn(0f, 1f)
        // Weighted combination: textual toxicity is 70%, prosody 30%
        val combined = 0.7f * t + 0.3f * a

        return when {
            combined < 0.20f -> Light.GREEN    // low combined score -> Calm (Green)
            combined < 0.55f -> Light.YELLOW   // moderate score -> Caution (Yellow)
            else -> Light.RED                  // high score -> Aggressive/Toxic (Red)
        }
    }
}
