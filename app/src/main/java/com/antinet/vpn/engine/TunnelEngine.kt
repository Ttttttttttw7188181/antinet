package com.antinet.vpn.engine

import android.content.Context

/**
 * One running proxy core. The service owns at most two instances: the main VPN
 * tunnel and the optional tethering tunnel, which are completely independent.
 *
 * [traffic] returns the bytes exchanged since the previous call, so the caller can
 * accumulate session totals and treat the value as a per-second speed.
 */
interface TunnelEngine {
    val label: String

    val running: Boolean

    suspend fun start(context: Context, config: String, tunFd: Int)

    suspend fun stop()

    fun traffic(): Traffic

    data class Traffic(val up: Long = 0L, val down: Long = 0L) {
        val total: Long get() = up + down
    }
}
