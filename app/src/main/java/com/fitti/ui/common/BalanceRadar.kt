package com.fitti.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 6-axis radar/spider chart for muscle-group balance.
 *
 * @param values map from muscle-group code (CHEST/BACK/LEGS/SHOULDERS/ARMS/ABS) to
 *   a 0..1 normalized strength value. Missing groups render as 0.
 */
@Composable
fun BalanceRadar(
    values: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    val order = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "ABS")
    val labels = order.map { muscleGroupLabels[it] ?: it }
    val axisCount = order.size

    if (values.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Noch keine Daten f\u00fcr Balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val ringColor = MaterialTheme.colorScheme.outline
    val fillColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val labelColorInt = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelSubColorInt = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx, cy) * 0.68f

            // Rings at 25/50/75/100
            for (step in 1..4) {
                val r = radius * step / 4f
                val ringPath = Path()
                for (i in 0 until axisCount) {
                    val angle = angleFor(i, axisCount)
                    val x = cx + r * cos(angle).toFloat()
                    val y = cy + r * sin(angle).toFloat()
                    if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                }
                ringPath.close()
                drawPath(
                    ringPath,
                    color = ringColor,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Axis lines
            for (i in 0 until axisCount) {
                val angle = angleFor(i, axisCount)
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                drawLine(
                    color = ringColor,
                    start = Offset(cx, cy),
                    end = Offset(x, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Data polygon
            val polygon = Path()
            for (i in 0 until axisCount) {
                val v = (values[order[i]] ?: 0f).coerceIn(0f, 1f)
                val angle = angleFor(i, axisCount)
                val x = cx + radius * v * cos(angle).toFloat()
                val y = cy + radius * v * sin(angle).toFloat()
                if (i == 0) polygon.moveTo(x, y) else polygon.lineTo(x, y)
            }
            polygon.close()
            drawPath(polygon, color = fillColor)
            drawPath(
                polygon,
                color = strokeColor,
                style = Stroke(width = 2.dp.toPx())
            )

            // Data dots
            for (i in 0 until axisCount) {
                val v = (values[order[i]] ?: 0f).coerceIn(0f, 1f)
                val angle = angleFor(i, axisCount)
                val x = cx + radius * v * cos(angle).toFloat()
                val y = cy + radius * v * sin(angle).toFloat()
                drawCircle(strokeColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }

            // Labels
            val paint = android.graphics.Paint().apply {
                color = labelColorInt
                textSize = 30f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val subPaint = android.graphics.Paint().apply {
                color = labelSubColorInt
                textSize = 24f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val labelOffset = radius + 20.dp.toPx()
            for (i in 0 until axisCount) {
                val angle = angleFor(i, axisCount)
                val x = cx + labelOffset * cos(angle).toFloat()
                val y = cy + labelOffset * sin(angle).toFloat() + paint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText(labels[i], x, y, paint)
                val pct = ((values[order[i]] ?: 0f).coerceIn(0f, 1f) * 100f).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "$pct%",
                    x,
                    y + subPaint.textSize + 2.dp.toPx(),
                    subPaint
                )
            }
        }
    }
}

/** Top (index 0) points up; remaining vertices step clockwise evenly across the circle. */
private fun angleFor(index: Int, count: Int): Double {
    return -Math.PI / 2.0 + index * 2.0 * Math.PI / count
}
