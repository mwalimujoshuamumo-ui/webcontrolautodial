package com.example.autodial.ui.offers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autodial.data.Offer
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

/**
 * Redesigned Offers Screen - 1:1 Visual Match
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(viewModel: MainViewModel, navController: NavController) {
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val activeMaps by viewModel.allActiveMaps.observeAsState(emptyList())
    val stats by viewModel.stats.observeAsState(com.example.autodial.ui.DashboardStats())
    val localContext = LocalContext.current
    
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categories = listOf("All", "Data", "SMS", "Minutes", "Other")
    
    var showAddDialog by remember { mutableStateOf(false) }
    var offerToDelete by remember { mutableStateOf<Offer?>(null) }
    var offerToEdit by remember { mutableStateOf<Offer?>(null) }
    
    var showImportConfirm by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var offerForTimerWarning by remember { mutableStateOf<Offer?>(null) }

    var cloudCode by remember { mutableStateOf("") }
    var generatedCloudCode by remember { mutableStateOf<String?>(null) }
    var isCloudLoading by remember { mutableStateOf(false) }

    if (showCloudSyncDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSyncDialog = false },
            title = { Text("LIN Code Share Center", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Export or import your complete offer list alongside all trained/learned USSD mappings via File, LIN Code, or Deep Link.", fontSize = 12.sp, color = Color.Gray)
                    
                    Text("1. EXPORT CATALOG", fontWeight = FontWeight.Bold, color = GreenLogo)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { 
                                showCloudSyncDialog = false
                                showExportConfirm = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B222C))
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("As File", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { 
                                isCloudLoading = true
                                viewModel.exportOffersToCloud(localContext) { code ->
                                    if (code != null) {
                                        generatedCloudCode = code
                                        // AUTOMATIC POP-UP AND AUTO-COPY WHEN LINK GENERATION IS SUCCESSFUL
                                        val shareLink = "autodial://offers/import?code=$code"
                                        val clipboard = localContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Offer Link", shareLink)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(localContext, "Offers LIN Code Auto-Generated & Copied!", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    isCloudLoading = false
                                }
                            }, 
                            modifier = Modifier.weight(1f),
                            enabled = !isCloudLoading
                        ) { 
                            if (isCloudLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Get LIN Code", fontSize = 11.sp)
                            }
                        }
                    }

                    if (generatedCloudCode != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Active LIN Code:", fontSize = 12.sp, color = Color.Gray)
                                Text(generatedCloudCode!!, fontSize = 22.sp, fontWeight = FontWeight.Black, color = GreenLogo)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        val clipboard = localContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Offer LIN Code", generatedCloudCode)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(localContext, "LIN Code Copied", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Text("COPY LIN CODE", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                                    TextButton(onClick = {
                                        val shareLink = "autodial://offers/import?code=$generatedCloudCode"
                                        val clipboard = localContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Offer Link", shareLink)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(localContext, "Shareable Deep Link Copied", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Text("COPY LINK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenLogo) }
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                    
                    Text("2. IMPORT CATALOG", fontWeight = FontWeight.Bold, color = GreenLogo)
                    OutlinedTextField(
                        value = cloudCode,
                        onValueChange = { cloudCode = it },
                        placeholder = { Text("Enter LIN Code or Paste Offers Link") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                showCloudSyncDialog = false
                                showImportConfirm = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B222C))
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("From File", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { 
                                if (cloudCode.isNotBlank()) {
                                    // Extract code if a full deep link is provided
                                    val cleanedCode = if (cloudCode.contains("code=")) {
                                        cloudCode.substringAfter("code=").substringBefore("&").trim()
                                    } else {
                                        cloudCode.trim()
                                    }
                                    
                                    if (cleanedCode.isNotEmpty()) {
                                        viewModel.importOffersFromCloud(cleanedCode, localContext)
                                        showCloudSyncDialog = false
                                    }
                                }
                            }, 
                            modifier = Modifier.weight(1f),
                            enabled = cloudCode.isNotBlank()
                        ) { 
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("From Link/Code", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCloudSyncDialog = false }) { Text("Close") } }
        )
    }

    if (offerForTimerWarning != null) {
        AlertDialog(
            onDismissRequest = { offerForTimerWarning = null },
            title = { Text("Timer Active", fontWeight = FontWeight.Bold) },
            text = { Text("This offer is currently controlled by a Timer. You must disable the Timer in the Timer Settings before you can manually toggle the offer status.") },
            confirmButton = {
                TextButton(onClick = { 
                    navController.navigate("offer_timer/${offerForTimerWarning?.id}")
                    offerForTimerWarning = null 
                }) {
                    Text("GO TO TIMER", color = GreenLogo, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { offerForTimerWarning = null }) { Text("CANCEL") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportOffers(localContext, it) } }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importOffers(localContext, it) } }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("Export Offers") },
            text = { Text("Select where to save the offers backup file.") },
            confirmButton = {
                TextButton(onClick = { 
                    exportLauncher.launch("autodial_offers_${System.currentTimeMillis()}.json")
                    showExportConfirm = false 
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Import Offers") },
            text = { Text("Select a backup file to import offers from. Existing offers with same price will be updated.") },
            confirmButton = {
                TextButton(onClick = { 
                    importLauncher.launch(arrayOf("*/*"))
                    showImportConfirm = false 
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddDialog || offerToEdit != null) {
        AddOfferDialog(
            viewModel = viewModel,
            editingOffer = offerToEdit,
            onDismiss = { 
                showAddDialog = false
                offerToEdit = null
            },
            onSave = { newOffer ->
                viewModel.saveOffer(newOffer)
                showAddDialog = false
                offerToEdit = null
            }
        )
    }

    if (offerToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete '${offerToDelete?.name}'?",
            onConfirm = {
                offerToDelete?.let { viewModel.deleteOffer(it) }
                offerToDelete = null
            },
            onDismiss = { offerToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offers", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showCloudSyncDialog = true }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync", tint = GreenLogo)
                    }
                    IconButton(onClick = { showImportConfirm = true }) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Import", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showExportConfirm = true }) {
                        Icon(Icons.Default.VerticalAlignTop, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3D2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00E676))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, color = Color.White) // Keep white for green button contrast
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Stats Row
            val totalOffersCount = offers.size
            val activeOffersCount = offers.count { it.isEnabled }
            val simpleOffersCount = offers.count { it.ussdMode == Offer.USSD_SIMPLE }
            val directOffersCount = offers.count { it.paymentMode == Offer.MODE_TILL || it.paymentMode == Offer.MODE_PAYBILL || it.ussdMode == Offer.USSD_SIMPLE }
            val advancedOffersCount = offers.count { it.ussdMode == Offer.USSD_ADVANCED }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { StatBox("Total", totalOffersCount.toString(), Color(0xFFFFD600)) }
                item { StatBox("Active", activeOffersCount.toString(), Color(0xFF00E676)) }
                item { StatBox("Simple", simpleOffersCount.toString(), Color(0xFF00B0FF)) }
                item { StatBox("Direct", directOffersCount.toString(), Color(0xFF2979FF)) }
                item { StatBox("Advanced", advancedOffersCount.toString(), Color(0xFF00BFA5)) }
            }

            // Category Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val catCount = if (category == "All") offers.size else offers.count { it.category == category }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text("$category ($catCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1B3D2F),
                            selectedLabelColor = Color(0xFF00E676),
                            containerColor = Color(0xFF1B222C),
                            labelColor = Color.Gray
                        ),
                        border = null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Offers List
            val filteredOffers = (if (selectedCategory == "All") offers else offers.filter { it.category == selectedCategory })
                .sortedBy { it.amount }
            
            if (filteredOffers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No offers found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredOffers) { offer ->
                        val isVerified = activeMaps.any { it.offerId == offer.id }
                        OfferCard(
                            offer = offer,
                            isVerified = isVerified,
                            onToggle = { 
                                if (offer.isTimerEnabled) {
                                    offerForTimerWarning = offer
                                } else {
                                    viewModel.toggleOffer(offer, it)
                                }
                            },
                            onEdit = { offerToEdit = offer },
                            onDelete = { offerToDelete = offer },
                            onMapTraining = { 
                                if (isVerified) {
                                    val map = activeMaps.find { it.offerId == offer.id }
                                    navController.navigate("map_review/${map?.id ?: 0}")
                                } else {
                                    navController.navigate("map_training/${offer.id}")
                                }
                            },
                            onTimerClick = {
                                navController.navigate("offer_timer/${offer.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(70.dp).height(85.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OfferCard(
    offer: Offer,
    isVerified: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMapTraining: () -> Unit,
    onTimerClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(offer.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(offer.ussdMode.uppercase(), Color(0xFF00BFA5))
                        Badge(offer.category.uppercase(), Color.Gray)
                        if (offer.isFallbackEnabled) {
                            Badge("FALLBACK", Color(0xFFFFD600))
                        }
                    }
                }
                Switch(
                    checked = offer.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE0FF00), checkedTrackColor = Color(0xFF1B3D2F))
                )
            }

            Spacer(Modifier.height(20.dp))

            // Info Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GridEntry("Agent Price", "KSh ${offer.amount.toInt()}", Color(0xFFFFD600))
                GridEntry("Saf. Price", "KSh ${offer.safPrice.toInt()}", Color(0xFF00E676))
                GridEntry("Commission", "KSh ${offer.commission.toInt()}", Color(0xFF00E676))
                GridEntry("SIM", "SIM ${offer.simSelection + 1}", Color(0xFF00BFA5))
                GridEntry("Payment", offer.paymentMode.replace(" Number", ""), MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(16.dp))

            // USSD Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(offer.ussdCode, modifier = Modifier.weight(1f), color = Color(0xFF00BFA5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    
                    Icon(
                        imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = if (isVerified) "Verified" else "Unverified",
                        tint = if (isVerified) Color(0xFF2196F3) else Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF00BFA5), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Map Training Card
            if (offer.ussdMode == Offer.USSD_ADVANCED) {
                Card(
                    onClick = onMapTraining,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isVerified) Color(0xFF2196F3).copy(alpha = 0.4f) else Color(0xFF00E676).copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isVerified) Icons.Default.Verified else Icons.Default.ModelTraining, 
                                contentDescription = null, 
                                tint = if (isVerified) Color(0xFF2196F3) else Color(0xFF00E676), 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (isVerified) "View Mapping" else "Map Training", 
                                color = MaterialTheme.colorScheme.onSurface, 
                                fontSize = 14.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Pills
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Auto Retry", Icons.Default.Block)
                Pill("Verifier: SAFARICOM", Icons.Default.Verified, showDot = true)
                Pill(
                    label = if (offer.isTimerEnabled) "Timer: ON" else "Timer", 
                    icon = Icons.Default.Schedule,
                    onClick = onTimerClick,
                    color = if (offer.isTimerEnabled) GreenLogo else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Last updated: ${java.text.SimpleDateFormat("MMMM dd, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(offer.lastModified))}", fontSize = 10.sp, color = Color.Gray)
            
            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun GridEntry(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = valueColor)
    }
}

