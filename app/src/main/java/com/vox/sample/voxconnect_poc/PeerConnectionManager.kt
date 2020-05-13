package com.vox.sample.voxconnect_poc

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ExecutorService

class PeerConnectionManager (context: Context, executor: ExecutorService, mode: String) {

    private lateinit var audioConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null

    init {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()

        val options = PeerConnectionFactory.Options().apply {
            disableNetworkMonitor = true
        }

        PeerConnectionFactory.initialize(initializationOptions)
        saveRecordedAudioToFile =
            RecordedAudioToFileController(executor, context)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setSamplesReadyCallback(saveRecordedAudioToFile)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        if (mode == "presenter") {
            audioConstraints = MediaConstraints()
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
        }
    }

    fun getLocalAudioTrack(): AudioTrack {
        return localAudioTrack
    }

    fun getPeerConnectionFactory(): PeerConnectionFactory {
        return peerConnectionFactory
    }

    fun getRecordedAudioToFileController(): RecordedAudioToFileController? {
        return saveRecordedAudioToFile
    }
}