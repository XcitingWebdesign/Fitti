package com.fitti.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.fitti.data.WeightLogEntity

@Composable
fun WeightChart(
    logs: List<WeightLogEntity>,
    modifier: Modifier = Modifier
) {
    if (logs.size < 2) {
        Text(
            text = if (logs.isEmpty()) "Noch keine Gewichtsdaten."
            else "Mindestens 2 Eintr\u00e4ge f\u00fcr Graph n\u00f6tig.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Chronological order (logs come DESC from DAO)
    val sorted = logs.reversed()
    val weights = sorted.map { it.weightKg }
    val minW = weights.min()
    val maxW = weights.max()
    val range = if (maxW - minW < 0.1) 1.0 else maxW - minW

    // Parse timestamps for date-proportional x-axis. Falls back to even spacing
    // when any timestamp is unparseable or all entries share the same time.
    val timestamps = sorted.map { parseDateTime(it.loggedAt)?.time }
    val firstT = timestamps.firstOrNull()
    val lastT = timestamps.lastOrNull()
    val timeProportional = timestamps.all { it != null } &&
        firstT != null && lastT != null && lastT > firstT
    val timeSpan = if (timeProportional) (lastT!! - firstT!!).toFloat() else 1f

    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(start = 40.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
        ) {
            val w = size.width
            val h = size.height
            val n = sorted.size
            val padding = 4f

            // Y-axis labels
            val paint = android.graphics.Paint().apply {
                color = labelColor.hashCode()
                textSize = 28f
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.drawText(
                "${maxW.cleanWeight()} kg",
                -36.dp.toPx(),
                paint.textSize,
                paint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${minW.cleanWeight()} kg",
                -36.dp.toPx(),
                h,
                paint
            )

            // X-axis date labels
            val firstDate = sorted.first().loggedAt.substringBefore(" ")
            val lastDate = sorted.last().loggedAt.substringBefore(" ")
            paint.textSize = 24f
            drawContext.canvas.nativeCanvas.drawText(
                firstDate,
                0f,
                h + 18.dp.toPx(),
                paint
            )
            val lastTextWidth = paint.measureText(lastDate)
            drawContext.canvas.nativeCanvas.drawText(
                lastDate,
                w - lastTextWidth,
                h + 18.dp.toPx(),
                paint
            )

            fun xFor(i: Int): Float {
                if (n == 1) return w / 2
                val frac = if (timeProportional) {
                    ((timestamps[i]!! - firstT!!).toFloat() / timeSpan)
                } else {
                    i.toFloat() / (n - 1)
                }
                return padding + (w - 2 * padding) * frac
            }

            // Draw line
            val path = Path()
            for (i in sorted.indices) {
                val x = xFor(i)
                val y = h - padding - ((weights[i] - minW) / range).toFloat() * (h - 2 * padding)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))

            // Draw dots
            for (i in sorted.indices) {
                val x = xFor(i)
                val y = h - padding - ((weights[i] - minW) / range).toFloat() * (h - 2 * padding)
                drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}
