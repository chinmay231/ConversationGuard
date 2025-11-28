package ai.guard2.ui

import ai.guard2.AppState
import ai.guard2.logic.Light
import ai.guard2.ui.theme.ConversationGuard2Theme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Main UI screen for the ConversationGuard2 app.
 * Displays the status light, transcript, scores, and a toggle button.
 */
@Composable
fun ConversationGuardScreen(onToggleListening: () -> Unit) {
    ConversationGuard2Theme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141218)),  // dark background
            color = Color(0xFF141218)
        ) {
            // Collect state values from AppState (as StateFlow)
            val isListening by AppState.isListening.collectAsState()
            val status by AppState.status.collectAsState()
            val text by AppState.lastText.collectAsState()
            val toxicity by AppState.toxicity.collectAsState()
            val anger by AppState.anger.collectAsState()
            val light by AppState.light.collectAsState()
            val error by AppState.error.collectAsState()

            // Determine the color of the status indicator circle based on Light
            val lightColor = when (light) {
                Light.GREEN -> Color(0xFF4CAF50)   // Green
                Light.YELLOW -> Color(0xFFFFC107)  // Yellow
                Light.RED -> Color(0xFFF44336)     // Red
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top portion: Indicator light and status text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Colored circular status indicator with label
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(lightColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (light) {
                                Light.GREEN -> "Calm"
                                Light.YELLOW -> "Caution"
                                Light.RED -> "Aggressive"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Status message (listening status or error info)
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEEEEEE),
                        textAlign = TextAlign.Center
                    )
                    // If there's an error message, show it in red
                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6F6F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // Middle portion: Transcript and scores
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Transcript",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text.ifBlank { "No speech captured yet." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEEEEEE)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Scores",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toxicity: ${(toxicity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEEEEEE)
                    )
                    Text(
                        text = "Aggression (prosody): ${(anger * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEEEEEE)
                    )
                }
                // Bottom: Start/Stop listening toggle button
                Button(
                    onClick = onToggleListening,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(text = if (isListening) "Stop Listening" else "Start Listening")
                }
            }
        }
    }
}
