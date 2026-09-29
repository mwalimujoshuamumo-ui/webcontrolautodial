package com.example.autodial.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.DarkBackground
import com.example.autodial.ui.theme.GreenLogo

@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("USSD & EXECUTION", fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        
        SettingsMenuItem(label = "Automation Behavior", icon = Icons.Default.PrecisionManufacturing) {
            navController.navigate("settings/behavior")
        }
        SettingsMenuItem(label = "M-PESA Inbox Scanner", icon = Icons.Default.Search) {
            navController.navigate("settings/mpesa_scan")
        }
        SettingsMenuItem(label = "Verified Senders", icon = Icons.Default.VerifiedUser) {
            // verified senders is small, maybe keep it in a dialog or separate page. 
            // The instructions say "dedicated page when necessary". 
            // Let's create a dedicated page for Verified Senders to be safe.
            navController.navigate("settings/verified_senders")
        }

        Spacer(Modifier.height(16.dp))
        Text("AUTOMATION", fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))

        SettingsMenuItem(label = "Quick Dial", icon = Icons.Default.Bolt) {
            navController.navigate("settings/quick_dial")
        }
        SettingsMenuItem(label = "Notifications", icon = Icons.Default.NotificationsActive) {
            navController.navigate("settings/notification")
        }
        SettingsMenuItem(label = "Engage Bot", icon = Icons.Default.Forum) {
            navController.navigate("settings/engage_bot")
        }
        SettingsMenuItem(label = "Silent Management", icon = Icons.Default.KeyboardHide) {
            navController.navigate("settings/silent")
        }
        SettingsMenuItem(label = "SMS Relay", icon = Icons.Default.ForwardToInbox) {
            navController.navigate("settings/relay_mgmt")
        }
        SettingsMenuItem(label = "Scheduler", icon = Icons.Default.Schedule) {
            navController.navigate("schedule")
        }
        SettingsMenuItem(label = "Emergency Cleanup", icon = Icons.Default.CleaningServices) {
            navController.navigate("settings/emergency_cleanup")
        }

        Spacer(Modifier.height(16.dp))
        Text("ACCOUNT & CONNECTIVITY", fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))

        SettingsMenuItem(label = "Connect", icon = Icons.Default.Lan) {
            navController.navigate("settings/connect")
        }
        SettingsMenuItem(label = "Remote Web Control", icon = Icons.Default.Language) {
            navController.navigate("settings/web_control")
        }
        SettingsMenuItem(label = "App Theme", icon = Icons.Default.Palette) {
            navController.navigate("settings/theme")
        }
        SettingsMenuItem(label = "Storage Management", icon = Icons.Default.SdCard) {
            navController.navigate("settings/storage")
        }
        SettingsMenuItem(label = "About AUTODIAL", icon = Icons.Default.Info) {
            navController.navigate("settings/about")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
