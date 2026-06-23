package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*

/**
 * In-app "How to use Xpense" guide, reached from Profile. A single scrollable page of collapsible
 * topics (accordion: opening one closes the others). Each topic pairs a plain-language explanation
 * with a small mock-up built from the app's own icons/colours/card styles — so it looks like the
 * real UI without bundling screenshots (no real data, no APK bloat, never stale).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(viewModel: ExpenseViewModel) {
    BackHandler { viewModel.navigateTo(Screen.PROFILE) }

    // Accordion state: index of the currently open topic, or -1 for all collapsed.
    var openIndex by remember { mutableIntStateOf(0) }

    val topics = helpTopics()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Help & Guide", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.PROFILE) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurplePrimary.copy(alpha = 0.12f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Welcome to Xpense 👋", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Xpense automatically turns your bank SMS into a neat spending tracker. " +
                            "Tap any topic below to learn what it does and how to use it.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }

            itemsIndexed(topics) { index, topic ->
                HelpSection(
                    title = topic.title,
                    icon = topic.icon,
                    tint = topic.tint,
                    expanded = openIndex == index,
                    onToggle = { openIndex = if (openIndex == index) -1 else index },
                    content = topic.body
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Section shell ────────────────────────────────────────────────────────────

private data class HelpTopic(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val body: @Composable () -> Unit
)

@Composable
private fun HelpSection(
    title: String,
    icon: ImageVector,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore, null, tint = TextMuted,
                modifier = Modifier.size(22.dp).rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

// ── Text helpers ─────────────────────────────────────────────────────────────

@Composable
private fun Para(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/** A small framed canvas that holds a mock-up of a real screen element. */
@Composable
private fun MockFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkBg)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) { content() }
}

// ── Mock illustrations (built from the app's real icons + colours) ───────────

