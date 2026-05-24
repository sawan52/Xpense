package com.example.xpense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.ui.ExpenseScreen
import com.example.xpense.ui.ExpenseViewModel
import com.example.xpense.ui.Screen
import com.example.xpense.ui.SummaryScreen
import com.example.xpense.ui.theme.XpenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XpenseTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                
                val context = LocalContext.current
                var hasSmsPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasSmsPermission = isGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasSmsPermission) {
                        permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (hasSmsPermission) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen == Screen.HOME,
                                    onClick = { viewModel.navigateTo(Screen.HOME) },
                                    label = { Text("Home") },
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.TRACKER,
                                    onClick = { viewModel.navigateTo(Screen.TRACKER) },
                                    label = { Text("Tracker") },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (hasSmsPermission) {
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
