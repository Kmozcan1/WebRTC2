package com.vox.sample.webrtc

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors
import android.content.Intent
import kotlinx.android.synthetic.main.activity_stream.*



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
    private lateinit var mode: String
    private var gotUserMedia: Boolean = false
    private var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()
    private var localPeer: PeerConnection? = null
    private val socketManager = SocketManager()
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null
    private val executor = Executors.newSingleThreadExecutor()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)
        mode = intent.getStringExtra("mode")
        if (mode == "presenter") {
            status_text_view.text = "presenting"
        } else {
            status_text_view.text = "connecting..."
        }
        socketManager.init(this)
        initializeOptions()
    }

    private fun initializeOptions() {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        val options = PeerConnectionFactory.Options()

        executor.execute {
            PeerConnectionFactory.initialize(initializationOptions)
            saveRecordedAudioToFile = RecordedAudioToFileController(executor, this.applicationContext)

            val audioDeviceModule = JavaAudioDeviceModule.builder(this)
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
            gotUserMedia = true
            createPeerConnection()

            if (mode == "listener") {
                socketManager.initClientSocket()
            } else {
                socketManager.initServerSocket()
            }
        }
    }


    private fun answer() {
        localPeer!!.createAnswer(object : CustomSdpObserver("localCreateAns") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(
                    CustomSdpObserver("localSetLocal"),
                    sessionDescription
                )
                socketManager.sendSdp(sessionDescription, mode)
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

        executor.execute {
            localPeer = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : CustomPeerConnectionObserver("localPeerCreation") {
                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        super.onIceCandidate(iceCandidate)
                        onIceCandidateReceived(iceCandidate)
                    }

                    override fun onAddStream(mediaStream: MediaStream) {
                        showToast("Received Remote stream")
                        if (mode == "presenter") {
                            mediaStream.audioTracks[0].setEnabled(false)
                        } else {
                            runOnUiThread {
                                record_button.visibility = View.VISIBLE
                                status_text_view.text = "listening"
                            }
                        }
                        super.onAddStream(mediaStream)
                        Log.e("audio", mediaStream.audioTracks[0].toString())
                        gotRemoteStream(mediaStream)
                    }
                })!!
            addStreamToLocalPeer()
        }
    }



    private fun addStreamToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        executor.execute {
            stream.addTrack(localAudioTrack)
            localPeer!!.addStream(stream)
        }
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
        executor.execute {
            socketManager.sendCandidate(iceCandidate, mode)
        }
    }

    override fun onRemoteHangUp(msg: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onOfferReceived(signalingMessage: SignalingMessage) {
        showToast("Received Offer")
        executor.execute {
            localPeer!!.setRemoteDescription(
                CustomSdpObserver("localSetRemote"),
                SessionDescription(SessionDescription.Type.OFFER,
                    signalingMessage.sessionDescription?.sdp
                )
            )
            answer()
        }
    }

    override fun onAnswerReceived(signalingMessage: SignalingMessage) {
        showToast("Received Answer")
        executor.execute {
            try {
                localPeer!!.setRemoteDescription(
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
    }

    override fun onIceCandidateReceived(signalingMessage: SignalingMessage) {
        try {
            localPeer!!.addIceCandidate(
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
        executor.execute {
            localPeer!!.createOffer(object : CustomSdpObserver("localCreateOffer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    localPeer!!.setLocalDescription(
                        CustomSdpObserver("localSetLocalDesc"),
                        sessionDescription
                    )
                    Log.e("onCreateSuccess", "SignallingClient emit ")
                    socketManager.sendSdp(sessionDescription, mode)
                }
            }, sdpConstraints)
        }
    }

    override fun onBackPressed() {
        localPeer!!.close()
        localPeer = null
        saveRecordedAudioToFile!!.stopRecordingStream()
        val intent = Intent()
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun startRecording(view: View) {
        if (saveRecordedAudioToFile != null) {
            if (!saveRecordedAudioToFile!!.isRunning) {
                if (saveRecordedAudioToFile!!.start()) {
                    Log.d("Record", "Recording input audio to file is activated")
                    record_button.text = "stop recording"
                }
            } else {
                record_button.text = "start recording"
                saveRecordedAudioToFile!!.stop()
            }
        }
    }
}