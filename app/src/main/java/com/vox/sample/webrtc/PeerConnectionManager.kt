package com.vox.sample.webrtc

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import com.microsoft.appcenter.utils.HandlerUtils
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

class PeerConnectionManager (private val context: Context) {

    private var audioConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory
    private var audioSource: AudioSource
    private var localAudioTrack: AudioTrack
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null
    private val executor = Executors.newSingleThreadExecutor()




    init {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        val options = PeerConnectionFactory.Options()

        PeerConnectionFactory.initialize(initializationOptions)
        saveRecordedAudioToFile = RecordedAudioToFileController(executor, context)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setSamplesReadyCallback(saveRecordedAudioToFile)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
    }

    fun getLocalAudioTrack(): AudioTrack {
        return localAudioTrack
    }


}