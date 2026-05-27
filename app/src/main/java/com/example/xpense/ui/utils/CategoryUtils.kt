package com.example.xpense.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.theme.*

object CategoryUtils {

    fun getCategoryIcon(category: Category): ImageVector = getIconByName(category.iconName)

    fun getCategoryColor(category: Category): Color = getColorByName(category.name)

    fun getColorByName(name: String): Color = when (name.lowercase()) {
        "food"          -> CategoryFoodColor
        "shopping"      -> CategoryShoppingColor
        "transport"     -> CategoryTravelColor
        "bills"         -> CategoryBillsColor
        "health"        -> CategoryHealthColor
        "entertainment" -> CategoryEntertainmentColor
        else            -> CategoryOthersColor
    }

    fun getIconByName(name: String): ImageVector = when (name) {
        "Restaurant"       -> Icons.Default.Restaurant
        "ShoppingCart"     -> Icons.Default.ShoppingCart
        "DirectionsCar"    -> Icons.Default.DirectionsCar
        "ReceiptLong"      -> Icons.AutoMirrored.Filled.ReceiptLong
        "MedicalServices"  -> Icons.Default.MedicalServices
        "ConfirmationNumber" -> Icons.Default.ConfirmationNumber
        "Savings"          -> Icons.Default.Savings
        "AccountBalance"   -> Icons.Default.AccountBalance
        "School"           -> Icons.Default.School
        "Home"             -> Icons.Default.Home
        "Flight"           -> Icons.Default.Flight
        else               -> Icons.Default.Category
    }

    val availableIcons = listOf(
        "Restaurant", "ShoppingCart", "DirectionsCar", "ReceiptLong",
        "MedicalServices", "ConfirmationNumber", "Category", "Savings",
        "AccountBalance", "School", "Home", "Flight"
    )
}
