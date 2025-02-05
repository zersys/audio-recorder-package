package com.audiorecorderpackage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        CHANNEL_ID, "Audio Recorder", NotificationManager.IMPORTANCE_LOW
      )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }


  private fun createNotification(): Notification {
    // Get the package name of your app
    val packageName = applicationContext.packageName

    // Create intent for React Native activity
    val intent = applicationContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      // Ensure we're using the React Native activity
      setClassName(packageName, "${packageName}.MainActivity")
      // Add action to help identify this intent in React Native
      action = "OPEN_FROM_NOTIFICATION"
    }

    // Create a PendingIntent
    val pendingIntent = if (intent != null) {
      PendingIntent.getActivity(
        this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    } else null
    return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Recording Audio")
      .setContentText("Recording in progress").setSmallIcon(android.R.drawable.sym_def_app_icon)
      .setPriority(NotificationCompat.PRIORITY_LOW).apply {
        pendingIntent?.let { setContentIntent(it) }
      }.build()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
