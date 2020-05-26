package com.vox.sample.voxconnect_poc

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

val mapper: ObjectMapper by lazy {
    jacksonObjectMapper()
}

interface JsonMappable {
    fun toJsonNode(): JsonNode {
        return mapper.valueToTree(this)
    }
}

inline fun <reified T> JsonNode.parse() : T? {
    try {
        return mapper.readValue<T>(mapper.writeValueAsString(this))
    } catch (e: Exception) {
        return null
    }
}

data class Message(
    val src: String? = null,
    val type: String? = null,
    var payload: Any? = null
) : JsonMappable {
    companion object {
        fun sdp(source: String?, sdp: SDP): Message = Message(
            src = source,
            type = "sdp", // MessageType.SDP,
            payload = sdp
        )

        fun candidate(candidate: Candidate): Message = Message(
            type = "candidate", // MessageType.CANDIDATE,
            payload = candidate
        )
    }
}

data class Join (
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
)

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
