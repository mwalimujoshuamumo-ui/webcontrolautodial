package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@Composable
fun SmsRelayScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Receiver Settings", "Sender Settings")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E2632),
            contentColor = GreenLogo
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> ReceiverSettings(viewModel)
            1 -> SenderSettings()
        }
    }
}

@Composable
fun ReceiverSettings(viewModel: MainViewModel) {
    var phoneNumber by remember { mutableStateOf("") }
    val verifiedSenders by viewModel.verifiedSenders.collectAsState()
    
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Number") },
            text = { Text("Are you sure you want to remove $showDeleteConfirm from verified senders?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeVerifiedSender(showDeleteConfirm!!)
                    showDeleteConfirm = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Verified Senders", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
        Text("Authorized numbers that can forward M-PESA messages.", color = Color.Gray, fontSize = 12.sp)
        
        Spacer(Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("07... or 01...") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (phoneNumber.startsWith("07") || phoneNumber.startsWith("01")) {
                        viewModel.addVerifiedSender(phoneNumber)
                        phoneNumber = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Saved Numbers", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(verifiedSenders.toList()) { sender ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2632))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sender, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showDeleteConfirm = sender }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SenderSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.example.autodial.util.PreferenceManager(context) }
    
    var isEnabled by remember { mutableStateOf(prefManager.isRelayEnabled()) }
    var targetPhone by remember { mutableStateOf(prefManager.getRelayTargetNumber()) }
    var simSlot by remember { mutableStateOf(prefManager.getRelaySimSlot()) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Relay Forwarding", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
        Text("Forward incoming M-PESA messages to another number.", color = Color.Gray, fontSize = 12.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable Relay", color = Color.White)
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo))
        }

        if (isEnabled) {
            OutlinedTextField(
                value = targetPhone,
                onValueChange = { targetPhone = it },
                label = { Text("Forward To (Phone Number)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("07...") },
                shape = RoundedCornerShape(12.dp)
            )

            Text("Select SIM Slot for Forwarding", color = Color.Gray, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = simSlot == 0, onClick = { simSlot = 0 })
                    Text("SIM 1", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = simSlot == 1, onClick = { simSlot = 1 })
                    Text("SIM 2", color = Color.White)
                }
            }
            
            // Note: Offer selection can be added here as a checklist if needed
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                prefManager.setRelayEnabled(isEnabled)
                prefManager.setRelayTargetNumber(targetPhone)
                prefManager.setRelaySimSlot(simSlot)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Relay Settings", fontWeight = FontWeight.Bold)
        }
    }
}
