package com.example.autodial.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.autodial.data.Offer
import com.example.autodial.data.UssdMapWithSteps
import com.example.autodial.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDialScreen(viewModel: MainViewModel) {
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val activeMaps by viewModel.allActiveMaps.observeAsState(emptyList())
    
    var selectedOffer by remember { mutableStateOf<Offer?>(null) }
    var targetNumber by remember { mutableStateOf("") }
    var simIndex by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val currentTime = remember { 
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Map info for selected offer
    var selectedMapWithSteps by remember { mutableStateOf<UssdMapWithSteps?>(null) }
    
    LaunchedEffect(selectedOffer) {
        selectedOffer?.let { offer ->
            if (offer.ussdMode == Offer.USSD_ADVANCED) {
                selectedMapWithSteps = viewModel.getMapWithSteps(activeMaps.find { it.offerId == offer.id }?.id ?: -1)
            } else {
                selectedMapWithSteps = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("⚡ Quick Dial", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 22.sp)
        Text("Execute any offer immediately. Mapped offers use intelligent automation.", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(Modifier.height(8.dp))

        // Offer Selector
        Text("1. Select Offer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOffer?.name ?: "Choose offer...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                leadingIcon = {
                    val isMapped = activeMaps.any { it.offerId == selectedOffer?.id }
                    Icon(
                        imageVector = if (isMapped) Icons.Default.Verified else Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = if (isMapped) Color(0xFF2196F3) else Color.Gray
                    )
                },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E676))
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                offers.forEach { offer ->
                    val timerActive = offer.isTimerActive(currentTime)
                    val isMapped = activeMaps.any { it.offerId == offer.id }
                    
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(offer.name, modifier = Modifier.weight(1f))
                                    if (isMapped) {
                                        Icon(Icons.Default.Verified, contentDescription = "Mapped", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (offer.isTimerEnabled && !timerActive) {
                                    Text("Disabled by timer", color = Color.Red, fontSize = 11.sp)
                                } else {
                                    Text("KSh ${offer.amount}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        },
                        onClick = {
                            selectedOffer = offer
                            simIndex = offer.simSelection
                            expanded = false
                        },
                        enabled = !offer.isTimerEnabled || timerActive
                    )
                }
            }
        }
        
        // Map Preview Section
        selectedOffer?.let { offer ->
            if (offer.ussdMode == Offer.USSD_ADVANCED) {
                MapPreviewCard(selectedMapWithSteps)
            } else {
                SimplePreviewCard(offer)
            }
        }

        // Target Number
        Text("2. Customer Number", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = targetNumber,
            onValueChange = { targetNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("07...") },
            prefix = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray); Spacer(Modifier.width(8.dp)) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E676))
        )

        // SIM Selection
        Text("3. Execution SIM", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SIMChip(selected = simIndex == 0, label = "SIM 1", onClick = { simIndex = 0 }, modifier = Modifier.weight(1f))
            SIMChip(selected = simIndex == 1, label = "SIM 2", onClick = { simIndex = 1 }, modifier = Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(16.dp))

        val isSelectedDisabledByTimer = selectedOffer?.let { it.isTimerEnabled && !it.isTimerActive(currentTime) } ?: false

        Button(
            onClick = {
                selectedOffer?.let { offer ->
                    viewModel.runOfferManually(offer, targetNumber, context, forcedSim = simIndex)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelectedDisabledByTimer) Color.Red.copy(alpha = 0.6f) else Color(0xFF00E676),
                disabledContainerColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = selectedOffer != null && targetNumber.length >= 10 && !isSelectedDisabledByTimer
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isSelectedDisabledByTimer) Color.White else Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                if (isSelectedDisabledByTimer) "Disabled by Timer" else "Run Now",
                fontWeight = FontWeight.Black,
                color = if (isSelectedDisabledByTimer) Color.White else Color.Black
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MapPreviewCard(mapWithSteps: UssdMapWithSteps?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (mapWithSteps != null) Color(0xFF2196F3).copy(alpha = 0.3f) else Color.Yellow.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (mapWithSteps != null) Icons.Default.Verified else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (mapWithSteps != null) Color(0xFF2196F3) else Color.Yellow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (mapWithSteps != null) "Automation Map Active" else "No Map Found (Template Only)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            if (mapWithSteps != null) {
                Spacer(Modifier.height(12.dp))
                val steps = mapWithSteps.steps.sortedBy { it.step.stepNumber }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dial ${mapWithSteps.map.startingCode}", color = Color.Gray, fontSize = 12.sp)
                    steps.forEach { step ->
                        val label = if (step.step.selectionType == "CUSTOMER_NUMBER") "Phone Number" else step.step.selectedText.ifEmpty { "Option ${step.step.selectedOption}" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Automation will attempt to follow standard template logic. For best results, use 'Map Training' in Offer settings.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SimplePreviewCard(offer: Offer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Direct Dial Execution", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("USSD: ${offer.ussdCode}", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun SIMChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .background(Color.Transparent),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF1B3D2F) else Color(0xFF1B222C),
        border = BorderStroke(1.dp, if (selected) Color(0xFF00E676) else Color.Gray.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color(0xFF00E676) else Color.Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