@Composable
fun Pill(
    label: String, 
    icon: ImageVector, 
    showDot: Boolean = false, 
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = Modifier
            .height(32.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = Color(0xFF1B222C),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (color == GreenLogo) GreenLogo else Color(0xFF00BFA5), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 10.sp, color = color)
            if (showDot) {
                Spacer(Modifier.width(4.dp))
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF00E676)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOfferDialog(
    viewModel: MainViewModel, 
    editingOffer: Offer? = null,
    onDismiss: () -> Unit, 
    onSave: (Offer) -> Unit
) {
    var name by remember { mutableStateOf(editingOffer?.name ?: "") }
    var agentPrice by remember { mutableStateOf(editingOffer?.amount?.toString() ?: "") }
    var safPrice by remember { mutableStateOf(editingOffer?.safPrice?.toString() ?: "") }
    var commission by remember { mutableStateOf(editingOffer?.commission?.toString() ?: "") }
    var category by remember { mutableStateOf(editingOffer?.category ?: Offer.CATEGORY_DATA) }
    var paymentMode by remember { mutableStateOf(editingOffer?.paymentMode ?: Offer.MODE_BOTH) }
    var simIndex by remember { mutableStateOf(editingOffer?.simSelection ?: 0) }
    var ussdMode by remember { mutableStateOf(editingOffer?.ussdMode ?: Offer.USSD_SIMPLE) }
    
    var ussdTemplate by remember { mutableStateOf(editingOffer?.ussdCode ?: "") }
    var mainStart by remember { mutableStateOf(editingOffer?.mainStartTime ?: "00:00") }
    var mainEnd by remember { mutableStateOf(editingOffer?.mainEndTime ?: "23:59") }
    
    var altUssdTemplate by remember { mutableStateOf(editingOffer?.altUssdCode ?: "") }
    var altStart by remember { mutableStateOf(editingOffer?.altStartTime ?: "") }
    var altEnd by remember { mutableStateOf(editingOffer?.altEndTime ?: "") }

    var isFallbackEnabled by remember { mutableStateOf(editingOffer?.isFallbackEnabled ?: false) }
    var fallbackOfferId by remember { mutableStateOf(editingOffer?.fallbackOfferId) }
    var expandedCat by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(false) }
    var expandedUssd by remember { mutableStateOf(false) }
    
    val isDuplicatePrice by remember {
        derivedStateOf {
            agentPrice.toDoubleOrNull()?.let { 
                it != editingOffer?.amount && viewModel.isPriceDuplicate(it) 
            } ?: false
        }
    }
    
    val canSave by remember {
        derivedStateOf {
            name.isNotEmpty() && agentPrice.isNotEmpty() && !isDuplicatePrice
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { 
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface) 
                }
                Text(if (editingOffer == null) "Add Offer" else "Edit Offer", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                var showSaveConfirm by remember { mutableStateOf(false) }
                if (showSaveConfirm) {
                    AlertDialog(
                        onDismissRequest = { showSaveConfirm = false },
                        title = { Text("Save Offer") },
                        text = { Text("Are you sure you want to save this offer?") },
                        confirmButton = {
                            TextButton(onClick = {
                                onSave((editingOffer ?: Offer(name = "")).copy(
                                    name = name,
                                    amount = agentPrice.toDoubleOrNull() ?: 0.0,
                                    safPrice = safPrice.toDoubleOrNull() ?: 0.0,
                                    commission = commission.toDoubleOrNull() ?: 0.0,
                                    ussdCode = ussdTemplate,
                                    mainStartTime = mainStart,
                                    mainEndTime = mainEnd,
                                    altUssdCode = altUssdTemplate,
                                    altStartTime = altStart,
                                    altEndTime = altEnd,
                                    category = category,
                                    paymentMode = paymentMode,
                                    ussdMode = ussdMode,
                                    simSelection = simIndex,
                                    isFallbackEnabled = isFallbackEnabled,
                                    fallbackOfferId = if (isFallbackEnabled) fallbackOfferId else null,
                                    lastModified = System.currentTimeMillis()
                                ))
                                showSaveConfirm = false
                            }) { Text("Confirm", color = Color(0xFF00E676)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") }
                        }
                    )
                }

                TextButton(
                    onClick = { showSaveConfirm = true },
                    enabled = canSave
                ) {
                    Text("SAVE", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Offer name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (isDuplicatePrice) {
                Text("⚠️ Price exists. Please use a unique agent price.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = agentPrice,
                    onValueChange = { agentPrice = it },
                    label = { Text("Agent Price") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    isError = isDuplicatePrice
                )
                OutlinedTextField(
                    value = safPrice,
                    onValueChange = { safPrice = it },
                    label = { Text("Saf. Price") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            OutlinedTextField(
                value = commission,
                onValueChange = { commission = it },
                label = { Text("Commission") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenuBox(
                expanded = expandedCat,
                onExpandedChange = { expandedCat = !expandedCat }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedCat,
                    onDismissRequest = { expandedCat = false }
                ) {
                    listOf(Offer.CATEGORY_DATA, Offer.CATEGORY_SMS, Offer.CATEGORY_MINUTES, Offer.CATEGORY_OTHER).forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expandedCat = false })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedMode,
                        onExpandedChange = { expandedMode = !expandedMode }
                    ) {
                        OutlinedTextField(
                            value = paymentMode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment Mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMode,
                            onDismissRequest = { expandedMode = false }
                        ) {
                            listOf(Offer.MODE_BOTH, Offer.MODE_TILL, Offer.MODE_PAYBILL).forEach { mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = { paymentMode = mode; expandedMode = false })
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp), 
                        onClick = { simIndex = if(simIndex == 0) 1 else 0 },
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("SIM ${simIndex + 1}", fontSize = 14.sp)
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = ussdTemplate,
                onValueChange = { ussdTemplate = it },
                label = { Text("Main USSD (e.g. *188*8*BF#)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenuBox(
                expanded = expandedUssd,
                onExpandedChange = { expandedUssd = !expandedUssd }
            ) {
                OutlinedTextField(
                    value = ussdMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Automation Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUssd) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedUssd,
                    onDismissRequest = { expandedUssd = false }
                ) {
                    listOf(Offer.USSD_SIMPLE, Offer.USSD_ADVANCED).forEach { mode ->
                        DropdownMenuItem(text = { Text(mode) }, onClick = { ussdMode = mode; expandedUssd = false })
                    }
                }
            }

            // Fallback Plan Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isFallbackEnabled = !isFallbackEnabled }) {
                    Checkbox(
                        checked = isFallbackEnabled, 
                        onCheckedChange = { isFallbackEnabled = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                    )
                    Text("Enable Fallback (Auto-retry failure)", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
                
                if (isFallbackEnabled) {
                    val offersList by viewModel.allOffers.observeAsState(emptyList())
                    val otherOffers = offersList.filter { it.id != editingOffer?.id }
                    var expandedFallback by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expandedFallback,
                        onExpandedChange = { expandedFallback = !expandedFallback }
                    ) {
                        OutlinedTextField(
                            value = otherOffers.find { it.id == fallbackOfferId }?.name ?: "Choose safety plan...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Fallback Offer") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFallback) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFallback,
                            onDismissRequest = { expandedFallback = false }
                        ) {
                            otherOffers.forEach { off ->
                                DropdownMenuItem(
                                    text = { Text("${off.name} (KSh ${off.amount.toInt()})") },
                                    onClick = {
                                        fallbackOfferId = off.id
                                        expandedFallback = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
