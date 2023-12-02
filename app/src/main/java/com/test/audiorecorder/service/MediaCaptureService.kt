package com.test.audiorecorder.service

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.sql.Time
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.experimental.and

class MediaCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private lateinit var audioCaptureThread: Thread
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            SERVICE_ID, NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
            ).build()
        )

        mediaProjectionManager = applicationContext.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

    }

    override fun onBind(p0: Intent?): IBinder? = null


    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Audio Capture Service Channel", NotificationManager.IMPORTANCE_DEFAULT
        )

        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, intent.getParcelableExtra(EXTRA_RESULT_DATA)!!) as MediaProjection

                    Log.d(LOG_TAG,"start");
                    startCapturingAndPlayingAudio()
//
//                    startAudioCapture()
                    Service.START_STICKY
                }

                ACTION_STOP -> {
                    Log.d(LOG_TAG,"stop");

                    stopAudioCapture()
                    Service.START_NOT_STICKY
                }

                else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
            }
        } else {
            Service.START_NOT_STICKY
        }
    }


    private fun startCapturingAndPlayingAudio() {
        // Set the desired audio format and buffer size
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val bufferSizeInBytes = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Create an AudioRecord instance for capturing audio
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()

        // Create an AudioTrack instance for playing audio
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()

        // Start capturing audio with AudioRecord
        audioRecord?.startRecording()

        // Start playing audio with AudioTrack
        audioTrack?.play()

        // Create a buffer to hold the captured audio data
        val buffer = ByteArray(bufferSizeInBytes)

        // Continuously capture and play the audio
        while (true) {
            // Read the captured audio data
            val bytesRead = audioRecord?.read(buffer, 0, bufferSizeInBytes)

            if (bytesRead != null && bytesRead > 0) {
                // Write the captured audio data to the AudioTrack for playback
                audioTrack?.write(buffer, 0, bytesRead)
            }
        }
    }

    fun stopCapturingAndPlayingAudio() {
        // Stop capturing audio
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Stop playing audio
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }


    private fun startAudioCapture() {
        // TODO: add code for executing audio capture itself
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(8000)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        Log.d(LOG_TAG,"startAudioCapture");

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            return
        }
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
            .setAudioPlaybackCaptureConfig(config)
            .build()


        audioRecord!!.startRecording()
        Log.d(LOG_TAG,"audioRecord")

        audioCaptureThread = thread(start = true) {
            val currentDate = Calendar.getInstance().time

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(currentDate)
            val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${formattedDate}-recording.pcm")
            Log.d(LOG_TAG, "Created file for capture target: ${outputFile.path}")
            writeAudioToFile(outputFile)
        }
    }

    private fun createAudioFile(): File {
        val audioCapturesDirectory = File(getExternalFilesDir(null), "/AudioCaptures")
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs()
        }
        val timestamp = SimpleDateFormat("dd-MM-yyyy-hh-mm-ss", Locale.US).format(Date())
        val fileName = "Capture-$timestamp.pcm"
        return File(audioCapturesDirectory.absolutePath + "/" + fileName)
    }
    private fun writeAudioToFile(outputFile: File) {
        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(outputFile))
        val capturedAudioSamples = ShortArray(NUM_SAMPLES_PER_READ)

        try {
            while (!audioCaptureThread.isInterrupted) {
                audioRecord?.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ)
                val byteArray = capturedAudioSamples.toByteArray()

                // Write the entire buffer at once
                bufferedOutputStream.write(byteArray)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // Close the stream
            bufferedOutputStream.close()
        }

        Log.d(
            LOG_TAG,
            "Audio capture finished for ${outputFile.absolutePath}. File size is ${outputFile.length()} bytes."
        )
    }


//    private fun writeAudioToFile(outputFile: File) {
//
//        val fileOutputStream = FileOutputStream(outputFile)
//
//        val capturedAudioSamples = ShortArray(NUM_SAMPLES_PER_READ)
//
//
//
//        while (!audioCaptureThread.isInterrupted) {
//            audioRecord?.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ)
//
//            fileOutputStream.write(
//                capturedAudioSamples.toByteArray(),
//                0,
//                BUFFER_SIZE_IN_BYTES
//            )
//        }
//
//        fileOutputStream.close()
//        Log.d(
//            LOG_TAG,
//            "Audio capture finished for ${outputFile.absolutePath}. File size is ${outputFile.length()} bytes."
//        )
//    }

    private fun stopAudioCapture() {
        // TODO: Add code for stopping the audio capture
        Log.d(LOG_TAG,"stopAudioCapture");

        requireNotNull(mediaProjection) { "Tried to stop audio capture, but there was no ongoing capture in place!" }

        audioCaptureThread.interrupt()
        audioCaptureThread.join()

        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null

        mediaProjection!!.stop()
        stopSelf()
    }

    private fun ShortArray.toByteArray(): ByteArray {
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            bytes[i * 2] = (this[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (this[i].toInt() shr 8).toByte()
            this[i] = 0
        }
        return bytes
    }

    companion object {
        private const val LOG_TAG = "AudioCaptureService"
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        private const val NUM_SAMPLES_PER_READ = 4096
        private const val BYTES_PER_SAMPLE = 2 // 2 bytes since we hardcoded the PCM 16-bit format
        private const val BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE

        const val ACTION_START = "AudioCaptureService:Start"
        const val ACTION_STOP = "AudioCaptureService:Stop"
        const val EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData"


    }

}