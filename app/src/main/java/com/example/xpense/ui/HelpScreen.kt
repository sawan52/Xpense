package com.example.xpense.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme

/** One accordion topic: an intro paragraph plus bullet points. */
private data class HelpTopic(val title: String, val icon: ImageVector, val color: Color, val body: String, val points: List<String>)

/**
 * In-app "How to use Xpense" guide, reached from Profile: a welcome card and collapsible topics.
 */
@Composable
fun HelpScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    var open by rememberSaveableInt(-1)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Help & guide", { viewModel.navigateBack() }) }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.padding(top = 6.dp).fillMaxWidth().clip(shape)
                    .background(Brush.linearGradient(listOf(c.acSoft, Color.Transparent)))
                    .topRightGlow(c.ac, 0.4f, 90.dp)
                    .border(1.dp, c.line, shape).padding(20.dp)
            ) {
                XIcon(Icons.Rounded.AutoAwesome, 26.dp, c.ac)
                Text("Welcome to Xpense", style = XType.section.copy(fontSize = XType.section.fontSize * 1.13f), color = c.tx, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Xpense turns your bank SMS into a neat spending tracker. Tap any topic to learn how it works.",
                    style = XType.small, color = c.tx2, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        itemsIndexed(topics) { i, t ->
            val expanded = open == i
            val border by animateColorAsState(if (expanded) t.color.copy(alpha = 0.5f) else c.line, label = "b")
            val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "r")
            GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, borderColor = border, contentPadding = PaddingValues(0.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (expanded) -1 else i }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconTile(t.icon, t.color)
                    Text(t.title, style = XType.bodyStrong, color = c.tx, modifier = Modifier.weight(1f))
                    XIcon(Icons.Rounded.ExpandMore, 20.dp, c.tx2, Modifier.rotate(rot))
                }
                AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(start = 70.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t.body, style = XType.small, color = c.tx2)
                        t.points.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(c.ac))
                                Text(p, style = XType.small, color = c.tx2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableInt(initial: Int) = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(initial) }

