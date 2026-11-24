package com.fitti.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zustand + laufender Call leben auf Bildschirm-Ebene, nicht im
 * LazyColumn-Eintrag: sonst bricht Scrollen (Eintrag wird entsorgt) den
 * Call ab und das Feedback ist weg.
 */
class AiFeedbackState(private val scope: CoroutineScope) {
    var feedback by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun request(onRequestFeedback: suspend () -> Result<String>) {
        if (isLoading) return
        isLoading = true
        error = null
        scope.launch {
            try {
                onRequestFeedback()
                    .onSuccess { feedback = it }
                    .onFailure { e -> error = "Fehler: ${e.message}" }
            } catch (e: Exception) {
                error = "Fehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

/** Ausserhalb der LazyColumn aufrufen. */
@Composable
fun rememberAiFeedbackState(): AiFeedbackState {
    val scope = rememberCoroutineScope()
    return remember { AiFeedbackState(scope) }
}

@Composable
fun AiFeedbackSection(
    state: AiFeedbackState,
    onRequestFeedback: suspend () -> Result<String>,
    modifier: Modifier = Modifier
) {
    val feedback = state.feedback
    val isLoading = state.isLoading
    val error = state.error

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        if (feedback == null && !isLoading) {
            FilledTonalButton(
                onClick = { state.request(onRequestFeedback) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "KI-Feedback",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Analyse l\u00e4uft...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (feedback != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KI-Feedback",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(text = feedback)
                }
            }
        }
    }
}
