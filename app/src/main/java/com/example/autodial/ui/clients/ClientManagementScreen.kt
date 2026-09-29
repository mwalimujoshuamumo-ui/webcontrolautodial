package com.example.autodial.ui.clients

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autodial.data.Client
import com.example.autodial.ui.MainViewModel
import com.example.autodial.util.PreferenceManager
import com.example.autodial.ui.theme.GreenLogo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClientManagementScreen(viewModel: ClientViewModel, mainViewModel: MainViewModel, navController: NavController) {
    val clients by viewModel.allClients.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val totalCount by viewModel.clientCount.collectAsState()
    val blacklistedCount by viewModel.blacklistedCount.collectAsState()
    val debtorsCount by viewModel.debtorsCount.collectAsState()
    
    val isAutosaveEnabled by mainViewModel.isAutosaveEnabled.collectAsState()
    val autosavePrefix by mainViewModel.autosavePrefix.collectAsState()

    val context = LocalContext.current
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanResult by mainViewModel.scanResult.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("CLIENT LIST", "IMPORT CONTACTS")

    var selectedClients by remember { mutableStateOf(setOf<Int>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAutosaveMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categories = listOf("All", "Most Active", "Most Spent", "Most Recent", "Debtors", "Blacklisted")

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Clients") },
            text = { Text("Are you sure you want to delete ${selectedClients.size} clients?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteClients(selectedClients.toList())
                    selectedClients = emptySet()
                    isSelectionMode = false
                    showDeleteConfirm = false
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // M-Pesa Scan Dialog logic
    if (scanResult != null) {
        val parts = scanResult!!.split("|")
        val resultType = parts[0]
        
        if (resultType == "COMPLETED" || resultType == "CONTACTS_SYNCED") {
            val totalScanned = parts.getOrNull(1)?.substringAfter(":") ?: "0"
            val matchedCount = parts.getOrNull(2)?.substringAfter(":") ?: "0"
            val alreadyDone = parts.getOrNull(4)?.substringAfter(":") ?: "0"
            val newClients = parts.getOrNull(7)?.substringAfter(":") ?: "0"

            AlertDialog(
                onDismissRequest = { mainViewModel.clearScanResult() },
                containerColor = Color(0xFF1B222C),
                title = { 
                    Text(
                        if (resultType == "CONTACTS_SYNCED") "CONTACTS IMPORTED" else "IMPORT COMPLETE", 
                        fontWeight = FontWeight.Black, 
                        color = GreenLogo
                    ) 
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResultRow("TOTAL MESSAGES SCANNED", totalScanned, Color.White)
                        ResultRow("NEW CLIENTS FOUND", newClients, GreenLogo)
                        ResultRow("ALREADY IN DATABASE", alreadyDone, Color.Gray)
                        
                        if (resultType == "COMPLETED") {
                            val processedAmt = parts.getOrNull(5)?.substringAfter(":") ?: "0.0"
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.05f))
                            ResultRow("POTENTIAL REVENUE", "KSh $processedAmt", GreenLogo)
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text("Clients have been automatically added/updated in your list.", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { mainViewModel.clearScanResult() },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                    ) {
                        Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedClients.size} Selected") },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedClients = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                selectedClients = clients.map { it.id }.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(onClick = { showBroadcastDialog = true }) {
                                Icon(Icons.Default.Send, contentDescription = "Broadcast SMS")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("Client Management", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { showCategoryMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                                    categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category) },
                                            onClick = {
                                                viewModel.setCategoryFilter(category)
                                                showCategoryMenu = false
                                            },
                                            trailingIcon = if (categoryFilter == category) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = GreenLogo) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { showAutosaveMenu = true }) {
                                Icon(Icons.Default.ContactPage, contentDescription = "Autosave Settings", tint = if (isAutosaveEnabled) GreenLogo else Color.Gray)
                                DropdownMenu(
                                    expanded = showAutosaveMenu, 
                                    onDismissRequest = { showAutosaveMenu = false },
                                    modifier = Modifier.width(240.dp).padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Autosave Contacts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Enable", fontSize = 12.sp)
                                            Switch(
                                                checked = isAutosaveEnabled,
                                                onCheckedChange = { mainViewModel.setAutosaveEnabled(it) },
                                                colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo)
                                            )
                                        }
                                        
                                        OutlinedTextField(
                                            value = autosavePrefix,
                                            onValueChange = { mainViewModel.setAutosavePrefix(it) },
                                            label = { Text("Name Prefix", fontSize = 10.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                        )
                                        
                                        Text(
                                            "New clients will be saved to phone with this prefix.", 
                                            fontSize = 10.sp, 
                                            color = Color.Gray,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Client")
                            }
                        }
                    )
                }
                
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = GreenLogo,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = GreenLogo
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title, 
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Normal,
                                    fontSize = 12.sp
                                ) 
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = GreenLogo,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Client")
                }
            }
        }
    ) { padding ->
        if (selectedTabIndex == 0) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search by name or phone number...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("Total", totalCount.toString(), Icons.Default.People, Modifier.weight(1f))
                    StatCard("Blacklisted", blacklistedCount.toString(), Icons.Default.Block, Modifier.weight(1f), Color.Red)
                    StatCard("Debtors", debtorsCount.toString(), Icons.Default.MoneyOff, Modifier.weight(1f), Color(0xFFFF9800))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Client List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clients) { client ->
                        ClientItem(
                            client = client,
                            isSelected = selectedClients.contains(client.id),
                            isSelectionMode = isSelectionMode,
                            onToggleSelect = {
                                if (selectedClients.contains(client.id)) {
                                    selectedClients = selectedClients - client.id
                                    if (selectedClients.isEmpty()) isSelectionMode = false
                                } else {
                                    selectedClients = selectedClients + client.id
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedClients = setOf(client.id)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (selectedClients.contains(client.id)) {
                                        selectedClients = selectedClients - client.id
                                        if (selectedClients.isEmpty()) isSelectionMode = false
                                    } else {
                                        selectedClients = selectedClients + client.id
                                    }
                                } else {
                                    navController.navigate("client_details/${client.id}")
                                }
                            }
                        )
                    }
                }
            }
        } else {
            ImportTab(padding, mainViewModel)
        }
    }

    if (showAddDialog) {
        AddClientDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, debt ->
                viewModel.addClient(name, phone, debt)
                showAddDialog = false
            }
        )
    }

    if (showBroadcastDialog) {
        BroadcastSmsDialog(
            recipientCount = selectedClients.size,
            onDismiss = { showBroadcastDialog = false },
            onConfirm = { message, notes ->
                viewModel.broadcastSms(selectedClients.toList(), message, notes)
                showBroadcastDialog = false
                isSelectionMode = false
                selectedClients = emptySet()
            }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, color: Color = GreenLogo) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientItem(
    client: Client,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GreenLogo.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(client.name.ifBlank { "UNKNOWN" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(client.phoneNumber, fontSize = 14.sp, color = Color.Gray)
            }

            Column(horizontalAlignment = Alignment.End) {
                if (client.outstandingDebt > 0) {
                    Text("KSh ${client.outstandingDebt.toInt()}", color = Color.Red, fontWeight = FontWeight.Bold)
                    Text("Debt", fontSize = 10.sp, color = Color.Red)
                }
                
                if (client.isBlacklisted) {
                    Box(
                        modifier = Modifier
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("BLACKLISTED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddClientDialog(onDismiss: () -> Unit, onConfirm: (String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var debt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Client", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = debt, onValueChange = { debt = it }, label = { Text("Initial Debt (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone, debt.toDoubleOrNull() ?: 0.0) },
                enabled = phone.length >= 10,
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BroadcastSmsDialog(recipientCount: Int, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var message by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast SMS & Notes", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sending to $recipientCount clients.")
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Type your message here...") }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    placeholder = { Text("Add notes for this broadcast...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(message, notes) },
                enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
            ) {
                Text("Send Individually")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportTab(padding: PaddingValues, viewModel: MainViewModel) {
    val isScanning by viewModel.isScanning.collectAsState()
    val context = LocalContext.current
    var scanSpan by remember { mutableStateOf(168f) } // Default 1 week

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "IMPORT CLIENTS", 
            fontWeight = FontWeight.Black, 
            fontSize = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Populate your database from external sources", 
            color = Color.Gray, 
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(24.dp))

        // M-PESA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = GreenLogo)
                    Spacer(Modifier.width(12.dp))
                    Text("M-PESA INBOX RECOVERY", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Scans your SMS inbox for M-Pesa payment confirmations and automatically adds the senders as clients.", color = Color.Gray, fontSize = 12.sp)
                
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SCAN DEPTH", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    val label = when {
                        scanSpan < 24 -> "${scanSpan.toInt()} HOURS"
                        scanSpan < 168 -> "${(scanSpan / 24).toInt()} DAYS"
                        scanSpan < 720 -> "${(scanSpan / 168).toInt()} WEEKS"
                        else -> "${(scanSpan / 720).toInt()} MONTHS"
                    }
                    Text(label, color = GreenLogo, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                
                Slider(
                    value = scanSpan,
                    onValueChange = { scanSpan = it },
                    valueRange = 1f..2160f, // Up to 3 months
                    colors = SliderDefaults.colors(thumbColor = GreenLogo, activeTrackColor = GreenLogo)
                )

                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.scanMpesaInbox(context, scanSpan.toInt(), importContactsOnly = true) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("SCANNING...", color = Color.Black, fontWeight = FontWeight.Black)
                    } else {
                        Text("IMPORT CONTACTS ONLY", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { viewModel.scanMpesaInbox(context, scanSpan.toInt(), importContactsOnly = false) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SCAN & PROCESS PAYMENTS", color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Placeholder cards
        ImportPlaceholderCard("IMPORT FROM VCF", "Load contacts from a .vcf file export", Icons.Default.FileDownload)
        Spacer(Modifier.height(16.dp))

        // Device Contacts Import Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContactPhone, contentDescription = null, tint = GreenLogo)
                    Spacer(Modifier.width(12.dp))
                    Text("DEVICE CONTACTS IMPORT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Import all contacts from your phone's address book into Autodial clients.", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.importDeviceContacts(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("IMPORTING...", color = Color.Black, fontWeight = FontWeight.Black)
                    } else {
                        Text("IMPORT ALL DEVICE CONTACTS", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ImportPlaceholderCard(title: String, subtitle: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Gray)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.6f))
            }
            Text("SOON", color = Color.Gray.copy(alpha = 0.4f), fontWeight = FontWeight.Black, fontSize = 10.sp)
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
