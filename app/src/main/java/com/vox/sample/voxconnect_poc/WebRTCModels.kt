package com.vox.sample.voxconnect_poc

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.webrtc.SessionDescription

val mapper: ObjectMapper by lazy {
    jacksonObjectMapper()
}

interface JsonMappable {
    fun toJsonNode(): JsonNode {
        return mapper.valueToTree(this)
    }

    fun toJsonString(): String {
        return mapper.writeValueAsString(this)
    }
}

inline fun <reified T> JsonNode.parse(): T? {
    try {
        return mapper.readValue<T>(mapper.writeValueAsString(this))
    } catch (e: Exception) {
        return null
    }
}

data class DataChannelMessage(
    val name: String,
    val message: String
) : JsonMappable

data class Message(
    val type: String? = null,
    var payload: Any? = null,
    var to: String? = null
) : JsonMappable {
    companion object {
        fun sdp(sdp: SDP, to: String? = null): Message = Message(
            type = "sdp", // MessageType.SDP,
            payload = sdp,
            to = to
        )

        fun candidate(candidate: Candidate, to: String? = null): Message = Message(
            type = "candidate", // MessageType.CANDIDATE,
            payload = candidate,
            to = to
        )
    }
}

data class Join(
    var role: SocketClientType
) : JsonMappable

data class SpeakerMessage(
    var to: String,
    var message: Message
)

data class Sync(
    var sourceId: String,
    var clientType: ClientType
)

data class SignalingMessage(
    var type: String,
    var sessionDescription: SDP?,
    var candidate: Candidate?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SdpReponse(
    val src: String?,
    val payload: SDP
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SDP(
    var sdp: String,
    var sdpType: SdpType
) {
    constructor(sessionDescription: SessionDescription) : this(
        sessionDescription.description,
        when (sessionDescription.type) {
            SessionDescription.Type.ANSWER -> SdpType.ANSWER
            SessionDescription.Type.OFFER -> SdpType.OFFER
            else -> SdpType.PRANSWER
        }
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CandidateReponse(
    val src: String?,
    val payload: Candidate
)

data class Candidate(
    var sdp: String,
    var sdpMLineIndex: Int,
    var sdpMid: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IceServer(
    val url: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwilioCredentials(
    var username: String,
    var password: String,
    var ice_servers: List<IceServer>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpeakerStatus(
    val status: String,
    val uuId: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListenerJoinResponse(
    val speaker_status: SpeakerStatus,
    val twilio_creds: TwilioCredentials
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpeakerJoinResponse(
    val twilio_creds: TwilioCredentials
)

enum class ClientType {
    @JsonProperty("listener")
    LISTENER,

    @JsonProperty("presenter")
    PRESENTER
}

enum class SocketClientType {
    @JsonProperty("listener")
    LISTENER,

    @JsonProperty("speaker")
    SPEAKER
}

enum class SdpType {
    @JsonProperty("offer")
    OFFER,

    @JsonProperty("answer")
    ANSWER,

    @JsonProperty("prAnswer")
    PRANSWER
}

enum class MessageType(var value: String) {
    @JsonProperty("sdp")
    SDP("sdp"),

    @JsonProperty("candidate")
    CANDIDATE("candidate")
}
