package com.example.autodial.ui.offers

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Timer
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
import com.example.autodial.data.Offer
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSettingsScreen(viewModel: MainViewModel, offerId: Int, onBack: () -> Unit) {
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val offer = remember(offers, offerId) { offers.find { it.id == offerId } }
    val context = LocalContext.current

    if (offer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GreenLogo)
        }
        return
    }

    var isTimerEnabled by remember(offer) { mutableStateOf(offer.isTimerEnabled) }
    var timerEnableAt by remember(offer) { mutableStateOf(offer.timerEnableAt.ifEmpty { "00:00" }) }
    var timerDisableAt by remember(offer) { mutableStateOf(offer.timerDisableAt.ifEmpty { "23:59" }) }
    var selectedSwitchOfferId by remember(offer) { mutableStateOf(offer.switchOfferId) }

    var expandedDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Timer Configuration", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to completely clear and turn off the timer settings for this offer?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val updated = offer.copy(
                        isTimerEnabled = false,
                        timerEnableAt = "00:00",
                        timerDisableAt = "23:59",
                        lastModified = System.currentTimeMillis()
                    )
                    viewModel.saveOffer(updated)
                    android.widget.Toast.makeText(context, "Timer deleted", android.widget.Toast.LENGTH_SHORT).show()
                    onBack()
                }) {
                    Text("DELETE", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Timer", tint = Color.Red)
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
            // Offer Info Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(offer.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(offer.ussdCode, fontSize = 14.sp, color = GreenLogo)
                }
            }

            // Enable/Disable Toggle Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = GreenLogo)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Enable Timer Control", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Automatically toggle offer status based on schedule", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = isTimerEnabled,
                        onCheckedChange = { isTimerEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE0FF00), checkedTrackColor = Color(0xFF1B3D2F))
                    )
                }
            }

            // Time Picking Controls
            if (isTimerEnabled) {
                Text("Schedule Config (24-Hour Format)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Enable At Picker
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            val parts = timerEnableAt.split(":")
                            val currentHour = parts.getOrNull(0)?.toIntOrNull() ?: 0
                            val currentMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            
                            TimePickerDialog(context, { _, hour, minute ->
                                timerEnableAt = String.format("%02d:%02d", hour, minute)
                            }, currentHour, currentMinute, true).show()
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Enable Offer At", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = GreenLogo, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(timerEnableAt, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Disable At Picker
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            val parts = timerDisableAt.split(":")
                            val currentHour = parts.getOrNull(0)?.toIntOrNull() ?: 23
                            val currentMinute = parts.getOrNull(1)?.toIntOrNull() ?: 59
                            
                            TimePickerDialog(context, { _, hour, minute ->
                                timerDisableAt = String.format("%02d:%02d", hour, minute)
                            }, currentHour, currentMinute, true).show()
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Disable Offer At", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(timerDisableAt, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Switch Target Offer (Optional)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

                val availableSwitchOffers = remember(offers, offerId) { offers.filter { it.id != offerId } }
                val currentSelectionName = availableSwitchOffers.find { it.id == selectedSwitchOfferId }?.name ?: "None (Turn Off Only)"

                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    OutlinedTextField(
                        value = currentSelectionName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Switch Offer when Disabled") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Turn Off Only)") },
                            onClick = {
                                selectedSwitchOfferId = null
                                expandedDropdown = false
                            }
                        )
                        availableSwitchOffers.forEach { alternate ->
                            DropdownMenuItem(
                                text = { Text("${alternate.name} (KSh ${alternate.amount.toInt()})") },
                                onClick = {
                                    selectedSwitchOfferId = alternate.id
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    val updated = offer.copy(
                        isTimerEnabled = isTimerEnabled,
                        timerEnableAt = timerEnableAt,
                        timerDisableAt = timerDisableAt,
                        switchOfferId = selectedSwitchOfferId,
                        lastModified = System.currentTimeMillis()
                    )
                    viewModel.saveOffer(updated)
                    android.widget.Toast.makeText(context, "Timer saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SAVE TIMER CONFIGURATION", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }
}
