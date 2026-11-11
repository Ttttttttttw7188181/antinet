package com.antinet.vpn.shizuku

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.TetheringManager
import android.os.Build
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.util.concurrent.Executor

/**
 * Runs in the Shizuku user-service process with shell (uid 2000) identity.
 *
 * Shizuku instantiates this class directly (it is not an Android Service and is not
 * declared in the manifest). The shell identity holds MANAGE_TEST_NETWORKS,
 * NETWORK_SETTINGS and TETHER_PRIVILEGED, which are exactly the permissions the
 * hidden test-network and tethering APIs require.
 *
 * The service creates an Android test TUN, registers it as a test network, asks the
 * tethering stack to prefer test networks and starts Wi-Fi tethering. The TUN
 * descriptor is passed back to the app, which proxies the packets with the selected
 * core. No root access is involved.
 */
class ShizukuUserService : IShizukuUserService.Stub {
    private companion object {
        const val TAG = "AntiNetShizuku"
        const val HOTSPOT_ADDRESS = "10.163.0.2"
        const val HOTSPOT_PREFIX = 24
        const val TETHERING_WIFI = 0
    }

    private var context: Context? = null
    private var activeFd: ParcelFileDescriptor? = null
    private var activeInterface: String = ""
    private var networkBinder: IBinder? = null
    private var preferSet = false
    private var tetheringRequested = false
    private var tetheringRequest: Any? = null

    constructor() : super()

    @Keep
    constructor(context: Context) : super() {
        this.context = context.applicationContext
    }

    override fun startHotspot(mtu: Int, ipv6: Boolean, dns: String?): Bundle =
        doStart(mtu, ipv6, dns.orEmpty())

    override fun stopHotspot() {
        doStop()
    }

