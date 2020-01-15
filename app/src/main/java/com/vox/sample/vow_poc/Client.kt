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







class Client constructor (var client: Socket, private val context: Context, private val peerConnectionManager: PeerConnectionManager, private val mode: String):
    SocketInterface {
    private lateinit var sdpConstraints: MediaConstraints
    private var peerConnectionFactory: PeerConnectionFactory = peerConnectionManager.getPeerConnectionFactory()
    private var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()
    private var localPeer: PeerConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var handler: Handler
    var firstTime = true
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
                        if (mode == "presenter") {
                            onIceCandidateReceived(iceCandidate)
                        } else {
                            if (firstTime) {
                                firstTime = false

                            }
                        }

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
                                recordButton.visibility = View.VISIBLE
                                statusTextView.text = "listening"
                            }
                        }
                    }

                    override fun onAddStream(mediaStream: MediaStream?) {
                        super.onAddStream(mediaStream)
                        if (mode == "listener") {
                            runOnUiThread {
                                val recordButton =
                                    (context as StreamActivity).findViewById<View>(
                                        R.id.record_button
                                    ) as Button
                                val statusTextView =
                                    (context).findViewById<View>(R.id.status_text_view) as TextView
                                recordButton.visibility = View.VISIBLE
                                statusTextView.text = "listening"
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
                    //var sdpDescription = preferCodec(sessionDescription.description, "opus", true)
                    //var sdpRemote =  SessionDescription(sessionDescription.type, sdpDescription)
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

    private fun preferCodec(sdpDescription: String, codec: String, isAudio: Boolean): String {
        val lines =
            sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val mLineIndex = findMediaDescriptionLine(isAudio, lines)
        if (mLineIndex == -1) {
            Log.w("asd", "No mediaDescription line, so can't prefer $codec")
            return sdpDescription
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        val codecPayloadTypes = arrayListOf<String>()
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        val codecPattern = Pattern.compile("^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$")
        for (line in lines) {
            val codecMatcher = codecPattern.matcher(line)
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1))
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w("asd", "No payload types with name $codec")
            return sdpDescription
        }

        val newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex])
            ?: return sdpDescription
        Log.d("asd", "Change media description from: " + lines[mLineIndex] + " to " + newMLine)
        lines[mLineIndex] = newMLine
        return joinString(lines.toList(), "\r\n", true /* delimiterAtEnd */);
    }

    /** Returns the line number containing "m=audio|video", or -1 if no such line exists.  */
    private fun findMediaDescriptionLine(isAudio: Boolean, sdpLines: Array<String>): Int {
        val mediaDescription = if (isAudio) "m=audio " else "m=video "
        for (i in sdpLines.indices) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i
            }
        }
        return -1
    }

    private fun movePayloadTypesToFront(
        preferredPayloadTypes: List<String>, mLine: String
    ): String? {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        val origLineParts = mLine.split(" ")
        if (origLineParts.size <= 3) {
            Log.e("asd", "Wrong SDP media description format: $mLine")
            return null
        }
        val header = origLineParts.subList(0, 3)
        val unpreferredPayloadTypes = ArrayList(origLineParts.subList(3, origLineParts.size))
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes)
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        val newLineParts = arrayListOf<String>()
        newLineParts.addAll(header)
        newLineParts.addAll(preferredPayloadTypes)
        newLineParts.addAll(unpreferredPayloadTypes)
        return joinString(newLineParts, " ", false /* delimiterAtEnd */)
    }

    private fun joinString(
        s: Iterable<CharSequence>, delimiter: String, delimiterAtEnd: Boolean
    ): String {
        val iter = s.iterator()
        if (!iter.hasNext()) {
            return ""
        }
        val buffer = StringBuilder(iter.next())
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next())
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter)
        }
        return buffer.toString()
    }

    override fun connectionInitialized() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}