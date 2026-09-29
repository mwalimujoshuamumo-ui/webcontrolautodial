package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.theme.DarkBackground

import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(Modifier.height(24.dp))
            Text("About AUTODIAL", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 24.sp)
            Text("Version 1.3.1", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Text("AUTODIAL is a high-performance USSD automation engine designed for agents.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(32.dp))
            Text("Support", color = Color(0xFF00B0FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("0791267095", color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("0700822857", color = Color.Gray)
            }
        }
    }
}
