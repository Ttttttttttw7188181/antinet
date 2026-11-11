package com.antinet.vpn.engine

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.antinet.vpn.data.AppSettings

/**
 * Builds the Android TUN interface for the main VPN session.
 *
 * Xray receives the descriptor directly. sing-box receives the same descriptor
 * through [io.nekohasekai.libbox.PlatformInterface.openTun], and its config uses the
 * matching address and MTU.
 */
object TunFactory {
    const val ADDRESS_V4 = "10.10.10.1"
    const val PREFIX_V4 = 30
    const val ADDRESS_V6 = "fd00::1"
    const val PREFIX_V6 = 126

    const val HOTSPOT_ADDRESS_V4 = "10.163.0.2"
    const val HOTSPOT_PREFIX_V4 = 24

    fun establish(service: VpnService, settings: AppSettings): ParcelFileDescriptor {
        // VpnService.Builder is an inner class: it must be created from the
        // running service instance.
        val builder = service.Builder()
        builder.setSession("AntiNet")
        builder.setMtu(settings.mtu)
        builder.addAddress(ADDRESS_V4, PREFIX_V4)
        builder.addRoute("0.0.0.0", 0)
        if (settings.ipv6) {
            builder.addAddress(ADDRESS_V6, PREFIX_V6)
            builder.addRoute("::", 0)
        }
        when (settings.dnsMode) {
            "google" -> {
                builder.addDnsServer("8.8.8.8")
                builder.addDnsServer("8.8.4.4")
                if (settings.ipv6) builder.addDnsServer("2001:4860:4860::8888")
            }
            "system" -> Unit
            else -> {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("1.0.0.1")
                if (settings.ipv6) builder.addDnsServer("2606:4700:4700::1111")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val self = service.packageName
        if (settings.perAppEnabled && settings.perAppPackages.isNotEmpty()) {
            if (settings.perAppBypass) {
                settings.perAppPackages.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
                runCatching { builder.addDisallowedApplication(self) }
            } else {
                settings.perAppPackages.forEach { pkg ->
                    if (pkg != self) runCatching { builder.addAllowedApplication(pkg) }
                }
            }
        } else {
            runCatching { builder.addDisallowedApplication(self) }
        }
        return builder.establish() ?: error("VPN-интерфейс не создан")
    }
}
