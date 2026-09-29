package com.example.autodial.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@Composable
fun MpesaScanScreen(viewModel: MainViewModel) {
    val isScanning by viewModel.isScanning.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val context = LocalContext.current
    var scanSpan by remember { mutableStateOf(24f) }
    
    // We need to store and show the list of found transactions
    var foundTransactions by remember { mutableStateOf<List<com.example.autodial.data.Transaction>>(emptyList()) }
    var showResultsDialog by remember { mutableStateOf(false) }

    if (scanResult != null) {
        val parts = scanResult!!.split("|")
        if (parts[0] == "COMPLETED") {
            val totalScanned = parts.getOrNull(1)?.substringAfter(":") ?: "0"
            val matchedCount = parts.getOrNull(2)?.substringAfter(":") ?: "0"
            val unmatchedCount = parts.getOrNull(3)?.substringAfter(":") ?: "0"
            val alreadyDone = parts.getOrNull(4)?.substringAfter(":") ?: "0"
            val processedAmt = parts.getOrNull(5)?.substringAfter(":") ?: "0.0"
            val unmatchedAmt = parts.getOrNull(6)?.substringAfter(":") ?: "0.0"
            val newClients = parts.getOrNull(7)?.substringAfter(":") ?: "0"

            AlertDialog(
                onDismissRequest = { viewModel.clearScanResult() },
                containerColor = Color(0xFF1B222C),
                title = { Text("M-PESA SCAN COMPLETE", fontWeight = FontWeight.Black, color = GreenLogo) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResultRow("TOTAL MESSAGES SCANNED", totalScanned, Color.White)
                        ResultRow("ALREADY PROCESSED", alreadyDone, Color.Gray)
                        ResultRow("NEW MATCHED FOUND", matchedCount, GreenLogo)
                        ResultRow("NEW UNMATCHED FOUND", unmatchedCount, Color.Yellow)
                        ResultRow("NEW CLIENTS ADDED", newClients, GreenLogo)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.05f))
                        
                        ResultRow("RECOVERED REVENUE", "KSh $processedAmt", GreenLogo)
                        ResultRow("UNMATCHED REVENUE", "KSh $unmatchedAmt", Color.Red.copy(alpha = 0.7f))
                        
                        Spacer(Modifier.height(8.dp))
                        Text("Matched transactions have been queued. Unmatched items require manual Offer association.", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            viewModel.processAllUnmatched(context)
                            viewModel.clearScanResult() 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                    ) {
                        Text("PROCESS ALL FOUND", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearScanResult() }) { 
                        Text("CLOSE", color = Color.Gray) 
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        
        Box(
            modifier = Modifier.size(140.dp).background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ManageSearch, 
                contentDescription = null, 
                modifier = Modifier.size(72.dp), 
                tint = GreenLogo
            )
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(120.dp), color = GreenLogo, strokeWidth = 3.dp)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text("M-PESA INBOX RECOVERY", fontWeight = FontWeight.Black, color = Color.White, fontSize = 22.sp)
        Text("Scan to find and process missed transactions", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SCAN RANGE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${scanSpan.toInt()} HOURS", color = GreenLogo, fontWeight = FontWeight.Black)
                }
                
                Spacer(Modifier.height(16.dp))
                
                Slider(
                    value = scanSpan,
                    onValueChange = { scanSpan = it },
                    valueRange = 1f..24f, // Strictly 24 hours for Main Navigation scan
                    colors = SliderDefaults.colors(thumbColor = GreenLogo, activeTrackColor = GreenLogo)
                )
                
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1H", color = Color.Gray, fontSize = 10.sp)
                    Text("24H", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = { viewModel.scanMpesaInbox(context, scanSpan.toInt(), importContactsOnly = false) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            enabled = !isScanning,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
        ) {
            if (isScanning) {
                Text("SCANNING INBOX...", color = Color.Black, fontWeight = FontWeight.Black)
            } else {
                Text("START RECOVERY SCAN", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { viewModel.scanMpesaInbox(context, scanSpan.toInt(), importContactsOnly = true) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isScanning,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Text("IMPORT CONTACTS ONLY", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("SECURE LOCAL SCAN. NO DATA LEAVES THIS DEVICE.", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}
