package com.example.autodial

import android.app.Application
import com.example.autodial.util.AppStorage
import com.example.autodial.util.CloudStore
import com.google.firebase.FirebaseApp
import java.util.concurrent.Executors

class AutodialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            CloudStore.disableDiskCache()

            // Asynchronously purge legacy disk caches & duplicate backups on startup
            Executors.newSingleThreadExecutor().execute {
                AppStorage.purgeLegacyDiskCaches(this)
            }
        } catch (e: Exception) {
            android.util.Log.e("AutodialApp", "Firebase/cloud cache setup failed", e)
        }
    }
}
