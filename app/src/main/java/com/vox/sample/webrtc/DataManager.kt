package com.vox.sample.webrtc

import android.net.nsd.NsdServiceInfo

object DataManager {
    var currentService: NsdServiceInfo? = null

    fun getCurrentServiceHost(): String {
        return currentService!!.host.hostAddress
    }

    fun getCurrentServicePort(): Int {
        return currentService!!.port
    }

    fun getCurrentServiceName(): String {
        return currentService!!.serviceName
    }
}