package com.example.autodial.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentManagementScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val isKeyboardSuppressed by viewModel.isKeyboardSuppressionEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Silent Management", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Manage how the app handles background tasks and UI suppression for a cleaner automation experience.",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            SilentOptionItem(
                title = "Keyboard Suppression",
                subtitle = "Automatically hide the soft keyboard during USSD sessions to prevent screen obstruction.",
                icon = Icons.Default.KeyboardHide,
                enabled = isKeyboardSuppressed,
                onToggle = { viewModel.setKeyboardSuppressionEnabled(it) }
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2632).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = GreenLogo)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Pro Tip: Enable these features to allow AUTODIAL to work efficiently without interrupting your manual phone usage.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SilentOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (enabled) GreenLogo else Color.Gray)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GreenLogo,
                    checkedTrackColor = GreenLogo.copy(alpha = 0.5f)
                )
            )
        }
    }
}
