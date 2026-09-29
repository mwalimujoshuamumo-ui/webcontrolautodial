package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val autoCleanup by viewModel.isAutoCleanupEnabled.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()
    val pruningEnabled by viewModel.isPruningEnabled.collectAsState()
    val dbSize by viewModel.databaseSize.collectAsState()
    
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeepCleanDialog by remember { mutableStateOf(false) }

    val formatFileSize = { size: Long ->
        when {
            size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size.toDouble() / (1024 * 1024 * 1024))
            size >= 1024 * 1024 -> String.format("%.2f MB", size.toDouble() / (1024 * 1024))
            size >= 1024 -> String.format("%.2f KB", size.toDouble() / 1024)
            else -> "$size B"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = GreenLogo)
                        Spacer(Modifier.width(12.dp))
                        Text("Automated Cleanup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Text(
                        "Automatically delete old transaction history. The database is kept on SD card or external app storage, not internal storage.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Auto Cleanup", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = autoCleanup,
                            onCheckedChange = { viewModel.setAutoCleanupEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo)
                        )
                    }

                    if (autoCleanup) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Prune Large Data", fontWeight = FontWeight.Medium)
                                Text("Clear SMS bodies for older records but keep history", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = pruningEnabled,
                                onCheckedChange = { viewModel.setPruningEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo)
                            )
                        }

                        Text("Retention Period: $retentionDays days", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        
                        val options = listOf(7, 14, 30, 90)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { days ->
                                FilterChip(
                                    selected = retentionDays == days,
                                    onClick = { viewModel.setRetentionDays(days) },
                                    label = { Text("$days Days") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GreenLogo.copy(alpha = 0.2f),
                                        selectedLabelColor = GreenLogo
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color.Cyan)
                            Spacer(Modifier.width(12.dp))
                            Text("Database Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Text(
                            formatFileSize(dbSize),
                            fontWeight = FontWeight.Black,
                            color = if (dbSize > 100 * 1024 * 1024) Color.Red else GreenLogo,
                            fontSize = 18.sp
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.optimizeStorage() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("OPTIMIZE NOW", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.compactDatabase() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("VACUUM", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Text(
                        "Optimize reclaims space by pruning logs. Vacuum rebuilds the database file to physically reduce size.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SdCard, contentDescription = null, tint = Color.Yellow)
                        Spacer(Modifier.width(12.dp))
                        Text("App Cache", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Button(
                        onClick = { viewModel.clearAppCache() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                    ) {
                        Text("CLEAR APP CACHE", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Clears temporary files, Webview data, and image cache.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.width(12.dp))
                        Text("Manual Purge", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Button(
                        onClick = { showDeepCleanDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DEEP CLEAN DATABASE NOW", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Delete history and immediately reclaim space via VACUUM.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    if (showDeepCleanDialog) {
        AlertDialog(
            onDismissRequest = { showDeepCleanDialog = false },
            title = { Text("Confirm Deep Clean") },
            text = { Text("Delete all transactions and logs older than $retentionDays days and reclaim storage space? This is an intensive operation.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deepCleanDatabase(retentionDays)
                    showDeepCleanDialog = false
                }) {
                    Text("PROCEED", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepCleanDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Confirm Cleanup") },
            text = { Text("Are you sure you want to delete all transaction history older than $retentionDays days? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearOldTransactions(retentionDays)
                    showClearDialog = false
                }) {
                    Text("YES, DELETE", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}
