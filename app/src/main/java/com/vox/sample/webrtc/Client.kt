package com.vox.sample.webrtc

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import kotlinx.android.synthetic.main.activity_stream.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.*
import java.net.Socket
import java.util.concurrent.Executors
import android.app.Activity
import android.widget.Button
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_stream.view.*
import org.json.JSONException


class Client constructor (var client: Socket, private val context: Context, private val peerConnectionManager: PeerConnectionManager, private val mode: String): SocketInterface {
    private lateinit var sdpConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory = peerConnectionManager.getPeerConnectionFactory()
    private var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()
    private var localPeer: PeerConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var handler: Handler
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null

    init {
        createPeerConnection()
        if (mode == "presenter") {
            onNewPeerJoined()
        }
    }

    fun getInputStream(): InputStream {
        return client.getInputStream()
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

                    override fun onAddTrack(
                        rtpReceiver: RtpReceiver?,
                        mediaStreams: Array<out MediaStream>?
                    ) {
                        showToast("Received Remote stream")
                        if (mode == "presenter") {
                            mediaStreams!![0].audioTracks[0].setEnabled(false)
                        } else {
                            runOnUiThread {
                                val recordButton =
                                    (context as StreamActivity).findViewById<View>(R.id.record_button) as Button
                                val statusTextView =
                                    (context).findViewById<View>(R.id.status_text_view) as TextView
                                recordButton.visibility = View.VISIBLE
                                statusTextView.text = "listening"
                            }
                        }
                        Log.e("audio", mediaStreams!![0].audioTracks[0].toString())
                        super.onAddTrack(rtpReceiver, mediaStreams)
                    }
                })!!
            addStreamToLocalPeer()
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
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
                Log.e("Socket", "sending answer to the server")
            }
            else {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
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


            if (mode == "listener") {
                val out =
                    PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
                Log.e("Client", "Sending ice candidate to the server")
            } else {
                val out =
                    PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
                Log.e("Client", "sending ice candidate to the client")
            }
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
                    sendSdp(sessionDescription)
                }
            }, sdpConstraints)
        }
    }

    override fun connectionInitialized() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}