package com.antinet.vpn.data

import java.util.UUID

enum class ProxyProtocol {
    VMESS,
    VLESS,
    TROJAN,
    SHADOWSOCKS,
    SOCKS,
    HTTP,
    WIREGUARD,
    CUSTOM,
    GROUP,
}

enum class GroupStrategy {
    LEAST_PING,
    LEAST_LOAD,
    RANDOM,
}

/**
 * One proxy endpoint. A profile with [ProxyProtocol.GROUP] is a virtual server that
 * lets Xray pick between [groupIds] with a balancer; it does not open a socket itself.
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val protocol: ProxyProtocol = ProxyProtocol.VLESS,
    val server: String = "",
    val port: Int = 443,
    val password: String = "",
    val method: String = "auto",
    val username: String = "",
    val alterId: Int = 0,
    val flow: String = "",
    val encryption: String = "none",
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "/",
    val allowInsecure: Boolean = false,
    val alpn: String = "",
    val network: String = "tcp",
    val headerType: String = "",
    val host: String = "",
    val path: String = "/",
    val serviceName: String = "",
    val mode: String = "",
    val rawLink: String = "",
    val customOutbound: String = "",
    val subscriptionId: String = "",
    val groupIds: List<String> = emptyList(),
    val groupStrategy: GroupStrategy = GroupStrategy.LEAST_PING,
    val chainIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    val enabled: Boolean = true,
    val lastPingMs: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isGroup: Boolean get() = protocol == ProxyProtocol.GROUP

    val endpoint: String
        get() = if (isGroup) "${groupIds.size} серверов" else "$server:$port"

    val protocolLabel: String
        get() = when (protocol) {
            ProxyProtocol.VMESS -> "VMess"
            ProxyProtocol.VLESS -> "VLESS"
            ProxyProtocol.TROJAN -> "Trojan"
            ProxyProtocol.SHADOWSOCKS -> "Shadowsocks"
            ProxyProtocol.SOCKS -> "SOCKS"
            ProxyProtocol.HTTP -> "HTTP"
            ProxyProtocol.WIREGUARD -> "WireGuard"
            ProxyProtocol.CUSTOM -> "Custom"
            ProxyProtocol.GROUP -> "Группа"
        }
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val updatedAt: Long = 0L,
    val count: Int = 0,
    val autoUpdate: Boolean = true,
) {
    val host: String
        get() = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
}

enum class CoreType {
    XRAY,
    SING_BOX,
}

data class AppSettings(
    val schema: Int = 1,
    val coreType: CoreType = CoreType.XRAY,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val accent: String = "ocean",
    val locale: String = "system",
    val routingMode: String = "global",
    val customRules: String = "",
    val bypassLan: Boolean = true,
    val blockAds: Boolean = false,
    val dnsMode: String = "cloudflare",
    val ipv6: Boolean = false,
    val mtu: Int = 1500,
    val mux: Boolean = false,
    val muxConcurrency: Int = 8,
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "50-100",
    val fragmentDelay: String = "10-20",
    val fragmentMaxSplit: String = "10",
    val singBoxLogLevel: String = "warn",
    val singBoxSniff: Boolean = true,
    val singBoxDomainStrategy: String = "prefer_ipv4",
    val singBoxStack: String = "gvisor",
    val hotspotEnabled: Boolean = false,
    val useShizuku: Boolean = true,
    val failoverEnabled: Boolean = true,
    val autoUpdateSubscriptions: Boolean = true,
    val subscriptionIntervalHours: Int = 12,
    val lastSubCheck: Long = 0L,
    val delayUrl: String = "https://www.gstatic.com/generate_204",
    val selectedProfileId: String = "",
    val notificationSpeed: Boolean = true,
    val perAppEnabled: Boolean = false,
    val perAppBypass: Boolean = false,
    val perAppPackages: List<String> = emptyList(),
    val sessionUp: Long = 0L,
    val sessionDown: Long = 0L,
    val sessionStartedAt: Long = 0L,
) {
    fun normalized(): AppSettings = copy(
        mtu = mtu.coerceIn(1200, 9000),
        muxConcurrency = muxConcurrency.coerceIn(1, 32),
        subscriptionIntervalHours = subscriptionIntervalHours.coerceIn(1, 168),
        perAppPackages = perAppPackages.distinct(),
    )
}

enum class EngineStatus {
    STOPPED,
    STARTING,
    CONNECTED,
    STOPPING,
    ERROR,
}

data class EngineState(
    val status: EngineStatus = EngineStatus.STOPPED,
    val profileId: String = "",
    val profileName: String = "",
    val startedAt: Long = 0L,
    val up: Long = 0L,
    val down: Long = 0L,
    val upSpeed: Long = 0L,
    val downSpeed: Long = 0L,
    val pingMs: Long = -1L,
    val error: String = "",
) {
    val active: Boolean get() = status == EngineStatus.CONNECTED || status == EngineStatus.STARTING
    val sessionSeconds: Long
        get() = if (startedAt <= 0L) 0L else ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
}

data class TrafficPoint(
    val up: Long = 0L,
    val down: Long = 0L,
    val at: Long = System.currentTimeMillis(),
)

data class ImportOutcome(
    val added: List<Profile> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val success: Boolean get() = added.isNotEmpty()
}

data class RoutingPreset(
    val id: String,
    val title: String,
    val description: String,
)

val RoutingPresets = listOf(
    RoutingPreset("global", "Весь трафик через прокси", "Только локальные адреса напрямую"),
    RoutingPreset("bypass_ru", "Обход России", "Российские сайты и адреса напрямую"),
    RoutingPreset("bypass_ir", "Обход Ирана", "Иранские сайты и адреса напрямую"),
)
