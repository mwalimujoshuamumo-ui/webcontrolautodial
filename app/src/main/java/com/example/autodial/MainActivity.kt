package com.example.autodial

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.autodial.data.AppDatabase
import com.example.autodial.service.UssdAccessibilityService
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.MainViewModelFactory
import com.example.autodial.ui.clients.ClientDetailsScreen
import com.example.autodial.ui.clients.ClientManagementScreen
import com.example.autodial.ui.clients.ClientViewModel
import com.example.autodial.ui.clients.ClientViewModelFactory
import com.example.autodial.ui.dashboard.DashboardScreen
import com.example.autodial.ui.offers.MapReviewScreen
import com.example.autodial.ui.offers.MapTrainingScreen
import com.example.autodial.ui.offers.OffersScreen
import com.example.autodial.ui.profile.ProfileScreen
import com.example.autodial.ui.schedule.ScheduleScreen
import com.example.autodial.ui.settings.SettingsScreen
import com.example.autodial.ui.tokens.TokenShopScreen
import com.example.autodial.ui.theme.AUTODIALTheme
import com.example.autodial.ui.theme.GreenLogo
import com.example.autodial.ui.transactions.TransactionDetailScreen
import com.example.autodial.ui.transactions.TransactionsScreen
import com.example.autodial.util.PreferenceManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { database.createRepository() }
    private val mpesaRepository by lazy { com.example.autodial.data.MpesaRepository() }
    private val preferenceManager by lazy { PreferenceManager(this) }
    private val firebaseDeviceManager by lazy { com.example.autodial.util.FirebaseDeviceManager.getInstance(applicationContext, preferenceManager, repository) }
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, repository, mpesaRepository, preferenceManager, firebaseDeviceManager)
    }
    private val clientViewModel: ClientViewModel by viewModels {
        ClientViewModelFactory(application, repository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkAccessibilityService()
        } else {
            Toast.makeText(this, "Permissions required for automation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 0. EXPLICIT FIREBASE INITIALIZATION
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("FATAL_START", "Firebase initialization failed", e)
        }
        
        // GLOBAL CRASH LOGGER
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("FATAL_CRASH", "Thread: ${thread?.name ?: "Unknown"}", throwable)
                // Log to shared preferences so we can see it on next launch
                val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash", "${System.currentTimeMillis()}: ${throwable?.message ?: "No message"}\n${throwable?.stackTraceToString() ?: "No stacktrace"}").apply()
            } catch (e: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Lock Screen & Wake-up Configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            firebaseDeviceManager.start()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase start failed", e)
        }

        enableEdgeToEdge()
        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val darkTheme = when(appTheme) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            AUTODIALTheme(darkTheme = darkTheme) {
                PermissionGate(viewModel) {
                    AuthGate(viewModel) {
                        MainScreen(viewModel)
                    }
                }
            }
        }
        checkPermissions()
        setupWorkers()
        checkWelcomeBonus()
        
        handleIntent(intent)
    }

    private fun setupWorkers() {
        try {
            val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.autodial.util.ScheduledTaskWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ScheduledTaskWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            // Midnight Reset Worker
            val now = java.util.Calendar.getInstance()
            val midnight = java.util.Calendar.getInstance()
            midnight.set(java.util.Calendar.HOUR_OF_DAY, 0)
            midnight.set(java.util.Calendar.MINUTE, 0)
            midnight.set(java.util.Calendar.SECOND, 0)
            midnight.set(java.util.Calendar.MILLISECOND, 0)
            
            if (midnight.before(now)) {
                midnight.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            
            val delay = midnight.timeInMillis - now.timeInMillis
            
            val midnightRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.autodial.util.MidnightWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
             .build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "MidnightResetWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                midnightRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "setupWorkers failed", e)
        }
    }

    private fun checkWelcomeBonus() {
        if (preferenceManager.isFirstRun()) {
            preferenceManager.addTokens(5)
            preferenceManager.setFirstRun(false)
            Toast.makeText(this, "Welcome! You've received 5 free units.", Toast.LENGTH_LONG).show()
        }
    }

    private fun configureLockScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error configuring lock screen flags", e)
        }
    }

    override fun onResume() {
        super.onResume()
        configureLockScreen()
        val isEnabled = isAccessibilityServiceEnabled(this, UssdAccessibilityService::class.java)
        viewModel.updateAccessibilityStatus(isEnabled)
        
        firebaseDeviceManager.triggerSync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreen()
        try {
            handleIntent(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "onNewIntent handle failed", e)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val markReadTitle = intent?.getStringExtra("MARK_AS_READ_TITLE")
        val markReadMsg = intent?.getStringExtra("MARK_AS_READ_MSG")
        if (markReadTitle != null && markReadMsg != null) {
            viewModel.markAnnouncementAsRead(markReadTitle, markReadMsg)
        }

        // Handle Deep Link for Offers Import
        intent?.data?.let { uri ->
            if (uri.scheme == "autodial" && uri.host == "offers" && uri.path == "/import") {
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    android.widget.Toast.makeText(this, "Importing offers from cloud...", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.importOffersFromCloud(code, this)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(viewModel: MainViewModel) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isDataHidden by viewModel.isDataHidden.collectAsState()
        val isGlobalPaused by viewModel.isGlobalAutomationEnabled.collectAsState()
        val announcementActive by viewModel.announcementActive.collectAsState()
        val announcementTitle by viewModel.announcementTitle.collectAsState()
        val announcementMessage by viewModel.announcementMessage.collectAsState()

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val activeDevices by com.example.autodial.util.RelayServer.activeDevices.collectAsState()
        val isLive = activeDevices.any { it.isConnected }

        var showExitDialog by remember { mutableStateOf(false) }

        // 1. Intercept Back Button for Exit Confirmation
        val isAtRoot = currentDestination?.route == "dashboard"
        BackHandler(enabled = true) {
            if (isAtRoot) {
                showExitDialog = true
            } else {
                navController.popBackStack()
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit App") },
                text = { Text("Are you sure you want to exit AUTODIAL?") },
                confirmButton = {
                    TextButton(onClick = { finish() }) { Text("Exit", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Announcement Notification Logic
        LaunchedEffect(announcementActive, announcementTitle, announcementMessage) {
            if (announcementActive && announcementTitle.isNotEmpty()) {
                val currentHash = (announcementTitle + announcementMessage).hashCode().toString()
                if (currentHash != preferenceManager.getLastAnnouncementHash()) {
                    showAnnouncementNotification(announcementTitle, announcementMessage)
                }
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                ) {
                    val mainItems = remember {
                        listOf(
                            Triple("DASHBOARD", "dashboard", Icons.Default.Home),
                            Triple("CLIENTS", "client_management", Icons.Default.People),
                            Triple("QUICK DIAL", "settings/quick_dial", Icons.Default.Bolt),
                            Triple("OFFERS", "offers", Icons.Default.LocalOffer),
                            Triple("TRANSACTIONS", "transactions", Icons.Default.History),
                            Triple("PROFILE", "profile", Icons.Default.Person)
                        )
                    }

                    val settingItems = remember {
                        listOf(
                            Triple("SCHEDULER", "schedule", Icons.Default.Schedule),
                            Triple("BEHAVIOR", "settings/behavior", Icons.Default.PrecisionManufacturing),
                            Triple("M-PESA SCAN", "settings/mpesa_scan", Icons.Default.Search),
                            Triple("VERIFIED SENDERS", "settings/verified_senders", Icons.Default.VerifiedUser),
                            Triple("NOTIFICATIONS", "settings/notification", Icons.Default.NotificationsActive),
                            Triple("SILENT MGMT", "settings/silent", Icons.Default.KeyboardHide),
                            Triple("ENGAGE BOT", "settings/engage_bot", Icons.Default.Forum),
                            Triple("SMS RELAY", "settings/relay_mgmt", Icons.AutoMirrored.Filled.ForwardToInbox),
                            Triple("WEB CONTROL", "settings/web_control", Icons.Default.Language),
                            Triple("CONNECT", "settings/connect", Icons.Default.Lan),
                            Triple("THEME", "settings/theme", Icons.Default.Palette),
                            Triple("ABOUT", "settings/about", Icons.Default.Info)
                        )
                    }

                    fun navigateSmoothly(route: String) {
                        scope.launch {
                            drawerState.close()
                            if (currentDestination?.route != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxHeight()) {
                        item {
                            Spacer(Modifier.height(48.dp))
                            Text(
                                "AUTODIAL", 
                                modifier = Modifier.padding(24.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                color = GreenLogo
                            )
                        }

                        items(mainItems, key = { it.second }) { (label, route, icon) ->
                            NavigationDrawerItem(
                                label = { Text(label, fontWeight = FontWeight.Black, fontSize = 15.sp) },
                                selected = currentDestination?.hierarchy?.any { it.route?.startsWith(route) == true } == true,
                                onClick = { navigateSmoothly(route) },
                                icon = { Icon(icon, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = GreenLogo.copy(alpha = 0.1f),
                                    selectedIconColor = GreenLogo,
                                    selectedTextColor = GreenLogo
                                )
                            )
                        }

                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                                color = Color.Gray.copy(alpha = 0.1f)
                            )
                            Text(
                                "SETTINGS", 
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }

                        items(settingItems, key = { it.second }) { (label, route, icon) ->
                            NavigationDrawerItem(
                                label = { Text(label, fontWeight = FontWeight.Black, fontSize = 15.sp) },
                                selected = currentDestination?.hierarchy?.any { it.route?.startsWith(route) == true } == true,
                                onClick = { navigateSmoothly(route) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = GreenLogo.copy(alpha = 0.05f),
                                    selectedIconColor = GreenLogo,
                                    selectedTextColor = GreenLogo,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                )
                            )
                        }

                        item {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "v1.3.3 (Master Pro Elite)", 
                                modifier = Modifier.padding(24.dp),
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    val title = when (currentDestination?.route) {
                        "dashboard" -> "Dashboard"
                        "offers" -> "Offers"
                        "transactions" -> "Transactions"
                        "profile" -> "Profile"
                        "client_management" -> "Client Management"
                        "client_details/{clientId}" -> "Client Details"
                        "settings" -> "Settings"
                        "schedule" -> "Schedule Execution"
                        else -> "AUTODIAL"
                    }
                    CenterAlignedTopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (isLive) {
                                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulsing")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.2f,
                                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                        animation = androidx.compose.animation.core.tween(1000),
                                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                        animation = androidx.compose.animation.core.tween(1000),
                                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(10.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            this.alpha = alpha
                                        }
                                        .background(Color(0xFF00E676), androidx.compose.foundation.shape.CircleShape)
                                )
                                Text("LIVE", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 12.dp))
                            }
                            val accessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
                            if (!accessibilityEnabled) {
                                val context = LocalContext.current
                                IconButton(onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }

                            if (currentDestination?.route == "dashboard") {
                                IconButton(onClick = { viewModel.toggleDataVisibility() }) {
                                    Icon(
                                        if (isDataHidden) Icons.Default.VisibilityOff else Icons.Outlined.Visibility, 
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            }
                            IconButton(onClick = { showExitDialog = true }) {
                                Icon(Icons.Outlined.ExitToApp, contentDescription = "Exit")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None }
                ) {
                    composable("dashboard") {
                        Box(modifier = Modifier.fillMaxSize()) {
                            DashboardScreen(viewModel, navController)
                            
                            if (!isGlobalPaused) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PauseCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp),
                                            tint = Color.Yellow
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "PAUSED BY ADMIN",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            "Automation is temporarily disabled by system administrator.",
                                            color = Color.LightGray,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    composable("client_management") {
                        ClientManagementScreen(clientViewModel, viewModel, navController)
                    }
                    composable("client_details/{clientId}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("clientId")?.toIntOrNull() ?: 0
                        ClientDetailsScreen(clientViewModel, id, onBack = { navController.popBackStack() })
                    }
                    composable("offers") {
                        OffersScreen(viewModel, navController)
                    }
                    composable("transactions?filter={filter}") { backStackEntry ->
                        val filter = backStackEntry.arguments?.getString("filter") ?: "All"
                        TransactionsScreen(viewModel, navController, initialFilter = filter)
                    }
                    composable("transaction_detail/{transactionId}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("transactionId")?.toIntOrNull() ?: 0
                        TransactionDetailScreen(viewModel, id, onBack = { navController.popBackStack() })
                    }
                    composable("profile") {
                        ProfileScreen(viewModel)
                    }
                    composable("settings") {
                        SettingsScreen(viewModel, navController)
                    }
                    composable("settings/mpesa_scan") {
                        com.example.autodial.ui.settings.MpesaScanScreen(viewModel)
                    }
                    composable("settings/quick_dial") {
                        com.example.autodial.ui.settings.QuickDialScreen(viewModel)
                    }
                    composable("settings/notification") {
                        com.example.autodial.ui.settings.NotificationSettingsScreen(viewModel)
                    }
                    composable("settings/engage_bot") {
                        com.example.autodial.ui.settings.EngageBotSettingsScreen(
                            viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings/silent") {
                        com.example.autodial.ui.settings.SilentManagementScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/relay_mgmt") {
                        com.example.autodial.ui.settings.RelaySettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/contacts") {
                        com.example.autodial.ui.settings.ContactsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/behavior") {
                        com.example.autodial.ui.settings.BehaviorSettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/theme") {
                        com.example.autodial.ui.settings.ThemeSettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/storage") {
                        com.example.autodial.ui.settings.StorageSettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/about") {
                        com.example.autodial.ui.settings.AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable("settings/verified_senders") {
                        com.example.autodial.ui.settings.VerifiedSendersScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/connect") {
                        com.example.autodial.ui.settings.ConnectSettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings/web_control") {
                        com.example.autodial.ui.settings.WebControlSettingsScreen(viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                    composable("schedule") {
                        ScheduleScreen(viewModel)
                    }
                    composable("map_training/{offerId}") { backStackEntry ->
                        val offerId = backStackEntry.arguments?.getString("offerId")?.toIntOrNull() ?: 0
                        MapTrainingScreen(viewModel, offerId, onBack = { navController.popBackStack() })
                    }
                    composable("map_review/{mapId}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("mapId")?.toIntOrNull() ?: 0
                        MapReviewScreen(viewModel, id, onBack = { navController.popBackStack() })
                    }
                    composable("offer_timer/{offerId}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("offerId")?.toIntOrNull() ?: 0
                        com.example.autodial.ui.offers.TimerSettingsScreen(viewModel, id, onBack = { navController.popBackStack() })
                    }
                    composable("token_shop") {
                        TokenShopScreen(viewModel, navController)
                    }
                    composable("commission") {
                        com.example.autodial.ui.dashboard.CommissionScreen(viewModel, navController)
                    }
                    composable("airtime_analytics") {
                        com.example.autodial.ui.dashboard.AirtimeAnalyticsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkAccessibilityService() {
        if (!isAccessibilityServiceEnabled(this, UssdAccessibilityService::class.java)) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @Composable
    fun AuthGate(viewModel: MainViewModel, content: @Composable () -> Unit) {
        val isRegistered by viewModel.isUserRegistered.collectAsState()
        val isVerified by viewModel.isEmailVerified.collectAsState()
        
        var authMode by remember { mutableStateOf("LOGIN") } // LOGIN, REGISTER

        // Refresh verification state only when pending verification
        LaunchedEffect(isRegistered, isVerified) {
            if (isRegistered && !isVerified) {
                while (!isVerified) {
                    viewModel.checkAuthState()
                    delay(5000)
                }
            }
        }

        if (!isRegistered) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (authMode == "LOGIN") {
                    com.example.autodial.ui.auth.LoginScreen(
                        viewModel = viewModel,
                        onNavigateToRegister = { authMode = "REGISTER" }
                    )
                } else {
                    com.example.autodial.ui.auth.RegistrationScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = { authMode = "LOGIN" }
                    )
                }
            }
        } else if (!isVerified) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                com.example.autodial.ui.auth.VerificationPendingScreen(viewModel)
            }
        } else {
            content()
        }
    }

    override fun onDestroy() {
        firebaseDeviceManager.setOffline()
        super.onDestroy()
    }

    @Composable
    fun PermissionGate(viewModel: MainViewModel, content: @Composable () -> Unit) {
        val context = LocalContext.current
        var permissionsGranted by remember { mutableStateOf(checkAllPermissionsGranted()) }
        val accessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()

        // Sync permissions on lifecycle RESUME (e.g. returning from settings) without polling loops
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    permissionsGranted = checkAllPermissionsGranted()
                    val isAccEnabled = isAccessibilityServiceEnabled(context, UssdAccessibilityService::class.java)
                    viewModel.updateAccessibilityStatus(isAccEnabled)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // Lightweight check loop ONLY when gate is blocking (not yet granted)
        LaunchedEffect(permissionsGranted) {
            if (!permissionsGranted) {
                while (!permissionsGranted) {
                    permissionsGranted = checkAllPermissionsGranted()
                    val isAccEnabled = isAccessibilityServiceEnabled(context, UssdAccessibilityService::class.java)
                    viewModel.updateAccessibilityStatus(isAccEnabled)
                    delay(1500)
                }
            }
        }

        if (permissionsGranted) {
            content()
        } else {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GppMaybe,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = GreenLogo
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Action Required",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AUTODIAL requires mandatory permissions to function. You cannot access the dashboard until these are granted.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(48.dp))
                    
                    if (!permissionsGranted) {
                        Button(
                            onClick = { checkPermissions() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo)
                        ) {
                            Text("GRANT SYSTEM PERMISSIONS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { finish() }) {
                        Text("EXIT APP", color = Color.Gray)
                    }
                }
            }
        }
    }

    private fun checkAllPermissionsGranted(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun showAnnouncementNotification(title: String, message: String) {
        val channelId = "announcements"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "System Announcements"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, name, importance)
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("MARK_AS_READ_TITLE", title)
            putExtra("MARK_AS_READ_MSG", message)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(androidx.core.app.NotificationManagerCompat.from(this)) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(2001, builder.build())
            }
        }
    }
}
