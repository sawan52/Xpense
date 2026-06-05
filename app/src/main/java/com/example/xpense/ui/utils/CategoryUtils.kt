package com.example.xpense.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
        // ── Original set ──
        "Restaurant"         -> Icons.Default.Restaurant
        "ShoppingCart"       -> Icons.Default.ShoppingCart
        "DirectionsCar"      -> Icons.Default.DirectionsCar
        "ReceiptLong"        -> Icons.AutoMirrored.Filled.ReceiptLong
        "MedicalServices"    -> Icons.Default.MedicalServices
        "ConfirmationNumber" -> Icons.Default.ConfirmationNumber
        "Savings"            -> Icons.Default.Savings
        "AccountBalance"     -> Icons.Default.AccountBalance
        "School"             -> Icons.Default.School
        "Home"               -> Icons.Default.Home
        "Flight"             -> Icons.Default.Flight
        // ── Food & drink ──
        "LocalGroceryStore"  -> Icons.Default.LocalGroceryStore
        "LocalCafe"          -> Icons.Default.LocalCafe
        "Fastfood"           -> Icons.Default.Fastfood
        "LocalBar"           -> Icons.Default.LocalBar
        "Cake"               -> Icons.Default.Cake
        // ── Transport ──
        "LocalGasStation"    -> Icons.Default.LocalGasStation
        "DirectionsBus"      -> Icons.Default.DirectionsBus
        "Train"              -> Icons.Default.Train
        "LocalTaxi"          -> Icons.Default.LocalTaxi
        "LocalParking"       -> Icons.Default.LocalParking
        // ── Health & personal ──
        "FitnessCenter"      -> Icons.Default.FitnessCenter
        "LocalPharmacy"      -> Icons.Default.LocalPharmacy
        "Spa"                -> Icons.Default.Spa
        "SportsSoccer"       -> Icons.Default.SportsSoccer
        // ── Entertainment ──
        "Movie"              -> Icons.Default.Movie
        "Tv"                 -> Icons.Default.Tv
        "MusicNote"          -> Icons.Default.MusicNote
        "SportsEsports"      -> Icons.Default.SportsEsports
        "MenuBook"           -> Icons.AutoMirrored.Filled.MenuBook
        "Subscriptions"      -> Icons.Default.Subscriptions
        // ── Lifestyle & home ──
        "Hotel"              -> Icons.Default.Hotel
        "Checkroom"          -> Icons.Default.Checkroom
        "Devices"            -> Icons.Default.Devices
        "Chair"              -> Icons.Default.Chair
        "Pets"               -> Icons.Default.Pets
        "ChildCare"          -> Icons.Default.ChildCare
        "CardGiftcard"       -> Icons.Default.CardGiftcard
        "VolunteerActivism"  -> Icons.Default.VolunteerActivism
        // ── Finance ──
        "TrendingUp"         -> Icons.AutoMirrored.Filled.TrendingUp
        "CreditCard"         -> Icons.Default.CreditCard
        "AccountBalanceWallet" -> Icons.Default.AccountBalanceWallet
        "HealthAndSafety"    -> Icons.Default.HealthAndSafety
        "Gavel"              -> Icons.Default.Gavel
        "Work"               -> Icons.Default.Work
        "Handyman"           -> Icons.Default.Handyman
        // ── Utilities ──
        "Bolt"               -> Icons.Default.Bolt
        "WaterDrop"          -> Icons.Default.WaterDrop
        "Wifi"               -> Icons.Default.Wifi
        "PhoneAndroid"       -> Icons.Default.PhoneAndroid
        "LocalLaundryService" -> Icons.Default.LocalLaundryService
        else                 -> Icons.Default.Category
    }

    val availableIcons = listOf(
        // Original
        "Restaurant", "ShoppingCart", "DirectionsCar", "ReceiptLong",
        "MedicalServices", "ConfirmationNumber", "Savings", "AccountBalance",
        "School", "Home", "Flight",
        // Food & drink
        "LocalGroceryStore", "LocalCafe", "Fastfood", "LocalBar", "Cake",
        // Transport
        "LocalGasStation", "DirectionsBus", "Train", "LocalTaxi", "LocalParking",
        // Health & personal
        "FitnessCenter", "LocalPharmacy", "Spa", "SportsSoccer",
        // Entertainment
        "Movie", "Tv", "MusicNote", "SportsEsports", "MenuBook", "Subscriptions",
        // Lifestyle & home
        "Hotel", "Checkroom", "Devices", "Chair", "Pets", "ChildCare",
        "CardGiftcard", "VolunteerActivism",
        // Finance
        "TrendingUp", "CreditCard", "AccountBalanceWallet", "HealthAndSafety",
        "Gavel", "Work", "Handyman",
        // Utilities
        "Bolt", "WaterDrop", "Wifi", "PhoneAndroid", "LocalLaundryService",
        // Generic fallback
        "Category"
    )
}
