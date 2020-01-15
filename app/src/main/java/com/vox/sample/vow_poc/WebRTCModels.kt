package com.vox.sample.vow_poc

data class SignalingMessage (
    var type: String,
    var sessionDescription: SDP?,
    var candidate: Candidate?
)

data class SDP (
    var sdp: String
)

data class Candidate (
    var sdp: String,
    var sdpMLineIndex: Int,
    var sdpMid: String
)
