package com.example.autodial.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.GreenLogo
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleScreen(viewModel: MainViewModel) {
    val tasks by viewModel.unifiedScheduledTasks.observeAsState(emptyList())
    val offers by viewModel.allOffers.observeAsState(emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<com.example.autodial.data.ScheduledTask?>(null) }
    var editingBotSession by remember { mutableStateOf<com.example.autodial.data.EngageSession?>(null) }

    if (showAddDialog || editingTask != null || editingBotSession != null) {
        val botOffer = if (editingBotSession != null) {
            offers.find { it.id == editingBotSession?.originalOfferId }
        } else null

        AddTaskDialog(
            offers = if (editingBotSession != null && botOffer != null) listOf(botOffer) else offers,
            editingTask = editingTask,
            editingBotSession = editingBotSession,
            onDismiss = { 
                showAddDialog = false
                editingTask = null
                editingBotSession = null
            },
            onConfirm = { task ->
                viewModel.scheduleTask(task)
                showAddDialog = false
                editingTask = null
            },
            onConfirmBot = { session, newTime ->
                viewModel.updateEngageSessionTime(session, newTime)
                editingBotSession = null
            }
        )
    }

    var taskToDelete by remember { mutableStateOf<com.example.autodial.data.ScheduledTask?>(null) }
    var botSessionToDelete by remember { mutableStateOf<com.example.autodial.data.EngageSession?>(null) }

    if (taskToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete this scheduled task for '${taskToDelete?.offerName}'?",
            onConfirm = {
                taskToDelete?.let { viewModel.deleteScheduledTask(it) }
                taskToDelete = null
            },
            onDismiss = { taskToDelete = null }
        )
    }

    if (botSessionToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to cancel this automated bot schedule for '${botSessionToDelete?.originalOfferName}'?",
            onConfirm = {
                botSessionToDelete?.let { viewModel.deleteEngageSession(it) }
                botSessionToDelete = null
            },
            onDismiss = { botSessionToDelete = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scheduled Executions", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
        Text("Manual and automated bot schedules", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(24.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Text("No tasks scheduled", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tasks) { task ->
                    UnifiedTaskItem(
                        task = task, 
                        onEdit = { 
                            if (task.type == "MANUAL") {
                                editingTask = task.originalObject as? com.example.autodial.data.ScheduledTask
                            } else {
                                editingBotSession = task.originalObject as? com.example.autodial.data.EngageSession
                            }
                        }, 
                        onDelete = { 
                            if (task.type == "MANUAL") {
                                taskToDelete = task.originalObject as? com.example.autodial.data.ScheduledTask
                            } else {
                                botSessionToDelete = task.originalObject as? com.example.autodial.data.EngageSession
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
        ) {
            Text("Schedule New Task", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UnifiedTaskItem(task: MainViewModel.UnifiedTask, onEdit: () -> Unit, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
    val isBot = task.type == "BOT"
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = if (isBot) Color(0xFF1A1A1A) else com.example.autodial.ui.theme.CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isBot) Color.Blue.copy(alpha = 0.3f) else GreenLogo.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(if (isBot) Color.Blue.copy(alpha = 0.1f) else GreenLogo.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isBot) Icons.Default.SmartToy else Icons.Default.Event, 
                    contentDescription = null, 
                    tint = if (isBot) Color.Cyan else GreenLogo, 
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
                    if (isBot) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = Color.Blue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("BOT", modifier = Modifier.padding(horizontal = 4.dp), fontSize = 9.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(task.subtitle, fontSize = 14.sp, color = Color.Gray)
                }
                Surface(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (task.time > 0) sdf.format(Date(task.time)) else "Pending", 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp, 
                        color = if (isBot) Color.Cyan else GreenLogo, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    offers: List<com.example.autodial.data.Offer>, 
    editingTask: com.example.autodial.data.ScheduledTask? = null,
    editingBotSession: com.example.autodial.data.EngageSession? = null,
    onDismiss: () -> Unit, 
    onConfirm: (com.example.autodial.data.ScheduledTask) -> Unit,
    onConfirmBot: (com.example.autodial.data.EngageSession, Long) -> Unit = { _, _ -> }
) {
    val isBot = editingBotSession != null
    
    var selectedOffer by remember { mutableStateOf(offers.find { it.id == (editingTask?.offerId ?: editingBotSession?.originalOfferId) } ?: offers.firstOrNull()) }
    var targetPhone by remember { mutableStateOf(editingTask?.targetNumber ?: editingBotSession?.customerNumber ?: "") }
    var expanded by remember { mutableStateOf(false) }
    
    val initialTime = editingTask?.scheduledTime ?: editingBotSession?.nextEligibleDate ?: System.currentTimeMillis()
    val calendar = remember { 
        Calendar.getInstance().apply { 
            timeInMillis = initialTime
        }
    }
    
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    var dateString by remember { mutableStateOf(sdfDate.format(Date(initialTime))) }
    var timeString by remember { mutableStateOf(sdfTime.format(Date(initialTime))) }
    
    var repeatMode by remember { mutableStateOf(editingTask?.repeatMode ?: com.example.autodial.data.ScheduledTask.REPEAT_ONCE) }
    val repeatOptions = listOf(
        com.example.autodial.data.ScheduledTask.REPEAT_ONCE,
        com.example.autodial.data.ScheduledTask.REPEAT_DAILY,
        com.example.autodial.data.ScheduledTask.REPEAT_DAYS
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBot) "Edit Bot Schedule" else "Schedule Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Offer Selection
                Text("Select Offer", fontSize = 12.sp, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isBot) expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedOffer?.name ?: "Select Offer",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { if (!isBot) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isBot
                    )
                    if (!isBot) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            offers.forEach { offer ->
                                DropdownMenuItem(
                                    text = { Text(offer.name) },
                                    onClick = {
                                        selectedOffer = offer
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetPhone,
                    onValueChange = { if (!isBot) targetPhone = it },
                    label = { Text("Target Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isBot
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val now = Calendar.getInstance()
                            android.app.DatePickerDialog(context, { _, year, month, day ->
                                calendar.set(year, month, day)
                                dateString = "$day/${month + 1}/$year"
                            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (dateString.isEmpty()) "Date" else dateString, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            android.app.TimePickerDialog(context, { _, hour, minute ->
                                calendar.set(Calendar.HOUR_OF_DAY, hour)
                                calendar.set(Calendar.MINUTE, minute)
                                timeString = String.format("%02d:%02d", hour, minute)
                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (timeString.isEmpty()) "Time" else timeString, fontSize = 12.sp)
                    }
                }

                if (!isBot) {
                    Text("Repeat Mode", fontSize = 12.sp, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeatOptions.forEach { mode ->
                            FilterChip(
                                selected = repeatMode == mode,
                                onClick = { repeatMode = mode },
                                label = { Text(mode, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedOffer != null && targetPhone.isNotEmpty() && dateString.isNotEmpty() && timeString.isNotEmpty()) {
                        if (isBot) {
                            onConfirmBot(editingBotSession!!, calendar.timeInMillis)
                        } else {
                            onConfirm(
                                com.example.autodial.data.ScheduledTask(
                                    id = editingTask?.id ?: 0,
                                    offerId = selectedOffer!!.id,
                                    offerName = selectedOffer!!.name,
                                    targetNumber = targetPhone,
                                    scheduledTime = calendar.timeInMillis,
                                    repeatMode = repeatMode
                                )
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) { Text(if (editingTask == null && !isBot) "Schedule" else "Update", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
