package com.example.jkt48bonusmonitor

import android.app.Application
import com.example.jkt48bonusmonitor.notification.NotificationHelper

class JKT48Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channel at app startup so it is ready before any worker fires
        NotificationHelper(this).createNotificationChannel()
    }
}
