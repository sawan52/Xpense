package com.example.xpense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*
import kotlin.math.roundToInt

// ── Sparkline used inside the balance card ──────────────────────────────────
@Composable
fun SparklineChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (data.size < 2) return
    val maxVal = data.maxOf { it.second }.coerceAtLeast(1.0)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = w / (data.size - 1).coerceAtLeast(1)
        val pts = data.mapIndexed { i, (_, v) ->
            Offset(i * step, h * (1f - (v / maxVal).toFloat()))
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val cx = (pts[i].x + pts[i + 1].x) / 2f
                cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.6f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Dot on last point
        drawCircle(Color.White, radius = 4.dp.toPx(), center = pts.last())
    }
}

// ── Full spending-activity line chart ───────────────────────────────────────
@Composable
fun SpendingLineChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data yet", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    val maxVal = data.maxOf { it.second }.coerceAtLeast(1.0)
    val purple = PurplePrimary
    val purpleLight = PurpleLight

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (data.size > 1) w / (data.size - 1) else w

            val pts = data.mapIndexed { i, (_, v) ->
                Offset(i * stepX, h * (1f - (v / maxVal).toFloat()).coerceIn(0.05f, 1f))
            }

            // Area fill
            val fill = Path().apply {
                moveTo(pts.first().x, h)
                lineTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.size - 1) {
                    val cx = (pts[i].x + pts[i + 1].x) / 2f
                    cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
                }
                lineTo(pts.last().x, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(
                listOf(purpleLight.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f, endY = h
            ))

            // Line
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.size - 1) {
                    val cx = (pts[i].x + pts[i + 1].x) / 2f
                    cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
                }
            }
            drawPath(line, purple,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Peak dot + halo
            val peakIdx = data.indexOfFirst { it.second == data.maxOf { p -> p.second } }
            val peak = pts[peakIdx]
            drawCircle(purple.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = peak)
            drawCircle(purple, radius = 5.dp.toPx(), center = peak)
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = peak)
        }

        // X-axis labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (month, _) ->
                Text(
                    text = month.split(" ").first().take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Full spending-activity bar chart (amount axis on the left, months along the bottom) ──────
@Composable
fun SpendingBarChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data yet", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    val maxVal = data.maxOf { it.second }.coerceAtLeast(1.0)
    val barTop = PurpleLight
    val barBottom = PurplePrimary
    // Three Y-axis ticks (top, middle, baseline) drawn from top to bottom.
    val ticks = listOf(maxVal, maxVal / 2.0, 0.0)
    val yAxisWidth = 40.dp

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Y-axis amount labels, aligned to the three gridlines.
            Column(
                modifier = Modifier.width(yAxisWidth).fillMaxHeight().padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                ticks.forEach { t ->
                    Text(compactAmount(t), color = TextMuted, fontSize = 9.sp)
                }
            }
            // Bars.
            Canvas(modifier = Modifier.fillMaxHeight().weight(1f)) {
                val w = size.width
                val h = size.height
                val slot = w / data.size
                val barW = (slot * 0.5f).coerceAtMost(28.dp.toPx())
                val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                // Subtle gridlines at the three tick positions.
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = h * f
                    drawLine(DarkBorder, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                data.forEachIndexed { i, (_, v) ->
                    val barH = (h * (v / maxVal).toFloat()).coerceIn(0f, h)
                    val left = i * slot + (slot - barW) / 2f
                    val top = h - barH
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(barTop, barBottom), startY = top, endY = h),
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = radius
                    )
                }
            }
        }

        // X-axis month labels, offset by the Y-axis width so they sit under their bars.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Spacer(Modifier.width(yAxisWidth))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                data.forEach { (month, _) ->
                    Text(
                        text = month.split(" ").first().take(3),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** Compact ₹ amount for the bar-chart Y-axis: 1500 → "₹1.5k", 250000 → "₹2.5L". */
private fun compactAmount(v: Double): String = when {
    v >= 1_00_00_000 -> "₹${trimDecimal(v / 1_00_00_000)}Cr"
    v >= 1_00_000    -> "₹${trimDecimal(v / 1_00_000)}L"
    v >= 1_000       -> "₹${trimDecimal(v / 1_000)}k"
    else             -> "₹${v.roundToInt()}"
}

private fun trimDecimal(x: Double): String {
    val rounded = (x * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
