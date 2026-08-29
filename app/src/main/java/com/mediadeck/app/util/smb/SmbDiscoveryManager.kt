package com.mediadeck.app.util.smb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object SmbDiscoveryManager {

    private val _discoveredServers = MutableStateFlow<Set<String>>(emptySet())
    val discoveredServers = _discoveredServers.asStateFlow()

    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDiscovery(context: Context) {
        if (discoveryJob?.isActive == true) return

        _discoveredServers.value = emptySet()
        discoveryJob = scope.launch {
            launch { startMdnsDiscovery(context) }

            launch { startIpRangeScan() }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
    }

    private suspend fun startMdnsDiscovery(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_smb") || service.serviceType.contains("_samba")) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(service, Dispatchers.IO.asExecutor(), object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                addServer(serviceInfo.hostAddresses.firstOrNull()?.hostAddress ?: "")
                            }
                        })
                    } else {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                addServer(serviceInfo.host.hostAddress ?: "")
                            }
                        })
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        try {
            nsdManager.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(15000)
        } catch (_: Exception) {
        } finally {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
    }

    private suspend fun startIpRangeScan() {
        val localIp = getLocalIpAddress() ?: return
        val lastDot = localIp.lastIndexOf('.')
        if (lastDot < 0) return
        val prefix = localIp.substring(0, lastDot + 1)

        coroutineScope {
            (1..254).map { i ->
                async {
                    val target = "$prefix$i"
                    if (target == localIp) return@async
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(target, 445), 250)
                            addServer(target)
                        }
                    } catch (_: Exception) {}
                }
            }.awaitAll()
        }
    }

    private fun addServer(ip: String) {
        if (ip.isEmpty()) return
        val current = _discoveredServers.value.toMutableSet()
        if (current.add(ip)) {
            _discoveredServers.value = current
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val itf = interfaces.nextElement()
                val addrs = itf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
