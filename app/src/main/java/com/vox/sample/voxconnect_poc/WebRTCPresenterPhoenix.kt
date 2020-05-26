package com.vox.sample.voxconnect_poc

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.Envelope
import java.nio.ByteBuffer
import java.nio.charset.Charset

class WebRTCPresenterPhoenix(
    private val channel: Channel,
    private var socketManager: SocketManager
) {

    private var msgCount = 0
    private lateinit var sourceId: String

    init {
        channel.join()
            .receive("ignore")
            { envelope ->
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
            msgCount++
            Log.d("Speaker_MSG", "Message Count: $msgCount")
            onMessage(ByteBuffer.wrap(envelope.payload.toString().toByteArray()))
        }
    }


    fun sendByteBuffer(message: String) {
        val messageObject: Message = Gson().fromJson(message, Message::class.java)
        val speakerMessage =
            Gson().toJson(SpeakerMessage(socketManager.getDestinationId(), messageObject)) + "\r\n"
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(speakerMessage)
        channel.push("speaker_msg", jsonNode)
    }

    private fun onStatusUpdate(envelope: Envelope) {

    }

    fun close() {
        channel.leave().receive(
            "ok"
        ) { envelope ->
            Log.d("Channel", "$envelope")
        }
    }


    @Synchronized
    private fun onMessage(msg: ByteBuffer) {
        try {
            val messageString = msg.array().toString(Charset.defaultCharset())
            Log.d("Speaker_MSG", messageString)
            val message = Gson().fromJson(messageString, Message::class.java)

            if (message.type == "sdp") { // MessageType.SDP) {
                Log.d("Speaker_MSG", "Received Offer")
                socketManager.setDestinationId(message.src)
                socketManager.offerReceived(message)
            } else if (message.type == "candidate") { // MessageType.CANDIDATE) {
                Log.d("Speaker_MSG", "Received Candidate")
//                socketManager.candidateReceived(message)
//            } else {
//                Log.d("Speaker_MSG","Received Unknown")
            }
        } catch (e: Exception) {
            Log.e("Speaker_MSG", e.localizedMessage)
        }
    }
}