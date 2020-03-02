package com.vox.sample.vow_poc

import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*


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

    fun sendByteBuffer(message: String) {
        this.send(ByteBuffer.wrap(message.toByteArray(Charset.defaultCharset())))
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        socketManager.setSourceId(UUID.randomUUID().toString())
        val sync = Sync(socketManager.getSourceId(), ClientType.PRESENTER)
        val message = Gson().toJson(Message.handshake(sync)) + "\r\n"
        sendByteBuffer(message)
    }

    override fun onClose(
        code: Int,
        reason: String,
        remote: Boolean
    ) {
        println("closed with exit code $code additional info: $reason")
    }

    override fun onMessage(msg: String) {
        val message = Gson().fromJson(msg, Message::class.java)

    }

    override fun onMessage(msg: ByteBuffer) {
        val messageString = msg.array().toString(Charset.defaultCharset())
        val message = Gson().fromJson(messageString, Message::class.java)

        if (message.type == MessageType.SDP) {
            val offer = Gson().fromJson(Gson().toJson(message.payload), SDP::class.java)
            socketManager.setDestinationId(offer.sourceId)
            socketManager.offerReceived(offer)
        } else if (message.type == MessageType.ICE) {
            val candidate = Gson().fromJson(Gson().toJson(message.payload), Candidate::class.java)
            socketManager.candidateReceived(candidate)
        }
    }

    override fun onError(ex: Exception) {
        System.err.println("an error occurred:$ex")
    }
}