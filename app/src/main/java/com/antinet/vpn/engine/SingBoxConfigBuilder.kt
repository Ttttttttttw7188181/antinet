package com.antinet.vpn.engine

import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.GroupStrategy
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box configuration from AntiNet profiles.
 *
 * The mapping follows sing-box 1.12+: new DNS server format, rule actions for
 * sniffing and DNS hijacking, merged TUN address fields. Xray-only features
 * (fragmentation, Xray JSON rules) are deliberately not emitted here.
 */
class SingBoxConfigBuilder(
    private val settings: AppSettings,
    private val allProfiles: List<Profile>,
) {
    data class BuiltConfig(
        val json: String,
        val profile: Profile,
        val entryTags: List<String>,
    )

    fun build(
        selectedId: String,
        tunAddress: String = "${TunFactory.ADDRESS_V4}/${TunFactory.PREFIX_V4}",
        tunMtu: Int = settings.mtu,
        cacheFile: Boolean = true,
    ): BuiltConfig {
        val selected = allProfiles.firstOrNull { it.id == selectedId }
            ?: error("Сервер не выбран")
        val outbounds = JSONArray()
        val entryTags = mutableListOf<String>()
        var mainEntry = ""

        if (selected.isGroup) {
            val children = selected.groupIds.mapNotNull { id ->
                allProfiles.firstOrNull { it.id == id && it.enabled }
            }
            if (children.isEmpty()) error("В группе нет доступных серверов")
            children.forEachIndexed { index, child ->
                val tag = buildChain(child, "g$index", outbounds)
                entryTags += tag
            }
            mainEntry = groupOutbound(selected, entryTags, outbounds)
        } else {
            mainEntry = buildChain(selected, "proxy", outbounds)
            entryTags += mainEntry
        }

        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))

        val config = JSONObject()
        config.put("log", JSONObject().put("level", settings.singBoxLogLevel.ifBlank { "warn" }).put("timestamp", true))
        config.put("dns", dnsConfig(mainEntry))
        config.put("inbounds", JSONArray().put(tunInbound(tunAddress, tunMtu)))
        config.put("outbounds", outbounds)
        config.put("route", routeConfig(selected, mainEntry))
        if (cacheFile) {
            // Two sing-box instances must not open the same cache.db: the
            // hotspot instance runs without a file cache.
            config.put(
                "experimental",
                JSONObject().put(
                    "cache_file",
                    JSONObject().put("enabled", true).put("path", "cache.db"),
                ),
            )
        }
        return BuiltConfig(config.toString(2), selected, entryTags)
    }

    private fun buildChain(profile: Profile, prefix: String, outbounds: JSONArray): String {
        val chain = profile.chainIds.mapNotNull { id ->
            allProfiles.firstOrNull { it.id == id && it.enabled }
        }
        if (chain.any { it.id == profile.id }) error("Цепочка не может ссылаться на саму себя")
        val all = listOf(profile) + chain
        if (all.map { it.id }.distinct().size != all.size) error("В цепочке есть повторяющийся сервер")
        val tags = all.mapIndexed { index, _ -> if (index == 0) prefix else "$prefix-c$index" }
        all.forEachIndexed { index, endpoint ->
            val outbound = outboundFor(endpoint, tags[index])
            if (index < all.lastIndex) outbound.put("detour", tags[index + 1])
            if (index == 0 && settings.mux) {
                outbound.put(
                    "multiplex",
                    JSONObject()
                        .put("enabled", true)
                        .put("protocol", "h2mux")
                        .put("max_streams", settings.muxConcurrency),
                )
            }
            outbounds.put(outbound)
        }
        return tags.first()
    }

    private fun groupOutbound(group: Profile, tags: List<String>, outbounds: JSONArray): String {
        val tag = "proxy-group"
        val outbound = JSONObject().put("tag", tag).put("outbounds", JSONArray(tags))
        when (group.groupStrategy) {
            GroupStrategy.LEAST_PING -> {
                outbound.put("type", "urltest")
                outbound.put("url", settings.delayUrl)
                outbound.put("interval", "3m")
                outbound.put("tolerance", 50)
            }
            GroupStrategy.LEAST_LOAD, GroupStrategy.RANDOM -> {
                outbound.put("type", "selector")
                outbound.put("default", tags.first())
            }
        }
        outbounds.put(outbound)
        return tag
    }

    private fun outboundFor(profile: Profile, tag: String): JSONObject {
        if (profile.protocol == ProxyProtocol.CUSTOM) {
            val custom = runCatching { JSONObject(profile.customOutbound) }.getOrNull()
                ?: error("${profile.name}: пустой или некорректный JSON outbound")
            if (!custom.has("type")) {
                error("${profile.name}: для sing-box нужен outbound в формате sing-box (поле type)")
            }
            custom.put("tag", tag)
            return custom
        }
        if (profile.protocol == ProxyProtocol.WIREGUARD) {
            error("${profile.name}: WireGuard добавляется через пользовательский JSON outbound")
        }
        if (profile.server.isBlank()) error("${profile.name}: не указан адрес")
        if (profile.port !in 1..65535) error("${profile.name}: неверный порт")

        val outbound = JSONObject()
            .put("type", singBoxType(profile.protocol))
            .put("tag", tag)
            .put("server", profile.server)
            .put("server_port", profile.port)

        when (profile.protocol) {
            ProxyProtocol.VMESS -> {
                outbound.put("uuid", profile.password)
                outbound.put("security", vmessSecurity(profile.method))
                outbound.put("alter_id", profile.alterId)
            }
            ProxyProtocol.VLESS -> {
                outbound.put("uuid", profile.password)
                if (profile.flow.isNotBlank()) outbound.put("flow", profile.flow)
            }
            ProxyProtocol.TROJAN -> outbound.put("password", profile.password)
            ProxyProtocol.SHADOWSOCKS -> {
                outbound.put("method", profile.method.ifBlank { "aes-256-gcm" })
                outbound.put("password", profile.password)
            }
            ProxyProtocol.SOCKS -> {
                outbound.put("version", "5")
                if (profile.username.isNotBlank()) outbound.put("username", profile.username)
                if (profile.password.isNotBlank()) outbound.put("password", profile.password)
            }
            ProxyProtocol.HTTP -> {
                if (profile.username.isNotBlank()) outbound.put("username", profile.username)
                if (profile.password.isNotBlank()) outbound.put("password", profile.password)
            }
            else -> Unit
        }

        tlsSettings(profile)?.let { outbound.put("tls", it) }
        transportSettings(profile)?.let { outbound.put("transport", it) }
        return outbound
    }

    private fun singBoxType(protocol: ProxyProtocol): String = when (protocol) {
        ProxyProtocol.VMESS -> "vmess"
        ProxyProtocol.VLESS -> "vless"
        ProxyProtocol.TROJAN -> "trojan"
        ProxyProtocol.SHADOWSOCKS -> "shadowsocks"
        ProxyProtocol.SOCKS -> "socks"
        ProxyProtocol.HTTP -> "http"
        else -> error("Неподдерживаемый протокол $protocol")
    }

    private fun vmessSecurity(method: String): String {
        val value = method.ifBlank { "auto" }.lowercase()
        return if (value in setOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305")) value else "auto"
    }

    private fun tlsSettings(profile: Profile): JSONObject? {
        val security = profile.security.lowercase()
        if (security != "tls" && security != "xtls" && security != "reality") return null
        val tls = JSONObject().put("enabled", true)
        val sni = profile.sni.ifBlank { profile.host.ifBlank { profile.server } }
        if (sni.isNotBlank()) tls.put("server_name", sni)
        tls.put("insecure", profile.allowInsecure)
        val alpn = profile.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn))
        tls.put(
            "utls",
            JSONObject()
                .put("enabled", true)
                .put("fingerprint", profile.fingerprint.ifBlank { "chrome" }),
        )
        if (security == "reality") {
            if (profile.publicKey.isBlank()) error("${profile.name}: REALITY без public key")
            tls.put(
                "reality",
                JSONObject()
                    .put("enabled", true)
                    .put("public_key", profile.publicKey)
                    .put("short_id", profile.shortId),
            )
        }
        return tls
    }

    private fun transportSettings(profile: Profile): JSONObject? {
        val network = profile.network.ifBlank { "tcp" }.lowercase()
        val path = profile.path.ifBlank { "/" }
        val host = profile.host.ifBlank { profile.sni.ifBlank { profile.server } }
        return when (network) {
            "tcp" -> null
            "ws", "websocket" -> JSONObject()
                .put("type", "ws")
                .put("path", path)
                .apply {
                    if (host.isNotBlank()) put("headers", JSONObject().put("Host", host))
                }
            "grpc", "gun" -> JSONObject()
                .put("type", "grpc")
                .put("service_name", profile.serviceName)
                .put("permit_without_stream", profile.mode.equals("multi", true))
            "http", "h2" -> JSONObject()
                .put("type", "http")
                .put("host", JSONArray().put(host))
                .put("path", path)
            "httpupgrade" -> JSONObject()
                .put("type", "httpupgrade")
                .put("host", host)
                .put("path", path)
            "xhttp", "splithttp" -> JSONObject()
                .put("type", "xhttp")
                .put("host", host)
                .put("path", path)
                .apply { if (profile.mode.isNotBlank()) put("mode", profile.mode) }
            "quic" -> JSONObject().put("type", "quic")
            "kcp", "mkcp" -> error("${profile.name}: sing-box не поддерживает mKCP — выберите Xray")
            else -> error("${profile.name}: sing-box не поддерживает транспорт $network")
        }
    }

    private fun tunInbound(address: String, mtu: Int): JSONObject = JSONObject()
        .put("type", "tun")
        .put("tag", "tun-in")
        .put("address", JSONArray().put(address))
        .put("mtu", mtu)
        .put("stack", settings.singBoxStack.ifBlank { "gvisor" })
        .put("auto_route", false)
        .put("strict_route", false)

    private fun dnsConfig(mainEntry: String): JSONObject {
        val servers = JSONArray()
        val address = when (settings.dnsMode) {
            "google" -> "8.8.8.8"
            "system" -> "1.1.1.1"
            else -> "1.1.1.1"
        }
        servers.put(
            JSONObject()
                .put("type", "udp")
                .put("tag", "dns-remote")
                .put("server", address)
                .put("detour", mainEntry),
        )
        servers.put(
            JSONObject()
                .put("type", "udp")
                .put("tag", "dns-direct")
                .put("server", address)
                .put("detour", "direct"),
        )
        val dns = JSONObject()
            .put("servers", servers)
            .put("final", "dns-remote")
            .put("strategy", settings.singBoxDomainStrategy.ifBlank { "prefer_ipv4" })
        return dns
    }

    private fun routeConfig(selected: Profile, mainEntry: String): JSONObject {
        val rules = JSONArray()
        if (settings.singBoxSniff) {
            rules.put(JSONObject().put("action", "sniff"))
        }
        rules.put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        if (settings.bypassLan) {
            rules.put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))
        }

        val ruleSets = JSONArray()
        if (settings.blockAds) {
            ruleSets.put(
                remoteRuleSet(
                    "geosite-ads",
                    "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs",
                ),
            )
            rules.put(JSONObject().put("rule_set", "geosite-ads").put("action", "reject"))
        }
        when (settings.routingMode) {
            "bypass_ru" -> {
                ruleSets.put(
                    remoteRuleSet(
                        "geosite-ru",
                        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
                    ),
                )
                ruleSets.put(
                    remoteRuleSet(
                        "geoip-ru",
                        "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
                    ),
                )
                rules.put(JSONObject().put("rule_set", "geosite-ru").put("outbound", "direct"))
                rules.put(JSONObject().put("rule_set", "geoip-ru").put("outbound", "direct"))
            }
            "bypass_ir" -> {
                ruleSets.put(
                    remoteRuleSet(
                        "geosite-ir",
                        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ir.srs",
                    ),
                )
                ruleSets.put(
                    remoteRuleSet(
                        "geoip-ir",
                        "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ir.srs",
                    ),
                )
                rules.put(JSONObject().put("rule_set", "geosite-ir").put("outbound", "direct"))
                rules.put(JSONObject().put("rule_set", "geoip-ir").put("outbound", "direct"))
            }
        }

        val route = JSONObject()
            .put("auto_detect_interface", true)
            .put("final", mainEntry)
            .put("rules", rules)
            .put(
                "default_domain_resolver",
                JSONObject()
                    .put("server", "dns-direct")
                    .put("strategy", settings.singBoxDomainStrategy.ifBlank { "prefer_ipv4" }),
            )
        if (ruleSets.length() > 0) route.put("rule_set", ruleSets)
        if (selected.isGroup && selected.groupStrategy == GroupStrategy.LEAST_PING) {
            route.put("final", mainEntry)
        }
        return route
    }

    private fun remoteRuleSet(tag: String, url: String): JSONObject = JSONObject()
        .put("type", "remote")
        .put("tag", tag)
        .put("format", "binary")
        .put("url", url)
        .put("download_detour", "direct")
}
