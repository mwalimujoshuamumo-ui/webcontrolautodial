package com.example.autodial.ui.offers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.autodial.data.UssdMap
import com.example.autodial.data.UssdMapStep
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.util.UssdSessionManager


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTrainingScreen(viewModel: MainViewModel, offerId: Int, onBack: () -> Unit) {
    val offers by viewModel.allOffers.observeAsState(emptyList())
    val offer = offers.find { it.id == offerId }
    val isDataHidden by viewModel.isDataHidden.collectAsState()
    val capturedSteps by viewModel.trainingSteps.collectAsState(emptyList())
    
    val context = LocalContext.current
    var isTraining by remember { mutableStateOf(false) }
    var trainingNumber by remember { mutableStateOf(viewModel.getTrainingPhoneNumber()) }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearTraining()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Map Training", fontWeight = FontWeight.Bold)
                        if (isDataHidden) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!isTraining) {
                FloatingActionButton(
                    onClick = { 
                        isTraining = true
                        offer?.let { viewModel.startMapTraining(it, context) }
                    },
                    containerColor = GreenLogo
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Automated Mapping", tint = Color.Black)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (offer == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Offer not found", color = Color.Gray)
                }
            } else {
                Text(
                    text = "Training Offer: ${offer.name}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "USSD Template: ${offer.ussdCode}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(24.dp))

                if (!isTraining) {
                    TrainingInstructions(
                        trainingNumber = trainingNumber,
                        onTrainingNumberChange = {
                            trainingNumber = it
                            viewModel.setTrainingPhoneNumber(it)
                        }
                    )
                } else {
                    LiveTrainingView(viewModel, isDataHidden, capturedSteps, onBack)
                }
            }
        }
    }
}

@Composable
fun TrainingInstructions(trainingNumber: String, onTrainingNumberChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ModelTraining,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = GreenLogo
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ready to Train",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Tapping Start will launch the USSD sequence.",
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Training Phone Number (Replaces 0700000000 / BF)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = trainingNumber,
                    onValueChange = onTrainingNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Phone Number") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This number will be used whenever a USSD step asks for a phone number or recipient during training.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun LiveTrainingView(viewModel: MainViewModel, isDataHidden: Boolean, capturedSteps: List<com.example.autodial.data.UssdStepWithOptions>, onBack: () -> Unit) {
    val context = LocalContext.current
    val currentMapId by viewModel.currentTrainingMapId.collectAsState()

    // This will show live USSD responses and captured steps
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LIVE USSD RESPONSE", fontSize = 10.sp, color = GreenLogo, fontWeight = FontWeight.Bold)
                    if (isDataHidden) {
                        Text("MASKED", fontSize = 10.sp, color = Color.Yellow, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isDataHidden) "Response masked for security..." else "Automation is running...",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CAPTURED STEPS (${capturedSteps.size})", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
            
            if (capturedSteps.isNotEmpty()) {
                Button(
                    onClick = { 
                        if (currentMapId != -1) {
                            viewModel.approveMapById(currentMapId)
                            android.widget.Toast.makeText(context, "Mapping Approved Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("APPROVE MAPPING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (capturedSteps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No steps captured yet", color = Color.DarkGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(capturedSteps.sortedBy { it.step.stepNumber }) { stepWithOptions ->
                    StepReviewCardSimple(stepWithOptions)
                }
            }
        }
    }
}

@Composable
fun StepReviewCardSimple(stepWithOptions: com.example.autodial.data.UssdStepWithOptions) {
    val step = stepWithOptions.step
    val headerBlue = Color(0xFF00B0FF)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MENU STEP ${step.stepNumber}",
                    fontWeight = FontWeight.Bold,
                    color = headerBlue,
                    fontSize = 12.sp
                )
                
                Surface(
                    color = headerBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val typedLabel = if (step.selectionType == "CUSTOMER_NUMBER") {
                        step.trainingValue
                    } else {
                        step.selectedText.ifEmpty { step.selectedOption }
                    }
                    
                    Text(
                        text = "Typed: $typedLabel",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = headerBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = step.fullResponse.take(100) + if(step.fullResponse.length > 100) "..." else "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

