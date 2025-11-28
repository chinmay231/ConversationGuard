package ai.guard2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark color scheme for the app (ConversationGuard2 uses a dark theme)
private val DarkColors = darkColorScheme(
    primary = CardBG,
    background = Black,
    surface = CardBG,
    onPrimary = Color.White,
    onSurface = Color.White,
    onBackground = Color.White
)

/**
 * Theme wrapper for ConversationGuard2 app.
 * Applies a dark Material3 theme with custom colors and typography.
 */
@Composable
fun ConversationGuard2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
