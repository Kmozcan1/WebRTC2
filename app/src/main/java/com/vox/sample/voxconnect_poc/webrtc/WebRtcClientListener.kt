package com.vox.sample.voxconnect_poc.webrtc

import com.vox.sample.voxconnect_poc.Candidate
import com.vox.sample.voxconnect_poc.DataChannelMessage
import com.vox.sample.voxconnect_poc.SDP
import org.webrtc.PeerConnection

interface WebRtcClientListener {
    fun onOfferCreated(offer: SDP)
    fun onAnswerCreated(answer: SDP, clientId: String?)
    fun onCandidateCreated(candidate: Candidate, clientId: String?)
    fun onReceiveMessage(message: DataChannelMessage, clientId: String?)
    fun onConnectionStateChanged(newState: PeerConnection.IceConnectionState, clientId: String?)
}