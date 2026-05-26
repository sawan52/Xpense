package com.example.xpense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.ui.ExpenseScreen
import com.example.xpense.ui.ExpenseViewModel
import com.example.xpense.ui.Screen
import com.example.xpense.ui.SummaryScreen
import com.example.xpense.ui.theme.XpenseTheme

@Composable
fun CustomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color(0xFF4F46E5) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (selected) Color(0xFF4F46E5) else Color.Gray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XpenseTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                
                val context = LocalContext.current
                var hasSmsPermissions by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasSmsPermissions = permissions.values.all { it }
                }

                LaunchedEffect(Unit) {
                    if (!hasSmsPermissions) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (hasSmsPermissions) {
                            Surface(
                                modifier = Modifier
                                    .padding(start = 32.dp, end = 32.dp, bottom = 24.dp)
                                    .height(72.dp)
                                    .shadow(24.dp, RoundedCornerShape(36.dp))
                                    .clip(RoundedCornerShape(36.dp)),
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    CustomNavItem(
                                        selected = currentScreen == Screen.HOME,
                                        onClick = { viewModel.navigateTo(Screen.HOME) },
                                        label = "Home",
                                        icon = Icons.Default.Home
                                    )
                                    CustomNavItem(
                                        selected = currentScreen == Screen.TRACKER,
                                        onClick = { viewModel.navigateTo(Screen.TRACKER) },
                                        label = "Insights",
                                        icon = Icons.AutoMirrored.Filled.List
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (hasSmsPermissions) {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                Screen.HOME -> SummaryScreen(viewModel)
                                Screen.TRACKER -> ExpenseScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
