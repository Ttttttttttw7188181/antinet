package com.antinet.vpn.hotspot

import android.content.Context
import android.os.ParcelFileDescriptor
import com.antinet.vpn.shizuku.ShizukuBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side half of tethering: asks the Shizuku user service for a test-network
 * TUN and keeps the UI state. The VPN service owns the second core instance that
 * actually reads that TUN.
 */
object HotspotBridge {
    data class Session(val fd: ParcelFileDescriptor, val iface: String)

    data class State(
        val active: Boolean = false,
        val starting: Boolean = false,
        val iface: String = "",
        val error: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun start(context: Context, mtu: Int, ipv6: Boolean, dns: String): Result<Session> {
        _state.value = State(starting = true)
        ShizukuBridge.init(context)
        ShizukuBridge.refresh(rebind = true)
        if (!ShizukuBridge.state.value.ready) {
            ShizukuBridge.awaitReady()
        }
        if (!ShizukuBridge.state.value.ready) {
            val message = when {
                !ShizukuBridge.state.value.installed -> "Shizuku не установлен"
                !ShizukuBridge.state.value.running -> "Shizuku не запущен"
                !ShizukuBridge.state.value.permission -> "Нет разрешения Shizuku"
                else -> "Shizuku ещё подключается"
            }
            _state.value = State(error = message)
            return Result.failure(IllegalStateException(message))
        }
        return ShizukuBridge.startHotspot(mtu, ipv6, dns).mapCatching { bundle ->
            val message = bundle.getString("error")
            if (!message.isNullOrBlank()) error(message)
            @Suppress("DEPRECATION")
            val fd = bundle.getParcelable<ParcelFileDescriptor>("fd")
                ?: error("Shizuku не вернул TUN")
            val iface = bundle.getString("iface").orEmpty()
            _state.value = State(active = true, iface = iface)
            Session(fd, iface)
        }.onFailure {
            _state.value = State(error = it.message ?: "Не удалось включить раздачу")
        }
    }

    suspend fun stop() {
        ShizukuBridge.stopHotspot()
        _state.value = State()
    }

    fun failed(message: String) {
        _state.value = State(error = message)
    }
}
