package com.musicplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.EqGreenIndicator
import com.musicplayer.app.ui.theme.EqSliderTrack
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    value: Float,
    label: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = EqSliderTrack
    val activeColor = if (enabled) EqGreenIndicator else EqGreenIndicator.copy(alpha = 0.3f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .size(100.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val angle = atan2(
                            change.position.y - centerY,
                            change.position.x - centerX
                        )
                        // Map angle to 0-100 range (arc from 135deg to 405deg)
                        val startAngle = 135f * PI.toFloat() / 180f
                        val endAngle = 405f * PI.toFloat() / 180f
                        var normalizedAngle = angle - startAngle
                        if (normalizedAngle < 0) normalizedAngle += 2 * PI.toFloat()
                        val totalArc = endAngle - startAngle
                        val fraction = (normalizedAngle / totalArc).coerceIn(0f, 1f)
                        onValueChange(fraction * 100f)
                    }
                }
        ) {
            val radius = size.minDimension / 2 - 12.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 6.dp.toPx()

            // Background arc (from 135 to 405 degrees = 270 degree sweep)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Active arc fill
            val activeSweep = (value / 100f) * 270f
            drawArc(
                color = activeColor,
                startAngle = 135f,
                sweepAngle = activeSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Indicator dot at the end of active arc
            val indicatorAngle = (135f + activeSweep) * PI.toFloat() / 180f
            val dotX = center.x + radius * cos(indicatorAngle)
            val dotY = center.y + radius * sin(indicatorAngle)
            drawCircle(
                color = activeColor,
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${value.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (value != 50f) EqGreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
