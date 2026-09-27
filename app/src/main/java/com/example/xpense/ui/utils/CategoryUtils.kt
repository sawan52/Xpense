package com.example.xpense.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.theme.*

object CategoryUtils {

    fun getCategoryIcon(category: Category): ImageVector = getIconByName(category.iconName)

    /** The design's tinted icon-tile background: the category colour at ~13% alpha. */
    fun soft(color: Color): Color = color.copy(alpha = 0.13f)

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

    fun getIconByName(name: String): ImageVector = when (name) {
        // ── Original set ──
        "Restaurant"         -> Icons.Rounded.Restaurant
        "ShoppingCart"       -> Icons.Rounded.ShoppingCart
        "DirectionsCar"      -> Icons.Rounded.DirectionsCar
        "ReceiptLong"        -> Icons.AutoMirrored.Rounded.ReceiptLong
        "MedicalServices"    -> Icons.Rounded.MedicalServices
        "ConfirmationNumber" -> Icons.Rounded.ConfirmationNumber
        "Savings"            -> Icons.Rounded.Savings
        "AccountBalance"     -> Icons.Rounded.AccountBalance
        "School"             -> Icons.Rounded.School
        "Home"               -> Icons.Rounded.Home
        "Flight"             -> Icons.Rounded.Flight
        // ── Food & drink ──
        "LocalGroceryStore"  -> Icons.Rounded.LocalGroceryStore
        "LocalCafe"          -> Icons.Rounded.LocalCafe
        "Fastfood"           -> Icons.Rounded.Fastfood
        "LocalBar"           -> Icons.Rounded.LocalBar
        "Cake"               -> Icons.Rounded.Cake
        // ── Transport ──
        "LocalGasStation"    -> Icons.Rounded.LocalGasStation
        "DirectionsBus"      -> Icons.Rounded.DirectionsBus
        "Train"              -> Icons.Rounded.Train
        "LocalTaxi"          -> Icons.Rounded.LocalTaxi
        "LocalParking"       -> Icons.Rounded.LocalParking
        // ── Health & personal ──
        "FitnessCenter"      -> Icons.Rounded.FitnessCenter
        "LocalPharmacy"      -> Icons.Rounded.LocalPharmacy
        "Spa"                -> Icons.Rounded.Spa
        "SportsSoccer"       -> Icons.Rounded.SportsSoccer
        // ── Entertainment ──
        "Movie"              -> Icons.Rounded.Movie
        "Tv"                 -> Icons.Rounded.Tv
        "MusicNote"          -> Icons.Rounded.MusicNote
        "SportsEsports"      -> Icons.Rounded.SportsEsports
        "MenuBook"           -> Icons.AutoMirrored.Rounded.MenuBook
        "Subscriptions"      -> Icons.Rounded.Subscriptions
        // ── Lifestyle & home ──
        "Hotel"              -> Icons.Rounded.Hotel
        "Checkroom"          -> Icons.Rounded.Checkroom
        "Devices"            -> Icons.Rounded.Devices
        "Chair"              -> Icons.Rounded.Chair
        "Pets"               -> Icons.Rounded.Pets
        "ChildCare"          -> Icons.Rounded.ChildCare
        "CardGiftcard"       -> Icons.Rounded.CardGiftcard
        "VolunteerActivism"  -> Icons.Rounded.VolunteerActivism
        // ── Finance ──
        "TrendingUp"         -> Icons.AutoMirrored.Rounded.TrendingUp
        "CreditCard"         -> Icons.Rounded.CreditCard
        "AccountBalanceWallet" -> Icons.Rounded.AccountBalanceWallet
        "HealthAndSafety"    -> Icons.Rounded.HealthAndSafety
        "Gavel"              -> Icons.Rounded.Gavel
        "Work"               -> Icons.Rounded.Work
        "Handyman"           -> Icons.Rounded.Handyman
        // ── Utilities ──
        "Bolt"               -> Icons.Rounded.Bolt
        "WaterDrop"          -> Icons.Rounded.WaterDrop
        "Wifi"               -> Icons.Rounded.Wifi
        "PhoneAndroid"       -> Icons.Rounded.PhoneAndroid
        "LocalLaundryService" -> Icons.Rounded.LocalLaundryService
        "Cookie"             -> Icons.Rounded.Cookie
        "Shield"             -> Icons.Rounded.Shield
        "Autorenew"          -> Icons.Rounded.Autorenew
        "Insights"           -> Icons.Rounded.Insights
        else                 -> Icons.Rounded.Category
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
        "Cookie", "Shield", "Autorenew", "Insights",
        // Generic fallback
        "Category"
    )
}
