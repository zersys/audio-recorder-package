package com.audiorecorderpackage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
//import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioRecorderService : Service() {
//  private var mediaRecorder: MediaRecorder? = null
  private val CHANNEL_ID = "AudioRecorderChannel"
  private val NOTIFICATION_ID = 1

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Audio Recorder",
        NotificationManager.IMPORTANCE_LOW
      )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Recording Audio")
      .setContentText("Recording in progress")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
