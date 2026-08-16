package com.fitti.ui.common

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitti.data.MealLogEntity
import com.fitti.data.ProteinEstimate
import com.fitti.data.ProteinEstimateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog zum Loggen einer Mahlzeit: Freitext (mit Spracheingabe), Foto
 * (Kamera/Galerie) mit KI-Schaetzung oder direkte Gramm-Eingabe.
 * Die KI-Schaetzung ist nur mit hinterlegtem API-Key verfuegbar; ohne
 * Key bleibt die manuelle Eingabe nutzbar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealEntryDialog(
    hasApiKey: Boolean,
    recentMeals: List<MealLogEntity>,
    estimateFromText: suspend (String) -> Result<String>,
    estimateFromImage: suspend (base64Jpeg: String, hint: String?) -> Result<String>,
    onSave: (description: String, proteinGrams: Double, source: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var description by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(MealLogEntity.SOURCE_MANUAL) }
    var estimate by remember { mutableStateOf<ProteinEstimate?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyEstimateResult(result: Result<String>, newSource: String) {
        result.onSuccess { response ->
            val parsed = ProteinEstimateParser.parse(response)
            if (parsed == null) {
                error = "Antwort nicht lesbar – bitte Gramm manuell eintragen."
            } else {
                estimate = parsed
                gramsText = formatGrams(parsed.totalProteinG)
                source = newSource
                if (description.isBlank() && parsed.items.isNotEmpty()) {
                    description = parsed.items.joinToString(", ") { it.name }
                }
            }
        }.onFailure { e ->
            error = "Fehler: ${e.message}"
        }
        isLoading = false
    }

    fun estimateFromBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        isLoading = true
        error = null
        scope.launch {
            val base64 = withContext(Dispatchers.Default) { bitmap.toScaledJpegBase64() }
            val hint = description.ifBlank { null }
            applyEstimateResult(estimateFromImage(base64, hint), MealLogEntity.SOURCE_AI_PHOTO)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                description = if (description.isBlank()) spoken else "$description $spoken"
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> estimateFromBitmap(bitmap) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            error = null
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { decodeScaledBitmap(context, uri) }
                if (bitmap == null) {
                    error = "Bild konnte nicht geladen werden."
                    isLoading = false
                } else {
                    val base64 = withContext(Dispatchers.Default) { bitmap.toScaledJpegBase64() }
                    val hint = description.ifBlank { null }
                    applyEstimateResult(
                        estimateFromImage(base64, hint),
                        MealLogEntity.SOURCE_AI_PHOTO
                    )
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mahlzeit loggen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Was hast du gegessen?") },
                        placeholder = { Text("z.B. 4 EL Haferflocken, 1 EL Mandelmus") },
                        modifier = Modifier.weight(1f),
                        minLines = 2
                    )
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mahlzeit beschreiben")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            error = "Keine Spracheingabe verfügbar – Tastatur-Diktat nutzen."
                        }
                    }) {
                        Text("🎤", fontSize = 20.sp)
                    }
                }

                if (hasApiKey) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!isLoading && description.isNotBlank()) {
                                    isLoading = true
                                    error = null
                                    scope.launch {
                                        applyEstimateResult(
                                            estimateFromText(description),
                                            MealLogEntity.SOURCE_AI_TEXT
                                        )
                                    }
                                }
                            },
                            enabled = !isLoading && description.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Schätzen")
                        }
                        OutlinedButton(
                            onClick = { if (!isLoading) cameraLauncher.launch(null) },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📷 Foto")
                        }
                        OutlinedButton(
                            onClick = { if (!isLoading) galleryLauncher.launch("image/*") },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Galerie")
                        }
                    }
                }

                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Schätzung läuft...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                estimate?.let { est ->
                    Spacer(Modifier.height(8.dp))
                    est.items.forEach { item ->
                        Text(
                            text = "${item.name}: ${formatGrams(item.proteinG)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (est.note.isNotBlank()) {
                        Text(
                            text = est.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (recentMeals.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Zuletzt gegessen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        recentMeals.forEach { meal ->
                            OutlinedButton(onClick = {
                                description = meal.description
                                gramsText = formatGrams(meal.proteinGrams)
                                source = MealLogEntity.SOURCE_MANUAL
                                estimate = null
                                error = null
                            }) {
                                Text(
                                    text = "${meal.description.take(24)} (${formatGrams(meal.proteinGrams)} g)",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val grams = gramsText.replace(",", ".").toDoubleOrNull()
                    if (grams != null && grams > 0) {
                        val desc = description.ifBlank { "Mahlzeit" }
                        onSave(desc, grams, source)
                    }
                },
                enabled = !isLoading &&
                    (gramsText.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/** Ganze Gramm ohne Nachkommastelle, sonst eine Nachkommastelle. */
fun formatGrams(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

/**
 * Skaliert das Bild auf max. [maxEdge] px lange Kante und liefert es als
 * Base64-JPEG (haelt die Token-Kosten der Vision-Anfrage klein).
 */
private fun Bitmap.toScaledJpegBase64(maxEdge: Int = 1568, quality: Int = 80): String {
    val longEdge = maxOf(width, height)
    val scaled = if (longEdge > maxEdge) {
        val factor = maxEdge.toDouble() / longEdge
        Bitmap.createScaledBitmap(
            this,
            (width * factor).toInt().coerceAtLeast(1),
            (height * factor).toInt().coerceAtLeast(1),
            true
        )
    } else {
        this
    }
    val stream = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

/** Laedt ein Galerie-Bild speicherschonend (grob vorskaliert per inSampleSize). */
private fun decodeScaledBitmap(
    context: android.content.Context,
    uri: Uri,
    maxEdge: Int = 1568
): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxEdge) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}
