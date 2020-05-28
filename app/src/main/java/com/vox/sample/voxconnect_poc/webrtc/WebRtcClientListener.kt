package com.vox.sample.voxconnect_poc.webrtc

import com.vox.sample.voxconnect_poc.Candidate
import com.vox.sample.voxconnect_poc.SDP

interface WebRtcClientListener {
    fun onOfferCreated(offer: SDP)
    fun onAnswerCreated(answer: SDP, clientId: String?)
    fun onCandidateCreated(candidate: Candidate, clientId: String?)
    fun onReceiveMessage(message: String, clientId: String?)
}