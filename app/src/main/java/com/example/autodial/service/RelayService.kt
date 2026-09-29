package com.example.autodial.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autodial.MainActivity
import com.example.autodial.R
import com.example.autodial.util.PreferenceManager
import com.example.autodial.util.RelayClient
import com.example.autodial.util.RelayServer
import com.example.autodial.util.WebServerManager

class RelayService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AUTODIAL CONNECT Active")
                .setContentText("Maintaining link between devices...")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e("RelayService", "startForeground failed", e)
                // If it fails, we still want the service to try to run if possible, 
                // but Android might kill it if it's not in foreground.
            }

            acquireWakeLock()
            
            val prefManager = PreferenceManager(this)
            WebServerManager.startServer(this)

            if (prefManager.getDeviceRole() == "MAIN") {
                RelayServer.startServer(this)
            } else if (prefManager.getDeviceRole() == "LINKED") {
                val lastIp = prefManager.getLastConnectedIp()
                if (lastIp.isNotEmpty()) {
                    RelayClient.connectToMain(lastIp, this)
                }
                setupNetworkMonitor(prefManager)
            }
        } catch (e: Exception) {
            Log.e("RelayService", "Critical start failure", e)
        }

        return START_STICKY
    }

    private fun setupNetworkMonitor(prefManager: PreferenceManager) {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                val lastIp = prefManager.getLastConnectedIp()
                if (lastIp.isNotEmpty()) {
                    Log.d("RelayService", "Network restored. Attempting auto-reconnect to $lastIp")
                    RelayClient.connectToMain(lastIp, this@RelayService)
                }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        RelayServer.stopServer()
        RelayClient.stopConnection()
        WebServerManager.stopServer()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Autodial:RelayWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes fallback */)
        Log.d("RelayService", "WakeLock acquired for connectivity persistence")
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        Log.d("RelayService", "WakeLock released")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Autodial Connect Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "RelayServiceChannel"
        const val NOTIFICATION_ID = 1001
    }
}
