package com.example.autodial.ui.offers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.UssdMapStep
import com.example.autodial.data.UssdStepWithOptions
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapReviewScreen(viewModel: MainViewModel, mapId: Int, onBack: () -> Unit) {
    var mapWithSteps by remember { mutableStateOf<com.example.autodial.data.UssdMapWithSteps?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Fetch real data from database
    LaunchedEffect(mapId) {
        mapWithSteps = viewModel.getMapWithSteps(mapId)
    }

    if (showDeleteConfirm) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "This will delete the approved interaction map and allow you to relearn. Are you sure?",
            onConfirm = {
                val offerId = mapWithSteps?.map?.offerId ?: 0
                viewModel.deleteMap(mapId)
                showDeleteConfirm = false
                android.widget.Toast.makeText(context, "Mapping Deleted. Relearning...", android.widget.Toast.LENGTH_SHORT).show()
                onBack() // Go back to offers, or I could navigate to training here if I had navController
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mapWithSteps?.map?.name ?: "Map Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Delete & Relearn", tint = Color.Red.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onBack() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Back", color = MaterialTheme.colorScheme.onSurface) }
                    
                    Button(
                        onClick = { 
                            mapWithSteps?.map?.let { 
                                viewModel.approveMap(it)
                                android.widget.Toast.makeText(context, "Map Approved & Activated", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Approve Map", color = Color.Black, fontWeight = FontWeight.Bold) }
                }

                Button(
                    onClick = { 
                        mapWithSteps?.map?.let { 
                            viewModel.relearnMap(it.id, context)
                            // NO LONGER CALLING onBack() TO LEAVE THE MAPPING PAGE OPEN W/ RELEARNING LOOP IN BACKGROUND
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RELEARN MAP", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Review the interaction structure captured by the recorder.", color = Color.Gray, fontSize = 14.sp)
            }
            
            val steps = mapWithSteps?.steps?.sortedBy { it.step.stepNumber } ?: emptyList()
            
            if (steps.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenLogo)
                    }
                }
            } else {
                items(steps) { stepWithOptions ->
                    StepReviewCard(stepWithOptions)
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StepReviewCard(stepWithOptions: com.example.autodial.data.UssdStepWithOptions) {
    val step = stepWithOptions.step
    val headerBlue = Color(0xFF00B0FF)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MENU STEP ${step.stepNumber}",
                    fontWeight = FontWeight.Bold,
                    color = headerBlue,
                    fontSize = 14.sp
                )
                
                Surface(
                    color = headerBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val typedLabel = if (step.selectionType == "CUSTOMER_NUMBER") {
                        step.trainingValue // e.g. 0700000000
                    } else {
                        step.selectedText.ifEmpty { step.selectedOption }
                    }
                    
                    Text(
                        text = "Typed: $typedLabel",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = headerBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Menu Content
            Text(
                text = step.fullResponse,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun OptionRow(number: String, text: String, isSelected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isSelected) GreenLogo else Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$number: $text",
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun Badge(containerColor: Color, content: @Composable () -> Unit) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            content()
        }
    }
}
