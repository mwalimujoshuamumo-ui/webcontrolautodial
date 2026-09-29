package com.example.autodial.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.*
import com.example.autodial.ui.theme.GreenLogo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailsScreen(viewModel: ClientViewModel, clientId: Int, onBack: () -> Unit) {
    val client by viewModel.getClient(clientId).collectAsState(initial = null)
    val debtHistory by viewModel.getDebtHistory(clientId).collectAsState(initial = emptyList())
    val smsHistory by viewModel.getSmsHistory(clientId).collectAsState(initial = emptyList())
    val recentPurchases by if (client != null) {
        viewModel.getRecentTransactions(client!!.phoneNumber).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<Transaction>()) }
    }

    var showDebtDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var debtDialogType by remember { mutableStateOf("DEBT") } // DEBT or PAYMENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (client == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenLogo)
            }
        } else {
            val c = client!!
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Customer Information
                SectionCard(title = "Customer Information", icon = Icons.Default.Person) {
                    InfoRow("Name", c.name.ifBlank { "UNKNOWN" })
                    InfoRow("Phone Number", c.phoneNumber)
                    InfoRow("First Added", formatDate(c.dateAdded))
                    InfoRow("Total Purchases", c.totalPurchases.toString())
                    InfoRow("Status", if (c.isBlacklisted) "BLACKLISTED" else "Active", if (c.isBlacklisted) Color.Red else Color(0xFF4CAF50))
                }

                // Balance / Debt Section
                SectionCard(title = "Balance / Debt", icon = Icons.Default.AccountBalanceWallet) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Current Balance", fontSize = 14.sp, color = Color.Gray)
                        Text("KSh ${c.outstandingDebt.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = if (c.outstandingDebt > 0) Color.Red else Color.Black)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { 
                                    debtDialogType = "DEBT"
                                    showDebtDialog = true 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Add Debt")
                            }
                            Button(
                                onClick = { 
                                    debtDialogType = "PAYMENT"
                                    showPaymentDialog = true 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Record Payment")
                            }
                        }
                    }
                }

                // Blacklist Status
                SectionCard(title = "Blacklist Management", icon = Icons.Default.Block) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (c.isBlacklisted) "This client is blacklisted." else "This client is active.", fontWeight = FontWeight.Medium)
                            if (c.isBlacklisted && c.blacklistedAt != null) {
                                Text("Blacklisted on ${formatDate(c.blacklistedAt)}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Switch(
                            checked = c.isBlacklisted,
                            onCheckedChange = { viewModel.toggleBlacklist(c) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Red, checkedTrackColor = Color.Red.copy(alpha = 0.5f))
                        )
                    }
                }

                // Five Recent Purchases
                SectionCard(title = "Recent Purchases (Last 5)", icon = Icons.Default.ShoppingBag) {
                    if (recentPurchases.isEmpty()) {
                        Text("No purchase history found.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        recentPurchases.forEach { tx ->
                            PurchaseItem(tx)
                            if (tx != recentPurchases.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                // SMS History
                SectionCard(title = "SMS History", icon = Icons.Default.Sms) {
                    if (smsHistory.isEmpty()) {
                        Text("No SMS history found.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        smsHistory.take(10).forEach { sms ->
                            SmsHistoryItem(sms)
                            if (sms != smsHistory.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDebtDialog) {
        DebtActionDialog(
            title = "Add Debt",
            onDismiss = { showDebtDialog = false },
            onConfirm = { amount, notes ->
                viewModel.addDebt(clientId, amount, notes)
                showDebtDialog = false
            }
        )
    }

    if (showPaymentDialog) {
        DebtActionDialog(
            title = "Record Payment",
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, notes ->
                viewModel.recordPayment(clientId, amount, notes)
                showPaymentDialog = false
            }
        )
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = GreenLogo)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = valueColor)
    }
}

@Composable
fun PurchaseItem(tx: Transaction) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(tx.offerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("KSh ${tx.amount.toInt()}", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(formatDate(tx.date), fontSize = 12.sp, color = Color.Gray)
            Text(tx.status, fontSize = 12.sp, color = if (tx.status == "SUCCESS") Color(0xFF4CAF50) else Color.Red)
        }
        if (tx.transactionCode.isNotBlank()) {
            Text("Ref: ${tx.transactionCode}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SmsHistoryItem(sms: ClientSms) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(sms.type.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenLogo)
            Text(formatDate(sms.date), fontSize = 11.sp, color = Color.Gray)
        }
        Text(sms.message, fontSize = 13.sp)
        Text("Status: ${sms.status}", fontSize = 11.sp, color = if (sms.status == "SENT") Color(0xFF4CAF50) else Color.Red)
    }
}

@Composable
fun DebtActionDialog(title: String, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (KSh)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, notes) },
                enabled = amount.toDoubleOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
