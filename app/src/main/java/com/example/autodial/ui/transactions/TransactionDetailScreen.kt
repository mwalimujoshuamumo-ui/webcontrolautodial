package com.example.autodial.ui.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.Transaction
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(viewModel: MainViewModel, transactionId: Int, onBack: () -> Unit) {
    val transactions by viewModel.allTransactions.observeAsState(emptyList())
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val transaction = transactions.find { it.id == transactionId }
    val context = LocalContext.current

    if (transaction == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Transaction not found", color = Color.Gray)
        }
        return
    }

    var showRetryConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAltOfferDialog by remember { mutableStateOf(false) }
    var showAltNumDialog by remember { mutableStateOf(false) }
    var showEditNotesDialog by remember { mutableStateOf(false) }
    var altPhone by remember { mutableStateOf("") }
    var editedNotes by remember { mutableStateOf(transaction.notes) }
    
    var showScheduleDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    // Edit Notes Dialog
    if (showEditNotesDialog) {
        AlertDialog(
            onDismissRequest = { showEditNotesDialog = false },
            title = { Text("Edit Transaction Notes") },
            text = {
                OutlinedTextField(
                    value = editedNotes,
                    onValueChange = { editedNotes = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    label = { Text("Notes") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTransactionNotes(transaction.id, editedNotes)
                    showEditNotesDialog = false
                }) { Text("Save", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNotesDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Retry Confirmation
    if (showRetryConfirm) {
        AlertDialog(
            onDismissRequest = { showRetryConfirm = false },
            title = { Text("Retry Transaction") },
            text = { Text("Do you want to retry this USSD automation?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.retryTransactions(listOf(transaction.id), context)
                    showRetryConfirm = false
                }) { Text("Yes", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showRetryConfirm = false }) { Text("No") }
            }
        )
    }

    // Delete Confirmation
    if (showDeleteConfirm) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete this transaction record? This action cannot be undone.",
            onConfirm = {
                viewModel.deleteTransactions(listOf(transaction.id))
                showDeleteConfirm = false
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    // Alt Offer Dialog
    if (showAltOfferDialog) {
        AlertDialog(
            onDismissRequest = { showAltOfferDialog = false },
            title = { Text("Select Alternative Offer") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    offers.forEach { offer ->
                        ListItem(
                            headlineContent = { Text(offer.name) },
                            supportingContent = { Text("KSh ${offer.amount}") },
                            modifier = Modifier.clickable {
                                viewModel.runAlternativeOffer(transaction, offer, context)
                                showAltOfferDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAltOfferDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule Execution") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DatePicker(state = datePickerState, title = null, headline = null, showModeToggle = false)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showScheduleDialog = false
                    Toast.makeText(context, "Task Scheduled", Toast.LENGTH_SHORT).show()
                }) { Text("Confirm", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAltNumDialog) {
        AlertDialog(
            onDismissRequest = { showAltNumDialog = false },
            title = { Text("Run with Alternative Number") },
            text = {
                OutlinedTextField(
                    value = altPhone,
                    onValueChange = { altPhone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("07...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (altPhone.isNotEmpty()) {
                        viewModel.runOfferWithAltNumber(transaction, altPhone, context) 
                        showAltNumDialog = false
                    }
                }) { Text("Run", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showAltNumDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Compressed Header Info
            CompactTransactionHeader(transaction)

            // Top-up Maths Section
            TopUpMathsSection(transaction)
            
            // Expandable Raw Message
            ExpandableRawMessage(transaction.rawMessage)

            // Matched Offer Section
            MatchedOfferSection(transaction)

            // Transaction Notes Section
            TransactionNotesSection(transaction.notes) {
                editedNotes = transaction.notes
                showEditNotesDialog = true
            }

            // Switch Offer Section
            if (transaction.isSwitched) {
                SwitchInfoSection(transaction)
            }

            // Step Trail Section
            StepTrailSection(transaction)

            // Final Response Section
            ResponseSection(transaction)

            // Verification Section
            if (transaction.verificationSms.isNotEmpty() || transaction.isVerified) {
                VerificationSmsSection(
                    message = transaction.verificationSms.ifEmpty { "Verified via Carrier SMS confirmation" },
                    isVerified = transaction.isVerified
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action Cards Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Retry", Icons.Default.Refresh, Color(0xFFE3F2FD), Color(0xFF1E88E5), Modifier.weight(1f)) {
                    showRetryConfirm = true
                }
                ActionCard("Apply Debt", Icons.Default.AccountBalanceWallet, Color(0xFFE8F5E9), Color(0xFF43A047), Modifier.weight(1f)) {
                    viewModel.applyTransactionToDebt(transaction, context)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Alt Num", Icons.Default.Call, Color(0xFFE8F5E9), Color(0xFF43A047), Modifier.weight(1f)) {
                    showAltNumDialog = true
                }
                ActionCard("Alt Offer", Icons.Default.Layers, Color(0xFFF3E5F5), Color(0xFF8E24AA), Modifier.weight(1f)) {
                    showAltOfferDialog = true
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Schedule", Icons.Default.Event, Color(0xFFFFF3E0), Color(0xFFFB8C00), Modifier.weight(1f)) {
                    showScheduleDialog = true
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StepTrailSection(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val trailData = transaction.executionTrail.split("*").filter { it.isNotBlank() }
            val stepCount = trailData.count { !it.startsWith("FINAL:::") && !it.startsWith("LAUNCH:::") }

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Step Trail", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                
                Surface(
                    color = Color(0xFF00C853),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { /* Show full timeline logic */ }
                ) {
                    Text(
                        text = "View timeline ($stepCount)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Trail Items
            trailData.forEach { data ->
                val parts = data.split(":::")
                val value = parts.getOrNull(0) ?: "?"
                val rawLabel = parts.getOrNull(1) ?: "Step"
                val status = parts.getOrNull(3) ?: "SUCCESS"
                
                if (value == "FINAL") return@forEach

                val isDial = rawLabel.contains("LAUNCH")
                
                // Formatting for the image style: "8:GIFT SMS"
                val displayText = if (isDial) value else {
                    if (value.matches(Regex("\\d+"))) "$value:$rawLabel" else rawLabel
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3D2F).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = if (isDial) Color(0xFFFBC02D) else Color(0xFF00C853),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isDial) "DIAL" else value,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (isDial) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = displayText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        
                        if (!isDial) {
                            Text(
                                text = "OK",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponseSection(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Response", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (transaction.isFallbackExecuted) {
                    Surface(
                        color = Color(0xFFFFD600).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "FALLBACK RUN",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color(0xFFFFD600),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = Color.Yellow.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "1 history item",
                                color = Color.Yellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            
            Text(
                text = transaction.finalResponse.ifEmpty { "No response recorded." },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun VerificationSmsSection(message: String, isVerified: Boolean = true) {
    val borderColor = if (isVerified) Color(0xFF2196F3) else Color.Red
    val headerTitle = if (isVerified) "Verification Message Used" else "Unverified / Negative Carrier SMS"
    val iconTint = if (isVerified) Color(0xFF2196F3) else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isVerified) Icons.Default.Verified else Icons.Default.Warning,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(headerTitle, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Surface(
                    color = iconTint.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (isVerified) "VERIFIED" else "FAILED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = iconTint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun MatchedOfferSection(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GreenLogo.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Matched Offer", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = transaction.offerName.ifEmpty { "No offer matched" },
                color = if (transaction.offerName == "Debt Payment") GreenLogo else MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            
            if (transaction.offerName == "Debt Payment") {
                Text(
                    text = "This payment was applied to client debt.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TransactionNotesSection(notes: String, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transaction Notes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(12.dp))
            
            Text(
                text = if (notes.isEmpty()) "Tap to add notes..." else notes,
                color = if (notes.isEmpty()) Color.Gray else Color.LightGray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SwitchInfoSection(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GreenLogo.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Offer Switched", color = GreenLogo, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "Original Offer: ${transaction.originalOfferName ?: "Unknown"}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = "Executed Offer: ${transaction.offerName}",
                color = GreenLogo,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Reason: ${transaction.switchReason ?: "N/A"}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CompactTransactionHeader(transaction: Transaction) {
    val context = LocalContext.current
    val fullSdf = SimpleDateFormat("MMM dd, yyyy - hh:mm:ss a", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sender", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        transaction.senderName.ifEmpty { "M-PESA" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { copyToClipboard(context, "Sender", transaction.senderName) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailInfoGridItem(
                    label = "Phone",
                    value = transaction.customerNumber,
                    modifier = Modifier.weight(1f),
                    onCopy = { copyToClipboard(context, "Phone", transaction.customerNumber) }
                )
                DetailInfoGridItem(
                    label = "Config",
                    value = if (transaction.usedUssdType == "ALT") "Alternative" else "Main",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text("Arrival Time", fontSize = 12.sp, color = Color.Gray)
            Text(
                fullSdf.format(Date(transaction.date)),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DetailInfoGridItem(label: String, value: String, modifier: Modifier = Modifier, onCopy: (() -> Unit)? = null) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (onCopy != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(12.dp)
                        .clickable { onCopy() }
                )
            }
        }
    }
}

@Composable
fun TopUpMathsSection(transaction: Transaction) {
    val profit = transaction.amount - transaction.safPrice
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GreenLogo.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Top-up Maths", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MathItem("Customer Sent", "KSh ${transaction.amount}", MaterialTheme.colorScheme.onSurface)
                Text("-", color = Color.Gray)
                MathItem("Offer Cost", "KSh ${transaction.safPrice}", Color.Red.copy(alpha = 0.7f))
                Text("=", color = Color.Gray)
                MathItem("Commission", "KSh ${transaction.commissionEarned}", GreenLogo)
            }

            if (profit != transaction.commissionEarned && transaction.safPrice > 0) {
                 Spacer(Modifier.height(8.dp))
                 Text(
                     text = "Net Margin: KSh ${String.format("%.2f", profit)}",
                     color = Color.Gray,
                     fontSize = 11.sp
                 )
            }
        }
    }
}

@Composable
fun MathItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun ExpandableRawMessage(message: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Raw Message", fontSize = 12.sp, color = Color.Gray)
                Row {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { copyToClipboard(context, "Message", message) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(
                    message.take(60) + if (message.length > 60) "..." else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun DetailInfoCard(label: String, value: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = Color.Gray)
                Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ActionCard(label: String, icon: ImageVector, bgColor: Color, iconColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = iconColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
