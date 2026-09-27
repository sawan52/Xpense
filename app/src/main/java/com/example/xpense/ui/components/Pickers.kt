package com.example.xpense.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * The design's date picker: big selected-date title, month navigation and a day grid. Future
 * days (and months) are disabled because an expense can't be in the future. Keeps the time of
 * day of [initial] on the returned millis.
 */
@Composable
fun XDatePickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val c = XpenseTheme.colors
    val now = remember { Calendar.getInstance() }
    var selected by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = initial }) }
    var shown by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = initial; set(Calendar.DAY_OF_MONTH, 1) }) }
    val atCurrentMonth = shown.get(Calendar.YEAR) == now.get(Calendar.YEAR) && shown.get(Calendar.MONTH) == now.get(Calendar.MONTH)

    XDialog(onDismiss) {
        Column {
            Overline("Select date", Modifier.offset(x = (-4).dp))
            Text(SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(selected.time), style = XType.h1.copy(fontSize = 28.sp), color = c.tx, modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(shown.time), style = XType.bodyStrong, color = c.tx, modifier = Modifier.weight(1f))
            NavSquare(Icons.Rounded.ChevronLeft, true) { shown = (shown.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }
            Spacer(Modifier.width(6.dp))
            NavSquare(Icons.Rounded.ChevronRight, !atCurrentMonth) { shown = (shown.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }
        }
        val firstDow = shown.get(Calendar.DAY_OF_WEEK) - 1 // Sunday-first grid
        val days = shown.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = List(firstDow) { 0 } + (1..days).toList()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, style = XType.captionStrong.copy(fontSize = 11.sp), color = c.tx3, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (d > 0) {
                                val cal = (shown.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
                                val future = atCurrentMonth && d > now.get(Calendar.DAY_OF_MONTH)
                                val isSel = sameDay(cal, selected)
                                val isToday = sameDay(cal, now)
                                val shape = RoundedCornerShape(14.dp)
                                Box(
                                    Modifier.fillMaxSize()
                                        .then(if (isSel) Modifier.shadow(10.dp, shape, ambientColor = c.ac, spotColor = c.ac) else Modifier)
                                        .clip(shape)
                                        .background(if (isSel) c.ac else Color.Transparent)
                                        .border(1.dp, if (isToday && !isSel) c.ac else Color.Transparent, shape)
                                        .clickable(enabled = !future) {
                                            selected = (selected.clone() as Calendar).apply {
                                                set(Calendar.YEAR, cal.get(Calendar.YEAR)); set(Calendar.MONTH, cal.get(Calendar.MONTH)); set(Calendar.DAY_OF_MONTH, d)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.toString(), style = XType.mono.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        color = when { isSel -> Color.White; future -> c.tx3.copy(alpha = 0.5f); else -> c.tx }
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        DialogButtons("OK", { onConfirm(selected.timeInMillis) }, onDismiss, confirmStyle = BtnStyle.Accent, confirmWeight = 1f)
    }
}

@Composable
private fun NavSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    Box(
        Modifier.size(34.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(11.dp)).background(c.card2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { XIcon(icon, 20.dp, c.tx2) }
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** The design's time picker: hour/minute boxes, AM/PM, and a clock dial with a glowing hand. */
@Composable
fun XTimePickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val c = XpenseTheme.colors
    val start = remember { Calendar.getInstance().apply { timeInMillis = initial } }
    var hour12 by remember { mutableIntStateOf(start.get(Calendar.HOUR).let { if (it == 0) 12 else it }) }
    var minute by remember { mutableIntStateOf(start.get(Calendar.MINUTE)) }
    var pm by remember { mutableStateOf(start.get(Calendar.AM_PM) == Calendar.PM) }
    var hourMode by remember { mutableStateOf(true) }

    XDialog(onDismiss) {
        Overline("Select time", Modifier.offset(x = (-4).dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeBox("%02d".format(hour12), hourMode, Modifier.weight(1f)) { hourMode = true }
            Text(":", style = XType.display.copy(fontSize = 40.sp), color = c.tx2)
            TimeBox("%02d".format(minute), !hourMode, Modifier.weight(1f)) { hourMode = false }
            Column(Modifier.height(80.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp))) {
                listOf(false to "AM", true to "PM").forEachIndexed { i, (isPm, label) ->
                    if (i == 1) Box(Modifier.width(52.dp).height(1.dp).background(c.line))
                    Box(
                        Modifier.weight(1f).width(52.dp).background(if (pm == isPm) c.acSoft else Color.Transparent).clickable { pm = isPm },
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = XType.captionStrong, color = if (pm == isPm) c.ac else c.tx2) }
                }
            }
        }
        val labels = if (hourMode) (1..12).toList() else (0..55 step 5).toList()
        val handValue = if (hourMode) hour12 % 12 * 30f else minute * 6f
        val angle by animateFloatAsState(handValue, label = "hand")
        Box(Modifier.align(Alignment.CenterHorizontally).size(240.dp).clip(CircleShape).background(c.card2).border(1.dp, c.line, CircleShape)) {
            val radius = 92.dp
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val rad = Math.toRadians((angle - 90).toDouble())
                val end = Offset(center.x + radius.toPx() * cos(rad).toFloat(), center.y + radius.toPx() * sin(rad).toFloat())
                drawLine(c.ac.copy(alpha = 0.35f), center, end, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(c.ac, center, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(c.ac, 5.dp.toPx(), center)
            }
            labels.forEachIndexed { i, v ->
                val a = Math.toRadians((i * 30 - if (hourMode) 60 else 90).toDouble())
                val x = 120.dp + radius * cos(a).toFloat() - 19.dp
                val y = 120.dp + radius * sin(a).toFloat() - 19.dp
                val on = if (hourMode) v == hour12 else v == minute
                val bg by animateColorAsState(if (on) c.ac else Color.Transparent, label = "dial")
                Box(
                    Modifier.offset(x, y).size(38.dp)
                        .then(if (on) Modifier.shadow(10.dp, CircleShape, ambientColor = c.ac, spotColor = c.ac) else Modifier)
                        .clip(CircleShape).background(bg)
                        .clickable { if (hourMode) { hour12 = v; hourMode = false } else minute = v },
                    contentAlignment = Alignment.Center
                ) { Text(if (hourMode) v.toString() else "%02d".format(v), style = XType.mono, color = if (on) Color.White else c.tx) }
            }
        }
        DialogButtons("OK", {
            val cal = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.HOUR_OF_DAY, (hour12 % 12) + if (pm) 12 else 0)
                set(Calendar.MINUTE, minute)
            }
            onConfirm(cal.timeInMillis)
        }, onDismiss, confirmStyle = BtnStyle.Accent, confirmWeight = 1f)
    }
}

@Composable
private fun TimeBox(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    Box(
        modifier.height(80.dp).clip(RoundedCornerShape(20.dp)).background(if (active) c.acSoft else c.card2)
            .border(1.dp, if (active) c.ac else Color.Transparent, RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, style = XType.display.copy(fontSize = 44.sp), color = if (active) c.ac else c.tx) }
}
