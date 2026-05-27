package com.example.xpense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils

@Composable
fun CategoryDonutChart(
    summary: Map<Category, Double>,
    modifier: Modifier = Modifier
) {
    val total = summary.values.sum().coerceAtLeast(1.0)
    val entries = summary.entries.toList()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val diameter = minOf(size.width, size.height) - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            var startAngle = -90f
            entries.forEachIndexed { _, entry ->
                val sweep = (entry.value / total * 360f).toFloat()
                drawArc(
                    color = CategoryUtils.getCategoryColor(entry.key),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total Spent", color = TextSecondary, fontSize = 10.sp)
            Text("Breakdown", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
