package com.vox.sample.voxconnect_poc

import com.google.gson.annotations.SerializedName

data class Message (val type: MessageType? = null, var payload: Any? = null) {
    companion object {
        fun join(join: Join): Message = Message(
            payload = join
        )
        fun handshake(sync: Sync): Message = Message(
            type = MessageType.HANDSHAKE,
            payload = sync
        )

        fun sdp(sdp: SDP): Message = Message (
            type = MessageType.SDP,
            payload = sdp
        )

        fun candidate(candidate: Candidate): Message = Message (
            type = MessageType.ICE,
            payload = candidate
        )
    }
}
data class Join (
    var role: SocketClientType
)
data class SpeakerMessage(
    var to: String,
    var message: Message
)
data class Sync (
    var sourceId: String,
    var clientType: ClientType
)
data class SignalingMessage (
    var type: String,
    var sessionDescription: SDP?,
    var candidate: Candidate?
)

data class SDP (
    var clientType: ClientType,
    var sourceId: String,
    var destinationId: String,
    var sdp: String,
    var sdpType: SdpType
)

data class Candidate (
    var clientType: ClientType,
    var sourceId: String,
    var destinationId: String,
    var sdp: String,
    var sdpMLineIndex: Int,
    var sdpMid: String
)

data class TwilioCredentials (
    var userName: String,
    var password: String,
    var iceServers: List<String>
)

enum class ClientType {
    @SerializedName("listener")
    LISTENER,
    @SerializedName("presenter")
    PRESENTER
}

enum class SocketClientType {
    @SerializedName("listener")
    LISTENER,
    @SerializedName("speaker")
    SPEAKER
}

enum class SdpType {
    @SerializedName("offer")
    OFFER,
    @SerializedName("answer")
    ANSWER,
    @SerializedName("prAnswer")
    PRANSWER
}

enum class MessageType(var value: String) {
    @SerializedName("presenter_list")
    PRESENTER_LIST("presenter_list"),
    @SerializedName("join")
    JOIN("join"),
    @SerializedName("handshake")
    HANDSHAKE("handshake"),
    @SerializedName("sdp")
    SDP("sdp"),
    @SerializedName("ice")
    ICE("ice")
}
