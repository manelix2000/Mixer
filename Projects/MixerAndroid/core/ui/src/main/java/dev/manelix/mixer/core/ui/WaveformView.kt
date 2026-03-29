package dev.manelix.mixer.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.pow

@Composable
fun WaveformView(
    samples: FloatArray,
    progress: Double,
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0.0, 1.0)
    val clampedZoom = zoom.coerceIn(0.2, 8.0)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1D1D1D))
            .fillMaxSize(),
    ) {
        val midY = size.height / 2f
        drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1f,
        )

        if (samples.isNotEmpty()) {
            val centerX = size.width / 2f
            val spacing = 1.8f * clampedZoom.toFloat().pow(1.35f)
            val centerSample = clampedProgress * max(samples.size - 1, 0)
            val maxHalfHeight = size.height * 0.46f
            var x = 0f
            while (x < size.width) {
                val samplePosition = centerSample + ((x - centerX) / spacing)
                val amplitude = interpolatedSample(samples, samplePosition).coerceIn(0f, 1f)
                val shaped = amplitude.toDouble().pow(1.35).toFloat()
                val halfHeight = max(0f, shaped * maxHalfHeight)
                val isPlayed = x <= centerX
                val color = if (isPlayed) Color(0xFFD67A00) else Color(0xFF9EA2A9)
                drawLine(
                    color = color,
                    start = Offset(x, midY - halfHeight),
                    end = Offset(x, midY + halfHeight),
                    strokeWidth = 1.3f,
                )
                x += 1f
            }
        }

        drawLine(
            color = Color(0xFFFF3B30),
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2f,
        )
        drawRoundRect(
            color = Color(0x44FFFFFF),
            style = Stroke(width = 1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        )
    }
}

private fun interpolatedSample(
    samples: FloatArray,
    position: Double,
): Float {
    if (samples.isEmpty()) return 0f
    if (position <= 0.0) return 0f
    if (position >= samples.lastIndex.toDouble()) return 0f
    val lower = position.toInt().coerceIn(0, samples.lastIndex)
    val upper = (lower + 1).coerceIn(0, samples.lastIndex)
    val fraction = (position - lower.toDouble()).toFloat()
    return samples[lower] + ((samples[upper] - samples[lower]) * fraction)
}
