package com.example.autodial.ui.settings

import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.RelayDevice
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.util.RelayClient
import com.example.autodial.util.RelayServer
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefManager = remember { com.example.autodial.util.PreferenceManager(context) }
    var deviceRole by remember { mutableStateOf(prefManager.getDeviceRole()) }
    
    val linkedDevices by viewModel.allLinkedDevices.observeAsState(emptyList())
    val activeDevices by RelayServer.activeDevices.collectAsState()
    
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeviceDetails by remember { mutableStateOf<RelayDevice?>(null) }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val data = result.contents
            if (data.startsWith("role:MAIN|ip:")) {
                val ip = data.substringAfter("ip:")
                RelayClient.connectToMain(ip, context)
                Toast.makeText(context, "Linking to Main Device...", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showDeviceDetails != null) {
        DeviceDetailsDialog(
            device = showDeviceDetails!!,
            onDismiss = { showDeviceDetails = null },
            onSave = { amounts, enabled ->
                viewModel.saveRelayDeviceConfig(showDeviceDetails!!.id, amounts, enabled)
                showDeviceDetails = null
            },
            onDisconnect = {
                viewModel.disconnectDevice(it)
                showDeviceDetails = null
            },
            onDelete = {
                viewModel.deleteDevice(it)
                showDeviceDetails = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CONNECT", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Link multiple devices to share SMS data. Ensure the Linked device is connected to the Main phone's Hotspot or Bluetooth.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(Modifier.height(16.dp))

            // Role Switcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                    RoleButton("MAIN", deviceRole == "MAIN", Modifier.weight(1f)) { 
                        deviceRole = "MAIN"
                        prefManager.setDeviceRole("MAIN")
                        val serviceIntent = Intent(context, com.example.autodial.service.RelayService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                    RoleButton("LINKED", deviceRole == "LINKED", Modifier.weight(1f)) { 
                        deviceRole = "LINKED"
                        prefManager.setDeviceRole("LINKED")
                        val serviceIntent = Intent(context, com.example.autodial.service.RelayService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (deviceRole == "MAIN") {
                MainDeviceView(
                    activeCount = activeDevices.size,
                    devices = linkedDevices,
                    onShowQr = {
                        val data = "role:MAIN|ip:${RelayServer.getLocalIpAddress()}"
                        qrBitmap = BarcodeEncoder().encodeBitmap(data, BarcodeFormat.QR_CODE, 512, 512)
                        showQrDialog = true
                    },
                    onDeviceClick = { showDeviceDetails = it }
                )
            } else {
                val relays by viewModel.receivedRelays.observeAsState(emptyList())
                val mainName by RelayClient.connectedMainName.collectAsState()
                LinkedDeviceView(
                    mainName = mainName,
                    relays = relays,
                    onScan = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan Main Device QR")
                            setBeepEnabled(false)
                        }
                        barcodeLauncher.launch(options)
                    }
                )
            }
        }
    }

    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Pairing QR Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR")
                    Text("IP: ${RelayServer.getLocalIpAddress()}", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = { TextButton(onClick = { showQrDialog = false }) { Text("Done") } }
        )
    }
}

@Composable
fun MainDeviceView(activeCount: Int, devices: List<RelayDevice>, onShowQr: () -> Unit, onDeviceClick: (RelayDevice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Automation Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GreenLogo.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(GreenLogo, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("AUTOMATION STATUS: ACTIVE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Connected: $activeCount | Available: $activeCount", color = GreenLogo, fontSize = 12.sp)
                }
            }
        }

        Text("CONNECTED DEVICES", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(devices) { device ->
                DeviceCard(device, onClick = { onDeviceClick(device) })
            }
        }

        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
        ) {
            Text("ADD LINKED DEVICE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DeviceCard(device: RelayDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Smartphone, contentDescription = null, tint = if (device.isConnected) GreenLogo else Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.deviceName, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Relay: ${device.relayAmounts.ifEmpty { "ALL" }}", color = Color.Gray, fontSize = 12.sp)
            }
            StatusBadge(device.status)
        }
    }
}

@Composable
fun LinkedDeviceView(mainName: String?, relays: List<com.example.autodial.data.Transaction>, onScan: () -> Unit) {
    val context = LocalContext.current
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Connection Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = GreenLogo)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(mainName ?: "SEARCHING FOR MAIN...", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                    Text(
                        text = if (mainName != null) "Status: Connected & Ready" else "Status: Waiting for Main device link...", 
                        color = if (mainName != null) GreenLogo else Color.Gray, 
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (relays.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("No Relays Received Today", color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Tasks from Main will appear here.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            Text("RECENT RELAYED TASKS", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(relays) { transaction ->
                    com.example.autodial.ui.components.TransactionItem(
                        transaction = transaction,
                        isHidden = false,
                        onClick = { /* Detail navigation if needed */ }
                    )
                }
            }
        }

        Button(
            onClick = onScan, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
        ) {
            Text("RE-SCAN MAIN QR", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DeviceDetailsDialog(
    device: RelayDevice, 
    onDismiss: () -> Unit, 
    onSave: (String, Boolean) -> Unit,
    onDisconnect: (RelayDevice) -> Unit,
    onDelete: (RelayDevice) -> Unit
) {
    var amounts by remember { mutableStateOf(device.relayAmounts) }
    var enabled by remember { mutableStateOf(device.automationEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(device.deviceName, fontWeight = FontWeight.Black)
                Text("${device.model} | v${device.androidVersion}", fontSize = 11.sp, color = Color.Gray)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = amounts,
                    onValueChange = { amounts = it },
                    label = { Text("Relay Amounts (e.g. 20, 50, 99)") },
                    placeholder = { Text("Leave empty for all") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(12.dp))
                    Text("Automation Enabled", fontSize = 14.sp)
                }
                
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatLabel("Received", device.totalReceived.toString())
                    StatLabel("Completed", device.totalCompleted.toString())
                    StatLabel("Failed", device.totalFailed.toString())
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDisconnect(device) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) { Text("DISCONNECT", fontSize = 10.sp) }
                    
                    Button(
                        onClick = { onDelete(device) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red)
                    ) { Text("DELETE", fontSize = 10.sp) }
                }
            }
        },
        confirmButton = { 
            Button(onClick = { onSave(amounts, enabled) }) { Text("SAVE") } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("CLOSE") } 
        }
    )
}

@Composable
fun StatLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when(status) {
        "ACTIVE" -> GreenLogo
        "RECONNECTING" -> Color.Yellow
        else -> Color.Red
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun RoleButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) GreenLogo else Color.Transparent,
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = null
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}
