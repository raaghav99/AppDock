package com.raaghav99.appdock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaghav99.appdock.data.AppDockDatabase
import com.raaghav99.appdock.model.AppEntry
import com.raaghav99.appdock.service.AppDockService
import com.raaghav99.appdock.service.ApkBackupHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start session service
        startService(Intent(this, AppDockService::class.java))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppDockScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDockScreen(context: Context) {
    val db = remember { AppDockDatabase.get(context) }
    val apps by db.appDao().getAllApps().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AppDock", fontWeight = FontWeight.Bold)
                        Text(
                            "${apps.count { it.isActive }} active · ${apps.size} in vault",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF0F3460)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add app to vault")
            }
        },
        containerColor = Color(0xFF16213E)
    ) { padding ->
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vault is empty", color = Color.Gray, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to add apps",
                        color = Color.DarkGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        onLaunch = {
                            AppDockService.launch(context, app.packageName)
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddAppDialog(
                context = context,
                onDismiss = { showAddDialog = false },
                onAdd = { pkg ->
                    scope.launch {
                        ApkBackupHelper.addToVault(context, pkg)
                    }
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AppCard(app: AppEntry, onLaunch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isActive) Color(0xFF0F3460) else Color(0xFF1A1A2E)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${app.apkSizeBytes / 1_000_000} MB · ${app.packageName}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                if (app.isActive) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "● ACTIVE",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!app.isActive) {
                IconButton(
                    onClick = onLaunch,
                    modifier = Modifier
                        .background(Color(0xFF0F3460), RoundedCornerShape(8.dp))
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Launch",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AddAppDialog(context: Context, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var pkgInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App to Vault") },
        text = {
            Column {
                Text(
                    "Enter the package name of an installed app to back it up.\nExample: com.instagram.android",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pkgInput,
                    onValueChange = { pkgInput = it },
                    label = { Text("Package name") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pkgInput.isNotBlank()) onAdd(pkgInput.trim()) },
                enabled = pkgInput.isNotBlank()
            ) {
                Text("Add to Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
