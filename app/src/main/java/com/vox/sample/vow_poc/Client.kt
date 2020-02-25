package com.vox.sample.vow_poc

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import org.webrtc.*
import java.io.*
import java.net.Socket
import java.util.concurrent.Executors
import android.widget.Button
import android.widget.TextView
import com.vox.sample.vow_poc.R
import org.json.JSONException
import java.util.*
import java.util.regex.Pattern


class Client constructor (private val context: Context, private val peerConnectionManager: PeerConnectionManager, private val mode: String, private val socketManager: SocketManager):
    SocketInterface {
    private lateinit var sdpConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory = peerConnectionManager.getPeerConnectionFactory()


    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer
            .builder(listOf("turn:global.turn.twilio.com:3478?transport=udp",
                "turn:global.turn.twilio.com:3478?transport=tcp",
                "turn:global.turn.twilio.com:443?transport=tcp"))
            .setUsername("41cce2a71a9d6ca7c9ac717c3182288f16d2a365493d7b3d00f8f984d1ad9747")
            .setPassword("TI4YxtEGFIn9dFlCX1t2tQywogFSokatPbVKBAzEHTY=")
            .createIceServer()
    )

    private var localPeer: PeerConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var handler: Handler
    var firstTime = true
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null
    private lateinit var localDescription: SessionDescription

    init {
        createPeerConnection()
        if (mode == "presenter") {
            onNewPeerJoined()
        }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        executor.execute {
            localPeer = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : CustomPeerConnectionObserver("localPeerCreation") {
                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        super.onIceCandidate(iceCandidate)
                        onIceCandidateReceived(iceCandidate)
                    }
                    override fun onAddTrack(rtpReceiver: RtpReceiver?,
                                            mediaStreams: Array<out MediaStream>?) {
                        super.onAddTrack(rtpReceiver, mediaStreams)
                        if (mode == "presenter") {
                            mediaStreams!![0].audioTracks[0].setEnabled(false)
                        }
                    }

                    override fun onTrack(rtpReceiver: RtpTransceiver?) {
                        super.onTrack(rtpReceiver)
                        if (mode == "listener") {
                            runOnUiThread {
                                val recordButton =
                                    (context as StreamActivity).findViewById<View>(
                                        R.id.record_button
                                    ) as Button
                                val statusTextView =
                                    (context).findViewById<View>(R.id.status_text_view) as TextView
                            }
                        }
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        super.onConnectionChange(newState)
                        if (newState == PeerConnection.PeerConnectionState.DISCONNECTED && mode == "listener") {
                            runOnUiThread {
                                showToast("Presenter closed the stream")
                                val hangupButton =
                                    (context as MainActivity).findViewById<View>(
                                        R.id.hangup_button
                                    ) as Button
                                hangupButton.performClick()
                                localPeer?.close()
                            }
                        }
                    }

                    override fun onAddStream(mediaStream: MediaStream?) {
                        super.onAddStream(mediaStream)
                        if (mode == "listener") {
                            runOnUiThread {
                            }
                        }
                    }
                })!!
            if (mode == "presenter") {
                addStreamToLocalPeer()
            }
        }
    }

    private fun addStreamToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(peerConnectionManager.getLocalAudioTrack())
        localPeer!!.addTrack(stream.audioTracks[0])
    }

    fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        executor.execute {
            sendCandidate(iceCandidate, mode)
        }
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private fun gotRemoteStream(stream: MediaStream) {


    }

    private fun showToast(message: String) {
        handler = Handler(context.mainLooper)
        runOnUiThread(Runnable {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        })
    }

    private fun runOnUiThread(r: Runnable) {
        handler.post(r)
    }


    fun sendSdp(sessionDescription: SessionDescription) {
        try {
            val type = sessionDescription.type.canonicalForm()
            val sdp = SDP(sessionDescription.description)
            val signalingMessage = SignalingMessage(type, sdp, null)
            val message = Gson().toJson(signalingMessage) + "\r\n"

            if (mode == "listener") {
                socketManager.sendAnswer(message)
                Log.e("Socket", "sending answer to the server")
            }
            else {
                socketManager.sendOffer(message)
                Log.e("Socket", "sending offer to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun sendCandidate(iceCandidate: IceCandidate, mode: String) {
        try {
            val type = "candidate"
            val candidate =
                Candidate(iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid)
            val signalingMessage = SignalingMessage(type, null, candidate)
            val message = Gson().toJson(signalingMessage) + "\r\n"
            socketManager.sendCandidate(message, mode)
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun answer() {
        localPeer!!.createAnswer(object : CustomSdpObserver("localCreateAns") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(
                    CustomSdpObserver("localSetLocal"),
                    sessionDescription
                )
                sendSdp(sessionDescription)
            }
        }, MediaConstraints())

    }

    override fun onAnswerReceived(signalingMessage: SignalingMessage) {
        showToast("Received Answer")
        executor.execute {
            localPeer!!.setLocalDescription(
                CustomSdpObserver("localSetLocalDesc"),
                localDescription
            )
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
        if (firstTime && mode == "listener") {
            runOnUiThread {
                val statusTextView =
                    (context as MainActivity).findViewById<View>(R.id.status_text_view) as TextView
                statusTextView.text = "Listening"
            }
            firstTime = false
        }

        try {
            var candidate = IceCandidate(
                signalingMessage.candidate!!.sdpMid,
                signalingMessage.candidate!!.sdpMLineIndex,
                signalingMessage.candidate!!.sdp
            )
            localPeer!!.addIceCandidate(
                candidate
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
        executor.execute {
            sdpConstraints = MediaConstraints()
            sdpConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            )
            localPeer!!.createOffer(object : CustomSdpObserver("localCreateOffer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    //var sdpDescription = preferCodec(sessionDescription.description, "opus", true)
                    //var sdpRemote =  SessionDescription(sessionDescription.type, sdpDescription)
                    super.onCreateSuccess(sessionDescription)

                    localDescription = sessionDescription


                    Log.e("onCreateSuccess", "SignallingClient emit ")
                    sendSdp(sessionDescription)
                }
            }, sdpConstraints)
        }
    }

    fun disconnect() {
        if (localPeer != null) {
            localPeer?.close()
        }
    }

    override fun connectionInitialized() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}