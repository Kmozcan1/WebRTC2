package com.vox.sample.voxconnect_poc.webrtc

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.vox.sample.voxconnect_poc.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer


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
    private lateinit var receivingDataChannel: DataChannel
    private lateinit var broadcastingDataChannel: DataChannel


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

            createRemoteDataChannel()

            if (mode == "presenter") {
                addAudioTrackToLocalPeer()
            } else {
                createOffer()
            }
        }
    }

    private fun createRemoteDataChannel() {
        val initializer = DataChannel.Init()
        initializer.id = 0
        receivingDataChannel = peerConnection.createDataChannel("dataChannel", initializer)
        receivingDataChannel.registerObserver(dataChannelObserver)
    }

    private fun addAudioTrackToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        peerConnection.addTrack(stream.audioTracks[0])
    }

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onMessage(buffer: DataChannel.Buffer?) {
            val data = buffer!!.data
            val bytes = ByteArray(data.remaining())
            data[bytes]
            val strMsg = String(bytes)

            val message = Gson().fromJson(strMsg, DataChannelMessage::class.java)

            listener.onReceiveMessage(message, clientId)
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
            Log.d(LOG_TAG, "New Peer Connection state: $newState")
        }

        override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
            super.onIceConnectionChange(iceConnectionState)
            Log.d(LOG_TAG, "New Ice Connection state: $iceConnectionState")
            listener.onConnectionStateChanged(iceConnectionState!!, clientId)
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            super.onAddStream(mediaStream)
        }

        override fun onDataChannel(dataChannel: DataChannel?) {
            super.onDataChannel(dataChannel)
            onIoThread {
                dataChannel?.let {
                    this@WebRtcClient.broadcastingDataChannel = it
                }
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

    fun sendMessage(message: DataChannelMessage) {
        val msgStr = message.toJsonString()
        val buffer = ByteBuffer.wrap(msgStr.toByteArray(Charsets.UTF_8))
        val data = DataChannel.Buffer(buffer, false)
        broadcastingDataChannel.send(data)
    }

    fun disconnect() {
        onIoThread {
            peerConnection.close()
            peerConnection.stopRtcEventLog()
        }
    }

    // endregion
}