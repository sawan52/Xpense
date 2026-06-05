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

    fun getCategoryColor(category: Category): Color {
        // Built-in categories keep their familiar colours...
        defaultColorByName(category.name)?.let { return it }
        // ...every other category gets a distinct, stable colour from the palette by its id,
        // so each pie-chart slice is clearly separable (instead of all sharing grey "Others").
        if (category.id <= 0L) return CategoryOthersColor
        return CategoryPalette[((category.id - 1) % CategoryPalette.size).toInt()]
    }

    /** Colour for one of the built-in default categories, or null for any custom category. */
    private fun defaultColorByName(name: String): Color? = when (name.lowercase()) {
        "food"          -> CategoryFoodColor
        "shopping"      -> CategoryShoppingColor
        "transport"     -> CategoryTravelColor
        "bills"         -> CategoryBillsColor
        "health"        -> CategoryHealthColor
        "entertainment" -> CategoryEntertainmentColor
        "others"        -> CategoryOthersColor
        else            -> null
    }

    fun getColorByName(name: String): Color = defaultColorByName(name) ?: CategoryOthersColor

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
