package io.github.c0dewars.pvcalculator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

data class Bar(val label: String, val value: Double)

/** Vertical bar chart with labels under each bar and the value of the highest bar. */
@Composable
fun BarChart(bars: List<Bar>, color: Color, valueLabel: (Double) -> String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { progress.animateTo(1f, tween(700)) }
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val valueStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        if (bars.isEmpty()) return@Canvas
        val maxValue = max(bars.maxOf { it.value }, 0.0001)
        val labelHeight = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - topPad
        val slot = size.width / bars.size
        val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        val radius = CornerRadius(barWidth / 2.5f)
        val maxIndex = bars.indexOfFirst { it.value == bars.maxOf { b -> b.value } }

        bars.forEachIndexed { i, bar ->
            val x = slot * i + (slot - barWidth) / 2
            drawRoundRect(trackColor, Offset(x, topPad), Size(barWidth, chartHeight), radius)
            val h = (bar.value / maxValue * chartHeight * progress.value).toFloat()
            if (h > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f)), startY = topPad + chartHeight - h, endY = topPad + chartHeight),
                    topLeft = Offset(x, topPad + chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
            val text = measurer.measure(bar.label, labelStyle)
            drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, size.height - text.size.height))
            if (i == maxIndex && bar.value > 0) {
                val v = measurer.measure(valueLabel(bar.value), valueStyle)
                val vx = (slot * i + (slot - v.size.width) / 2).coerceIn(0f, size.width - v.size.width)
                drawText(v, topLeft = Offset(vx, (topPad + chartHeight - h - v.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
    }
}

/**
 * Cumulative savings over the years compared with the investment.
 * [points] are (years since installation, cumulative savings).
 */
@Composable
fun PaybackChart(
    points: List<Pair<Double, Double>>,
    investment: Double,
    paybackYear: Double?,
    lineColor: Color,
    investmentColor: Color,
    yearLabel: (Int) -> String,
    moneyLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { progress.animateTo(1f, tween(900)) }
    val style = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        if (points.size < 2) return@Canvas
        val maxX = points.last().first
        val maxY = max(points.maxOf { it.second }, investment) * 1.08
        val bottomPad = 18.dp.toPx()
        val chartH = size.height - bottomPad
        fun px(x: Double) = (x / maxX * size.width).toFloat()
        fun py(y: Double) = (chartH - y / maxY * chartH).toFloat()

        // Horizontal grid
        for (i in 1..3) {
            val y = chartH * i / 4f
            drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }

        // Savings area + line, clipped by the animation progress.
        val visibleX = maxX * progress.value
        val visible = points.filter { it.first <= visibleX }.toMutableList()
        if (visible.size < points.size && visible.isNotEmpty()) {
            val next = points[visible.size]
            val prev = visible.last()
            val f = if (next.first > prev.first) (visibleX - prev.first) / (next.first - prev.first) else 0.0
            visible += visibleX to (prev.second + (next.second - prev.second) * f)
        }
        if (visible.size >= 2) {
            val line = Path().apply {
                moveTo(px(visible.first().first), py(visible.first().second))
                visible.drop(1).forEach { lineTo(px(it.first), py(it.second)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(px(visible.last().first), chartH)
                lineTo(px(visible.first().first), chartH)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f)), endY = chartH))
            drawPath(line, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }

        // Investment line
        if (investment > 0) {
            val y = py(investment)
            drawLine(
                investmentColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val t = measurer.measure(moneyLabel(investment), style.copy(color = investmentColor))
            drawText(t, topLeft = Offset(4.dp.toPx(), (y - t.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
        }

        // Payback marker
        if (paybackYear != null && paybackYear <= maxX && investment > 0 && progress.value > 0.99f) {
            val c = Offset(px(paybackYear), py(investment))
            drawCircle(surface, 9.dp.toPx(), c)
            drawCircle(lineColor, 6.dp.toPx(), c)
        }

        // X axis labels (every 5 years)
        val step = if (maxX > 30) 10 else 5
        var year = 0
        while (year <= maxX) {
            val t = measurer.measure(yearLabel(year), style)
            val x = (px(year.toDouble()) - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(x, size.height - t.size.height))
            year += step
        }
    }
}

/** Horizontal bar split in two parts (e.g. self-consumed vs exported). */
@Composable
fun SplitBar(first: Double, second: Double, firstColor: Color, secondColor: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val total = first + second
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (total <= 0) return@Canvas
        val w1 = (first / total * size.width).toFloat()
        drawRoundRect(secondColor, cornerRadius = r)
        if (w1 > 0) drawRoundRect(firstColor, size = Size(w1, size.height), cornerRadius = r)
    }
}
