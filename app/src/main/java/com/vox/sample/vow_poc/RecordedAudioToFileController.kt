package com.vox.sample.vow_poc

import android.content.Context
import android.media.AudioFormat
import android.os.Environment
import android.util.Log
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.*
import java.util.concurrent.ExecutorService

class RecordedAudioToFileController(private val executor: ExecutorService, private val context: Context) :
    JavaAudioDeviceModule.SamplesReadyCallback {


    private val lock = Any()
    var isRunning: Boolean = false
    private var fileSizeInBytes: Long = 0
    private lateinit var aLawWriter: ALawFileWriter
    lateinit var pathToDir: String
    lateinit var name: String
    var started: Long = 0


    // Checks if external storage is available for read and write.
    private val isExternalStorageWritable: Boolean
        get() {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }

    init {
        Log.d(TAG, "ctor")
    }

    private fun startRecordingStream() {
        pathToDir = RecordingFactory.folderNameForRecordingKind(
            context,
            RecordingFactory.RECORDING_KIND_STREAM_LISTENER
        )
        started = System.currentTimeMillis()
        name = "stream_$started$AUDIO_FILE_EXTENSION"

        val f = File(pathToDir)
        if (!f.exists()) {
            f.mkdirs()
        }

        aLawWriter = ALawFileWriter(
            "$pathToDir/$name",
            SAMPLE_RATE.toLong()
        )

        try {
            aLawWriter.startWriting()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Should be called on the same executor thread as the one provided at
     * construction.
     */
    fun start(): Boolean {
        Log.d(TAG, "start")
        if (!isExternalStorageWritable) {
            Log.e(TAG, "Writing to external media is not possible")
            return false
        }
        synchronized(lock) {
            startRecordingStream()
            isRunning = true
        }
        return true
    }

    /**
     * Should be called on the same executor thread as the one provided at
     * construction.
     */
    fun stop() {
        Log.d(TAG, "stop")
        synchronized(lock) {
            stopRecordingStream()
            isRunning = false
            fileSizeInBytes = 0
        }
    }


    // Called when new audio samples are ready.
    override fun onWebRtcAudioRecordSamplesReady(samples: JavaAudioDeviceModule.AudioSamples) {
        // The native audio layer on Android should use 16-bit PCM format.
        if (samples.audioFormat != AudioFormat.ENCODING_PCM_16BIT) {
            Log.e(TAG, "Invalid audio format")
            return
        }
        synchronized(lock) {
            // Abort early if stop() has been called.
            if (!isRunning) {
                return
            }
        }
        // Append the recorded 16-bit audio samples to the open output file.
        executor.execute {
            try {
                // Set a limit on max file size. 58348800 bytes corresponds to
                // approximately 10 minutes of recording in mono at 48kHz.
                if (fileSizeInBytes < MAX_FILE_SIZE_IN_BYTES) {
                    // Writes samples.getData().length bytes to output stream.
                    aLawWriter.pushPCMData(samples.data)
                    fileSizeInBytes += samples.data.size.toLong()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write audio to file: " + e.message)
            }
        }
    }

    fun stopRecordingStream() {
        try {
            aLawWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "RecordedAudioToFile"
        const val MAX_FILE_SIZE_IN_BYTES = 58348800L
        const val AUDIO_FILE_EXTENSION = ".wav"
        const val SAMPLE_RATE = 48000 // Hz

    }
}
