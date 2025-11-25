package ai.guard.ui

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
import ai.guard.AppState
import ai.guard.logic.Light
import ai.guard.ui.theme.ConversationGuardTheme

@Composable
fun ConversationGuardScreen(
    onToggleListening: () -> Unit
) {
    ConversationGuardTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141218)),
            color = Color(0xFF141218)
        ) {
            val isListening by AppState.isListening.collectAsState()
            val status by AppState.status.collectAsState()
            val text by AppState.lastText.collectAsState()
            val toxicity by AppState.toxicity.collectAsState()
            val anger by AppState.anger.collectAsState()
            val light by AppState.light.collectAsState()
            val error by AppState.error.collectAsState()

            val lightColor = when (light) {
                Light.GREEN -> Color(0xFF4CAF50)
                Light.YELLOW -> Color(0xFFFFC107)
                Light.RED -> Color(0xFFF44336)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

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

                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEEEEEE),
                        textAlign = TextAlign.Center
                    )

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

                Button(
                    onClick = onToggleListening,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (isListening) "Stop listening" else "Start listening"
                    )
                }
            }
        }
    }
}
