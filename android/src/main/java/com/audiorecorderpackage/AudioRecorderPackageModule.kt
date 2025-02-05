package com.audiorecorderpackage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import java.io.IOException


@ReactModule(name = AudioRecorderPackageModule.NAME)
class AudioRecorderPackageModule(reactContext: ReactApplicationContext) :
  NativeAudioRecorderPackageSpec(reactContext) {

  private var mediaRecorder: MediaRecorder? = null
  private var outputFilePath: String? = null
  private var isRecording = false
  private var wasRecordingBeforeInterruption = false
  private lateinit var audioManager: AudioManager
  private lateinit var notificationManager: NotificationManager
  private var audioFocusRequest: AudioFocusRequest? = null

  private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        wasRecordingBeforeInterruption = isRecording
        if (isRecording) {
          mediaRecorder?.pause()
          sendRecordingStatusEvent(RECORDING_PAUSED_DUE_TO_EXTERNAL_ACTION)
          showNotification(
            "Recording Paused", "Recording paused due to audio interruption", NOTIFICATION_ID_PAUSED
          )
          notificationManager.cancel(NOTIFICATION_ID_RESUMED)
        }
      }

      AudioManager.AUDIOFOCUS_GAIN -> {
        if (wasRecordingBeforeInterruption) {
          mediaRecorder?.resume()
          sendRecordingStatusEvent(RECORDING_RESUMED)
          wasRecordingBeforeInterruption = false
          showNotification(
            "Recording Resumed", "Recording has been resumed", NOTIFICATION_ID_RESUMED
          )
          notificationManager.cancel(NOTIFICATION_ID_PAUSED)
        }
      }
    }
  }

  private fun showNotification(title: String, content: String, notificationId: Int) {
    val channelId = "audio_recorder_channel"
    val notification =
      NotificationCompat.Builder(reactApplicationContext, channelId).setContentTitle(title)
        .setContentText(content).setSmallIcon(android.R.drawable.sym_def_app_icon)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()

    try {
      notificationManager.notify(notificationId, notification)
    } catch (e: SecurityException) {
      // Handle notification permission not granted
    }
  }

  init {
    audioManager = reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        "audio_recorder_channel", "Audio Recorder", NotificationManager.IMPORTANCE_DEFAULT
      )
      notificationManager =
        reactApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }


  override fun getName(): String {
    return NAME
  }

  private fun sendRecordingStatusEvent(status: String) {
    val statusMap: WritableMap = Arguments.createMap().apply {
      putString("status", status)
    }
    emitOnRecordingStatusChanged(statusMap)
  }

  private fun startForeground() {
    val serviceIntent = Intent(reactApplicationContext, AudioRecorderService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      reactApplicationContext.startForegroundService(serviceIntent)
    } else {
      reactApplicationContext.startService(serviceIntent)
    }
  }

  private fun stopForeground() {
    val serviceIntent = Intent(reactApplicationContext, AudioRecorderService::class.java)
    reactApplicationContext.stopService(serviceIntent)
  }


  @RequiresApi(Build.VERSION_CODES.S)
  override fun startRecording(
    recordingTimeLimit: Double,
    notifyTimeLimitReached: Boolean?,
    notifyTimeLimit: Double?,
    promise: Promise
  ) {
    try {
      if (notifyTimeLimit != null && notifyTimeLimit >= recordingTimeLimit) {
        promise.reject(
          "INVALID_PARAMS",
          "notifyTimeLimit must be less than recordingTimeLimit"
        )
        return
      }
      val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

      audioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
          .setAudioAttributes(audioAttributes).setAcceptsDelayedFocusGain(false)
          .setOnAudioFocusChangeListener(audioFocusListener).build()

      // Request audio focus using the new API
      val result = audioManager.requestAudioFocus(audioFocusRequest!!)

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        promise.reject("AUDIO_FOCUS_ERROR", "Cannot get audio focus")
        return
      }

      val outputDir = reactApplicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
      if (outputDir == null) {
        promise.reject("FILE_ERROR", "Unable to access storage directory")
        return
      }
      outputFilePath = "${outputDir.absolutePath}/recording_${System.currentTimeMillis()}.mp4"

      mediaRecorder = MediaRecorder(reactApplicationContext).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(outputFilePath)
        prepare()
        start()
      }

      isRecording = true
      val response: WritableMap = Arguments.createMap().apply {
        putBoolean("started", true)
        putString("filePath", outputFilePath)
      }
      sendRecordingStatusEvent(RECORDING_STARTED)
      startForeground()
      promise.resolve(response)
    } catch (e: IOException) {
      promise.reject("START_ERROR", "Failed to start recording: ${e.message}")
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun abandonAudioFocus() {
    audioFocusRequest?.let { request ->
      audioManager.abandonAudioFocusRequest(request)
      audioFocusRequest = null
    }
  }


  @RequiresApi(Build.VERSION_CODES.O)
  override fun stopRecording(promise: Promise) {
    if (isRecording) {
      try {
        abandonAudioFocus()
        mediaRecorder?.apply {
          stop()
          release()
        }
        mediaRecorder = null
        isRecording = false

        val response: WritableMap = Arguments.createMap().apply {
          putString("filePath", outputFilePath)
        }
        sendRecordingStatusEvent(RECORDING_STOPPED)
        stopForeground()
        promise.resolve(response)
      } catch (e: RuntimeException) {
        promise.reject("STOP_ERROR", "Failed to stop recording: ${e.message}")
      }
    } else {
      promise.reject("STOP_ERROR", "Recording is not active")
    }
  }

  override fun pauseRecording(promise: Promise) {
    if (isRecording) {
      try {
        mediaRecorder?.pause()
        val response: WritableMap = Arguments.createMap().apply {
          putBoolean("paused", true)
        }
        sendRecordingStatusEvent(RECORDING_PAUSED)
        promise.resolve(response)
      } catch (e: IllegalStateException) {
        promise.reject("PAUSE_ERROR", "Failed to pause recording: ${e.message}")
      }
    } else {
      promise.reject("PAUSE_ERROR", "Recording is not active")
    }
  }

  override fun resumeRecording(promise: Promise) {
    if (isRecording) {
      try {
        mediaRecorder?.resume()
        val response: WritableMap = Arguments.createMap().apply {
          putBoolean("resumed", true)
        }
        sendRecordingStatusEvent(RECORDING_RESUMED)
        promise.resolve(response)
      } catch (e: IllegalStateException) {
        promise.reject("RESUME_ERROR", "Failed to resume recording: ${e.message}")
      }
    } else {
      promise.reject("RESUME_ERROR", "Recording is not active")
    }
  }

  companion object {
    const val NAME = "AudioRecorderPackage"
    private const val NOTIFICATION_ID_PAUSED = 2
    private const val NOTIFICATION_ID_RESUMED = 3
    private const val NOTIFICATION_ID_TIME_LIMIT = 4
    private const val RECORDING_PAUSED = "Paused"
    private const val RECORDING_STARTED = "Started"
    private const val RECORDING_STOPPED = "Stopped"
    private const val RECORDING_STOPPED_DUE_TO_TIME_LIMIT = "Stopped Due To Time Limit"
    private const val RECORDING_PAUSED_DUE_TO_EXTERNAL_ACTION = "Paused Due To External Action"
    private const val RECORDING_RESUMED = "Resumed"
  }
}
