package com.vox.sample.voxconnect_poc

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.Envelope
import java.nio.ByteBuffer
import java.nio.charset.Charset


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
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(message)
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
            socketManager.setDestinationId(message.src)
            socketManager.offerReceived(message)
        } else if (message.type == MessageType.CANDIDATE) {
            socketManager.candidateReceived(message)
        }
    }
}