private val topics = listOf(
    HelpTopic(
        "Getting started & permissions", Icons.Rounded.RocketLaunch, Color(0xFF34D399),
        "On first launch, Xpense sets up 7 default categories (Food, Shopping, Transport, Bills, Health, Entertainment, Others) and a few starter rules, so it works right away.",
        listOf(
            "SMS permission is required — it's how Xpense reads your bank's transaction messages. Your messages never leave your phone.",
            "Notification permission (Android 13+) is optional — it lets Xpense alert you when a transaction needs a category.",
            "Get around with the floating bar: Home, Insights, the centre + to add an expense, Categories, and Profile.",
            "Prefer a lighter look? Switch between dark and light mode under Profile → Appearance."
        )
    ),
    HelpTopic(
        "Home & budget", Icons.Rounded.Home, Color(0xFF60A5FA),
        "Home is a quick snapshot of this month.",
        listOf(
            "The hero card shows what you've spent this month, with the change versus last month. Tap the eye to hide the amount.",
            "Set a monthly budget (tap the Budget tile, or Profile → Monthly budget) and the card shows how much is left.",
            "Spending activity charts your last 6 months; the current month is the highlighted bar.",
            "Recent shows your latest transactions — tap one to edit it, or View all to open Insights.",
            "The search button finds any transaction, category or rule."
        )
    ),
    HelpTopic(
        "Insights & transactions", Icons.Rounded.DonutLarge, Color(0xFFFBBF24),
        "The Insights tab is both your spending breakdown and your full transaction list.",
        listOf(
            "Pick a month from the pills at the top. The donut card shows the total, your top category, the daily average and what's excluded (archived).",
            "Explore full breakdown opens a big donut — tap a slice to see that category, or tap a category to open its detail page with a 6-month trend and top merchants.",
            "Smart insights are generated from your own spending: big changes, recurring payments, your top category.",
            "Tap the tune button to filter by category, amount or source (SMS or manual) and to sort. Active filters appear as chips you can tap to remove.",
            "Tap a transaction to edit it; long-press to select several and archive or delete them together."
        )
    ),
    HelpTopic(
        "Adding & editing an expense", Icons.Rounded.AddCircle, Color(0xFF34D399),
        "Most spending is tracked automatically from SMS, but you can add anything by hand with the + button.",
        listOf(
            "Type the amount, pick a category chip, and say where you spent. Date, time and a note are optional.",
            "Need a category that doesn't exist yet? Tap + New in the category row.",
            "For SMS transactions, “Add a rule for this” teaches Xpense how to categorize similar ones in future.",
            "If you re-categorize a transaction a rule already covers, “Force auto rule” appears next time — tap it to snap back to what the rule says (amount and note are kept).",
            "Archive or delete a transaction from the bottom of its edit sheet."
        )
    ),
    HelpTopic(
        "Categories & auto-rules", Icons.Rounded.Category, Color(0xFFA78BFA),
        "Categories group your spending; auto-rules decide which category a transaction lands in. Both live on the Categories tab.",
        listOf(
            "Each category tile shows this month's spend. Use its ⋯ menu to edit it, view its transactions, or delete it. “Others” can't be deleted — a deleted category's transactions and rules move to Others.",
            "On Auto-Rules, rules are grouped under their category. Tap a group to open it; each rule shows its name and keyword count.",
            "Open a rule to see its keywords. Tap one to edit it; press and hold to select several and delete them. Deleting every keyword deletes the rule.",
            "A keyword of four letters or more also matches inside a longer word, so “zomato” catches “paytmpayzomato”. Shorter ones must start a word, so “ola” can't hijack “Cholas”.",
            "Rules ignore everything after “@” in a UPI id, so your keyword matches the merchant — not the handle.",
            "Within one keyword a comma means every word must appear; “|” separates alternatives.",
            "Run Re-apply rules to update past transactions."
        )
    ),
    HelpTopic(
        "Automatic SMS tracking", Icons.Rounded.Sms, Color(0xFF22D3EE),
        "Xpense reads incoming bank SMS and records your spending without any typing.",
        listOf(
            "It captures debits and payments, and skips OTPs, credits and refunds, credit-card bill payments, mutual-fund confirmations and payment reminders — so nothing is double-counted.",
            "Duplicate messages are ignored automatically.",
            "Use Sync SMS history (Profile) to import the last 6 months in one go.",
            "If you change a category by hand your choice is kept, but a matching auto-rule takes priority — use “Force auto rule” to snap back."
        )
    ),
    HelpTopic(
        "Notifications inbox", Icons.Rounded.Notifications, Color(0xFFFACC15),
        "When Xpense can't confidently categorize a transaction, it files it under Others and lists it in Notifications so you can fix it later.",
        listOf(
            "Tap an item to create a rule for it — it (and similar future ones) gets categorized instantly.",
            "Items clear themselves once categorized. Dismiss one with ✕, or use Clear all at the top.",
            "The Pop-up alerts toggle controls the heads-up notification only — items are still saved to this list."
        )
    ),
    HelpTopic(
        "Archived transactions", Icons.Rounded.Inventory2, Color(0xFF4ADE80),
        "Archiving hides a transaction from all totals and charts — perfect for self-transfers or anything that isn't real spending.",
        listOf(
            "On Insights, swipe a transaction left to reveal Archive, then tap it. Nothing is archived until you tap, so an accidental swipe is harmless.",
            "Find everything archived under Profile → Archived transactions. Tap the restore button on a row, or swipe it right, to bring it back."
        )
    ),
    HelpTopic(
        "Backup & restore", Icons.Rounded.CloudSync, Color(0xFF38BDF8),
        "Keep your data safe and move it between phones with Google Drive (Profile → Backup & restore).",
        listOf(
            "Connect your Google account, then tap Back up now. The last backup time is shown.",
            "Automatic backup runs around 2:00 am — turn it on and pick Daily, Weekly or Monthly. Signing out of Drive turns it off.",
            "Restore offers Merge (adds the backup to your data, skipping duplicates) or Replace (wipes this device first). Replace can't be undone.",
            "Drive sign-in may be limited to Google accounts the app owner has approved."
        )
    )
)
