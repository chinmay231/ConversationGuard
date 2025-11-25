package ai.guard.logic

enum class Light {
    GREEN,
    YELLOW,
    RED
}

/**
 * Maps continuous scores (0..1) to a coarse traffic-light signal.
 * You can adjust thresholds later if you want stricter or looser behavior.
 */
class LightDecider {

    fun decide(
        toxicity: Float,
        anger: Float
    ): Light {
        val t = toxicity.coerceIn(0f, 1f)
        val a = anger.coerceIn(0f, 1f)

        // Weighted combination: textual toxicity carries more weight.
        val combined = 0.7f * t + 0.3f * a

        return when {
            combined < 0.20f -> Light.GREEN
            combined < 0.55f  -> Light.YELLOW
            else             -> Light.RED
        }
    }
}
