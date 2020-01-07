package com.vox.sample.webrtc

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.webrtc.*


class StreamActivity: AppCompatActivity(), SocketManager.SocketInterface {
    override fun connectionInitialized() {

    }

    companion object {
        private const val ALL_PERMISSIONS_CODE = 1
    }

    private lateinit var sdpConstraints: MediaConstraints
    private lateinit var audioConstraints: MediaConstraints
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private var gotUserMedia: Boolean = false
    private var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()
    private lateinit var localPeer: PeerConnection
    private val socketManager = SocketManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)
        socketManager.init(this)
        initializeOptions()
    }

    private fun initializeOptions() {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        gotUserMedia = true
        createPeerConnection()
        if (intent.getStringExtra("mode") == "listener") {
            socketManager.initClientSocket()
        } else {
            socketManager.initServerSocket()
        }
    }


    private fun answer() {
        localPeer.createAnswer(object : CustomSdpObserver("localCreateAns") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer.setLocalDescription(
                    CustomSdpObserver("localSetLocal"),
                    sessionDescription
                )
                socketManager.sendSdp(sessionDescription, intent.getStringExtra("mode"))
            }
        }, MediaConstraints())
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(peerIceServers)
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA

        localPeer = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(iceCandidate)
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    showToast("Received Remote stream")
                    if (intent.getStringExtra("mode") == "presenter") {
                        mediaStream.audioTracks[0].setEnabled(false)
                    }
                    super.onAddStream(mediaStream)
                    Log.e("audio", mediaStream.audioTracks[0].toString())
                    gotRemoteStream(mediaStream)
                }
            })!!
        addStreamToLocalPeer()
    }



    private fun addStreamToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        localPeer.addStream(stream)
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private fun gotRemoteStream(stream: MediaStream) {


    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == ALL_PERMISSIONS_CODE
            && grantResults.size == 2
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) {
            // all permissions granted
            initializeOptions()
        } else {
            finish()
        }
    }



    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        socketManager.sendCandidate(iceCandidate, intent.getStringExtra("mode"))
    }

    override fun onRemoteHangUp(msg: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onOfferReceived(signalingMessage: SignalingMessage) {
        showToast("Received Offer")
        localPeer.setRemoteDescription(
            CustomSdpObserver("localSetRemote"),
            SessionDescription(SessionDescription.Type.OFFER,
                signalingMessage.sessionDescription?.sdp
            )
        )
        answer()
    }

    override fun onAnswerReceived(signalingMessage: SignalingMessage) {
        showToast("Received Answer")
        try {
            localPeer.setRemoteDescription(
                CustomSdpObserver("localSetRemote"),
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    signalingMessage.sessionDescription!!.sdp
                )
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    override fun onIceCandidateReceived(signalingMessage: SignalingMessage) {
        try {
            localPeer.addIceCandidate(
                IceCandidate(
                    signalingMessage.candidate!!.sdpMid,
                    signalingMessage.candidate!!.sdpMLineIndex,
                    signalingMessage.candidate!!.sdp
                )
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    override fun onTryToStart() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreatedRoom() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onJoinedRoom() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onNewPeerJoined() {
        sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        localPeer.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer.setLocalDescription(
                    CustomSdpObserver("localSetLocalDesc"),
                    sessionDescription
                )
                Log.e("onCreateSuccess", "SignallingClient emit ")
                socketManager.sendSdp(sessionDescription, intent.getStringExtra("mode"))
            }
        }, sdpConstraints)
    }
}