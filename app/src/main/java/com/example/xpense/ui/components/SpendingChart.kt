package com.example.xpense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*

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
