package com.vox.sample.vow_poc

import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*


class WebRTCListener(serverURI: URI?, private var socketManager: SocketManager) :
    WebSocketClient(serverURI) {

    private var offerReceived = false
    private val candidateSet: MutableSet<String?> = mutableSetOf()
    private lateinit var sourceId: String

    override fun onOpen(handshakedata: ServerHandshake) {
        socketManager.setSourceId(UUID.randomUUID().toString())
        val sync = Sync(socketManager.getSourceId(), ClientType.LISTENER)
        val message = Gson().toJson(Message.handshake(sync)) + "\r\n"
        sendByteBuffer(message)
    }

    fun sendByteBuffer(message: String) {
        this.send(ByteBuffer.wrap(message.toByteArray(Charset.defaultCharset())))
    }

    override fun onClose(
        code: Int,
        reason: String,
        remote: Boolean
    ) {
        println("closed with exit code $code additional info: $reason")
    }

    override fun onMessage(msg: String) {

    }

    override fun onMessage(msg: ByteBuffer) {
        val messageString = msg.array().toString(Charset.defaultCharset())
        val message = Gson().fromJson(messageString, Message::class.java)

        when (message.type) {
            MessageType.PRESENTER_LIST -> {
                val streamList = Gson().fromJson<List<String>>(Gson().toJson(message.payload), List::class.java)
                socketManager.updateStreamList(streamList)
            }
            MessageType.SDP -> {
                val answer = Gson().fromJson(Gson().toJson(message.payload), SDP::class.java)
                socketManager.setDestinationId(answer.sourceId)
                socketManager.answerReceived(answer)
            }
            MessageType.ICE -> {
                val candidate = Gson().fromJson(Gson().toJson(message.payload), Candidate::class.java)
                socketManager.candidateReceived(candidate)
            }
        }
    }

    override fun onError(ex: Exception) {
        System.err.println("an error occurred:$ex")
    }
}