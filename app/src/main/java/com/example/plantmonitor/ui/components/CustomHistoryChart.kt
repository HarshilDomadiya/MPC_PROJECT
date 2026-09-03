package com.example.plantmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantmonitor.data.model.AggregatedHistoryPoint
import kotlin.math.abs

@Composable
fun CustomHistoryChart(
    title: String,
    unit: String,
    points: List<AggregatedHistoryPoint>,
    valueSelector: (AggregatedHistoryPoint) -> Double,
    lineColor: Color,
    isPercentage: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<AggregatedHistoryPoint?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (selectedPoint != null) {
                    val valFormatted = String.format("%.1f %s", valueSelector(selectedPoint!!), unit)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = lineColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${selectedPoint!!.formattedTime}: $valFormatted",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = lineColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history data available",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }
            } else {
                val values = points.map { valueSelector(it) }
                val minY = if (isPercentage) 0.0 else (values.minOrNull() ?: 0.0) - 2.0
                val maxY = if (isPercentage) 100.0 else (values.maxOrNull() ?: 50.0) + 2.0
                val rangeY = if (maxY - minY == 0.0) 1.0 else (maxY - minY)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) // Guaranteed fixed non-zero height
                        .pointerInput(points) {
                            detectTapGestures { tapOffset ->
                                val width = size.width
                                val paddingLeft = 40.dp.toPx()
                                val paddingRight = 16.dp.toPx()
                                val chartWidth = width - paddingLeft - paddingRight

                                if (chartWidth > 0 && points.isNotEmpty()) {
                                    val stepX = if (points.size > 1) chartWidth / (points.size - 1) else 0f
                                    var closestPoint: AggregatedHistoryPoint? = null
                                    var minDistance = Float.MAX_VALUE

                                    points.forEachIndexed { index, point ->
                                        val pointX = paddingLeft + (index * stepX)
                                        val dist = abs(tapOffset.x - pointX)
                                        if (dist < minDistance && dist < stepX + 40f) {
                                            minDistance = dist
                                            closestPoint = point
                                        }
                                    }
                                    selectedPoint = closestPoint
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val paddingLeft = 40.dp.toPx()
                    val paddingRight = 16.dp.toPx()
                    val paddingTop = 16.dp.toPx()
                    val paddingBottom = 28.dp.toPx()

                    val chartWidth = width - paddingLeft - paddingRight
                    val chartHeight = height - paddingTop - paddingBottom

                    val gridColor = Color.LightGray.copy(alpha = 0.3f)
                    val labelColor = android.graphics.Color.GRAY

                    // 1. Draw Y-axis grid lines and labels
                    val gridSteps = 4
                    val textPaint = android.graphics.Paint().apply {
                        color = labelColor
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    for (i in 0..gridSteps) {
                        val fraction = i.toFloat() / gridSteps
                        val yPos = paddingTop + (chartHeight * (1f - fraction))
                        val gridValue = minY + (rangeY * fraction)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, yPos),
                            end = Offset(width - paddingRight, yPos),
                            strokeWidth = 1.dp.toPx()
                        )

                        val labelStr = String.format("%.0f%s", gridValue, if (isPercentage) "%" else "")
                        drawContext.canvas.nativeCanvas.drawText(
                            labelStr,
                            paddingLeft - 8.dp.toPx(),
                            yPos + 4.dp.toPx(),
                            textPaint
                        )
                    }

                    // 2. Plot data points & paths
                    if (points.isNotEmpty()) {
                        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else 0f
                        val strokePath = Path()
                        val fillPath = Path()

                        val firstX = paddingLeft
                        val firstY = paddingTop + (chartHeight * (1f - ((values[0] - minY) / rangeY).toFloat()))

                        strokePath.moveTo(firstX, firstY)
                        fillPath.moveTo(firstX, height - paddingBottom)
                        fillPath.lineTo(firstX, firstY)

                        points.forEachIndexed { index, point ->
                            if (index > 0) {
                                val currentVal = values[index]
                                val x = paddingLeft + (index * stepX)
                                val y = paddingTop + (chartHeight * (1f - ((currentVal - minY) / rangeY).toFloat()))
                                strokePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        val lastX = paddingLeft + ((points.size - 1) * stepX)
                        fillPath.lineTo(lastX, height - paddingBottom)
                        fillPath.close()

                        // Draw area fill gradient
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f)),
                                startY = paddingTop,
                                endY = height - paddingBottom
                            )
                        )

                        // Draw line stroke
                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // 3. Draw X-axis time labels
                        val xLabelPaint = android.graphics.Paint().apply {
                            color = labelColor
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        val labelInterval = when {
                            points.size <= 5 -> 1
                            points.size <= 12 -> 2
                            else -> points.size / 5
                        }

                        points.forEachIndexed { index, _ ->
                            if (index % labelInterval == 0 || index == points.size - 1) {
                                val x = paddingLeft + (index * stepX)
                                drawContext.canvas.nativeCanvas.drawText(
                                    points[index].formattedTime,
                                    x,
                                    height - 6.dp.toPx(),
                                    xLabelPaint
                                )
                            }
                        }

                        // 4. Highlight selected point if tapped
                        selectedPoint?.let { sel ->
                            val selIndex = points.indexOf(sel)
                            if (selIndex >= 0) {
                                val selX = paddingLeft + (selIndex * stepX)
                                val selY = paddingTop + (chartHeight * (1f - ((values[selIndex] - minY) / rangeY).toFloat()))

                                drawLine(
                                    color = lineColor.copy(alpha = 0.5f),
                                    start = Offset(selX, paddingTop),
                                    end = Offset(selX, height - paddingBottom),
                                    strokeWidth = 1.dp.toPx()
                                )

                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = Offset(selX, selY)
                                )
                                drawCircle(
                                    color = lineColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(selX, selY)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
