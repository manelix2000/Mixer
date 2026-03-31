package dev.manelix.mixer.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
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
    val baseSampleSpacing = 1.8f
    val spacingMultiplier = 1.45f
    val amplitudeExponent = 1.35
    val smoothingFactor = 1.0f
    val minimumHalfHeight = 0f
    val maxWaveHeightRatio = 0.46f
    val innerLayerScale = 0.46f
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
            val sampleSpacing = baseSampleSpacing * spacingMultiplier * clampedZoom.toFloat().pow(1.35f)
            val middleSample = clampedProgress * max(samples.size - 1, 0)
            val maxHalfHeight = size.height * min(maxWaveHeightRatio, 0.48f)
            val columns = max(size.width.toInt() + 1, 2)
            val effectiveSmoothing = smoothingFactor.coerceIn(0f, 1f)

            val points = ArrayList<Offset>(columns)
            var smoothedAmplitude = 0f
            for (column in 0 until columns) {
                val x = column.toFloat()
                val samplePosition = middleSample + ((x - centerX) / sampleSpacing)
                val amplitude = interpolatedSample(samples, samplePosition).coerceIn(0f, 1f)
                val shapedAmplitude = amplitude.toDouble().pow(amplitudeExponent).toFloat()
                smoothedAmplitude = if (effectiveSmoothing >= 0.999f) {
                    shapedAmplitude
                } else {
                    smoothedAmplitude + ((shapedAmplitude - smoothedAmplitude) * effectiveSmoothing)
                }
                val halfHeight = max(minimumHalfHeight, smoothedAmplitude * maxHalfHeight)
                points.add(Offset(x, halfHeight))
            }

            if (points.size > 4) {
                val body = Path().apply {
                    moveTo(points.first().x, midY - points.first().y)
                    for (point in points.drop(1)) {
                        lineTo(point.x, midY - point.y)
                    }
                    for (index in points.lastIndex downTo 0) {
                        val point = points[index]
                        lineTo(point.x, midY + point.y)
                    }
                    close()
                }

                val innerBody = Path().apply {
                    val first = points.first()
                    moveTo(first.x, midY - (first.y * innerLayerScale))
                    for (point in points.drop(1)) {
                        lineTo(point.x, midY - (point.y * innerLayerScale))
                    }
                    for (index in points.lastIndex downTo 0) {
                        val point = points[index]
                        lineTo(point.x, midY + (point.y * innerLayerScale))
                    }
                    close()
                }

                drawPath(path = body, color = Color(0xFFAEAEB2).copy(alpha = 0.54f))
                clipRect(left = 0f, top = 0f, right = centerX, bottom = size.height) {
                    drawPath(path = body, color = Color(0xFFFF9500).copy(alpha = 0.82f))
                }

                drawPath(path = innerBody, color = Color.White.copy(alpha = 0.06f))
                clipRect(left = 0f, top = 0f, right = centerX, bottom = size.height) {
                    drawPath(path = innerBody, color = Color(0xFFFF9500).copy(alpha = 0.18f))
                }

                val upperEdge = Path().apply {
                    moveTo(points.first().x, midY - points.first().y)
                    for (point in points.drop(1)) {
                        lineTo(point.x, midY - point.y)
                    }
                }
                drawPath(path = upperEdge, color = Color.White.copy(alpha = 0.22f), style = Stroke(width = 1f))
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
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
    }
}

private fun interpolatedSample(
    samples: FloatArray,
    position: Double,
): Float {
    if (samples.isEmpty()) return 0f
    if (position < 0.0) return 0f
    if (position > samples.lastIndex.toDouble()) return 0f
    val lower = position.toInt().coerceIn(0, samples.lastIndex)
    val upper = (lower + 1).coerceIn(0, samples.lastIndex)
    val fraction = (position - lower.toDouble()).toFloat()
    return samples[lower] + ((samples[upper] - samples[lower]) * fraction)
}
