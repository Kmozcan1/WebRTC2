package com.vox.sample.voxconnect_poc.webrtc

import android.content.Context
import com.vox.sample.voxconnect_poc.Candidate
import com.vox.sample.voxconnect_poc.CustomPeerConnectionObserver
import com.vox.sample.voxconnect_poc.CustomSdpObserver
import com.vox.sample.voxconnect_poc.SDP
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcClient(
    private val context: Context,
    private val iceServer: PeerConnection.IceServer,
    private val mode: String,
    private val listener: WebRtcClientListener,
    private val clientId: String? = null
) {
    private val LOG_TAG = WebRtcClient::class.java.simpleName

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options().apply {
            disableNetworkMonitor = true
        }

        return@lazy PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    private val localAudioTrack: AudioTrack by lazy {
        val audioConstraints = MediaConstraints()
        val audiosource = peerConnectionFactory.createAudioSource(audioConstraints)

        return@lazy peerConnectionFactory.createAudioTrack("audio", audiosource)
    }

    private lateinit var peerConnection: PeerConnection
    private lateinit var remoteDataChannel: DataChannel
    private lateinit var dataChannel: DataChannel


    init {
        createPeerConnection()
    }

    private fun createPeerConnection() {
        onIoThread {
            val rtcConfig = PeerConnection.RTCConfiguration(listOf(iceServer))
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                peerConnectionObserver
            )!!

            if (mode == "presenter") {
                addAudioTrackToLocalPeer()
            } else {
                createOffer()
            }

            createRemoteDataChannel()
        }
    }

    private fun createRemoteDataChannel() {
        val initializer = DataChannel.Init()
        initializer.id = 0
        remoteDataChannel = peerConnection.createDataChannel("0", initializer)
        remoteDataChannel.registerObserver(dataChannelObserver)
    }

    private fun addAudioTrackToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        peerConnection.addTrack(stream.audioTracks[0])
    }

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onMessage(buffer: DataChannel.Buffer?) {

        }

        override fun onBufferedAmountChange(bufferAmount: Long) {

        }

        override fun onStateChange() {

        }
    }

    private val peerConnectionObserver = object : CustomPeerConnectionObserver(LOG_TAG) {

        override fun onIceCandidate(iceCandidate: IceCandidate?) {
            super.onIceCandidate(iceCandidate)
            iceCandidate?.let {
                listener.onCandidateCreated(
                    Candidate(it.sdp, it.sdpMLineIndex, it.sdpMid),
                    clientId
                )
            }
        }

        override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            super.onAddTrack(rtpReceiver, mediaStreams)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            super.onConnectionChange(newState)

        }

        override fun onAddStream(mediaStream: MediaStream?) {
            super.onAddStream(mediaStream)
        }

        override fun onDataChannel(dataChannel: DataChannel?) {
            super.onDataChannel(dataChannel)
            dataChannel?.let {
                this@WebRtcClient.dataChannel = it
            }
        }
    }

    private fun mediaConstraintsForMode(mode: String): MediaConstraints {
        return MediaConstraints().also {
            if (mode == "listener") {
                it.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
                )
            }
        }
    }

    private fun createOffer() {
        peerConnection.createOffer(
            object : CustomSdpObserver(LOG_TAG) {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    super.onCreateSuccess(sessionDescription)

                    peerConnection.setLocalDescription(
                        CustomSdpObserver("Create Offer"),
                        sessionDescription
                    )

                    listener.onOfferCreated(
                        SDP(sessionDescription!!)
                    )
                }
            },
            mediaConstraintsForMode(mode)
        )
    }

    private fun createAnswer() {
        peerConnection.createAnswer(
            object : CustomSdpObserver(LOG_TAG) {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    super.onCreateSuccess(sessionDescription)
                    sessionDescription?.let {
                        peerConnection.setLocalDescription(
                            CustomSdpObserver("Create Answer"),
                            it
                        )
                        listener.onAnswerCreated(SDP(it), clientId)
                    }
                }
            }, MediaConstraints()
        )
    }

    // region === Public Methods ===

    fun receiveOffer(offer: SDP) {
        onIoThread {
            val sdp = SessionDescription(
                SessionDescription.Type.OFFER,
                offer.sdp
            )

            peerConnection.setRemoteDescription(
                object : CustomSdpObserver("Receive Offer"){
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        createAnswer()
                    }
                },
                sdp
            )
        }
    }

    fun receiveCandidate(candidate: Candidate) {
        onIoThread {
            val ice = IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
            )

            peerConnection.addIceCandidate(ice)
        }
    }

    fun receiveAnswer(answer: SDP) {
        onIoThread {
            val sdp = SessionDescription(
                SessionDescription.Type.ANSWER,
                answer.sdp
            )

            peerConnection.setRemoteDescription(
                CustomSdpObserver(LOG_TAG),
                sdp
            )
        }
    }

    // endregion
}