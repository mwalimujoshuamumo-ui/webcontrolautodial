package com.example.autodial.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.Offer
import com.example.autodial.data.TopUpPlan
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngageBotSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val engageEnabled by viewModel.isEngageBotEnabled.collectAsState()
    val exactMatch by viewModel.isEngageExactTopUpMatch.collectAsState()
    val plans by viewModel.allTopUpPlans.observeAsState(emptyList())
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val offerMap = remember(offers) { offers.associateBy { it.id } }

    var showAddPlan by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<TopUpPlan?>(null) }

    if (planToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Delete this top-up plan?",
            onConfirm = {
                planToDelete?.let { viewModel.deleteTopUpPlan(it) }
                planToDelete = null
            },
            onDismiss = { planToDelete = null }
        )
    }

    if (showAddPlan) {
        AddTopUpPlanDialog(
            offers = offers.filter { it.isEnabled },
            onDismiss = { showAddPlan = false },
            onSave = { amount, offerId ->
                viewModel.saveTopUpPlan(
                    TopUpPlan(requiredAmount = amount, offerId = offerId, isEnabled = true)
                )
                showAddPlan = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engage Bot", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPlan = true },
                containerColor = GreenLogo
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Top-Up Plan", tint = Color.Black)
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Activates only when a recommendation response says the customer number was already recommended the offer today.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Engage Bot", fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    if (engageEnabled) "ON — watches for already-recommended-today" else "OFF — normal processing only",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = engageEnabled,
                                onCheckedChange = { viewModel.setEngageBotEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo, checkedTrackColor = GreenLogo.copy(alpha = 0.4f))
                            )
                        }

                        HorizontalDivider(color = Color.DarkGray)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exact top-up amount", fontWeight = FontWeight.Medium, color = Color.White)
                                Text(
                                    "Reject overpays; require exact KSh amount",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = exactMatch,
                                onCheckedChange = { viewModel.setEngageExactTopUpMatch(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo, checkedTrackColor = GreenLogo.copy(alpha = 0.4f))
                            )
                        }

                        Text(
                            "Engage Bot SMS uses the same Notification SIM configured in Notifications settings.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("TOP-UP PLANS", fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 11.sp)
                Text(
                    "Plans reference offers from the Offer List (no duplicated USSD).",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (plans.isEmpty()) {
                item {
                    Text(
                        "No top-up plans yet. Tap + to add one.",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(plans, key = { it.id }) { plan ->
                val offer = offerMap[plan.offerId]
                val priceToShow = plan.requiredAmount

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Required Total (Agent Price): KSh ${formatKsh(priceToShow)}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Loads Offer: ${offer?.name ?: "Offer #${plan.offerId} (missing)"}",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Status: ${if (plan.isEnabled) "ACTIVE" else "DISABLED"}",
                                fontSize = 11.sp,
                                color = if (plan.isEnabled) GreenLogo else Color.Gray
                            )
                        }
                        Switch(
                            checked = plan.isEnabled,
                            onCheckedChange = { viewModel.saveTopUpPlan(plan.copy(isEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo, checkedTrackColor = GreenLogo.copy(alpha = 0.4f))
                        )
                        IconButton(onClick = { planToDelete = plan }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTopUpPlanDialog(
    offers: List<Offer>,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit
) {
    var selectedOffer by remember { mutableStateOf<Offer?>(null) }
    var offerExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Top-Up Plan", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select an offer to toggle it as an available top-up option for the Engage Bot.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Text("Select Offer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (offers.isEmpty()) {
                    Text("No enabled offers in the Offer List.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = offerExpanded,
                        onExpandedChange = { offerExpanded = !offerExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOffer?.let { "${it.name} (KSh ${formatKsh(it.amount)})" } ?: "Select from Offer List",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = offerExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = offerExpanded,
                            onDismissRequest = { offerExpanded = false }
                        ) {
                            offers.forEach { offer ->
                                DropdownMenuItem(
                                    text = { Text("${offer.name} — Agent Price: KSh ${formatKsh(offer.amount)}") },
                                    onClick = {
                                        selectedOffer = offer
                                        offerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                selectedOffer?.let {
                    Text(
                        "The engine will automatically fetch the Agent Price (KSh ${formatKsh(it.amount)}) directly from this offer's details to calculate required customer balances.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val offer = selectedOffer
                    if (offer != null) {
                        onSave(offer.amount, offer.id)
                    }
                },
                enabled = selectedOffer != null
            ) {
                Text("SAVE", color = GreenLogo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

private fun formatKsh(amount: Double): String {
    return if (amount == amount.toLong().toDouble()) amount.toLong().toString()
    else String.format("%.2f", amount)
}
