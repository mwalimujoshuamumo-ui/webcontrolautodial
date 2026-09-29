package com.example.autodial.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenShopScreen(viewModel: MainViewModel, navController: NavController) {
    val units by viewModel.units.collectAsState()
    val isUnlimited by viewModel.isUnlimitedActive.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val context = LocalContext.current
    
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showSimSelectDialog by remember { mutableStateOf(false) }
    var showMpesaConfirmDialog by remember { mutableStateOf(false) }
    var mpesaPhone by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<UnitPackage?>(null) }
    
    val mpesaLoading by viewModel.mpesaLoading.collectAsState()
    val isWaitingForMpesa by viewModel.isWaitingForMpesa.collectAsState()
    val mpesaStatusMessage by viewModel.mpesaStatusMessage.collectAsState()

    LaunchedEffect(isWaitingForMpesa) {
        if (!isWaitingForMpesa && !mpesaLoading) {
            showMpesaConfirmDialog = false
        }
    }

    LaunchedEffect(viewModel.uniqueDeviceId.collectAsState().value) {
        mpesaPhone = viewModel.uniqueDeviceId.value
    }
    
    var promocode by remember { mutableStateOf("") }
    var backupCode by remember { mutableStateOf("") }
    var generatedBackupCode by remember { mutableStateOf<String?>(null) }

    if (generatedBackupCode != null) {
        AlertDialog(
            onDismissRequest = { generatedBackupCode = null },
            title = { Text("Backup Successful", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Your account data (Units + Unlimited Plans) has been moved to the cloud.")
                    Spacer(Modifier.height(8.dp))
                    Text("Backup Code:", color = Color.Gray, fontSize = 12.sp)
                    Text(generatedBackupCode!!, fontSize = 24.sp, fontWeight = FontWeight.Black, color = GreenLogo)
                    Spacer(Modifier.height(8.dp))
                    Text("IMPORTANT: This code can only be used once. Write it down to restore your access on any device.", 
                         color = Color.Red.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Backup Code", generatedBackupCode)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Code Copied", android.widget.Toast.LENGTH_SHORT).show()
                        generatedBackupCode = null 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                ) {
                    Text("COPY & CLOSE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPaymentDialog && selectedPackage != null) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Choose Payment Method") },
            text = {
                Column {
                    PaymentMethodItem(
                        "Direct Payment", 
                        "Pay KSh ${selectedPackage!!.price} to activate units", 
                        Icons.Default.PhoneAndroid, 
                        true
                    ) {
                        showPaymentDialog = false
                        showSimSelectDialog = true
                    }
                    Spacer(Modifier.height(8.dp))
                    PaymentMethodItem(
                        "M-PESA", 
                        "STK Push to any number", 
                        Icons.Default.Wallet, 
                        true
                    ) {
                        showPaymentDialog = false
                        showMpesaConfirmDialog = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSimSelectDialog && selectedPackage != null) {
        AlertDialog(
            onDismissRequest = { showSimSelectDialog = false },
            title = { Text("Select SIM for Transfer") },
            text = { Text("Transfer KSh ${selectedPackage!!.price} will be initiated from:") },
            confirmButton = {
                TextButton(onClick = { 
                    showSimSelectDialog = false
                    val pkg = selectedPackage!!
                    viewModel.buyTokens(pkg.price, pkg.units, pkg.isUnlimited, pkg.durationDays, 0, context)
                }) { Text("SIM 1") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSimSelectDialog = false
                    val pkg = selectedPackage!!
                    viewModel.buyTokens(pkg.price, pkg.units, pkg.isUnlimited, pkg.durationDays, 1, context)
                }) { Text("SIM 2") }
            }
        )
    }

    if (showMpesaConfirmDialog && selectedPackage != null) {
        AlertDialog(
            onDismissRequest = { showMpesaConfirmDialog = false },
            title = { Text("Confirm M-PESA Number", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pay KSh ${selectedPackage!!.price} via M-PESA STK Push.")
                    
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = GreenLogo.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Tokens will be awarded automatically once the payment is confirmed by the system.",
                            fontSize = 11.sp,
                            color = GreenLogo,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mpesaPhone,
                        onValueChange = { mpesaPhone = it },
                        label = { Text("Phone Number (254...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !mpesaLoading && !isWaitingForMpesa
                    )
                    
                    if (mpesaLoading || isWaitingForMpesa) {
                        Spacer(Modifier.height(24.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = GreenLogo, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = mpesaStatusMessage, 
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenLogo,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = selectedPackage!!
                        viewModel.initiateMpesaPayment(
                            phone = mpesaPhone,
                            packageName = pkg.name,
                            price = pkg.price,
                            unitsToAdd = pkg.units,
                            isUnlimited = pkg.isUnlimited,
                            durationDays = pkg.durationDays,
                            context = context
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                    enabled = !mpesaLoading && !isWaitingForMpesa && mpesaPhone.length >= 10
                ) {
                    Text("INITIATE PAYMENT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMpesaConfirmDialog = false }, enabled = !mpesaLoading && !isWaitingForMpesa) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unit Shop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Token Balance Display
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenLogo.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenLogo.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Current Balance", fontSize = 14.sp, color = Color.Gray)
                        Text(if (isUnlimited) "UNLIMITED ACTIVE" else "$units Units", 
                             fontSize = if (isUnlimited) 24.sp else 32.sp, 
                             fontWeight = FontWeight.Black, color = GreenLogo)
                    }
                }
            }

            // REDEEM & SYNC SECTION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Promocodes & Sync", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = promocode,
                                onValueChange = { promocode = it },
                                label = { Text("Enter Promocode", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    if (promocode.isNotBlank()) {
                                        viewModel.redeemPromocode(promocode, context)
                                        promocode = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("REDEEM", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.manualSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sync", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { 
                                    viewModel.backupTokensToCloud(context) { code ->
                                        generatedBackupCode = code
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isBackingUp && !isRestoring,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isBackingUp) {
                                    CircularProgressIndicator(color = GreenLogo, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Backing up...", color = Color.White, fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Move to Cloud", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Restore from Backup Code or System ID", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = backupCode,
                                onValueChange = { backupCode = it },
                                label = { Text("Enter BK-XXXXXX or Unique ID", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    if (backupCode.isNotBlank()) {
                                        val codeToRestore = backupCode
                                        backupCode = ""
                                        viewModel.restoreTokensFromBackupCode(codeToRestore, context) { success, _ ->
                                            if (!success) {
                                                backupCode = codeToRestore
                                            }
                                        }
                                    }
                                },
                                enabled = !isBackingUp && !isRestoring && backupCode.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restoring...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("RESTORE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // UNLIMITED PACKAGES FIRST
            item {
                Text(
                    "Unlimited Packages",
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            val unlimitedPackages = listOf(
                UnitPackage("Unlimited 1 Day", 0, 15, "Full day access", Color(0xFFFF5252), Icons.Default.AllInclusive, true, 1),
                UnitPackage("Unlimited 3 Days", 0, 40, "Long weekend power", Color(0xFF673AB7), Icons.Default.AllInclusive, true, 3),
                UnitPackage("Unlimited 1 Week", 0, 85, "Weekly efficiency", Color(0xFF3F51B5), Icons.Default.AllInclusive, true, 7),
                UnitPackage("Unlimited 1 Month", 0, 299, "Professional freedom", Color(0xFF009688), Icons.Default.AllInclusive, true, 30)
            )

            items(unlimitedPackages) { pkg ->
                TokenPackageCard(
                    pkg = pkg,
                    modifier = Modifier.fillMaxWidth(),
                    onBuy = { 
                        selectedPackage = pkg
                        showPaymentDialog = true 
                    },
                    isWide = true
                )
            }

            item {
                Text(
                    "Standard Packages",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            val standardPackages = listOf(
                UnitPackage("Starter", 100, 10, "Small volume automation", Color(0xFF4CAF50), Icons.Default.Bolt),
                UnitPackage("Basic", 300, 25, "Everyday automation", Color(0xFF2196F3), Icons.Default.Grade),
                UnitPackage("Standard", 600, 40, "Steady flow of tasks", Color(0xFF9C27B0), Icons.Default.Stars),
                UnitPackage("Premium", 1500, 99, "Power user tools", Color(0xFFFF9800), Icons.Default.Diamond),
                UnitPackage("Business", 3000, 180, "High volume scaling", Color(0xFFE91E63), Icons.Default.Work),
                UnitPackage("Enterprise", 5000, 280, "The automation pro", Color(0xFFFFD700), Icons.Default.Business),
                UnitPackage("Ultimate", 10000, 500, "Maximum productivity", Color(0xFF00E5FF), Icons.Default.RocketLaunch)
            )

            items(standardPackages.chunked(2)) { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { pkg ->
                        TokenPackageCard(
                            pkg = pkg,
                            modifier = Modifier.weight(1f),
                            onBuy = { 
                                selectedPackage = pkg
                                showPaymentDialog = true 
                            }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "App can't process USSD without units",
                    fontSize = 12.sp,
                    color = Color.Red.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

data class UnitPackage(
    val name: String,
    val units: Int,
    val price: Int,
    val description: String,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isUnlimited: Boolean = false,
    val durationDays: Int = 0
)

@Composable
fun TokenPackageCard(pkg: UnitPackage, modifier: Modifier = Modifier, onBuy: () -> Unit, isWide: Boolean = false) {
    Card(
        modifier = modifier.then(if (isWide) Modifier.height(110.dp) else Modifier.height(180.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, pkg.accentColor.copy(alpha = 0.3f))
    ) {
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(pkg.accentColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(pkg.icon, contentDescription = null, tint = pkg.accentColor)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text("KSh ${pkg.price}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pkg.accentColor)
                }
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = pkg.accentColor),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("BUY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(pkg.icon, contentDescription = null, tint = pkg.accentColor, modifier = Modifier.size(32.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text("${pkg.units} Units", fontWeight = FontWeight.Black, fontSize = 18.sp, color = pkg.accentColor)
                    Text("KSh ${pkg.price}", fontSize = 12.sp, color = Color.Gray)
                }
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = pkg.accentColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BUY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PaymentMethodItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) Color(0xFF1E2632) else Color.DarkGray.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) GreenLogo else Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color.Gray)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
