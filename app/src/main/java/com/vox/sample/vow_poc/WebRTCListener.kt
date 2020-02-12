package com.vox.sample.vow_poc

import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer


class WebRTCListener : WebSocketClient {

    private var offerReceived = false
    private lateinit var socketManager: SocketManager
    private val candidateSet: MutableSet<String?> = mutableSetOf()

    constructor(
        serverUri: URI?,
        draft: Draft?
    ) : super(serverUri, draft) {
    }

    constructor(serverURI: URI?, socketManager: SocketManager) : super(serverURI) {
        this.socketManager = socketManager
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        send("listener")
        println("new connection opened")
    }

    override fun onClose(
        code: Int,
        reason: String,
        remote: Boolean
    ) {
        println("closed with exit code $code additional info: $reason")
    }

    override fun onMessage(message: String) {
        val signalingMessage = Gson().fromJson(message, SignalingMessage::class.java )
        if (signalingMessage != null) {
            if (signalingMessage.type == "offer" && !offerReceived) {
                offerReceived = true
                socketManager.offerReceived(signalingMessage)
            } else if (signalingMessage.type == "candidate") {
                if (!candidateSet.contains(signalingMessage.candidate?.sdp)) {
                    candidateSet.add(signalingMessage.candidate?.sdp)
                    socketManager.candidateReceived(signalingMessage)
                }
            }
        }
    }

    override fun onMessage(message: ByteBuffer) {
        println("received ByteBuffer")
    }

    override fun onError(ex: Exception) {
        System.err.println("an error occurred:$ex")
    }
}