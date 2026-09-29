package com.example.autodial.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifiedSendersScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val verifiedSenders by viewModel.verifiedSenders.collectAsState()
    var newSender by remember { mutableStateOf("") }
    
    // We don't have a bulk save for verified senders yet in ViewModel, 
    // but the rule is Save Button. 
    // I'll implement a local state and save all at once.
    var localSenders by remember { mutableStateOf(verifiedSenders.toList()) }
    var senderToDelete by remember { mutableStateOf<String?>(null) }

    if (senderToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to remove '$senderToDelete' from verified senders?",
            onConfirm = {
                localSenders = localSenders - senderToDelete!!
                senderToDelete = null
            },
            onDismiss = { senderToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verified Senders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Clear all and re-add
                        verifiedSenders.forEach { viewModel.removeVerifiedSender(it) }
                        localSenders.forEach { viewModel.addVerifiedSender(it) }
                        Toast.makeText(context, "Changes saved successfully.", Toast.LENGTH_SHORT).show()
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
        ) {
            Text("Numbers authorized to trigger automation (e.g., MPESA, 07...).", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSender,
                    onValueChange = { newSender = it },
                    label = { Text("Add Phone/Sender Name") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { 
                        if (newSender.isNotBlank() && !localSenders.contains(newSender)) {
                            localSenders = localSenders + newSender
                            newSender = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = GreenLogo)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (localSenders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No verified senders", color = Color.DarkGray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(localSenders) { sender ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sender, color = Color.White, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { senderToDelete = sender }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
