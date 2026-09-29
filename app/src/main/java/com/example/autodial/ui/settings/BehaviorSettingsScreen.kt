package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val ussdDelay by viewModel.ussdResponseDelay.collectAsState()
    val queueCooldown by viewModel.queueCooldownDelay.collectAsState()
    val simpleTimeout by viewModel.simpleModeTimeout.collectAsState()
    val advancedTimeout by viewModel.advancedModeTimeout.collectAsState()

    var ussdDelayStr by remember(ussdDelay) { mutableStateOf(ussdDelay.toString()) }
    var queueCooldownStr by remember(queueCooldown) { mutableStateOf(queueCooldown.toString()) }
    var simpleTimeoutStr by remember(simpleTimeout) { mutableStateOf(simpleTimeout.toString()) }
    var advancedTimeoutStr by remember(advancedTimeout) { mutableStateOf(advancedTimeout.toString()) }
    
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation Behavior", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val delay = ussdDelayStr.toLongOrNull() ?: 0L
                        val cooldown = queueCooldownStr.toLongOrNull() ?: 100L
                        val simple = simpleTimeoutStr.toIntOrNull() ?: 10
                        val advanced = advancedTimeoutStr.toIntOrNull() ?: 30
                        
                        viewModel.setUssdResponseDelay(delay)
                        viewModel.setQueueCooldownDelay(cooldown)
                        viewModel.setSimpleTimeout(simple)
                        viewModel.setAdvancedTimeout(advanced)
                                        
                        android.widget.Toast.makeText(context, "Changes saved successfully.", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text("SAVE", color = GreenLogo, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Configure how the app handles USSD interactions and response speed.", color = Color.Gray, fontSize = 14.sp)
            
            Text("Speed Presets", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ussdDelayStr == "0",
                    onClick = {
                        ussdDelayStr = "0"
                        queueCooldownStr = "100"
                    },
                    label = { Text("Ultra Fast (0ms)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenLogo,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = ussdDelayStr == "50",
                    onClick = {
                        ussdDelayStr = "50"
                        queueCooldownStr = "300"
                    },
                    label = { Text("Fast (50ms)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenLogo,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = ussdDelayStr == "200",
                    onClick = {
                        ussdDelayStr = "200"
                        queueCooldownStr = "800"
                    },
                    label = { Text("Balanced (200ms)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenLogo,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            Spacer(Modifier.height(4.dp))
            
            OutlinedTextField(
                value = ussdDelayStr,
                onValueChange = { ussdDelayStr = it },
                label = { Text("USSD Response Delay (ms)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                supportingText = { Text("Time to wait before responding to each USSD menu screen (0ms for instant reply)", color = Color.Gray) }
            )

            OutlinedTextField(
                value = queueCooldownStr,
                onValueChange = { queueCooldownStr = it },
                label = { Text("Queue Cooldown Delay (ms)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                supportingText = { Text("Time to wait between consecutive automatic transactions in queue", color = Color.Gray) }
            )
            
            OutlinedTextField(
                value = simpleTimeoutStr,
                onValueChange = { simpleTimeoutStr = it },
                label = { Text("Simple Mode Timeout (Seconds)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = advancedTimeoutStr,
                onValueChange = { advancedTimeoutStr = it },
                label = { Text("Advanced Mode Timeout (Seconds)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

        }
    }
}
