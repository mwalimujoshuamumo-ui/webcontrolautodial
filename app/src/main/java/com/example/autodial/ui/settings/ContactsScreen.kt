package com.example.autodial.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.autodial.data.Contact
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.CardBackground
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val contacts by viewModel.allContacts.observeAsState(emptyList())
    
    val context = LocalContext.current
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/vcard")
    ) { uri ->
        uri?.let { exportContactsAsVcf(context, contacts, it) }
    }

    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    if (contactToDelete != null) {
        com.example.autodial.ui.components.DeleteConfirmDialog(
            message = "Are you sure you want to delete contact '${contactToDelete?.name}'?",
            onConfirm = {
                contactToDelete?.let { viewModel.deleteContact(it) }
                contactToDelete = null
            },
            onDismiss = { contactToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Center", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("autodial_contacts.vcf") }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Export VCF", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts saved", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onDelete = { contactToDelete = contact }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${contact.name} ${contact.prefix}", fontWeight = FontWeight.Bold, color = Color.White)
                Text(contact.phone, color = Color.Gray, fontSize = 14.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
        }
    }
}

private fun exportContactsAsVcf(context: Context, contacts: List<Contact>, uri: Uri) {
    try {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
        outputStream?.use { out ->
            contacts.forEach { contact ->
                val vcf = "BEGIN:VCARD\n" +
                        "VERSION:3.0\n" +
                        "FN:${contact.name} ${contact.prefix}\n" +
                        "TEL;TYPE=CELL:${contact.phone}\n" +
                        "END:VCARD\n"
                out.write(vcf.toByteArray())
            }
        }
        android.widget.Toast.makeText(context, "Contacts exported successfully", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.e("ContactsScreen", "Export failed", e)
    }
}
