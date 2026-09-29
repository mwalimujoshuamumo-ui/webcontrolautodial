package com.example.autodial.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.util.PreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirtimeAnalyticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    
    val airtime1 by viewModel.airtimeSim1.collectAsState()
    val airtime2 by viewModel.airtimeSim2.collectAsState()
    val commissionSummary by viewModel.commissionSummary.collectAsState()
    val hourlyData by viewModel.todayHourlyAirtimeUsage.observeAsState(emptyList())

    var autoRefreshEnabled by remember { mutableStateOf(prefManager.isAutoRefreshAirtimeEnabled()) }
    var autoRefreshSim by remember { mutableStateOf(prefManager.getAutoRefreshSimSlot()) } // 0=SIM1, 1=SIM2, 2=BOTH
    var autoRefreshInterval by remember { mutableStateOf(prefManager.getAutoRefreshIntervalMins()) } // 5, 10, 15, 30, 60, 120

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Airtime Usage & Analytics", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
            // Balance & Today/Yesterday Overview Cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SIM Airtime Balances", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SIM 1", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                if (isDataHidden) "*****" else "KSh $airtime1",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = GreenLogo
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("SIM 2", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                if (isDataHidden) "*****" else "KSh $airtime2",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = GreenLogo
                            )
                        }
                    }

                    HorizontalDivider()

                    val todayUsage = commissionSummary?.todayAirtime ?: 0.0
                    val yesterdayUsage = commissionSummary?.yesterdayAirtime ?: 0.0

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Today's Airtime Used", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                if (isDataHidden) "*****" else "KSh ${todayUsage.toInt()}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Yesterday's Airtime Used", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                if (isDataHidden) "*****" else "KSh ${yesterdayUsage.toInt()}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Today's Hourly Graph
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Airtime consumed per hour of the day in KSh", fontSize = 11.sp, color = Color.Gray)
                    
                    Spacer(Modifier.height(8.dp))

                    HourlyAirtimeLineChart(
                        hourlyData = hourlyData,
                        isDataHidden = isDataHidden,
                        height = 230.dp
                    )
                }
            }

            // Auto-Refresh Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Auto-Refresh", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Automatically check airtime balance periodically via USSD", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = autoRefreshEnabled,
                            onCheckedChange = { autoRefreshEnabled = it }
                        )
                    }

                    if (autoRefreshEnabled) {
                        HorizontalDivider()

                        Text("Select Target SIM", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = autoRefreshSim == 0,
                                onClick = { autoRefreshSim = 0 },
                                label = { Text("SIM 1") }
                            )
                            FilterChip(
                                selected = autoRefreshSim == 1,
                                onClick = { autoRefreshSim = 1 },
                                label = { Text("SIM 2") }
                            )
                            FilterChip(
                                selected = autoRefreshSim == 2,
                                onClick = { autoRefreshSim = 2 },
                                label = { Text("Both SIMs") }
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Text("Refresh Interval (Minutes)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val intervals = listOf(5, 10, 15, 30, 60, 120)
                            items(intervals) { mins ->
                                FilterChip(
                                    selected = autoRefreshInterval == mins,
                                    onClick = { autoRefreshInterval = mins },
                                    label = { Text("${mins} Mins") }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.setAutoRefreshConfig(context, autoRefreshEnabled, autoRefreshSim, autoRefreshInterval)
                                android.widget.Toast.makeText(context, "Auto-refresh settings saved", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SAVE AUTO-REFRESH SETTINGS", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
