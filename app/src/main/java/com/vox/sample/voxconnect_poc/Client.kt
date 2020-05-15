package com.vox.sample.voxconnect_poc

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import org.json.JSONException
import org.webrtc.*
import java.util.concurrent.Executors


class Client constructor (private val context: Context,
                          private val peerConnectionManager: PeerConnectionManager,
                          private val mode: String,
                          private val twilioCredentials: TwilioCredentials,
                          private val socketManager: SocketManager):
    SocketInterface {
    private lateinit var sdpConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory = peerConnectionManager.getPeerConnectionFactory()


    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer
            .builder(twilioCredentials.iceServers)
            .setUsername(twilioCredentials.userName)
            .setPassword(twilioCredentials.password)
            .createIceServer()
    )

    private var localPeer: PeerConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var handler: Handler
    var firstTime = true
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null
    private lateinit var localDescription: SessionDescription

    init {
        executor.execute {
            createPeerConnection()
            if (mode == "listener") {
                sendOffer()
            }
        }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        executor.execute {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (mode == "presenter") {
                onOfferReceived(socketManager.getSDP())
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


    fun sendSdp(sessionDescription: SessionDescription, clientType: ClientType, sdpType: SdpType) {
        try {
            val sdp = SDP(clientType,
                socketManager.getSourceId(),
                socketManager.getDestinationId(),
                sessionDescription.description,
                sdpType
                )

            var message = Gson().toJson(Message.sdp(sdp))

            if (mode == "listener") {
                message += "\r\n"
            }
            if (mode == "listener") {
                socketManager.sendOffer(message)
                Log.e("Socket", "sending offer to the server")
            }
            else {
                socketManager.sendAnswer(message)
                Log.e("Socket", "sending offer to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun sendCandidate(iceCandidate: IceCandidate, mode: String) {
        try {
            var clientType = if (mode == "presenter") {
                ClientType.PRESENTER
            } else {
                ClientType.LISTENER
            }
            val candidate =
                Candidate(clientType, socketManager.getSourceId(), socketManager.getDestinationId(),
                    iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid)
            var message = Gson().toJson(Message.candidate(candidate))
            if (mode == "listener") {
                message += "\r\n"
            }
            socketManager.sendCandidate(message, mode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRemoteHangUp(msg: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onOfferReceived(offer: SDP?) {
        showToast("Received Offer")

        executor.execute {
            localPeer!!.setRemoteDescription(
                CustomSdpObserver("localSetRemote"),
                SessionDescription(SessionDescription.Type.OFFER,
                    offer!!.sdp
                )
            )
            answer()
        }

    }

    private fun answer() {
        localPeer!!.createAnswer(object : CustomSdpObserver("localCreateAns") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                sendSdp(sessionDescription, ClientType.PRESENTER, SdpType.ANSWER)
                localPeer!!.setLocalDescription(
                    CustomSdpObserver("localSetLocal"),
                    sessionDescription
                )
            }
        }, MediaConstraints())

    }

    override fun onAnswerReceived(answer: SDP) {
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
                        answer.sdp
                    )
                )
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override fun onIceCandidateReceived(candidate: Candidate) {
        if (firstTime && mode == "listener") {
            runOnUiThread {
                val statusTextView =
                    (context as MainActivity).findViewById<View>(R.id.status_text_view) as TextView
                statusTextView.text = "Listening"
            }
            firstTime = false
        }

        try {
            val cnd = IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
            )
            localPeer!!.addIceCandidate(
                cnd
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

    fun sendOffer() {
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
                    sendSdp(sessionDescription, ClientType.LISTENER, SdpType.OFFER)
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