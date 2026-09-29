package com.example.autodial.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.Offer
import com.example.autodial.data.RelayConfig
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val configs by viewModel.allRelayConfigs.observeAsState(emptyList())
    val allOffers by viewModel.allOffers.observeAsState(emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var configToEdit by remember { mutableStateOf<RelayConfig?>(null) }
    var configToDelete by remember { mutableStateOf<RelayConfig?>(null) }

    if (configToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete relay rule '${configToDelete?.name}'?",
            onConfirm = {
                configToDelete?.let { viewModel.deleteRelayConfig(it) }
                configToDelete = null
            },
            onDismiss = { configToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relay Management", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GreenLogo,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Relay", tint = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (configs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No relays configured", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(configs) { config ->
                        RelayConfigItem(
                            config = config,
                            onToggle = { viewModel.toggleRelayConfig(config, it) },
                            onDelete = { configToDelete = config },
                            onEdit = { configToEdit = config }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || configToEdit != null) {
        AddRelayDialog(
            allOffers = allOffers,
            allConfigs = configs,
            editingConfig = configToEdit,
            onDismiss = {
                showAddDialog = false
                configToEdit = null
            },
            onSave = {
                viewModel.saveRelayConfig(it)
                showAddDialog = false
                configToEdit = null
            }
        )
    }
}

@Composable
fun RelayConfigItem(config: RelayConfig, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(config.name.ifEmpty { "Relay Rule" }, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = config.isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo))
            }
            Text("Target: ${config.targetNumbers}", color = Color.Gray, fontSize = 12.sp)
            Text("SIM: ${config.simSlot + 1}", color = Color.Gray, fontSize = 12.sp)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRelayDialog(
    allOffers: List<Offer>, 
    allConfigs: List<RelayConfig>,
    editingConfig: RelayConfig?, 
    onDismiss: () -> Unit, 
    onSave: (RelayConfig) -> Unit
) {
    var name by remember { mutableStateOf(editingConfig?.name ?: "") }
    var targetNumbers by remember { mutableStateOf(editingConfig?.targetNumbers ?: "") }
    var simSlot by remember { mutableStateOf(editingConfig?.simSlot ?: 0) }
    var selectedOffers by remember { mutableStateOf(editingConfig?.offerIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet<String>()) }

    // Identify offers used in OTHER configurations
    val usedOfferIds = remember(allConfigs, editingConfig) {
        allConfigs.filter { it.id != editingConfig?.id }
            .flatMap { it.offerIds.split(",").filter { id -> id.isNotBlank() } }
            .toSet()
    }

    val isSaveEnabled = name.isNotBlank() && targetNumbers.isNotBlank() && selectedOffers.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Configure Relay", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                TextButton(
                    onClick = {
                        onSave((editingConfig ?: RelayConfig()).copy(
                            name = name,
                            targetNumbers = targetNumbers,
                            simSlot = simSlot,
                            offerIds = selectedOffers.joinToString(",")
                        ))
                    },
                    enabled = isSaveEnabled
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold, color = if (isSaveEnabled) GreenLogo else Color.Gray)
                }
            }
            
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Relay Name") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(
                value = targetNumbers, 
                onValueChange = { targetNumbers = it }, 
                label = { Text("Target Numbers (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("07..., 07...") }
            )
            
            Text("Execute via SIM", color = Color.Gray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1).forEach { slot ->
                    FilterChip(
                        selected = simSlot == slot,
                        onClick = { simSlot = slot },
                        label = { Text("SIM ${slot + 1}") }
                    )
                }
            }
            
            Text("Filter by Offers", color = Color.Gray, fontSize = 12.sp)
            allOffers.forEach { offer ->
                val isUsedElsewhere = usedOfferIds.contains(offer.id.toString())
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedOffers.contains(offer.id.toString()),
                        onCheckedChange = { checked ->
                            if (!isUsedElsewhere) {
                                selectedOffers = if (checked) selectedOffers + offer.id.toString()
                                else selectedOffers - offer.id.toString()
                            }
                        },
                        enabled = !isUsedElsewhere
                    )
                    Column {
                        Text(offer.name, color = if (isUsedElsewhere) Color.Gray else MaterialTheme.colorScheme.onSurface)
                        if (isUsedElsewhere) {
                            Text("Already assigned to another relay", fontSize = 10.sp, color = Color.Red.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
