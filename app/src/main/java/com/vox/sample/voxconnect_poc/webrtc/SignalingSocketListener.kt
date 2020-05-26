package com.vox.sample.voxconnect_poc.webrtc

import com.vox.sample.voxconnect_poc.Candidate
import com.vox.sample.voxconnect_poc.SDP
import com.vox.sample.voxconnect_poc.TwilioCredentials

interface SignalingSocketListener {
    fun onSocketConnected()
    fun onSpeakerStateChanged(speakerState: SpeakerState)
    fun onReceiveTwilioCredentials(twilioCredentials: TwilioCredentials)
    fun onReceiveOffer(offer: SDP, clientId: String?)
    fun onReceiveAnswer(answer: SDP, clientId: String?)
    fun onReceiveCandidate(candidate: Candidate, clientId: String?)
    fun onError(error: Throwable)
}