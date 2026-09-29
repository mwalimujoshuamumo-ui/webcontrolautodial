package com.example.autodial.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyCleanupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val storageStats by viewModel.storageStats.collectAsState()
    
    var showPurgeDialog by remember { mutableStateOf(false) }
    var showQuickDialog by remember { mutableStateOf(false) }
    var showDeepDialog by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableIntStateOf(7) }

    val formatFileSize = { size: Long ->
        when {
            size >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", size.toDouble() / (1024 * 1024 * 1024))
            size >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024))
            size >= 1024 -> String.format(Locale.US, "%.2f KB", size.toDouble() / 1024)
            else -> "$size B"
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDatabaseSize()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Cleanup", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Red
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STORAGE OVERVIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatFileSize(storageStats.totalSize), fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("Current Total", fontSize = 11.sp, color = Color.Gray)
                        }
                        
                        if (storageStats.deletedAmount > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("-" + formatFileSize(storageStats.deletedAmount), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                                Text("Reclaimed", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Text(
                "Cleanup Options",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                "Choose a cleanup level to fix issues or reclaim storage space.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.Gray,
                fontSize = 13.sp
            )

            // 1. Quick Cleanup
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                onClick = { showQuickDialog = true }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quick Cleanup", fontWeight = FontWeight.Bold, color = GreenLogo)
                        Text("Fix stuck processing and clear temporary cache. Safe to use anytime.", fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = GreenLogo)
                }
            }

            // 2. Deep Cleanup
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                onClick = { showDeepDialog = true }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deep Cleanup", fontWeight = FontWeight.Bold, color = Color.Cyan)
                        Text("Prune old logs and compact database. Does not delete transaction history.", fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.Cyan)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

            // 3. Emergency Purge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Emergency Purge", fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Delete history and client details older than $retentionDays days. Also wipes internal storage files and cache to reclaim maximum space.", fontSize = 12.sp, color = Color.Gray)

                    val options = listOf(3, 7, 14, 30)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = { retentionDays = days },
                                label = { Text("$days Days") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Red.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.Red
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { showPurgeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PERFORM FULL PURGE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }

    if (showQuickDialog) {
        AlertDialog(
            onDismissRequest = { showQuickDialog = false },
            title = { Text("Run Quick Cleanup") },
            text = { Text("This will fix any stuck USSD sessions and clear temporary cache files. It is fast and safe.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performQuickCleanup()
                    showQuickDialog = false
                }) { Text("RUN NOW", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickDialog = false }) { Text("CANCEL") }
            }
        )
    }

    if (showDeepDialog) {
        AlertDialog(
            onDismissRequest = { showDeepDialog = false },
            title = { Text("Run Deep Cleanup") },
            text = { Text("This will prune heavy logs and rebuild the database file to reclaim space. No history will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performDeepCleanup()
                    showDeepDialog = false
                }) { Text("RUN NOW", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeepDialog = false }) { Text("CANCEL") }
            }
        )
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text("Confirm Emergency Purge") },
            text = { Text("Are you sure you want to delete all history older than $retentionDays days? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performEmergencyCleanup(retentionDays)
                    showPurgeDialog = false
                }) {
                    Text("YES, PURGE DATA", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}
