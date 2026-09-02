package com.wannaverse.chimesdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private lateinit var notificationManager: NotificationManager

    private val CHANNEL_ID = "ScreenCaptureServiceChannelID"
    private val CHANNEL_NAME = "Screen Share"
    private val SERVICE_ID = 1

    private val binder = ScreenCaptureBinder()

    class ScreenCaptureBinder : Binder()

    override fun onCreate() {
        super.onCreate()

        println("capture service created")

        notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        startForeground(
            SERVICE_ID,
            NotificationCompat.Builder(this, CHANNEL_ID).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder
}