@Composable
private fun MockBalanceCard() = MockFrame {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total Balance", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Visibility, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
        Text("₹ ••••••", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("▲ 12%", color = GreenPositive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("vs last month", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MockTransactionRow() = MockFrame {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(38.dp).background(CategoryFoodColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Restaurant, null, tint = CategoryFoodColor, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text("Swiggy", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Food · 8:24 PM", color = TextMuted, fontSize = 11.sp)
        }
        Text("−₹450", color = RedNegative, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Archive, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MockCategoryPills() = MockFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockPill("Food", selected = true)
        MockPill("Shopping", selected = false)
        MockPill("+ New", selected = false, accent = true)
    }
}

@Composable
private fun MockPill(label: String, selected: Boolean, accent: Boolean = false) {
    val bg = when {
        selected -> PurplePrimary
        accent -> PurplePrimary.copy(alpha = 0.18f)
        else -> DarkSurface
    }
    val fg = when {
        selected -> Color.White
        accent -> PurpleLight
        else -> TextSecondary
    }
    Text(
        label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun MockRuleChip() = MockFrame {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).background(CategoryFoodColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Restaurant, null, tint = CategoryFoodColor, modifier = Modifier.size(16.dp)) }
            Column {
                Text("swiggy | zomato", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Food", color = TextMuted, fontSize = 11.sp)
            }
        }
        Para("“,” means all words must appear · “|” means any alternative can match.")
    }
}

@Composable
private fun MockNotificationItem() = MockFrame {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(36.dp).background(PurplePrimary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, null, tint = PurpleLight, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text("BigBasket", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("₹820 · Tap to create a rule", color = TextMuted, fontSize = 11.sp)
        }
        Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MockDonut() = MockFrame {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                val stroke = 16.dp.toPx()
                val segments = listOf(
                    CategoryFoodColor to 130f,
                    CategoryShoppingColor to 90f,
                    CategoryTravelColor to 80f,
                    CategoryOthersColor to 60f
                )
                var start = -90f
                val inset = stroke / 2
                segments.forEach { (color, sweep) ->
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep - 4f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("₹24k", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Spent", color = TextMuted, fontSize = 10.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendDot(CategoryFoodColor, "Food")
            LegendDot(CategoryShoppingColor, "Shopping")
            LegendDot(CategoryTravelColor, "Transport")
            LegendDot(CategoryOthersColor, "Others")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

// ── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun helpTopics(): List<HelpTopic> = listOf(
    HelpTopic("Getting started & permissions", Icons.Default.RocketLaunch, GreenPositive) {
        Para("On first launch, Xpense sets up 7 default categories (Food, Shopping, Transport, Bills, Health, Entertainment, Others) and a few starter rules, so it works right away.")
        Bullet("SMS permission is required — it's how Xpense reads your bank's transaction messages and tracks spending automatically. Your messages never leave your phone.")
        Bullet("Notification permission (Android 13+) is optional — it lets Xpense alert you when a transaction needs a category. You can use the app without it.")
    },
    HelpTopic("Home & balance", Icons.Default.Home, CategoryBillsColor) {
        Para("Your home screen is a quick snapshot of your money.")
        Bullet("Total Balance shows your overall spending. Tap the eye icon to hide or reveal the amount.")
        Bullet("The ▲/▼ figure compares this month with last month, and the mini graph shows the last 6 months.")
        Bullet("Quick Actions let you Add an Expense or jump to Categories. Recent transactions appear below — tap View All for the full list.")
        Bullet("The sync icon (top-right) imports your past bank SMS.")
        MockBalanceCard()
    },
    HelpTopic("Insights & charts", Icons.Default.PieChart, CategoryEntertainmentColor) {
        Para("The Insights tab breaks down where your money goes.")
        Bullet("Pick a month from the chips at the top.")
        Bullet("The donut shows each category's share of spending, with the month's total in the centre.")
        Bullet("Use the search icon to find a transaction by name, category, or amount.")
        Bullet("Tap View Breakdown for a category-by-category bar list and Smart Insights — friendly tips about your trends and top category.")
        MockDonut()
    },
    HelpTopic("History", Icons.Default.History, CategoryTravelColor) {
        Para("History lists every transaction, grouped by day with a daily total.")
        Bullet("Tap any transaction to edit it.")
        Bullet("Long-press to select several at once, then archive or delete them together.")
        Bullet("Each row has an archive icon to set it aside (see Archived transactions).")
        MockTransactionRow()
    },
    HelpTopic("Adding & editing an expense", Icons.Default.AddCircle, CategoryHealthColor) {
        Para("Most spending is tracked automatically from SMS, but you can add anything by hand with the + button.")
        Bullet("Enter the amount, pick a category, and type where you spent. Date, time, and an optional note are all editable.")
        Bullet("Need a category that doesn't exist yet? Tap the + New pill to create one on the spot.")
        Bullet("Tap any existing transaction to edit it. For SMS transactions, an “Add a rule for this” button lets you teach Xpense how to categorize similar ones in future.")
        MockCategoryPills()
    },
    HelpTopic("Categories & auto-rules", Icons.Default.Category, CategoryShoppingColor) {
        Para("Categories group your spending; auto-rules decide which category a transaction lands in. Manage both under Profile → Category Rules.")
        Bullet("Add, rename, or delete categories. “Others” can't be deleted — if you delete a category, its transactions and rules move to Others.")
        Bullet("A rule maps keywords to a category. In the keyword box: a comma means all those words must appear, and a “|” separates alternatives.")
        Bullet("Example: “swiggy | zomato” sends either to Food. You can also set a display name (e.g. “MF SIP”).")
        Bullet("Re-apply rules updates existing transactions; Merge duplicate rules combines rules that share a category and label into one.")
        MockRuleChip()
    },
    HelpTopic("Automatic SMS tracking", Icons.Default.Sms, CategoryBillsColor) {
        Para("Xpense reads incoming bank SMS and records your spending without any typing.")
        Bullet("It captures debits/payments, and intelligently skips OTPs, credits & refunds, credit-card bill payments, and mutual-fund confirmations, so nothing is double-counted.")
        Bullet("Duplicate messages are ignored automatically.")
        Bullet("Use Sync SMS History (home or Profile) to import the last 6 months in one go.")
        Bullet("If you change a transaction's category by hand, Xpense locks it and never overwrites your choice.")
    },
    HelpTopic("Notifications inbox", Icons.Default.Notifications, CategoryEntertainmentColor) {
        Para("When Xpense can't confidently categorize a transaction, it files it under Others and lists it here (Profile → Notifications) so you can fix it later.")
        Bullet("Tap an item to create a rule for it — the transaction (and similar future ones) gets categorized instantly.")
        Bullet("Items clear themselves once categorized. You can also dismiss one, Clear all, or Send a test notification.")
        Bullet("The Pop-up alerts toggle controls the heads-up notification only — even when it's off, items are still saved to this list.")
        MockNotificationItem()
    },
    HelpTopic("Archived transactions", Icons.Default.Archive, CategoryHealthColor) {
        Para("Archiving hides a transaction from all totals and charts — perfect for self-transfers or anything that isn't real spending.")
        Bullet("Tap the archive icon on any transaction to set it aside.")
        Bullet("Find everything you've archived under Profile → Archived Transactions, and restore any of them with one tap.")
    },
    HelpTopic("Backup & restore", Icons.Default.CloudUpload, CategoryTravelColor) {
        Para("Keep your data safe and move it between phones using Google Drive (Profile → Backup & Restore).")
        Bullet("Connect your Google account, then tap Back up now. The last backup time is shown.")
        Bullet("Restore offers two modes — Merge adds the backup to your current data (skipping duplicates), while Replace wipes this device first and restores exactly. Replace can't be undone.")
        Bullet("Drive sign-in may be limited to Google accounts the app owner has approved.")
    }
)
