package com.qryde.qraie

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.qryde.qraie.prefs.Prefs

class QraieApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Prefs.init(this)
        FirebaseApp.initializeApp(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                "qraie_notifications",
                "Qraie Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications from Qraie"
            }
        )
    }
}