    @Synchronized
    private fun doStart(mtu: Int, ipv6: Boolean, dns: String): Bundle {
        val result = Bundle()
        val ctx = context
        if (ctx == null) {
            result.putString("error", "Нет контекста Shizuku")
            return result
        }
        if (activeFd != null) {
            result.putParcelable("fd", activeFd)
            result.putString("iface", activeInterface)
            return result
        }
        ensureHiddenApiBypass()
        runCatching {
            val manager = ctx.getSystemService("test_network")
                ?: error("TestNetworkManager недоступен на этом Android")
            // LinkAddress constructors are hidden: build the address reflectively.
            val linkAddress = Class.forName("android.net.LinkAddress")
                .getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
                .newInstance(InetAddress.getByName(HOTSPOT_ADDRESS), HOTSPOT_PREFIX)
            val create = manager.javaClass.getMethod("createTunInterface", Collection::class.java)
            val testInterface = create.invoke(manager, listOf(linkAddress))
                ?: error("Не удалось создать тестовый TUN")
            val interfaceName = testInterface.javaClass
                .getMethod("getInterfaceName")
                .invoke(testInterface) as String
            val fd = testInterface.javaClass
                .getMethod("getFileDescriptor")
                .invoke(testInterface) as ParcelFileDescriptor

            networkBinder = Binder()
            // The 3-argument variant carries DNS servers for the test network;
            // the 2-argument one is the long-standing fallback.
            val link = LinkProperties()
            link.setInterfaceName(interfaceName)
            link.setDnsServers(listOf(InetAddress.getByName(dns.ifBlank { "1.1.1.1" })))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                link.setMtu(mtu)
            }
            runCatching {
                manager.javaClass
                    .getMethod(
                        "setupTestNetwork",
                        LinkProperties::class.java,
                        Boolean::class.javaPrimitiveType,
                        IBinder::class.java,
                    )
                    .invoke(manager, link, false, networkBinder)
            }.onFailure {
                Log.w(TAG, "setupTestNetwork(lp) failed, falling back", it)
                setupByName(manager, interfaceName)
            }

            val tethering = ctx.getSystemService(TetheringManager::class.java)
                ?: error("TetheringManager недоступен")
            preferTestNetworks(tethering, true)
            startTethering(tethering)

            activeFd = fd
            activeInterface = interfaceName
            preferSet = true
            result.putParcelable("fd", fd)
            result.putString("iface", interfaceName)
        }.onFailure { error ->
            Log.e(TAG, "hotspot start failed", error)
            result.putString("error", error.message ?: error.toString())
            runCatching { doStop() }
        }
        return result
    }

    private fun setupByName(manager: Any, interfaceName: String) {
        manager.javaClass
            .getMethod("setupTestNetwork", String::class.java, IBinder::class.java)
            .invoke(manager, interfaceName, networkBinder)
    }

    private fun ensureHiddenApiBypass() {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
            .onFailure { Log.w(TAG, "hidden API bypass failed", it) }
    }

    private fun preferTestNetworks(manager: TetheringManager, enabled: Boolean) {
        val method = TetheringManager::class.java
            .getDeclaredMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(manager, enabled)
    }

    private fun startTethering(manager: TetheringManager) {
        tetheringRequested = true
        val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val callback = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(callbackClass),
        ) { proxy, method, args ->
            when (method.name) {
                "onTetheringFailed" -> {
                    Log.w(TAG, "tethering failed: ${args?.firstOrNull()}")
                    null
                }
                "toString" -> "AntiNetStartTetheringCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        val executor = Executor { it.run() }
        // Android 13+ exposes TetheringManager.TetheringRequest.Builder; a few
        // builds renamed it to StartTetheringRequest.
        val request = listOf(
            "android.net.TetheringManager\$TetheringRequest\$Builder",
            "android.net.TetheringManager\$StartTetheringRequest\$Builder",
        ).firstNotNullOfOrNull { name ->
            runCatching {
                val builderClass = Class.forName(name)
                val builder = builderClass
                    .getConstructor(Int::class.javaPrimitiveType)
                    .newInstance(TETHERING_WIFI)
                builderClass.getMethod("build").invoke(builder)
            }.getOrNull()
        }
        tetheringRequest = request
        runCatching {
            if (request != null) {
                TetheringManager::class.java
                    .getMethod(
                        "startTethering",
                        request.javaClass,
                        Executor::class.java,
                        callbackClass,
                    )
                    .invoke(manager, request, executor, callback)
            } else {
                TetheringManager::class.java
                    .getMethod(
                        "startTethering",
                        Int::class.javaPrimitiveType,
                        Executor::class.java,
                        callbackClass,
                    )
                    .invoke(manager, TETHERING_WIFI, executor, callback)
            }
        }.onFailure { Log.w(TAG, "startTethering failed", it) }
    }

    /**
     * Android 16 removed the int overload from the SDK, so the request-based
     * call is preferred and the int one is only a fallback.
     */
    private fun stopTethering(manager: TetheringManager) {
        val request = tetheringRequest
        tetheringRequest = null
        if (request != null) {
            val stopped = runCatching {
                val callbackClass = Class.forName("android.net.TetheringManager\$StopTetheringCallback")
                val callback = Proxy.newProxyInstance(
                    javaClass.classLoader,
                    arrayOf(callbackClass),
                ) { proxy, method, args ->
                    when (method.name) {
                        "toString" -> "AntiNetStopTetheringCallback"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }
                }
                val requestClass = Class.forName("android.net.TetheringManager\$TetheringRequest")
                TetheringManager::class.java
                    .getMethod(
                        "stopTethering",
                        requestClass,
                        Executor::class.java,
                        callbackClass,
                    )
                    .invoke(manager, request, Executor { it.run() }, callback)
            }
            if (stopped.isSuccess) return
        }
        runCatching {
            TetheringManager::class.java
                .getMethod("stopTethering", Int::class.javaPrimitiveType)
                .invoke(manager, TETHERING_WIFI)
        }.onFailure { Log.w(TAG, "stopTethering failed", it) }
    }

    @Synchronized
    private fun doStop() {
        val ctx = context
        if (tetheringRequested) {
            tetheringRequested = false
            ctx?.getSystemService(TetheringManager::class.java)?.let { stopTethering(it) }
        }
        if (preferSet) {
            preferSet = false
            runCatching {
                ctx?.getSystemService(TetheringManager::class.java)?.let { preferTestNetworks(it, false) }
            }
        }
        val manager = ctx?.getSystemService("test_network")
        val name = activeInterface
        if (manager != null && name.isNotBlank()) {
            runCatching {
                val cm = ctx?.getSystemService(ConnectivityManager::class.java)
                val network: Network? = cm?.let { connectivity ->
                    connectivity.allNetworks.firstOrNull { candidate ->
                        connectivity.getLinkProperties(candidate)?.interfaceName == name
                    }
                }
                if (network != null) {
                    manager.javaClass
                        .getMethod("teardownTestNetwork", Network::class.java)
                        .invoke(manager, network)
                }
            }
        }
        runCatching { activeFd?.close() }
        activeFd = null
        activeInterface = ""
        networkBinder = null
    }
}
