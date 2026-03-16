package com.musicplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.EqGreenIndicator
import com.musicplayer.app.ui.theme.EqSliderTrack
import com.musicplayer.app.ui.theme.EqZeroLineDash

@Composable
fun EqBandSlider(
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    frequency: String,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = maxLevel - minLevel
    val fraction = if (range > 0) (level - minLevel).toFloat() / range else 0.5f
    val dbValue = level / 100f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(44.dp)
    ) {
        // dB value display
        Text(
            text = "%+.1f".format(dbValue),
            style = MaterialTheme.typography.labelSmall,
            color = if (level != 0) EqGreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Custom vertical slider canvas
        val trackColor = EqSliderTrack
        val fillColor = if (enabled) EqGreenIndicator else EqGreenIndicator.copy(alpha = 0.3f)
        val dashColor = EqZeroLineDash
        val thumbColor = if (enabled) EqGreenIndicator else EqGreenIndicator.copy(alpha = 0.3f)

        Canvas(
            modifier = Modifier
                .width(32.dp)
                .height(160.dp)
                .pointerInput(enabled, minLevel, maxLevel) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val yFraction = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        val newLevel = (minLevel + yFraction * range).toInt()
                        onLevelChange(newLevel)
                    }
                }
        ) {
            val centerX = size.width / 2
            val trackTop = 8.dp.toPx()
            val trackBottom = size.height - 8.dp.toPx()
            val trackHeight = trackBottom - trackTop
            val centerY = trackTop + trackHeight / 2
            val thumbY = trackBottom - (fraction * trackHeight)

            // Track background
            drawLine(
                color = trackColor,
                start = Offset(centerX, trackTop),
                end = Offset(centerX, trackBottom),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Dashed zero line
            drawLine(
                color = dashColor,
                start = Offset(centerX - 10.dp.toPx(), centerY),
                end = Offset(centerX + 10.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )

            // Green fill from center to thumb
            drawLine(
                color = fillColor,
                start = Offset(centerX, centerY),
                end = Offset(centerX, thumbY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Thumb circle
            drawCircle(
                color = thumbColor,
                radius = 7.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency label
        Text(
            text = frequency,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
