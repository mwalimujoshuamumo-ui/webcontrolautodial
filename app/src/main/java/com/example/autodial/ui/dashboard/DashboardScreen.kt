package com.example.autodial.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.autodial.data.Transaction
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.components.TransactionItem
import com.example.autodial.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val isEnabled by viewModel.isAutomationEnabled.collectAsState()
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    val transactions: List<Transaction> by viewModel.todayTransactions.observeAsState(emptyList())
    val stats by viewModel.stats.observeAsState(com.example.autodial.ui.DashboardStats())
    val airtime1 by viewModel.airtimeSim1.collectAsState()
    val airtime2 by viewModel.airtimeSim2.collectAsState()
    val waitingCount by viewModel.waitingTransactionCount.observeAsState(0)
    val units by viewModel.units.collectAsState()
    val isUnlimited by viewModel.isUnlimitedActive.collectAsState()
    
    val context = LocalContext.current

    var showSimDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showProcessPrompt by remember { mutableStateOf(false) }
    var showAirtimeStats by remember { mutableStateOf(false) }
    var wasConditionMet by remember { mutableStateOf(true) } // Assume met on start to avoid initial popup

    // Logic to prompt processing only when issues are sorted
    LaunchedEffect(isEnabled, units, isUnlimited) {
        val isMet = isEnabled && (units > 0 || isUnlimited)
        if (isMet && !wasConditionMet && waitingCount > 0) {
            showProcessPrompt = true
        }
        wasConditionMet = isMet
    }

    if (showProcessPrompt) {
        AlertDialog(
            onDismissRequest = { showProcessPrompt = false },
            title = { Text("Pending Transactions", fontWeight = FontWeight.ExtraBold) },
            text = { Text("There are $waitingCount transactions waiting for execution. Would you like to start processing them now?") },
            confirmButton = {
                Button(
                    onClick = {
                        showProcessPrompt = false
                        viewModel.processAllWaiting(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                ) {
                    Text("Process All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProcessPrompt = false }) {
                    Text("Later", color = Color.Gray)
                }
            }
        )
    }

    val prefManager = remember { com.example.autodial.util.PreferenceManager(context) }
    var autoRefreshEnabled by remember { mutableStateOf(prefManager.isAutoRefreshAirtimeEnabled()) }
    var autoRefreshSim by remember { mutableStateOf(prefManager.getAutoRefreshSimSlot()) }
    var autoRefreshInterval by remember { mutableStateOf(prefManager.getAutoRefreshIntervalMins()) }
    var showAutoRefreshTab by remember { mutableStateOf(false) }

    if (showSimDialog) {
        AlertDialog(
            onDismissRequest = { showSimDialog = false },
            title = { Text(if (showAutoRefreshTab) "Airtime Auto-Refresh Config" else "Refresh Airtime Balance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAutoRefreshTab = false }) {
                            Text("Manual Refresh", color = if (!showAutoRefreshTab) GreenLogo else Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showAutoRefreshTab = true }) {
                            Text("Auto-Refresh Config", color = if (showAutoRefreshTab) GreenLogo else Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!showAutoRefreshTab) {
                        Text("Trigger immediate balance update via USSD:")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    showSimDialog = false
                                    viewModel.refreshAirtime(0, context)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                            ) {
                                Text("SIM 1", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showSimDialog = false
                                    viewModel.refreshAirtime(1, context)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                            ) {
                                Text("SIM 2", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showSimDialog = false
                                    viewModel.refreshAirtime(2, context)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                            ) {
                                Text("Both", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Auto-Refresh", fontWeight = FontWeight.Bold)
                            Switch(
                                checked = autoRefreshEnabled,
                                onCheckedChange = { autoRefreshEnabled = it }
                            )
                        }

                        if (autoRefreshEnabled) {
                            Text("Select Target SIM", fontSize = 12.sp, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = autoRefreshSim == 0, onClick = { autoRefreshSim = 0 }, label = { Text("SIM 1") })
                                FilterChip(selected = autoRefreshSim == 1, onClick = { autoRefreshSim = 1 }, label = { Text("SIM 2") })
                                FilterChip(selected = autoRefreshSim == 2, onClick = { autoRefreshSim = 2 }, label = { Text("Both") })
                            }

                            Text("Refresh Interval (Minutes)", fontSize = 12.sp, color = Color.Gray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val intervals = listOf(5, 10, 15, 30, 60, 120)
                                items(intervals) { mins ->
                                    FilterChip(
                                        selected = autoRefreshInterval == mins,
                                        onClick = { autoRefreshInterval = mins },
                                        label = { Text("${mins}m") }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (showAutoRefreshTab) {
                    TextButton(onClick = {
                        viewModel.setAutoRefreshConfig(context, autoRefreshEnabled, autoRefreshSim, autoRefreshInterval)
                        android.widget.Toast.makeText(context, "Auto-refresh settings saved", android.widget.Toast.LENGTH_SHORT).show()
                        showSimDialog = false
                    }) {
                        Text("SAVE", color = GreenLogo, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showSimDialog = false }) { Text("Close") }
                }
            }
        )
    }

    if (showTokenDialog) {
        // Redirect to TokenShopScreen instead of a simple dialog
        LaunchedEffect(Unit) {
            navController.navigate("token_shop")
            showTokenDialog = false
        }
    }

    if (showAirtimeStats) {
        val commissionSummary by viewModel.commissionSummary.collectAsState()
        val hourlyData by viewModel.todayHourlyAirtimeUsage.observeAsState(emptyList())
        
        if (commissionSummary != null) {
            val summary = commissionSummary!!
            val todayUsage = summary.todayAirtime
            val yesterdayUsage = summary.yesterdayAirtime
            
            AlertDialog(
                onDismissRequest = { showAirtimeStats = false },
                title = { Text("Airtime Usage & Analytics", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Today's Total Usage", color = Color.Gray, fontSize = 12.sp)
                                Text("KSh ${todayUsage.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = GreenLogo)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Yesterday's Total Usage", color = Color.Gray, fontSize = 12.sp)
                                Text("KSh ${yesterdayUsage.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        HorizontalDivider()

                        HourlyAirtimeLineChart(hourlyData = hourlyData, isDataHidden = isDataHidden, height = 180.dp)
                    }
                },
                confirmButton = { TextButton(onClick = { showAirtimeStats = false }) { Text("Close") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAirtimeStats = false },
                title = { Text("Loading Airtime...") },
                text = { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GreenLogo) } },
                confirmButton = {}
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatsSection(
                    airtime = if (isDataHidden) "*****" else airtime1,
                    commission = if (isDataHidden) "*****" else "KSh ${"%.1f".format(stats.totalCommission)}",
                    viewModel = viewModel,
                    onAirtimeStatsClick = { navController.navigate("airtime_analytics") },
                    onTokenClick = { showTokenDialog = true },
                    onCommissionClick = { navController.navigate("commission") },
                    onAirtimeRefreshClick = { showSimDialog = true }
                )
            }
            item {
                StatusGrid(stats, isDataHidden) { filter ->
                    navController.navigate("transactions?filter=$filter")
                }
            }
            item {
                SuccessRateSection(stats, viewModel)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = { navController.navigate("transactions") }) {
                        Text("View All", color = GreenLogo, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (transactions.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No recent activity", color = Color.Gray)
                    }
                }
            } else {
                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    var showContextMenu by remember { mutableStateOf(false) }
                    
                    if (showContextMenu) {
                        AlertDialog(
                            onDismissRequest = { showContextMenu = false },
                            title = { Text("Transaction Options") },
                            text = { Text("Actions for ${transaction.transactionCode.ifEmpty { "Transaction" }}") },
                            confirmButton = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { 
                                        viewModel.retryTransactions(listOf(transaction.id), context)
                                        showContextMenu = false
                                    }) { Text("Retry Transaction", color = GreenLogo) }

                                    TextButton(onClick = { 
                                        viewModel.applyTransactionToDebt(transaction, context)
                                        showContextMenu = false
                                    }) { Text("Apply to Debt", color = GreenLogo) }
                                    
                                    TextButton(onClick = { 
                                        viewModel.markTransactionComplete(transaction.id)
                                        showContextMenu = false
                                    }) { Text("Mark as Complete", color = GreenLogo) }
                                    
                                    TextButton(onClick = { 
                                        viewModel.deleteTransactions(listOf(transaction.id))
                                        showContextMenu = false
                                    }) { Text("Delete Record", color = Color.Red) }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showContextMenu = false }) { Text("Cancel") }
                            }
                        )
                    }

                    TransactionItem(
                        transaction = transaction, 
                        isHidden = isDataHidden,
                        onClick = { navController.navigate("transaction_detail/${transaction.id}") },
                        onLongClick = { showContextMenu = true }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            LongClickSurface(
                onClick = { /* Regular tap ignored to prevent accidental toggle */ },
                onLongClick = { viewModel.toggleAutomation(context) },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = if (isEnabled) Color.Red else Color(0xFF00C853),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isEnabled) "ON" else "OFF",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongClickSurface(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = color,
        shadowElevation = shadowElevation,
        content = content
    )
}

private fun dialUssd(context: android.content.Context, ussd: String) {
    val encodedUssd = Uri.encode(ussd)
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encodedUssd"))
    context.startActivity(intent)
}

@Composable
fun StatsSection(
    airtime: String, 
    commission: String, 
    viewModel: MainViewModel, 
    onAirtimeStatsClick: () -> Unit, 
    onTokenClick: () -> Unit,
    onCommissionClick: () -> Unit,
    onAirtimeRefreshClick: () -> Unit
) {
    val units by viewModel.units.collectAsState()
    val isUnlimited by viewModel.isUnlimitedActive.collectAsState()
    val airtime2 by viewModel.airtimeSim2.collectAsState()
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    val commissionSummary by viewModel.commissionSummary.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.example.autodial.util.PreferenceManager(context) }
    
    var timeRemaining by remember { mutableStateOf("") }
    
    LaunchedEffect(isUnlimited) {
        if (isUnlimited) {
            while(true) {
                val diff = prefManager.getUnlimitedExpiry() - System.currentTimeMillis()
                if (diff > 0) {
                    val days = diff / (24 * 60 * 60 * 1000)
                    val hours = (diff / (60 * 60 * 1000)) % 24
                    val mins = (diff / (60 * 1000)) % 60
                    timeRemaining = "${days}d ${hours}h ${mins}m"
                } else {
                    timeRemaining = "EXPIRED"
                }
                kotlinx.coroutines.delay(60000)
            }
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val f1 = if (airtime.replace(Regex("[^0-9.]"), "").isNotEmpty()) "KSh $airtime" else airtime
        val f2 = if (airtime2.replace(Regex("[^0-9.]"), "").isNotEmpty()) "KSh $airtime2" else airtime2
        
        val airtimeDisplay = if (isDataHidden) "*****" else "S1: $f1\nS2: $f2"
        StatCard(
            value = airtimeDisplay, 
            label = "Airtime Bal", 
            icon = Icons.Default.PhoneAndroid, 
            modifier = Modifier.weight(1.2f).clickable { onAirtimeStatsClick() }, 
            showRefresh = true, 
            onRefresh = onAirtimeRefreshClick,
            viewModel = viewModel
        )
        
        val tokenDisplay = if (isUnlimited) {
            if (units > 0) "$timeRemaining = $units" else timeRemaining
        } else {
            units.toString()
        }

        StatCard(
            value = tokenDisplay, 
            label = if (isUnlimited) "Unlimited + Units" else "Units", 
            icon = if (isUnlimited) Icons.Default.AllInclusive else Icons.Default.Circle, 
            modifier = Modifier.weight(1f).clickable { onTokenClick() },
            valueColor = if (isUnlimited) GreenLogo else MaterialTheme.colorScheme.onSurface,
            viewModel = viewModel
        )
        
        StatCard(
            value = commission, 
            label = "Commission", 
            icon = Icons.Default.MonetizationOn, 
            modifier = Modifier.weight(1f).clickable { onCommissionClick() }, 
            viewModel = viewModel,
            sparklineData = if (!isDataHidden) commissionSummary?.trendData?.map { it.second } else null
        )
    }
}

@Composable
fun StatCard(
    value: String, 
    label: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier, 
    showRefresh: Boolean = false, 
    onRefresh: () -> Unit = {},
    valueColor: Color = MaterialTheme.colorScheme.onSurface, 
    viewModel: MainViewModel,
    sparklineData: List<Double>? = null
) {
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    val displayValue = if (isDataHidden && (label.contains("Airtime") || label.contains("Commission") || label.contains("Tokens") || label.contains("Units") || label.contains("Unlimited"))) "****" else value

    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, valueColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Sparkline Background
            if (sparklineData != null && sparklineData.isNotEmpty()) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val maxVal = sparklineData.max().coerceAtLeast(1.0).toFloat()
                    val spacing = width / (sparklineData.size - 1)
                    
                    val path = androidx.compose.ui.graphics.Path().apply {
                        sparklineData.forEachIndexed { i, sum ->
                            val x = i * spacing
                            val y = height - (sum.toFloat() / maxVal * height)
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = valueColor.copy(alpha = 0.3f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        modifier = Modifier.size(32.dp).background(valueColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = valueColor)
                    }
                    if (showRefresh) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp), tint = Color.Gray)
                        }
                    }
                }
                Column {
                    val isMultiLine = displayValue.contains("\n")
                    Text(
                        text = displayValue, 
                        fontWeight = FontWeight.Black, 
                        fontSize = if (isMultiLine) 11.sp else if (displayValue.length > 8) 14.sp else 18.sp, 
                        color = valueColor, 
                        lineHeight = 13.sp,
                        maxLines = 2
                    )
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatusGrid(stats: com.example.autodial.ui.DashboardStats, isHidden: Boolean, onFilterClick: (String) -> Unit) {
    val countColor = MaterialTheme.colorScheme.onSurface
    val blueTickColor = Color(0xFF2196F3)
    val disabledColor = Color.Gray

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item { StatusButton(if (isHidden) "*" else stats.totalCount.toString(), "All", countColor, icon = Icons.AutoMirrored.Filled.ListAlt) { onFilterClick("All") } }
        item { StatusButton(if (isHidden) "*" else stats.doneCount.toString(), "Done", Color(0xFF00C853), icon = Icons.Default.CheckCircle) { onFilterClick("Done") } }
        item { StatusButton(if (isHidden) "*" else stats.queuedCount.toString(), "Queued", countColor, icon = Icons.Default.Schedule) { onFilterClick("Queued") } }
        item { StatusButton(if (isHidden) "*" else stats.failedCount.toString(), "Failed", Color.Red, icon = Icons.Default.Cancel) { onFilterClick("Failed") } }
        item { StatusButton(if (isHidden) "*" else stats.disabledCount.toString(), "Disabled", disabledColor, icon = Icons.Default.Block) { onFilterClick("Disabled") } }
        item { StatusButton(if (isHidden) "*" else stats.unmatchedCount.toString(), "Unmatched", countColor, icon = Icons.AutoMirrored.Filled.HelpOutline) { onFilterClick("Unmatched") } }
        item { StatusButton(if (isHidden) "*" else stats.verifiedCount.toString(), "Verified", blueTickColor, icon = Icons.Default.Verified) { onFilterClick("Verified") } }
        item { StatusButton(if (isHidden) "*" else stats.unverifiedCount.toString(), "Unverified", countColor, icon = Icons.Default.GppMaybe) { onFilterClick("Unverified") } }
        item { StatusButton(if (isHidden) "*" else stats.skippedCount.toString(), "Skipped", countColor, icon = Icons.Default.SkipNext) { onFilterClick("Skipped") } }
        item { StatusButton(if (isHidden) "*" else stats.scheduledCount.toString(), "Scheduled", countColor, icon = Icons.Default.Event) { onFilterClick("Scheduled") } }
    }
}

@Composable
fun StatusButton(
    count: String, 
    label: String, 
    color: Color, 
    icon: ImageVector? = null,
    modifier: Modifier = Modifier, 
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .width(88.dp)
            .height(62.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
                Text(count, fontWeight = FontWeight.Black, color = color, fontSize = 16.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label, 
                fontSize = 10.sp, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun HourlyAirtimeBarChart(hourlyData: List<Pair<Int, Double>>) {
    HourlyAirtimeLineChart(hourlyData = hourlyData)
}

@Composable
fun HourlyAirtimeLineChart(
    hourlyData: List<Pair<Int, Double>>,
    isDataHidden: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 220.dp
) {
    val points = hourlyData.map { it.second }
    val maxVal = points.maxOrNull()?.coerceAtLeast(10.0) ?: 100.0

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Hourly Usage (00:00 - 23:00)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Peak: KSh ${if (isDataHidden) "*****" else maxVal.toInt().toString()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GreenLogo
            )
        }
        
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val canvasHeight = size.height - 24.dp.toPx()
                val count = if (points.isEmpty()) 24 else points.size
                val spacing = width / (count - 1).coerceAtLeast(1)

                val gridColor = Color.Gray.copy(alpha = 0.15f)
                for (i in 0..4) {
                    val y = canvasHeight - (canvasHeight * i / 4)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (points.isNotEmpty()) {
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, amount ->
                        val x = i * spacing
                        val y = canvasHeight - (amount.toFloat() / maxVal.toFloat() * canvasHeight)

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, canvasHeight)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = (i - 1) * spacing
                            val prevAmount = points[i - 1]
                            val prevY = canvasHeight - (prevAmount.toFloat() / maxVal.toFloat() * canvasHeight)

                            path.cubicTo(
                                prevX + spacing / 2, prevY,
                                x - spacing / 2, y,
                                x, y
                            )
                            fillPath.cubicTo(
                                prevX + spacing / 2, prevY,
                                x - spacing / 2, y,
                                x, y
                            )
                        }

                        if (i == points.size - 1) {
                            fillPath.lineTo(x, canvasHeight)
                            fillPath.close()
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(GreenLogo.copy(alpha = 0.38f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = path,
                        color = GreenLogo,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    points.forEachIndexed { i, amount ->
                        val x = i * spacing
                        val y = canvasHeight - (amount.toFloat() / maxVal.toFloat() * canvasHeight)

                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = GreenLogo,
                            radius = 2.dp.toPx(),
                            center = Offset(x, y)
                        )

                        if (amount > 0) {
                            val text = if (isDataHidden) "*****" else amount.toInt().toString()
                            drawContext.canvas.nativeCanvas.drawText(
                                text,
                                x,
                                (y - 8.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 9.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }

                        if (i % 3 == 0 || i == 23) {
                            val timeLabel = "${i.toString().padStart(2, '0')}h"
                            drawContext.canvas.nativeCanvas.drawText(
                                timeLabel,
                                x,
                                size.height - 2.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessRateSection(stats: com.example.autodial.ui.DashboardStats, viewModel: MainViewModel) {
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Success", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            
            LinearProgressIndicator(
                progress = { stats.rate / 100f },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00E676),
                trackColor = Color.LightGray
            )
            
            Text(if (isDataHidden) "**%" else "${stats.rate}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            
            Text(if (isDataHidden) "*/*" else "${stats.doneCount}/${stats.totalCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp // Faster rendering
    ) {
        val items = listOf(
            Triple("Home", "dashboard", Icons.Default.Dashboard),
            Triple("Offers", "offers", Icons.Default.LocalOffer),
            Triple("Trans", "transactions", Icons.Default.History),
            Triple("Profile", "profile", Icons.Default.Person),
            Triple("Settings", "settings", Icons.Default.Settings)
        )

        items.forEach { (label, route, icon) ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route?.startsWith(route) == true } == true,
                onClick = {
                    if (currentDestination?.route != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GreenLogo,
                    selectedTextColor = GreenLogo,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
