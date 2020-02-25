package com.vox.sample.vow_poc

data class SignalingMessage (
    var type: String,
    var sessionDescription: SDP?,
    var candidate: Candidate?
)

data class SDP (
    var clientType: String,
    var sourceId: String,
    var destinationId: String,
    var sdp: String,
    var sdpType: SdpType
)

enum class SdpType (val rtcSdpType: String) {
    offer("offer"),
    answer("answer"),
    prAnswer("prAnswer")
}

data class Candidate (
    var clientType: String,
    var sourceId: String,
    var destinationId: String,
    var sdp: String,
    var sdpMLineIndex: Int,
    var sdpMid: String?
)

data class Syn (
    var clientType: String,
    var sourceId: String
)

