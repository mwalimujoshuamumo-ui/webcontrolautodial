package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.NotificationTemplate
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(viewModel: MainViewModel) {
    val templates by viewModel.allTemplates.observeAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<NotificationTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<NotificationTemplate?>(null) }

    if (templateToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete this notification template?",
            onConfirm = {
                templateToDelete?.let { viewModel.deleteTemplate(it) }
                templateToDelete = null
            },
            onDismiss = { templateToDelete = null }
        )
    }

    if (showAddDialog) {
        TemplateDialog(
            template = selectedTemplate,
            onDismiss = { 
                showAddDialog = false
                selectedTemplate = null
            },
            onSave = { category, body ->
                viewModel.saveTemplate(
                    selectedTemplate?.copy(category = category, body = body) ?: NotificationTemplate(category = category, body = body)
                )
                showAddDialog = false
                selectedTemplate = null
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.example.autodial.util.PreferenceManager(context) }
    var selectedSim by remember { mutableStateOf(prefManager.getNotificationSimSlot()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { /* Back handled by NavHost or passed via param */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        prefManager.setNotificationSimSlot(selectedSim)
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
        ) {
            Text("SMS replies sent automatically to customers based on transaction outcome.", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.height(24.dp))

            // SIM Selector
            Text("Send Notifications Via", color = Color.White, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedSim == 0, onClick = { selectedSim = 0 })
                    Text("SIM 1", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedSim == 1, onClick = { selectedSim = 1 })
                    Text("SIM 2", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedSim == -1, onClick = { selectedSim = -1 })
                    Text("Default", color = Color.White)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (templates.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No templates added yet", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(templates) { template ->
                        TemplateItem(
                            template = template,
                            onEdit = { 
                                selectedTemplate = template
                                showAddDialog = true 
                            },
                            onDelete = { templateToDelete = template }
                        )
                    }
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Add Template", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun TemplateItem(template: NotificationTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when(template.category) {
                        NotificationTemplate.CAT_COMPLETE -> Color(0xFF00C853).copy(alpha = 0.2f)
                        NotificationTemplate.CAT_FAILED -> Color.Red.copy(alpha = 0.2f)
                        else -> Color.Gray.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        template.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = when(template.category) {
                            NotificationTemplate.CAT_COMPLETE -> Color(0xFF00C853)
                            NotificationTemplate.CAT_FAILED -> Color.Red
                            else -> Color.Gray
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(template.body, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateDialog(template: NotificationTemplate?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var category by remember { mutableStateOf(template?.category ?: NotificationTemplate.CAT_COMPLETE) }
    var body by remember { mutableStateOf(template?.body ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val placeholders = listOf("{txnid}", "{fname}", "{Lname}", "{Phonenumber}", "{time}", "{matchedoffer}")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) "New Notification" else "Edit Notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Event Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(NotificationTemplate.CAT_COMPLETE, NotificationTemplate.CAT_FAILED, NotificationTemplate.CAT_UNAVAILABLE).forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Body Input
                var textFieldValue by remember { 
                    mutableStateOf(
                        androidx.compose.ui.text.input.TextFieldValue(
                            text = body,
                            selection = androidx.compose.ui.text.TextRange(body.length)
                        )
                    ) 
                }
                
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { 
                        textFieldValue = it
                        body = it.text 
                    },
                    label = { Text("Message Body") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Hi {fname}, your {matchedoffer} was loaded successfully.") }
                )

                // Placeholder Chips
                Column {
                    Text("Tap to insert placeholder:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        placeholders.forEach { tag ->
                            SuggestionChip(
                                onClick = { 
                                    val newText = body + tag
                                    body = newText
                                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newText.length)
                                    )
                                },
                                label = { Text(tag, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(category, body) }, enabled = body.isNotBlank()) {
                Text("Save Template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
