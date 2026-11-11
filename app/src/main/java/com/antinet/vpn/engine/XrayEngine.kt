package com.antinet.vpn.engine

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Xray engine backed by AndroidLibXrayLite. Each instance owns its own
 * [CoreController], so a second controller can serve the tethering TUN while the
 * main tunnel keeps running.
 */
class XrayEngine : TunnelEngine {
    override val label: String = "Xray"

    private var controller: CoreController? = null

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "core started")
            return 0
        }

        override fun shutdown(): Long {
            Log.i(TAG, "core stopped")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, "core status $code: ${message.orEmpty()}")
            return 0
        }
    }

    override val running: Boolean
        get() = controller?.isRunning == true

    override suspend fun start(context: Context, config: String, tunFd: Int) = withContext(Dispatchers.IO) {
        XrayCore.init(context)
        val instance = Libv2ray.newCoreController(callback)
        instance.startLoop(config, tunFd)
        if (!instance.isRunning) {
            runCatching { instance.stopLoop() }
            error("Ядро Xray не запустилось")
        }
        controller = instance
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching { controller?.stopLoop() }
        controller = null
    }

    /**
     * AndroidLibXrayLite resets every outbound counter on each call and returns
     * "tag,direction,value;" entries, so the payload is already the delta since
     * the previous poll.
     */
    override fun traffic(): TunnelEngine.Traffic {
        val instance = controller ?: return TunnelEngine.Traffic()
        if (!instance.isRunning) return TunnelEngine.Traffic()
        val payload = runCatching { instance.queryAllOutboundTrafficStats() }.getOrDefault("")
        var up = 0L
        var down = 0L
        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach
            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach
            val value = parts[2].toLongOrNull() ?: return@forEach
            when (parts[1].lowercase()) {
                "uplink", "up" -> up += value
                "downlink", "down" -> down += value
            }
        }
        return TunnelEngine.Traffic(up = up, down = down)
    }

    private companion object {
        const val TAG = "AntiNetXray"
    }
}

/**
 * Process-wide pieces of AndroidLibXrayLite: the Go runtime and delay probes.
 */
object XrayCore {
    private const val TAG = "AntiNetXray"
    private val initialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv("", deviceKey(context))
        }.onFailure {
            initialized.set(false)
            Log.e(TAG, "init failed", it)
            throw it
        }
    }

    val version: String
        get() = runCatching { Libv2ray.checkVersionX() }.getOrDefault("Xray")

    suspend fun measureDelay(context: Context, config: String, url: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            Libv2ray.measureOutboundDelay(config, url)
        }.getOrDefault(-1L)
    }

    private fun deviceKey(context: Context): String = runCatching {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .toByteArray(Charsets.UTF_8)
        Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING or Base64.URL_SAFE)
    }.getOrDefault("")
}
