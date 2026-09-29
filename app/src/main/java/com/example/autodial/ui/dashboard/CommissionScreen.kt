package com.example.autodial.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.CommissionFilter
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.ui.theme.SurfaceVariant
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionScreen(viewModel: MainViewModel, navController: NavController) {
    val commissionSummary by viewModel.commissionSummary.collectAsState()
    val activeFilter by viewModel.activeCommissionFilter.collectAsState()
    
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var showExportDialog by remember { mutableStateOf(false) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    val summary = commissionSummary ?: return // Should show loading if null

    fun formatAmount(amount: Double): String {
        return if (isPrivacyMode) "KSh ••••" else "KSh ${"%.1f".format(amount)}"
    }

    fun formatAmountRaw(amountStr: String): String {
        return if (isPrivacyMode) "••••" else amountStr
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Default.Download, contentDescription = null, tint = GreenLogo) },
            title = { Text("Export Commission Report") },
            text = { Text("Your commission statement for the selected period has been prepared successfully.") },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                ) {
                    Text("Download PDF/CSV", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AUTODIAL", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Commission & Earnings Analytics", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.background(GreenLogo.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Report", tint = GreenLogo, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0A0E14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CommissionSummaryCard(
                    title = "Today's Commission",
                    amount = formatAmount(summary.todayCommission),
                    diff = "${summary.todayDiffPercent}%",
                    comparison = "vs yesterday",
                    isPositive = summary.todayDiffPercent >= 0,
                    modifier = Modifier.weight(1f),
                    amountColor = if (summary.todayDiffPercent >= 0) GreenLogo else Color.Red
                )
                CommissionSummaryCard(
                    title = "This Period",
                    amount = formatAmount(summary.totalCommission),
                    diff = "${if (summary.periodDiffPercent >= 0) "+" else ""}${summary.periodDiffPercent}%",
                    comparison = "vs prev period",
                    isPositive = summary.periodDiffPercent >= 0,
                    modifier = Modifier.weight(1f),
                    amountColor = if (summary.periodDiffPercent >= 0) GreenLogo else Color.Red
                )
                CommissionSummaryCard(
                    title = "This Month",
                    amount = formatAmount(summary.monthTotal),
                    diff = null,
                    comparison = "1st to ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())}",
                    isPositive = true,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarMonth,
                    amountColor = Color(0xFF9C27B0)
                )
            }

            // Ergonomic Filter Bar (Replacing old AlertDialog)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Time Range Filter", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Text(
                            text = when(activeFilter) {
                                CommissionFilter.THREE_DAYS -> "Last 3 Days"
                                CommissionFilter.SEVEN_DAYS -> "Last 7 Days"
                                CommissionFilter.THIS_MONTH -> "This Month"
                                CommissionFilter.THREE_MONTHS -> "Last 3 Months"
                            },
                            fontSize = 12.sp,
                            color = GreenLogo,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Filter Chips Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filters = listOf(
                            CommissionFilter.THREE_DAYS to "3 Days",
                            CommissionFilter.SEVEN_DAYS to "7 Days",
                            CommissionFilter.THIS_MONTH to "This Month",
                            CommissionFilter.THREE_MONTHS to "3 Months"
                        )
                        items(filters) { (filter, label) ->
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { viewModel.setCommissionFilter(filter) },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenLogo.copy(alpha = 0.25f),
                                    selectedLabelColor = GreenLogo,
                                    containerColor = Color.DarkGray.copy(alpha = 0.3f),
                                    labelColor = Color.LightGray
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = activeFilter == filter,
                                    selectedBorderColor = GreenLogo,
                                    borderColor = Color.Transparent
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Secondary Status Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        val statuses = listOf("All", "Paid", "Pending", "Reversed")
                        statuses.forEach { status ->
                            AssistChip(
                                onClick = { selectedStatusFilter = status },
                                label = { Text(status, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selectedStatusFilter == status) GreenLogo.copy(alpha = 0.2f) else Color.Transparent,
                                    labelColor = if (selectedStatusFilter == status) GreenLogo else Color.Gray
                                ),
                                border = BorderStroke(1.dp, if (selectedStatusFilter == status) GreenLogo.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            // Trend Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(32.dp).background(GreenLogo.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Earnings Trend", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text("Interactive performance chart", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Surface(
                            color = GreenLogo.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Live Data",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenLogo
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Trend Chart
                    CommissionTrendChart(summary.trendData, isPrivacyMode)

                    Spacer(Modifier.height(20.dp))

                    // Weekly Day Cards
                    Text("Daily Breakdown", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(summary.trendData) { (day, amount) ->
                            DayCommissionCard(day, amount, isPrivacyMode)
                        }
                    }
                }
            }

            // Recent Breakdown Ledger
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Commission Ledger", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("Showing ${summary.trendData.size} entries", fontSize = 11.sp, color = Color.Gray)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    summary.trendData.takeLast(4).reversed().forEach { (day, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(GreenLogo.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Campaign Payout - $day", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Status: Paid • Automated Autodial", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                formatAmount(amount),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = GreenLogo
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommissionSummaryCard(
    title: String,
    amount: String,
    diff: String?,
    comparison: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    amountColor: Color = GreenLogo
) {
    Card(
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, (if (isPositive) GreenLogo else Color.Red).copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = amountColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                } else if (diff != null) {
                    Icon(
                        if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (isPositive) GreenLogo else Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(amount, fontWeight = FontWeight.Black, fontSize = 15.sp, color = amountColor)
            if (diff != null) {
                Text(diff, color = if (isPositive) GreenLogo else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            Text(comparison, fontSize = 8.5.sp, color = Color.Gray)
        }
    }
}

@Composable
fun CommissionTrendChart(data: List<Pair<String, Double>>, isPrivacyMode: Boolean = false) {
    val points = data.map { it.second }
    val maxVal = points.maxOrNull()?.coerceAtLeast(100.0) ?: 100.0
    val labels = data.map { it.first }

    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spacing = if (points.size > 1) width / (points.size - 1) else width
            
            // Grid Lines
            val gridColor = Color.Gray.copy(alpha = 0.12f)
            for (i in 0..4) {
                val y = height - (height * i / 4)
                drawLine(gridColor, start = Offset(0f, y), end = Offset(width, y))
            }
            
            if (points.isNotEmpty()) {
                val path = Path()
                val fillPath = Path()
                
                points.forEachIndexed { i, valDouble ->
                    val x = i * spacing
                    val y = height - (valDouble.toFloat() / maxVal.toFloat() * height)
                    
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (i - 1) * spacing
                        val prevY = height - (points[i - 1].toFloat() / maxVal.toFloat() * height)
                        
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
                        fillPath.lineTo(x, height)
                        fillPath.close()
                    }
                }
                
                // Draw Gradient Fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(GreenLogo.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
                
                // Draw Line
                drawPath(
                    path = path,
                    color = GreenLogo,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // Draw Points and Text Labels
                points.forEachIndexed { i, valDouble ->
                    val x = i * spacing
                    val y = height - (valDouble.toFloat() / maxVal.toFloat() * height)
                    
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

                    if (valDouble > 0) {
                        val text = if (isPrivacyMode) "••••" else valDouble.toInt().toString()
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            x,
                            y - 10.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                        )
                    }
                }
            }
        }
    }
    
    // X-Axis Labels
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label ->
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}

@Composable
fun DayCommissionCard(day: String, amount: Double, isPrivacyMode: Boolean = false) {
    Card(
        modifier = Modifier.width(85.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GreenLogo.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(day, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val amountStr = if (isPrivacyMode) "••••" else "KSh ${"%.1f".format(amount)}"
            Text(amountStr, fontSize = 12.sp, fontWeight = FontWeight.Black, color = GreenLogo)
        }
    }
}
