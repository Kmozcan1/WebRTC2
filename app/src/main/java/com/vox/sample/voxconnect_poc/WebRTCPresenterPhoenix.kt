package com.vox.sample.voxconnect_poc

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
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
                socketManager.parseTwilioCredentials(envelope.payload)
                socketManager.setSourceId(socketManager.getUUID())
            }
            .receive("error")
            { envelope ->
                Log.d("Channel", "error with $envelope")
            }
        channel.on("status") { envelope ->
            onStatusUpdate(envelope)
        }
        channel.on("listener_msg") { envelope ->
            onMessage(ByteBuffer.wrap(envelope.payload.toString().toByteArray(Charset.defaultCharset())))
        }
    }



    fun sendByteBuffer(message: String) {
        val messageObject: Message = Gson().fromJson(message, Message::class.java)
        val speakerMessage = Gson().toJson(SpeakerMessage(socketManager.getDestinationId(), messageObject)) + "\r\n"
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(speakerMessage)
        channel.push("speaker_msg", jsonNode)
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

        if (message.type == MessageType.SDP) {
            val offer = Gson().fromJson(Gson().toJson(message.payload), SDP::class.java)
            socketManager.setDestinationId(offer.sourceId)
            socketManager.offerReceived(offer)
        } else if (message.type == MessageType.ICE) {
            val candidate = Gson().fromJson(Gson().toJson(message.payload), Candidate::class.java)
            socketManager.candidateReceived(candidate)
        }
    }
}