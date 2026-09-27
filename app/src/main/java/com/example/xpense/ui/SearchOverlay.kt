package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils

/**
 * Full-screen search across transactions, categories and rules. Opened from Home, Insights and
 * Categories with a scope matching where it came from; tapping a result opens it.
 */
@Composable
fun SearchOverlay(viewModel: ExpenseViewModel, initialScope: SearchScope) {
    val c = XpenseTheme.colors
    val active     by viewModel.activeExpenses.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val rules      by viewModel.allRules.collectAsState()
    val recent     by viewModel.recentSearches.collectAsState()

    var q by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(initialScope) }
    val results = remember(q, active, categories, rules) { searchAll(q, active, categories, rules) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { viewModel.closeSearch() }

    fun open(action: () -> Unit) {
        viewModel.addRecentSearch(q)
        keyboard?.hide()
        viewModel.closeSearch()
        action()
    }

    GlowBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.weight(1f).height(50.dp)
                            .border(4.dp, c.acSoft, RoundedCornerShape(19.dp)).padding(3.dp)
                            .clip(shape).background(c.bg2).border(1.5.dp, c.ac, shape)
                            .padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        XIcon(Icons.Rounded.Search, 21.dp, c.ac)
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text(placeholder(scope), style = XType.body, color = c.tx3, maxLines = 1)
                            BasicTextField(
                                q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus),
                                singleLine = true, textStyle = XType.body.copy(color = c.tx), cursorBrush = SolidColor(c.ac),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { viewModel.addRecentSearch(q); keyboard?.hide() })
                            )
                        }
                        if (q.isNotEmpty()) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c.card2).clickable { q = "" }, contentAlignment = Alignment.Center) {
                                XIcon(Icons.Rounded.Close, 15.dp, c.tx2)
                            }
                        }
                    }
                    LinkText("Cancel", { keyboard?.hide(); viewModel.closeSearch() }, style = XType.bodyStrong)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 20.dp)) {
                    items(SearchScope.entries) { s ->
                        Pill(s.label, s == scope, { scope = s }, trailing = if (q.isNotBlank()) results.count(s).toString() else null)
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (q.isBlank()) {
                    if (recent.isNotEmpty()) {
                        item {
                            Overline("Recent") { LinkText("Clear", { viewModel.clearRecentSearches() }) }
                        }
                        items(recent, key = { "r_$it" }) { r ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { q = r }.padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                XIcon(Icons.Rounded.History, 20.dp, c.tx3)
                                Text(r, style = XType.body, color = c.tx, modifier = Modifier.weight(1f))
                                Box(Modifier.clip(CircleShape).clickable { viewModel.removeRecentSearch(r) }.padding(2.dp)) { XIcon(Icons.Rounded.Close, 18.dp, c.tx3) }
                            }
                        }
                    }
                    item { Overline("Browse by category", Modifier.padding(top = if (recent.isNotEmpty()) 14.dp else 0.dp, bottom = 2.dp)) }
                    items(categories.chunked(4)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { cat ->
                                val col = CategoryUtils.getCategoryColor(cat)
                                GlassCard(
                                    Modifier.weight(1f), radius = 18.dp, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                                    onClick = { open { viewModel.openCategory(cat.id) } }
                                ) {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconTile(CategoryUtils.getCategoryIcon(cat), col, size = 38.dp, radius = 12.dp, iconSize = 19.dp)
                                        Text(cat.name, style = XType.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    val showTx = scope == SearchScope.ALL || scope == SearchScope.TRANSACTIONS
                    val showCat = scope == SearchScope.ALL || scope == SearchScope.CATEGORIES
                    val showRules = scope == SearchScope.ALL || scope == SearchScope.RULES
                    val inScope = results.count(scope)
                    if (inScope == 0 && results.total > 0) {
                        item {
                            InfoBanner(Icons.Rounded.Info, "No ${scope.label.lowercase()} match. ${results.total} found in other tabs.", accent = true, action = "Show all", onAction = { scope = SearchScope.ALL })
                        }
                    }
                    if (showCat && results.categories.isNotEmpty()) {
                        item { ResultHeader("Categories", results.categories.size) }
                        items(results.categories, key = { "c_${it.id}" }) { cat ->
                            val n = active.count { it.category.id == cat.id }
                            ResultRow(CategoryUtils.getCategoryIcon(cat), CategoryUtils.getCategoryColor(cat), cat.name, q, "$n transaction${if (n == 1) "" else "s"}", null) {
                                open { viewModel.openCategory(cat.id) }
                            }
                        }
                    }
                    if (showRules && results.rules.isNotEmpty()) {
                        item { ResultHeader("Rules", results.rules.size) }
                        items(results.rules, key = { "ru_${it.id}" }) { rule ->
                            val cat = categories.find { it.id == rule.categoryId }
                            val kws = splitAlternatives(rule.keyword)
                            val hit = kws.firstOrNull { it.contains(q.trim(), ignoreCase = true) }
                            ResultRow(
                                Icons.Rounded.Rule, cat?.let { CategoryUtils.getCategoryColor(it) } ?: c.ac,
                                rule.label?.takeIf { it.isNotBlank() } ?: kws.firstOrNull() ?: rule.keyword, q,
                                "${cat?.name ?: "Unknown"} · ${hit?.let { "matches “$it”" } ?: "${kws.size} keywords"}", null
                            ) { open { viewModel.openRule(rule.id) } }
                        }
                    }
                    if (showTx && results.transactions.isNotEmpty()) {
                        val shown = results.transactions.take(60)
                        item { ResultHeader("Transactions", results.transactions.size) }
                        items(shown, key = { "t_${it.expense.id}" }) { row ->
                            ResultRow(
                                CategoryUtils.getCategoryIcon(row.category), CategoryUtils.getCategoryColor(row.category),
                                row.expense.merchant, q, "${row.category.name} · ${formatCardDate(row.expense.date)}",
                                "-" + CurrencyUtils.exact(row.expense.amount)
                            ) { open { viewModel.editExpense(row.expense.id) } }
                        }
                    }
                    if (results.total == 0) {
                        item { EmptyState(Icons.Rounded.SearchOff, "No results for “${q.trim()}”", "Try a merchant, category or keyword.", tinted = false) }
                    }
                }
            }
        }
    }
}

private fun placeholder(scope: SearchScope) = when (scope) {
    SearchScope.ALL -> "Search everything"
    SearchScope.TRANSACTIONS -> "Search transactions"
    SearchScope.CATEGORIES -> "Search categories"
    SearchScope.RULES -> "Search rules & keywords"
}

@Composable
private fun ResultHeader(title: String, count: Int) {
    val c = XpenseTheme.colors
    Overline(title, Modifier.padding(top = 8.dp)) { Text(count.toString(), style = XType.monoS, color = c.tx3) }
}

@Composable
private fun ResultRow(icon: ImageVector, color: Color, title: String, query: String, subtitle: String, right: String?, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    val range = matchRange(title, query)
    val annotated = buildAnnotatedString {
        if (range == null) append(title) else {
            append(title.substring(0, range.first))
            withStyle(SpanStyle(color = c.ac, background = c.acSoft)) { append(title.substring(range.first, range.last + 1)) }
            append(title.substring(range.last + 1))
        }
    }
    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, contentPadding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, color)
            Column(Modifier.weight(1f)) {
                Text(annotated, style = XType.bodyStrong, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = XType.caption, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (right != null) Text(right, style = XType.mono.copy(fontSize = XType.small.fontSize), color = c.neg)
            else XIcon(Icons.Rounded.ChevronRight, 19.dp, c.tx3)
        }
    }
}
