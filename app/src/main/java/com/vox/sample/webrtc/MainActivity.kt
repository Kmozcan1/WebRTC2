package com.vox.sample.webrtc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    override fun onRefresh() {
        refreshService()
    }

    companion object {
        private const val SERVICE_TYPE = "_populi._tcp"
        private const val TAG = "NSD"
        private const val PRESENTER = 0
        private const val LISTENER = 1
        private const val SERVICE_RESOLVE_COUNT = 5
        const val TCP_SERVER_PORT = 51493
    }

    private lateinit var nsdManager: NsdManager
    private lateinit var wifiManager: WifiManager
    private lateinit var lock: WifiManager.MulticastLock
    private val serviceInfoList = ArrayList<NsdServiceInfo>()
    private val serviceResolverMap = HashMap<String, Int>()
    private val discoveryListenerList = ArrayList<NsdDiscoveryListener>()
    private var refreshingDiscovery = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nsdManager = this.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        wifiManager = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        stream_recycler_view.setAdapterWithCustomDivider(
            LinearLayoutManager(applicationContext),
            StreamListAdapter(emptyList(), this))
        discoverServices()
    }

    fun onListClick(serviceInfo : NsdServiceInfo) {
        DataManager.currentService = serviceInfo
        startStreamActivity("listener")
    }

    fun startStreaming(view: View) {
        registerService()
    }

    private fun registerService() {
        createMulticastLock(wifiManager)

        val wifiInfo = wifiManager.connectionInfo
        val intAddress = wifiInfo.ipAddress


        val byteAddress = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(intAddress).array()
        try {
            val address = InetAddress.getByAddress(byteAddress)
            val host = InetAddress.getByName(address.hostAddress)
            val serviceInfo = createServiceInfo("service", TCP_SERVER_PORT, host)
            var registrationListener = NsdRegistrationListener()
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            DataManager.currentService = serviceInfo
            stopServiceDiscovery()
            startStreamActivity("presenter")
        } catch (e: UnknownHostException) {
        }
    }

    private fun startStreamActivity(mode: String) {
        val intent = Intent(this, StreamActivity::class.java)
        intent.putExtra("mode", mode)
        startActivityForResult(intent, PRESENTER)
    }

    private fun discoverServices() {
        serviceInfoList.removeAll(serviceInfoList)
        createMulticastLock(wifiManager)

        /**
         * A new discovery listener instance is created per NSD instructions
         * https://android.googlesource.com/platform/frameworks/base/+/e7369bd%5E!/
         * Creating a new listener instance is also useful in refresh scenario,
         * refreshing too fast can result in an error with a single listener.
         */
        val discoveryListener = NsdDiscoveryListener()
        discoveryListenerList.add(discoveryListener)

        nsdManager.discoverServices(
            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun refreshService() {
        refreshingDiscovery = true
        stopServiceDiscovery()
        discoverServices()
    }


    private fun stopServiceDiscovery() {
        for (discoveryListener in discoveryListenerList) {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }
    }

    private fun createMulticastLock(wifiManager: WifiManager) {
        lock = wifiManager.createMulticastLock("VoW NSD Multicastlock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun createServiceInfo(serviceName: String, port: Int, host: InetAddress?): NsdServiceInfo {
        return NsdServiceInfo().apply {
            // The serviceName is subject to change based on conflicts
            // with other services advertised on the same network.
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
            this.host = host
        }
    }

    private fun refreshRecyclerView() {
        runOnUiThread {
            stream_recycler_view.adapter = StreamListAdapter(serviceInfoList, this@MainActivity)

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PRESENTER) {
            }
        }
    }

    //region REGISTRATION LISTENER
    private inner class NsdRegistrationListener : NsdManager.RegistrationListener {
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d(TAG, "onUnregistrationFailed")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "onServiceUnregistered")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d(TAG, "onRegistrationFailed")
        }

        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "onServiceRegistered")
        }
    }
    //endregion
    //region DISCOVERY LISTENER
    private inner class NsdDiscoveryListener : NsdManager.DiscoveryListener {
        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            // A service was found! Resolve it and get additional info.
            Log.d(TAG, "Service discovery success$serviceInfo")
            serviceResolverMap[serviceInfo.serviceName] = SERVICE_RESOLVE_COUNT
            nsdManager.resolveService(serviceInfo, NsdResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "service lost: $serviceInfo")

            /** We only have the name and port of the service here,
             * since other info is obtained when the service is resolved.
             * If two devices on the network both have the NsdChat application installed,
             * one of them changes the service name automatically, to something like "NsdChat (1)".
             */
            val lostServiceInfo = serviceInfoList.indexOf(
                serviceInfoList.find {
                    it.serviceName == serviceInfo.serviceName
                })
            if (lostServiceInfo != -1) {
                serviceInfoList.removeAt(lostServiceInfo)
                refreshRecyclerView()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped: $serviceType")
            serviceInfoList.removeAll(serviceInfoList)
            discoveryListenerList.remove(this)
            if (refreshingDiscovery) {
                discoverServices()
                refreshingDiscovery = false
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
        }
    }

    //endregion
    //region RESOLVE LISTENER
    private inner class NsdResolveListener :  NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed: $errorCode $this")
            when (errorCode) {
                NsdManager.FAILURE_ALREADY_ACTIVE -> {
                    /**
                     * NSD throws this error when we create a new
                     * listener instance to resolve every discovered service
                     * 20 ms sleep seems to prevent calling this function infinitely when user
                     * performs too many refreshes rapidly.
                     * Will experiment & look for alternate solutions.
                     */
                    val resolveCount = serviceResolverMap[serviceInfo.serviceName]
                    if (resolveCount != null && resolveCount > 0) {
                        Thread.sleep(20)
                        serviceResolverMap[serviceInfo.serviceName] = resolveCount - 1
                        nsdManager.resolveService(serviceInfo, NsdResolveListener())
                    }
                }
            }
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.e(TAG, "Resolve Succeeded. $serviceInfo")
            serviceInfoList.add(serviceInfo)
            refreshRecyclerView()
        }
    }
    //endregion

}
