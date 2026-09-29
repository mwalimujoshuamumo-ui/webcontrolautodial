package com.example.autodial.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.GreenLogo

@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val deviceId by viewModel.uniqueDeviceId.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showTokenIdDialog by remember { mutableStateOf(false) }
    var showPasswordResetConfirm by remember { mutableStateOf(false) }
    var restoreId by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    if (showPasswordResetConfirm) {
        AlertDialog(
            onDismissRequest = { showPasswordResetConfirm = false },
            title = { Text("Reset Password") },
            text = { Text("We will send a password reset link to $userEmail. Proceed?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.forgotPassword(userEmail)
                    showPasswordResetConfirm = false
                    Toast.makeText(context, "Reset email sent", Toast.LENGTH_SHORT).show()
                }) { Text("SEND LINK", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordResetConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    if (showTokenIdDialog) {
        AlertDialog(
            onDismissRequest = { showTokenIdDialog = false },
            title = { Text("Set Token ID") },
            text = { Text("Select SIM to retrieve your ID from (*100*4*1#):") },
            confirmButton = {
                Row {
                    TextButton(onClick = { 
                        viewModel.setTokenIdFromSim(0, context)
                        showTokenIdDialog = false
                    }) { Text("SIM 1") }
                    TextButton(onClick = { 
                        viewModel.setTokenIdFromSim(1, context)
                        showTokenIdDialog = false
                    }) { Text("SIM 2") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenIdDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout & Reset") },
            text = { Text("This will log you out and clear session data from this device. Tokens will remain linked to your account.") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.logout()
                    showLogoutConfirm = false
                }) { Text("LOGOUT", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Account") },
            text = {
                Column {
                    Text("Enter your Unique ID to restore tokens and settings.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreId,
                        onValueChange = { restoreId = it },
                        label = { Text("Unique ID") },
                        placeholder = { Text("AD-XXXXXX") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.restoreWithId(restoreId, context)
                    showRestoreDialog = false
                }) { Text("Restore", color = GreenLogo) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier.size(100.dp).clip(CircleShape).background(CardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = GreenLogo)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(userName.ifBlank { "Agent Terminal" }, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
        Text(userEmail.ifBlank { "Authorized Terminal" }, color = GreenLogo, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(40.dp))

        // ID Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileInfoRow(
                    icon = Icons.Default.Email,
                    label = "Registered Email",
                    value = userEmail,
                    showCopy = false
                )
                
                ProfileInfoRow(
                    icon = Icons.Default.Fingerprint,
                    label = "Unique Device ID",
                    value = deviceId,
                    showCopy = true,
                    onCopy = { 
                        copyToClipboard(context, "Unique ID", deviceId)
                        Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showPasswordResetConfirm = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Reset / Change Password", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { showTokenIdDialog = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Update Token ID from SIM", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = { showLogoutConfirm = true }) {
            Text("Logout & Reset Terminal", color = Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
        }
        
        Text(
            "AUTODIAL v1.3", 
            color = Color.Gray, 
            fontSize = 10.sp, 
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    showCopy: Boolean = false,
    onCopy: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, color = Color.Gray, fontSize = 11.sp)
                Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        if (showCopy) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GreenLogo, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}
