package com.audiorecorderpackage

import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
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

  object RecordingStatus {
    const val PAUSED = "Paused"
    const val STARTED = "Started"
    const val STOPPED = "Stopped"
    const val STOPPED_DUE_TO_TIME_LIMIT = "Stopped Due To Time Limit"
    const val PAUSED_DUE_TO_EXTERNAL_ACTION = "Paused Due To External Action"
    const val RESUMED = "Resumed"
  }

  override fun getName(): String {
    return NAME
  }

  private fun sentRecordingStatusEvent(status: String) {
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
      sentRecordingStatusEvent(RecordingStatus.STARTED)
      startForeground()
      promise.resolve(response)
    } catch (e: IOException) {
      promise.reject("START_ERROR", "Failed to start recording: ${e.message}")
    }
  }


  override fun stopRecording(promise: Promise) {
    if (isRecording) {
      try {
        mediaRecorder?.apply {
          stop()
          release()
        }
        mediaRecorder = null
        isRecording = false

        val response: WritableMap = Arguments.createMap().apply {
          putString("filePath", outputFilePath)
        }
        sentRecordingStatusEvent(RecordingStatus.STOPPED)
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
        sentRecordingStatusEvent(RecordingStatus.PAUSED)
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
        sentRecordingStatusEvent(RecordingStatus.RESUMED)
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
  }
}
