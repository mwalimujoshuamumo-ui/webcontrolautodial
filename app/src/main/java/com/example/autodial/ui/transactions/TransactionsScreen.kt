package com.example.autodial.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.Transaction
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.components.TransactionItem
import com.example.autodial.ui.theme.DarkBackground
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: MainViewModel, navController: NavController, initialFilter: String = "All") {
    val dateRestrictedTransactions by viewModel.filteredTransactions.observeAsState(emptyList())
    val allTransactions by viewModel.allTransactions.observeAsState(emptyList())
    val dateFilter by viewModel.transactionDateFilter.collectAsState()
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    val stats by viewModel.stats.observeAsState(com.example.autodial.ui.DashboardStats())
    val context = LocalContext.current
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    val filters = listOf("All", "Done", "Queued", "Failed", "Disabled", "Unmatched", "Verified", "Unverified", "Skipped", "Scheduled")
    val dateFilters = listOf("All", "Today", "Yesterday", "This Week")

    val transactionsToFilter = if (searchQuery.isNotBlank()) allTransactions else dateRestrictedTransactions

    val filteredTransactions = transactionsToFilter.filter {
        val normalizedQuery = searchQuery.trim().replace(" ", "")
        val queryForPhone = if (normalizedQuery.startsWith("254")) "0" + normalizedQuery.drop(3) else normalizedQuery
        
        val matchesSearch = searchQuery.isBlank() || 
            it.senderName.contains(searchQuery, ignoreCase = true) || 
            it.customerNumber.contains(queryForPhone) ||
            it.transactionCode.contains(searchQuery, ignoreCase = true) ||
            it.id.toString() == searchQuery.trim()

        val matchesFilter = selectedFilter == "All" || 
            (selectedFilter == "Done" && it.status == Transaction.STATUS_SUCCESS) ||
            (selectedFilter == "Queued" && (it.status == Transaction.STATUS_WAITING || it.status == Transaction.STATUS_PROCESSING)) ||
            (selectedFilter == "Verified" && it.isVerified) ||
            (selectedFilter == "Unverified" && !it.isVerified) ||
            (selectedFilter == "Disabled" && it.status == Transaction.STATUS_DISABLED) ||
            (selectedFilter == "Failed" && it.status == Transaction.STATUS_FAILED) ||
            (selectedFilter == "Unmatched" && it.status == Transaction.STATUS_UNMATCHED) ||
            (selectedFilter == "Skipped" && it.status == Transaction.STATUS_SKIPPED) ||
            (selectedFilter == "Scheduled" && it.status == Transaction.STATUS_SCHEDULED)

        matchesSearch && matchesFilter
    }

    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRetryConfirm by remember { mutableStateOf(false) }
    var showDateFilterMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    var bulkDeleteTarget by remember { mutableStateOf<String?>(null) }
    var bulkDeleteIds by remember { mutableStateOf<List<Int>>(emptyList()) }

    if (bulkDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { bulkDeleteTarget = null },
            title = { Text("Delete Transactions", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete ${bulkDeleteIds.size} transactions for $bulkDeleteTarget?") },
            confirmButton = {
                TextButton(onClick = {
                    if (bulkDeleteIds.isNotEmpty()) {
                        viewModel.deleteTransactions(bulkDeleteIds)
                        android.widget.Toast.makeText(context, "Deleted ${bulkDeleteIds.size} records", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    bulkDeleteTarget = null
                    bulkDeleteIds = emptyList()
                }) { Text("DELETE ALL", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteTarget = null }) { Text("CANCEL") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transactions") },
            text = { Text("Are you sure you want to delete ${selectedIds.size} transaction records?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactions(selectedIds.toList())
                    selectedIds = emptySet()
                    showDeleteConfirm = false
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRetryConfirm) {
        AlertDialog(
            onDismissRequest = { showRetryConfirm = false },
            title = { Text("Retry Transactions") },
            text = { Text("Do you want to retry ${selectedIds.size} USSD automations?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.retryTransactions(selectedIds.toList(), context)
                    selectedIds = emptySet()
                    showRetryConfirm = false
                }) { Text("Yes", color = Color(0xFF4CAF50)) }
            },
            dismissButton = {
                TextButton(onClick = { showRetryConfirm = false }) { Text("No") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedIds.size} Selected", color = Color.White)
                    } else {
                        Text("Transactions", color = Color.White)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { 
                            selectedIds = filteredTransactions.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
                        }
                        IconButton(onClick = { showRetryConfirm = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White)
                        }
                        IconButton(onClick = { showStatusMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Mark As", tint = Color.White)
                            DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Mark as Complete") },
                                    onClick = {
                                        viewModel.updateTransactionsStatus(selectedIds.toList(), Transaction.STATUS_SUCCESS)
                                        selectedIds = emptySet()
                                        showStatusMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853)) }
                                )
                                listOf(Transaction.STATUS_SKIPPED, Transaction.STATUS_FAILED, Transaction.STATUS_WAITING).forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status) },
                                        onClick = {
                                            viewModel.updateTransactionsStatus(selectedIds.toList(), status)
                                            selectedIds = emptySet()
                                            showStatusMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    } else {
                        IconButton(onClick = { showDateFilterMenu = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Date Filter", tint = Color.White)
                            DropdownMenu(expanded = showDateFilterMenu, onDismissRequest = { showDateFilterMenu = false }) {
                                dateFilters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter) },
                                        onClick = {
                                            viewModel.setTransactionDateFilter(filter)
                                            showDateFilterMenu = false
                                        },
                                        trailingIcon = if (dateFilter == filter) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00C853)) }
                                        } else null
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                            DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                                if (selectedFilter != "All") {
                                    DropdownMenuItem(
                                        text = { Text("Delete Active Filter ($selectedFilter: ${filteredTransactions.size})", color = Color.Red, fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                        onClick = {
                                            bulkDeleteTarget = "Filter '$selectedFilter'"
                                            bulkDeleteIds = filteredTransactions.map { it.id }
                                            showOptionsMenu = false
                                        }
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete All Done Transactions") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853)) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_SUCCESS }.map { it.id }
                                        bulkDeleteTarget = "All Done"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Queued Transactions") },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_WAITING || it.status == Transaction.STATUS_PROCESSING }.map { it.id }
                                        bulkDeleteTarget = "All Queued"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Failed Transactions") },
                                    leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Red) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_FAILED }.map { it.id }
                                        bulkDeleteTarget = "All Failed"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Disabled Transactions") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_DISABLED }.map { it.id }
                                        bulkDeleteTarget = "All Disabled"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Unmatched Transactions") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_UNMATCHED }.map { it.id }
                                        bulkDeleteTarget = "All Unmatched"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Verified Transactions") },
                                    leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2196F3)) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.isVerified }.map { it.id }
                                        bulkDeleteTarget = "All Verified"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Unverified Transactions") },
                                    leadingIcon = { Icon(Icons.Default.GppMaybe, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { !it.isVerified }.map { it.id }
                                        bulkDeleteTarget = "All Unverified"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Skipped Transactions") },
                                    leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_SKIPPED }.map { it.id }
                                        bulkDeleteTarget = "All Skipped"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Scheduled Transactions") },
                                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, tint = Color.Gray) },
                                    onClick = {
                                        val ids = transactionsToFilter.filter { it.status == Transaction.STATUS_SCHEDULED }.map { it.id }
                                        bulkDeleteTarget = "All Scheduled"
                                        bulkDeleteIds = ids
                                        showOptionsMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Success Rate Header
            if (!isSelectionMode) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Success Rate", fontSize = 12.sp, color = Color.Gray)
                            Text("${stats.rate}%", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Today's Tasks", fontSize = 12.sp, color = Color.Gray)
                            Text("${stats.doneCount}/${stats.totalCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search by name, phone or ID", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )

                // Filter Chips in LazyRow for horizontal scroll
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00C853),
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = Color.Gray
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No transactions found", color = Color.Gray)
                        if (searchQuery.isNotBlank() || selectedFilter != "All") {
                            TextButton(onClick = {
                                searchQuery = ""
                                selectedFilter = "All"
                                viewModel.setTransactionDateFilter("All")
                            }) {
                                Text("Clear all filters")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredTransactions,
                        key = { it.id }
                    ) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            isHidden = isDataHidden,
                            isSelected = selectedIds.contains(transaction.id),
                            isSelectionMode = isSelectionMode,
                            modifier = Modifier.animateItem(
                                placementSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            ),
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedIds = setOf(transaction.id)
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (selectedIds.contains(transaction.id)) {
                                        selectedIds - transaction.id
                                    } else {
                                        selectedIds + transaction.id
                                    }
                                } else {
                                    navController.navigate("transaction_detail/${transaction.id}")
                                }
                            },
                            onMarkAsComplete = {
                                viewModel.updateTransactionsStatus(listOf(transaction.id), Transaction.STATUS_SUCCESS)
                            }
                        )
                    }
                }
            }
        }
    }
}
