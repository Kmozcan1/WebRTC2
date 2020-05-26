package com.vox.sample.voxconnect_poc.webrtc

import android.net.Uri
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.vox.sample.voxconnect_poc.*
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.Socket
import java.util.*

class PhoenixSocket(
    private val socketUrl: String,
    private val channelCode: String,
    private val clientType: SocketClientType,
    private val listener: SignalingSocketListener
) {
    private val LOG_TAG = "SocketMessage"

    private val uuid = UUID.randomUUID().toString()
    private val token = "TOKEN123"
    private val channel: Channel

    private val LISTENER_MSG = "listener_msg"
    private val SPEAKER_MSG = "speaker_msg"

    private val STATUS_TAG = "status"
    private val ERROR_TAG = "error"
    private val OK_TAG = "ok"

    private val PUSH_TAG = when (clientType) {
        SocketClientType.SPEAKER -> SPEAKER_MSG
        SocketClientType.LISTENER -> LISTENER_MSG
    }

    private val PULL_TAG = when (clientType) {
        SocketClientType.SPEAKER -> LISTENER_MSG
        SocketClientType.LISTENER -> SPEAKER_MSG
    }

    init {
        val url = Uri.parse(socketUrl).buildUpon()
            .appendQueryParameter("token", token)
            .appendQueryParameter("uuid", uuid)
            .build().toString()

        val socket = Socket(url)
        socket.onError { reason ->
            listener.onError(Throwable(reason))
        }
        socket.onOpen {
            listener.onSocketConnected()
        }
        socket.connect()

        val joinNode = Join(SocketClientType.SPEAKER).toJsonNode()
        channel = socket.chan("room:$channelCode", joinNode)
        channel.join()
            .receive(OK_TAG) {
                handleJoinResponse(it.payload.get("response"))
            }
            .receive(ERROR_TAG) {
                listener.onError(Throwable(it.reason))
            }

        channel.on(STATUS_TAG) {
            handleStatus(it.payload)
        }
        channel.on(PULL_TAG) {
            handleMessage(it.payload)
        }
    }

    private fun sendMesage(message: Message) {
        channel.push(PUSH_TAG, message.toJsonNode())
    }

    private fun handleJoinResponse(payload: JsonNode) {
        payload.parse<SpeakerJoinResponse>()?.let {
            listener.onReceiveTwilioCredentials(it.twilio_creds)
            return
        }
        payload.parse<ListenerJoinResponse>()?.let {
            listener.onReceiveTwilioCredentials(it.twilio_creds)
            listener.onSpeakerStateChanged(
                when (it.speaker_status.status) {
                    "online" -> SpeakerState.ONLINE
                    else -> SpeakerState.OFFLINE
                }
            )
            return
        }
    }

    private fun handleStatus(payload: JsonNode) {
        payload.parse<SpeakerStatus>()?.let {
            listener.onSpeakerStateChanged(
                when (it.status) {
                    "online" -> SpeakerState.ONLINE
                    else -> SpeakerState.OFFLINE
                }
            )
            return
        }
    }

    private fun handleMessage(payload: JsonNode) {
        payload.parse<SdpReponse>()?.let {
            when (it.payload.sdpType) {
                SdpType.OFFER -> listener.onReceiveOffer(it.payload, it.src)
                SdpType.ANSWER -> listener.onReceiveAnswer(it.payload, it.src)
                else -> Log.d(LOG_TAG, "Received a PrAnswer")
            }
            return
        }
        payload.parse<CandidateReponse>()?.let {
            listener.onReceiveCandidate(it.payload, it.src)
            return
        }
    }
}
