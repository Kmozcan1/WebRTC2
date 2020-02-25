package com.vox.sample.vow_poc

import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer


class WebRTCPresenter : WebSocketClient {

    private lateinit var client: Client
    private lateinit var socketManager: SocketManager

    constructor(
        serverUri: URI?,
        draft: Draft?
    ) : super(serverUri, draft) {
    }

    constructor(serverURI: URI?, socketManager: SocketManager) : super(serverURI) {
        this.socketManager = socketManager
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        send("presenter")
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
        if (message == "listener") {
            socketManager.createPresenter()
        } else {
            val signalingMessage = Gson().fromJson(message, SignalingMessage::class.java )
            if (signalingMessage != null) {
                if (signalingMessage.type == "answer") {
                    socketManager.answerReceived(signalingMessage)
                } else if (signalingMessage.type == "candidate") {
                    socketManager.candidateReceived(signalingMessage, "presenter")
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