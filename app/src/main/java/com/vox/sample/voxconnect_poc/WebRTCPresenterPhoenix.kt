package com.vox.sample.voxconnect_poc

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import org.java_websocket.handshake.ServerHandshake
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.ChannelEvent
import org.phoenixframework.channels.Envelope
import org.phoenixframework.channels.IMessageCallback
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*


class WebRTCPresenterPhoenix(private val channel: Channel, private var socketManager: SocketManager) {

    private lateinit var sourceId: String

    init {
        channel.join()
            .receive("ignore")
            {
                envelope ->
                Log.d("Channel", "Ignored with $envelope")
            }
            .receive("ok")
            { envelope ->
                Log.d("Channel", "Joined with $envelope")
                socketManager.setSourceId(socketManager.getUUID())
                socketManager.createPresenter(socketManager.getSourceId())
            }
            .receive("error")
            { envelope ->
                Log.d("Channel", "error with $envelope")
            }
        channel.on("status") { envelope ->
            onStatusUpdate(envelope)
        }
        channel.on("speaker_msg") { envelope ->
            onMessage(ByteBuffer.wrap(envelope.toString().toByteArray(Charset.defaultCharset())))
        }
        channel.on(ChannelEvent.ERROR.phxEvent) {
            var a = 1
        }
        socketManager.setSourceId(socketManager.getUUID())
        socketManager.createPresenter(socketManager.getSourceId())
    }

    fun sendByteBuffer(message: String) {
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(message)
        channel.push("listener_msg", jsonNode)
    }

    private fun onStatusUpdate(envelope: Envelope) {

    }

    fun close() {
        channel.leave().receive("ok"
        ) { envelope ->
            Log.d("Channel", "$envelope")
        }
    }


    private fun onMessage(msg: ByteBuffer) {
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
}