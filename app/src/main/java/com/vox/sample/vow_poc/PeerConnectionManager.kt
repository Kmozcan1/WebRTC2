package com.vox.sample.vow_poc

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ExecutorService

class PeerConnectionManager (context: Context, executor: ExecutorService) {

    private var audioConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory
    private var audioSource: AudioSource
    private var localAudioTrack: AudioTrack
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null

    init {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        val options = PeerConnectionFactory.Options()

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

        audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
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