package com.example.xpense.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.xpense.data.model.Category

object CategoryUtils {
    fun getCategoryIcon(category: Category): ImageVector {
        return when (category) {
            Category.FOOD -> Icons.Default.Restaurant
            Category.SHOPPING -> Icons.Default.ShoppingCart
            Category.TRANSPORT -> Icons.Default.DirectionsCar
            Category.BILLS -> Icons.AutoMirrored.Filled.ReceiptLong
            Category.HEALTH -> Icons.Default.MedicalServices
            Category.ENTERTAINMENT -> Icons.Default.ConfirmationNumber
            Category.OTHERS -> Icons.Default.Category
        }
    }
}
