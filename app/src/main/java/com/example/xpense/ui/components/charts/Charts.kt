package com.example.xpense.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import kotlin.math.atan2
import kotlin.math.hypot

/** White line + fading area, drawn on the hero card. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Color.White) {
    if (values.size < 2) { Spacer(modifier); return }
    val max = values.max().coerceAtLeast(1.0)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, h - (v / max).toFloat() * h * 0.9f) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val cx = (pts[i].x + pts[i + 1].x) / 2f
                cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
        }
        val area = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

data class BarDatum(val label: String, val value: Double)

/**
 * Month bars: the last (current) bar gets the accent gradient and a glow, the rest are muted.
 * [valueLabel] formats the figure printed above each bar.
 */
@Composable
fun MonthBars(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    valueLabel: ((Double) -> String)? = null,
    highlight: Brush? = null,
    glow: Color? = null,
    barRadius: Dp = 10.dp,
    muted: Color = XpenseTheme.colors.card2,
    gap: Dp = 10.dp
) {
    val c = XpenseTheme.colors
    val max = (data.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(1.0)
    val hl = highlight ?: Brush.verticalGradient(listOf(c.ac2, c.ac))
    val glowColor = glow ?: c.ac
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { i, d ->
            val last = i == data.lastIndex
            val target = (d.value / max).toFloat().coerceIn(0.03f, 1f)
            val frac by animateFloatAsState(target, tween(600), label = "bar")
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
            ) {
                if (valueLabel != null) Text(valueLabel(d.value), style = XType.monoXS, color = if (last) c.tx else c.tx3, maxLines = 1)
                Box(Modifier.weight(1f, fill = true).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val shape = RoundedCornerShape(barRadius)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(frac * 0.95f)
                            .then(if (last) Modifier.shadow(14.dp, shape, ambientColor = glowColor, spotColor = glowColor) else Modifier)
                            .clip(shape)
                            .background(if (last) hl else Brush.linearGradient(listOf(muted, muted)))
                    )
                }
                Text(d.label, style = XType.micro, color = if (last) c.tx else c.tx2, maxLines = 1)
            }
        }
    }
}

data class DonutSlice(val key: Long, val value: Double, val color: Color)

/**
 * Segmented ring with small gaps between slices. When [onSelect] is set, a tap on the ring picks
 * the slice under the finger (tap the hole or the same slice again to clear), and the other
 * slices dim so the chosen one stands out.
 */
@Composable
fun Donut(
    slices: List<DonutSlice>,
    size: Dp,
    thickness: Dp,
    modifier: Modifier = Modifier,
    selectedKey: Long? = null,
    onSelect: ((Long?) -> Unit)? = null,
    holeColor: Color = XpenseTheme.colors.bg2,
    center: @Composable BoxScope.() -> Unit = {}
) {
    val c = XpenseTheme.colors
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val sweep by animateFloatAsState(1f, tween(700), label = "donut")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().then(
                if (onSelect != null) Modifier.pointerInput(slices, selectedKey) {
                    detectTapGestures { p ->
                        val cx = this.size.width / 2f
                        val cy = this.size.height / 2f
                        val r = hypot(p.x - cx, p.y - cy)
                        val outer = this.size.width / 2f
                        val inner = outer - thickness.toPx()
                        if (r < inner || r > outer) { onSelect(null); return@detectTapGestures }
                        var deg = Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())) + 90.0
                        if (deg < 0) deg += 360.0
                        var acc = 0.0
                        val hit = slices.firstOrNull { s -> acc += s.value / total * 360.0; deg <= acc }
                        onSelect(if (hit == null || hit.key == selectedKey) null else hit.key)
                    }
                } else Modifier
            )
        ) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (slices.isEmpty()) {
                drawArc(c.card2, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 1.6f else 0f
            var start = -90f
            slices.forEach { s ->
                val full = (s.value / total * 360.0).toFloat()
                val dim = selectedKey != null && s.key != selectedKey
                val sw = ((full - gap).coerceAtLeast(0.4f)) * sweep
                drawArc(
                    color = if (dim) s.color.copy(alpha = 0.25f) else s.color,
                    startAngle = start, sweepAngle = sw, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                )
                start += full * sweep
            }
        }
        Box(
            Modifier.size(size - thickness * 2).clip(CircleShape).background(holeColor),
            contentAlignment = Alignment.Center,
            content = center
        )
    }
}
