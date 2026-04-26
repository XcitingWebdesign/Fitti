package com.fitti.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitti.data.BodyMeasurementDao
import com.fitti.data.BodyMeasurementEntity
import com.fitti.ui.common.formatDateTime
import com.fitti.ui.common.parseDateTime
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun MeasurementsScreen(
    bodyMeasurementDao: BodyMeasurementDao,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BodyMeasurementEntity>>(emptyList()) }
    var chestText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }
    var bicepsText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        entries = bodyMeasurementDao.getAll()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Tape-Maße",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Neue Messung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = chestText,
                            onValueChange = { chestText = it },
                            label = { Text("Brust (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = waistText,
                            onValueChange = { waistText = it },
                            label = { Text("Bauch (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bicepsText,
                            onValueChange = { bicepsText = it },
                            label = { Text("Bizeps (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val chest = chestText.replace(",", ".").toDoubleOrNull()
                                val waist = waistText.replace(",", ".").toDoubleOrNull()
                                val biceps = bicepsText.replace(",", ".").toDoubleOrNull()
                                if (chest == null || waist == null || biceps == null ||
                                    chest <= 0 || waist <= 0 || biceps <= 0
                                ) {
                                    error = "Bitte alle drei Werte als Zahl eintragen."
                                    return@Button
                                }
                                error = null
                                scope.launch {
                                    bodyMeasurementDao.insert(
                                        BodyMeasurementEntity(
                                            measuredAt = formatDateTime(Date()),
                                            chestCm = chest,
                                            waistCm = waist,
                                            bicepsCm = biceps
                                        )
                                    )
                                    entries = bodyMeasurementDao.getAll()
                                    chestText = ""
                                    waistText = ""
                                    bicepsText = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Speichern")
                        }
                    }
                }
            }

            if (entries.size >= 2) {
                item {
                    Text(
                        text = "Verlauf",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    MeasurementChart(
                        entries = entries.reversed(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            if (entries.isNotEmpty()) {
                item {
                    Text(
                        text = "Bisherige Messungen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatMeasuredAt(entry.measuredAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Brust ${entry.chestCm} cm · Bauch ${entry.waistCm} cm · Bizeps ${entry.bicepsCm} cm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    bodyMeasurementDao.deleteById(entry.id)
                                    entries = bodyMeasurementDao.getAll()
                                }
                            }) {
                                Text("Löschen", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMeasuredAt(raw: String): String {
    val date = parseDateTime(raw) ?: return raw
    val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
    return fmt.format(date)
}

@Composable
private fun MeasurementChart(
    entries: List<BodyMeasurementEntity>,
    modifier: Modifier = Modifier
) {
    val chestColor = MaterialTheme.colorScheme.primary
    val waistColor = MaterialTheme.colorScheme.tertiary
    val bicepsColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)) {
            if (entries.size < 2) return@Canvas
            val all = entries.flatMap { listOf(it.chestCm, it.waistCm, it.bicepsCm) }
            val minVal = all.min()
            val maxVal = all.max()
            val range = (maxVal - minVal).takeIf { it > 0.5 } ?: 1.0

            val stepX = size.width / (entries.size - 1).coerceAtLeast(1)
            fun yFor(v: Double): Float =
                size.height - ((v - minVal) / range * size.height).toFloat()

            // Gridlines
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            fun drawSeries(values: List<Double>, color: Color) {
                val path = Path()
                values.forEachIndexed { index, v ->
                    val x = stepX * index
                    val y = yFor(v)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                values.forEachIndexed { index, v ->
                    val x = stepX * index
                    val y = yFor(v)
                    drawCircle(color = color, radius = 5f, center = Offset(x, y))
                }
            }

            drawSeries(entries.map { it.chestCm }, chestColor)
            drawSeries(entries.map { it.waistCm }, waistColor)
            drawSeries(entries.map { it.bicepsCm }, bicepsColor)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(label = "Brust", color = chestColor)
            LegendDot(label = "Bauch", color = waistColor)
            LegendDot(label = "Bizeps", color = bicepsColor)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color, radius = 5f)
        }
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
