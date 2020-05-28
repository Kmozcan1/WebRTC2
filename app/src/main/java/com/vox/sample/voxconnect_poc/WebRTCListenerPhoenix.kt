package com.vox.sample.voxconnect_poc

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import com.vox.sample.voxconnect_poc.ui.main.MainActivity
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.Envelope
import java.nio.ByteBuffer
import java.nio.charset.Charset


class WebRTCListenerPhoenix(
    private val channel: Channel,
    private var socketManager: SocketManager
) {

    private lateinit var sourceId: String

    init {
        channel.join()
            .receive(
                "ok"
            ) { envelope ->
                Log.d("Channel", "Joined with $envelope")
                socketManager.parseTwilioCredentials(envelope.payload)
                if (envelope.payload["response"]["speaker_status"]["status"].toString() == "\"offline\"") {
                    runOnUiThread {
                        val statusTextView =
                            (socketManager.context as MainActivity).findViewById<View>(R.id.status_text_view) as TextView
                        statusTextView.text = "Waiting for the Presenter..."
                    }

                } else if (envelope.payload["response"]["speaker_status"]["status"].toString() == "\"online\"") {
                    socketManager.setSourceId(socketManager.getUUID())
                    socketManager.createListener()
                }
            }
        channel.on("status") { envelope ->
            onStatusUpdate(envelope)
        }
        channel.on("speaker_msg") { envelope ->
            onMessage(
                ByteBuffer.wrap(
                    envelope.payload["message"].toString().toByteArray(Charset.defaultCharset())
                )
            )
        }
    }

    fun close() {
        channel.leave().receive("ok") { envelope ->
            Log.d("Channel", "$envelope")
        }
    }

    fun sendByteBuffer(message: String) {
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(message)
        channel.push("listener_msg", jsonNode)
    }

    private fun onStatusUpdate(envelope: Envelope) {
        val status = envelope.payload["status"].toString().replace("\"", "")
        if (status == "offline") {
            runOnUiThread {
                socketManager.showToast("Presenter closed the stream")
//                val hangupButton = (socketManager.context as MainActivity)
//                    .findViewById<View>(R.id.hangup_button) as Button
//                hangupButton.performClick()
            }
            socketManager.disconnect("listener")
        } else if (status == "online") {
            socketManager.setSourceId(socketManager.getUUID())
            socketManager.createListener()
        }
    }


    private fun onMessage(msg: ByteBuffer) {
        val messageString = msg.array().toString(Charset.defaultCharset())
        val message = Gson().fromJson(messageString, Message::class.java)

        when (message.type) {
            /*MessageType.SDP*/ "sdp" -> {
                val answer = Gson().fromJson(Gson().toJson(message.payload), SDP::class.java)
//                socketManager.setDestinationId(message.src)
                socketManager.answerReceived(answer)
            }
            /*MessageType.CANDIDATE*/ "candidate" -> {
                socketManager.candidateReceived(message)
            }
        }
    }
}