package com.example.xpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.AmountRange
import com.example.xpense.ui.TxFilter
import com.example.xpense.ui.TxSort
import com.example.xpense.ui.TxSource
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils

/**
 * Filter & sort for the Insights list. Edits a draft; nothing changes until "Show N results".
 * [resultCount] previews how many rows the draft would keep.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initial: TxFilter,
    categories: List<Category>,
    resultCount: (TxFilter) -> Int,
    onDismiss: () -> Unit,
    onApply: (TxFilter) -> Unit
) {
    val c = XpenseTheme.colors
    var draft by remember { mutableStateOf(initial) }
    val count = remember(draft) { resultCount(draft) }

    XBottomSheet(onDismiss, "Filter & sort", "Refine your transactions") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Group("Sort by") {
                val icons = mapOf(
                    TxSort.NEWEST to Icons.Rounded.Schedule, TxSort.OLDEST to Icons.Rounded.History,
                    TxSort.HIGHEST to Icons.Rounded.ArrowUpward, TxSort.LOWEST to Icons.Rounded.ArrowDownward
                )
                TxSort.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { s ->
                            val on = draft.sort == s
                            val shape = RoundedCornerShape(16.dp)
                            Row(
                                Modifier.weight(1f).clip(shape).background(if (on) c.acSoft else c.card2)
                                    .border(1.dp, if (on) c.ac else c.line, shape).clickable { draft = draft.copy(sort = s) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                XIcon(icons.getValue(s), 19.dp, if (on) c.ac else c.tx2)
                                Text(s.label, style = XType.smallStrong, color = c.tx)
                            }
                        }
                    }
                }
            }
            Group("Categories") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        val on = cat.id in draft.categoryIds
                        CategoryChip(cat.name, CategoryUtils.getCategoryIcon(cat), CategoryUtils.getCategoryColor(cat), on, {
                            draft = draft.copy(categoryIds = if (on) draft.categoryIds - cat.id else draft.categoryIds + cat.id)
                        })
                    }
                }
            }
            Group("Amount") {
                SegmentedControl(AmountRange.entries.map { it.label }, draft.amount.ordinal, { draft = draft.copy(amount = AmountRange.entries[it]) }, compact = true)
            }
            Group("Source") {
                SegmentedControl(TxSource.entries.map { it.label }, draft.source.ordinal, { draft = draft.copy(source = TxSource.entries[it]) }, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                XButton("Reset", { draft = TxFilter() }, Modifier.weight(1f), style = BtnStyle.Secondary, height = 54.dp, radius = 18.dp)
                XButton(
                    if (count == 1) "Show 1 result" else "Show $count results",
                    { onApply(draft) }, Modifier.weight(1.6f), height = 54.dp, radius = 18.dp
                )
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = XType.overline, color = XpenseTheme.colors.tx2)
        content()
    }
}
