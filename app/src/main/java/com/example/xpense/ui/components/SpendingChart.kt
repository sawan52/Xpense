package com.example.xpense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush

@Composable
fun SpendingChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No transactions found", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        return
    }

    val maxAmount = data.maxOf { it.second }.coerceAtLeast(1.0)
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5).copy(alpha = 0.5f))
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (_, amount) ->
                val barHeightFraction = (amount / maxAmount).toFloat().coerceAtLeast(0.05f)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(barHeightFraction)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(gradient)
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (month, _) ->
                Text(
                    text = month.split(" ")[0], // Just "May" instead of "May 2024"
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
