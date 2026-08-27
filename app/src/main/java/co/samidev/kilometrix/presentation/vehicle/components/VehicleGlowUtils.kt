package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun OdometerWaveCanvas(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4C8DFF)
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val path = Path().apply {
            moveTo(0f, height * 0.7f)
            cubicTo(
                width * 0.25f, height * 0.9f,
                width * 0.45f, height * 0.2f,
                width * 0.75f, height * 0.6f
            )
            quadraticTo(
                width * 0.9f, height * 0.4f,
                width, height * 0.5f
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun OilArcGaugeCanvas(
    fraction: Float, // 0.0f to 1.0f
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        // Background track (240 degrees arc)
        drawArc(
            color = Color(0xFF2A364F),
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active progress arc
        val clampedFraction = fraction.coerceIn(0f, 1f)
        if (clampedFraction > 0f) {
            drawArc(
                color = color,
                startAngle = 150f,
                sweepAngle = 240f * clampedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
