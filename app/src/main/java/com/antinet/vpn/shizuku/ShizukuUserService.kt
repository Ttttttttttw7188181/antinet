package com.antinet.vpn.shizuku

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.TetheringManager
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
            val linkAddress = LinkAddress(InetAddress.getByName(HOTSPOT_ADDRESS), HOTSPOT_PREFIX)
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
            val link = LinkProperties()
            val nameSet = runCatching {
                LinkProperties::class.java
                    .getMethod("setInterfaceName", String::class.java)
                    .invoke(link, interfaceName)
            }.isSuccess
            link.addDnsServer(InetAddress.getByName(dns.ifBlank { "1.1.1.1" }))

            if (nameSet) {
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
            } else {
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
        val request = runCatching {
            val builderClass = Class.forName("android.net.TetheringManager\$StartTetheringRequest\$Builder")
            val builder = builderClass
                .getConstructor(Int::class.javaPrimitiveType)
                .newInstance(TETHERING_WIFI)
            builderClass.getMethod("build").invoke(builder)
        }.getOrNull()
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

    @Synchronized
    private fun doStop() {
        val ctx = context
        if (tetheringRequested) {
            tetheringRequested = false
            runCatching {
                ctx?.getSystemService(TetheringManager::class.java)?.stopTethering(TETHERING_WIFI)
            }
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
