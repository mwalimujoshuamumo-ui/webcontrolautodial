package com.example.autodial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val currentTheme by viewModel.appTheme.collectAsState()
    var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Theme", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.setAppTheme(selectedTheme)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Choose your preferred visual style.", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.height(16.dp))
            
            ThemeOption("System Default", Icons.Default.DarkMode, selectedTheme == "System") { selectedTheme = "System" }
            ThemeOption("Light Mode", Icons.Default.LightMode, selectedTheme == "Light") { selectedTheme = "Light" }
            ThemeOption("Dark Mode", Icons.Default.DarkMode, selectedTheme == "Dark") { selectedTheme = "Dark" }
        }
    }
}

@Composable
fun ThemeOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onSelect: () -> Unit) {
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) Color(0xFF1E2632) else Color.Transparent,
            contentColor = if (isSelected) GreenLogo else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GreenLogo else Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text(label, fontWeight = FontWeight.Medium)
            }
            RadioButton(selected = isSelected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = GreenLogo))
        }
    }
}
