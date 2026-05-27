package com.example.xpense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.ui.*
import com.example.xpense.ui.components.AddExpenseBottomSheet
import com.example.xpense.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.example.xpense.ui.theme.XpenseTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val categories    by viewModel.allCategories.collectAsState()

                val context = LocalContext.current
                fun hasPermission(p: String) =
                    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

                var hasSmsPermission by remember {
                    mutableStateOf(
                        hasPermission(Manifest.permission.RECEIVE_SMS) &&
                        hasPermission(Manifest.permission.READ_SMS)
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results -> hasSmsPermission = results.values.all { it } }

                LaunchedEffect(Unit) {
                    if (!hasSmsPermission) {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        ))
                    }
                }

                var showAddSheet by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBg,
                    bottomBar = {
                        if (hasSmsPermission) {
                            XpenseBottomBar(
                                currentScreen = currentScreen,
                                onNavigate = { viewModel.navigateTo(it) },
                                onAddClick = { showAddSheet = true }
                            )
                        }
                    }
                ) { innerPadding ->
                    if (hasSmsPermission) {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                Screen.HOME           -> SummaryScreen(viewModel, onAddExpense = { showAddSheet = true })
                                Screen.INSIGHTS       -> ExpenseScreen(viewModel)
                                Screen.HISTORY        -> HistoryScreen(viewModel)
                                Screen.PROFILE        -> ProfileScreen(viewModel)
                                Screen.CATEGORY_RULES -> CategoryRuleScreen(viewModel)
                            }
                        }
                    } else {
                        // Permission denied state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBg)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(Icons.Default.Sms, null, tint = PurpleLight, modifier = Modifier.size(64.dp))
                                Text("SMS Permission Required", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Xpense needs SMS access to automatically track your bank transactions.",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(listOf(PurplePrimary, PurpleLight)))
                                        .clickable {
                                            permissionLauncher.launch(arrayOf(
                                                Manifest.permission.RECEIVE_SMS,
                                                Manifest.permission.READ_SMS
                                            ))
                                        }
                                        .padding(horizontal = 32.dp, vertical = 14.dp)
                                ) {
                                    Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (showAddSheet) {
                    AddExpenseBottomSheet(
                        categories = categories,
                        onDismiss = { showAddSheet = false },
                        onConfirm = { amount, merchant, categoryId, date ->
                            viewModel.addExpense(amount, merchant, categoryId, date)
                            showAddSheet = false
                        }
                    )
                }
            }
        }
    }
}

// ── Bottom navigation bar ────────────────────────────────────────────────────
@Composable
fun XpenseBottomBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = currentScreen == Screen.HOME,
                onClick = { onNavigate(Screen.HOME) }
            )
            NavBarItem(
                icon = Icons.Default.BarChart,
                label = "Insights",
                selected = currentScreen == Screen.INSIGHTS,
                onClick = { onNavigate(Screen.INSIGHTS) }
            )
            // Centre FAB
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PurplePrimary, PurpleLight)))
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, "Add expense", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            NavBarItem(
                icon = Icons.Default.History,
                label = "History",
                selected = currentScreen == Screen.HISTORY,
                onClick = { onNavigate(Screen.HISTORY) }
            )
            NavBarItem(
                icon = Icons.Default.Person,
                label = "Profile",
                selected = currentScreen == Screen.PROFILE,
                onClick = { onNavigate(Screen.PROFILE) }
            )
        }
    }
}

@Composable
private fun NavBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PurpleLight else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (selected) PurpleLight else TextMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
