package com.example.autodial.ui.settings

import android.content.Context
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.service.RelayService
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.util.PreferenceManager
import com.example.autodial.util.WebServerManager
import com.google.gson.JsonParser
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebControlSettingsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val clipboardManager = LocalClipboardManager.current

    var webEnabled by remember { mutableStateOf(prefManager.isWebControlEnabled()) }
    var portText by remember { mutableStateOf(prefManager.getWebControlPort().toString()) }
    var notesJson by remember { mutableStateOf(prefManager.getTerminalNotes()) }

    val localIp = remember { getLocalIpAddress(context) }
    val serverUrl = "http://$localIp:${prefManager.getWebControlPort()}"
    val isRunning = WebServerManager.isServerRunning()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Web Control", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Enable / Disable Switch Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable Web Control Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            "Allows remote management from web browser",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = webEnabled,
                        onCheckedChange = { enabled ->
                            webEnabled = enabled
                            prefManager.setWebControlEnabled(enabled)
                            if (enabled) {
                                WebServerManager.startServer(context)
                            } else {
                                WebServerManager.stopServer()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GreenLogo, checkedTrackColor = GreenLogo.copy(alpha = 0.3f))
                    )
                }
            }

            // Global Cloud Control Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Global Cloud Web Portal", fontWeight = FontWeight.Bold, color = Color.White)
                        Surface(
                            color = GreenLogo.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "WORLDWIDE",
                                color = GreenLogo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Firebase Cloud Access URL", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))

                    val cloudUrl = "https://adminautodial.web.app"
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cloudUrl,
                                fontWeight = FontWeight.Bold,
                                color = GreenLogo,
                                fontSize = 15.sp
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(cloudUrl))
                                Toast.makeText(context, "Cloud URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Cloud URL", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Access your AUTODIAL terminal from ANY browser on ANY network worldwide using your registered email and password.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                }
            }

            // GitHub Pages Web Portal Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GitHub Pages Web Portal", fontWeight = FontWeight.Bold, color = Color.White)
                        Surface(
                            color = GreenLogo.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "GITHUB HOSTED",
                                color = GreenLogo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("GitHub Pages Access URL", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))

                    val githubUrl = "https://mwalimujoshuamumo-ui.github.io/webcontrolautodial/public/"
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = githubUrl,
                                fontWeight = FontWeight.Bold,
                                color = GreenLogo,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(githubUrl))
                                Toast.makeText(context, "GitHub Pages URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy GitHub URL", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Access your AUTODIAL terminal from ANY browser via GitHub Pages web deployment.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                }
            }

            // Local Wi-Fi Status & Connection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Server Status", fontWeight = FontWeight.Bold, color = Color.White)
                        Surface(
                            color = if (isRunning) GreenLogo.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isRunning) "ACTIVE" else "STOPPED",
                                color = if (isRunning) GreenLogo else Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Local Network Access URL (Same Wi-Fi)", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serverUrl,
                                fontWeight = FontWeight.Bold,
                                color = GreenLogo,
                                fontSize = 15.sp
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(serverUrl))
                                Toast.makeText(context, "Local URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Open this URL on any device connected to the same Wi-Fi/network to monitor live transactions, pause automation, and drop notes to this terminal.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                }
            }

            // Config Port Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text("Server Port Settings", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("HTTP Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val newPort = portText.toIntOrNull()
                            if (newPort != null && newPort in 1024..65535) {
                                prefManager.setWebControlPort(newPort)
                                WebServerManager.stopServer()
                                if (webEnabled) {
                                    WebServerManager.startServer(context)
                                }
                                Toast.makeText(context, "Port updated to $newPort", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enter a valid port between 1024 and 65535", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SAVE PORT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Terminal Drop Notes Display
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Web Drop Notes", fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { notesJson = prefManager.getTerminalNotes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Notes", tint = GreenLogo)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val notesList = parseNotesList(notesJson)
                    if (notesList.isEmpty()) {
                        Text("No notes dropped from web interface yet.", fontSize = 13.sp, color = Color.Gray)
                    } else {
                        notesList.forEach { note ->
                            Surface(
                                color = Color(0xFF161B22),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(note.first, color = Color.White, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(note.second, color = GreenLogo, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseNotesList(json: String): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    try {
        val array = JsonParser.parseString(json).asJsonArray
        array.forEach { elem ->
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val text = obj.get("text")?.asString ?: ""
                val ts = obj.get("timestamp")?.asLong ?: 0L
                val dateStr = if (ts > 0) java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(java.util.Date(ts)) else "Web Note"
                if (text.isNotEmpty()) {
                    list.add(Pair(text, dateStr))
                }
            }
        }
    } catch (e: Exception) {
        // ignore parse error
    }
    return list.asReversed()
}

private fun getLocalIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress != 0) {
            return String.format(
                Locale.getDefault(),
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        }
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: ""
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "127.0.0.1"
}
