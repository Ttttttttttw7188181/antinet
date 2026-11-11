package com.antinet.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * sing-box engine backed by the prebuilt libbox AAR.
 *
 * The TUN descriptor is always created by the app (the main VpnService builder or
 * the tethering test network), and [PlatformInterface.openTun] simply hands that
 * descriptor to libbox. That keeps Android's VPN permission and routing under the
 * app's control and makes a second tethering instance possible.
 */
class SingBoxEngine(
    private val reportTraffic: Boolean = true,
) : TunnelEngine {
    override val label: String = "sing-box"

    private var server: CommandServer? = null
    private var tunFd: Int = -1
    private val connectionStats = HashMap<String, Pair<Long, Long>>()

    private val handler = object : CommandServerHandler {
        override fun getSystemProxyStatus(): SystemProxyStatus =
            SystemProxyStatus().apply {
                setAvailable(false)
                setEnabled(false)
            }

        override fun serviceReload() = Unit

        override fun serviceStop() = Unit

        override fun setSystemProxyEnabled(enabled: Boolean) = Unit

        override fun writeDebugMessage(message: String?) {
            Log.d(TAG, message.orEmpty())
        }
    }

    private val platform = object : PlatformInterface {
        private val connectivity: ConnectivityManager? =
            appContext?.getSystemService(ConnectivityManager::class.java)
        private var networkCallback: ConnectivityManager.NetworkCallback? = null

        override fun openTun(options: TunOptions): Int {
            val fd = tunFd
            if (fd < 0) error("TUN не создан")
            return fd
        }

        override fun autoDetectInterfaceControl(fd: Int) {
            protectSocket?.invoke(fd)
        }

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun getInterfaces(): NetworkInterfaceIterator = NetworkInterfaceIteratorImpl()

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
            if (networkCallback != null) return
            val manager = connectivity ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = publish(network, listener)

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                    publish(network, listener)
            }
            networkCallback = callback
            runCatching { manager.registerDefaultNetworkCallback(callback) }
        }

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
            val callback = networkCallback ?: return
            networkCallback = null
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }

        private fun publish(network: Network, listener: InterfaceUpdateListener?) {
            val manager = connectivity ?: return
            val link = runCatching { manager.getLinkProperties(network) }.getOrNull() ?: return
            val name = link.interfaceName ?: return
            val index = runCatching { java.net.NetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
            val metered = runCatching { manager.isActiveNetworkMetered }.getOrDefault(false)
            runCatching { listener?.updateDefaultInterface(name, index, metered, false) }
        }

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String,
            sourcePort: Int,
            destinationAddress: String,
            destinationPort: Int,
        ): ConnectionOwner? = null

        override fun systemCertificates(): StringIterator = StringIteratorImpl(emptyList())

        override fun localDNSTransport(): LocalDNSTransport? = null

        override fun readWIFIState(): WIFIState? = null

        override fun sendNotification(notification: Notification?) = Unit

        override fun includeAllNetworks(): Boolean = false

        override fun underNetworkExtension(): Boolean = false

        override fun useProcFS(): Boolean = false

        override fun clearDNSCache() = Unit
    }

    override val running: Boolean
        get() = server != null

    override suspend fun start(context: Context, config: String, tunFd: Int) = withContext(Dispatchers.IO) {
        SingBoxCore.init(context)
        this@SingBoxEngine.tunFd = tunFd
        val instance = CommandServer(handler, platform)
        runCatching {
            instance.checkConfig(config)
            instance.startOrReloadService(config, OverrideOptions())
        }.onFailure {
            runCatching { instance.close() }
            error(it.message ?: "sing-box не запустился")
        }
        server = instance
        connectionStats.clear()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        val instance = server
        server = null
        runCatching { instance?.closeService() }
        runCatching { instance?.close() }
        tunFd = -1
        connectionStats.clear()
    }

    /**
     * libbox tracks per-connection byte counters. Summing deltas keeps the total
     * accurate even when a connection disappears from the live list, which is what
     * happens as soon as sing-box closes it.
     */
    override fun traffic(): TunnelEngine.Traffic {
        if (!reportTraffic || server == null) return TunnelEngine.Traffic()
        var deltaUp = 0L
        var deltaDown = 0L
        runCatching {
            val current = HashMap<String, Pair<Long, Long>>()
            val iterator = Libbox.newConnections().iterator()
            while (iterator.hasNext()) {
                val connection = iterator.next()
                val id = connection.getID() ?: continue
                val up = connection.getUplink()
                val down = connection.getDownlink()
                current[id] = up to down
                val previous = connectionStats[id] ?: continue
                deltaUp += (up - previous.first).coerceAtLeast(0L)
                deltaDown += (down - previous.second).coerceAtLeast(0L)
            }
            connectionStats.clear()
            connectionStats.putAll(current)
        }
        return TunnelEngine.Traffic(up = deltaUp, down = deltaDown)
    }

    companion object {
        private const val TAG = "AntiNetSingBox"

        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var protectSocket: ((Int) -> Boolean)? = null

        fun install(context: Context, protect: (Int) -> Boolean) {
            appContext = context.applicationContext
            protectSocket = protect
        }
    }
}

/**
 * Process-wide sing-box setup. libbox keeps global paths and the Go runtime, so the
 * initialisation is guarded and shared by the main and tethering instances.
 */
object SingBoxCore {
    private const val TAG = "AntiNetSingBox"
    private val initialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            val working = File(context.filesDir, "sing-box").apply { mkdirs() }
            Libbox.setup(
                SetupOptions().apply {
                    setBasePath(context.filesDir.absolutePath)
                    setWorkingPath(working.absolutePath)
                    setTempPath(context.cacheDir.absolutePath)
                    setFixAndroidStack(true)
                    setDebug(false)
                    setLogMaxLines(500L)
                },
            )
        }.onFailure {
            initialized.set(false)
            Log.e(TAG, "setup failed", it)
            throw it
        }
    }

    val version: String
        get() = runCatching { Libbox.version() }.getOrDefault("sing-box")

    suspend fun measureDelay(host: String, port: Int, timeoutMs: Int = 4_000): Long =
        withContext(Dispatchers.IO) {
            if (host.isBlank() || port !in 1..65535) return@withContext -1L
            val started = System.nanoTime()
            runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
            }.map { (System.nanoTime() - started) / 1_000_000L }.getOrDefault(-1L)
        }
}

private class StringIteratorImpl(private val items: List<String>) : StringIterator {
    private var index = 0

    override fun hasNext(): Boolean = index < items.size

    override fun len(): Int = (items.size - index).coerceAtLeast(0)

    override fun next(): String = items[index++]
}

private class NetworkInterfaceIteratorImpl : NetworkInterfaceIterator {
    private val interfaces: List<java.net.NetworkInterface> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private var index = 0

    override fun hasNext(): Boolean = index < interfaces.size

    override fun next(): io.nekohasekai.libbox.NetworkInterface {
        val source = interfaces[index++]
        return io.nekohasekai.libbox.NetworkInterface().apply {
            setName(source.getName())
            setIndex(source.getIndex())
            setMTU(runCatching { source.getMTU() }.getOrDefault(1500))
            setFlags(0)
            setType(0)
            setMetered(false)
            setAddresses(
                StringIteratorImpl(
                    source.getInterfaceAddresses().mapNotNull { address ->
                        val host = address.address?.getHostAddress() ?: return@mapNotNull null
                        "$host/${address.networkPrefixLength}"
                    },
                ),
            )
            setDNSServer(StringIteratorImpl(emptyList()))
        }
    }
}
