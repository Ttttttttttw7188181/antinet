package com.antinet.vpn.engine

import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.GroupStrategy
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds an Xray configuration from AntiNet profiles.
 *
 * Everything here is plain JSON on purpose: the engine is the only Xray component in
 * the app, and the mapping stays visible and testable instead of hiding behind a
 * generated config template.
 */
class XrayConfigBuilder(
    private val settings: AppSettings,
    private val allProfiles: List<Profile>,
) {
    data class BuiltConfig(
        val json: String,
        val profile: Profile,
        val entryTags: List<String>,
        val leafTags: List<String>,
    )

    fun build(selectedId: String): BuiltConfig {
        val selected = allProfiles.firstOrNull { it.id == selectedId }
            ?: error("Сервер не выбран")
        val outbounds = JSONArray()
        val entryTags = mutableListOf<String>()
        val leafTags = mutableListOf<String>()
        var mainEntry = ""

        if (selected.isGroup) {
            val children = selected.groupIds.mapNotNull { id -> allProfiles.firstOrNull { it.id == id && it.enabled } }
            if (children.isEmpty()) error("В группе нет доступных серверов")
            children.forEachIndexed { index, child ->
                val tags = buildChain(child, "g$index", outbounds, leafTags)
                entryTags += tags.first()
                mainEntry = mainEntry.ifBlank { tags.first() }
            }
        } else {
            val tags = buildChain(selected, "proxy", outbounds, leafTags)
            entryTags += tags.first()
            mainEntry = tags.first()
        }

        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))

        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put("stats", JSONObject())
        config.put("policy", policy())
        config.put("inbounds", JSONArray().put(tunInbound()))
        config.put("outbounds", outbounds)
        config.put("routing", routing(selected, mainEntry, entryTags))
        if (selected.isGroup && selected.groupStrategy != GroupStrategy.RANDOM) {
            config.put("observatory", observatory(entryTags))
            if (selected.groupStrategy == GroupStrategy.LEAST_LOAD) {
                config.put("burstObservatory", burstObservatory(entryTags))
            }
        }
        return BuiltConfig(config.toString(2), selected, entryTags, leafTags)
    }

    fun buildPing(profileId: String): String {
        val built = build(profileId)
        val root = JSONObject(built.json)
        root.put("inbounds", JSONArray())
        return root.toString()
    }

    private fun buildChain(
        profile: Profile,
        prefix: String,
        outbounds: JSONArray,
        leafTags: MutableList<String>,
    ): List<String> {
        val chainProfiles = profile.chainIds.mapNotNull { id -> allProfiles.firstOrNull { it.id == id && it.enabled } }
        if (chainProfiles.any { it.id == profile.id }) error("Цепочка не может ссылаться на саму себя")
        val all = listOf(profile) + chainProfiles
        if (all.map { it.id }.distinct().size != all.size) error("В цепочке есть повторяющийся сервер")
        val tags = all.mapIndexed { index, _ ->
            if (index == 0) prefix else "$prefix-c$index"
        }
        all.forEachIndexed { index, endpoint ->
            val outbound = outboundFor(endpoint, tags[index])
            if (index < all.lastIndex) {
                addDialerProxy(outbound, tags[index + 1])
            }
            applyFragment(outbound, endpoint)
            if (index == 0 && settings.mux && !endpoint.isGroup) {
                outbound.put("mux", JSONObject().put("enabled", true).put("concurrency", settings.muxConcurrency))
            }
            outbounds.put(outbound)
        }
        leafTags += tags.last()
        return tags
    }

    private fun outboundFor(profile: Profile, tag: String): JSONObject {
        if (profile.protocol == ProxyProtocol.CUSTOM) {
            val custom = runCatching { JSONObject(profile.customOutbound) }.getOrNull()
                ?: error("${profile.name}: пустой или некорректный JSON outbound")
            custom.put("tag", tag)
            return custom
        }
        if (profile.protocol == ProxyProtocol.WIREGUARD) {
            error("${profile.name}: WireGuard добавляется через пользовательский JSON outbound")
        }
        if (profile.server.isBlank()) error("${profile.name}: не указан адрес")
        if (profile.port !in 1..65535) error("${profile.name}: неверный порт")
        val outbound = JSONObject()
            .put("tag", tag)
            .put("protocol", xrayProtocol(profile.protocol))
        val stream = streamSettings(profile)
        outbound.put("settings", protocolSettings(profile))
        if (stream.length() > 0) outbound.put("streamSettings", stream)
        return outbound
    }

    private fun xrayProtocol(protocol: ProxyProtocol): String = when (protocol) {
        ProxyProtocol.VMESS -> "vmess"
        ProxyProtocol.VLESS -> "vless"
        ProxyProtocol.TROJAN -> "trojan"
        ProxyProtocol.SHADOWSOCKS -> "shadowsocks"
        ProxyProtocol.SOCKS -> "socks"
        ProxyProtocol.HTTP -> "http"
        ProxyProtocol.GROUP -> error("Группа не является outbound")
        else -> error("Неподдерживаемый протокол")
    }

    private fun protocolSettings(profile: Profile): JSONObject = when (profile.protocol) {
        ProxyProtocol.VMESS -> JSONObject().put("vnext", JSONArray().put(
            JSONObject()
                .put("address", profile.server)
                .put("port", profile.port)
                .put("users", JSONArray().put(
                    JSONObject()
                        .put("id", profile.password)
                        .put("alterId", profile.alterId)
                        .put("security", profile.method.ifBlank { "auto" })
                        .put("level", 8)
                ))
        ))

        ProxyProtocol.VLESS -> JSONObject().put("vnext", JSONArray().put(
            JSONObject()
                .put("address", profile.server)
                .put("port", profile.port)
                .put("users", JSONArray().put(
                    JSONObject()
                        .put("id", profile.password)
                        .put("encryption", profile.encryption.ifBlank { "none" })
                        .put("flow", profile.flow)
                        .put("level", 8)
                ))
        ))

        ProxyProtocol.TROJAN -> JSONObject().put("servers", JSONArray().put(
            JSONObject()
                .put("address", profile.server)
                .put("port", profile.port)
                .put("password", profile.password)
                .put("level", 8)
        ))

        ProxyProtocol.SHADOWSOCKS -> JSONObject().put("servers", JSONArray().put(
            JSONObject()
                .put("address", profile.server)
                .put("port", profile.port)
                .put("method", profile.method.ifBlank { "aes-256-gcm" })
                .put("password", profile.password)
                .put("level", 8)
        ))

        ProxyProtocol.SOCKS, ProxyProtocol.HTTP -> {
            val server = JSONObject()
                .put("address", profile.server)
                .put("port", profile.port)
            if (profile.username.isNotBlank() || profile.password.isNotBlank()) {
                server.put("users", JSONArray().put(
                    JSONObject()
                        .put("user", profile.username)
                        .put("pass", profile.password)
                        .put("level", 8)
                ))
            }
            JSONObject().put("servers", JSONArray().put(server))
        }

        else -> error("Неподдерживаемый протокол ${profile.protocol}")
    }

    private fun streamSettings(profile: Profile): JSONObject {
        val stream = JSONObject()
        val network = profile.network.ifBlank { "tcp" }
        stream.put("network", network)
        val security = profile.security.lowercase()
        when (security) {
            "tls", "xtls" -> {
                stream.put("security", "tls")
                stream.put("tlsSettings", tlsSettings(profile))
            }
            "reality" -> {
                stream.put("security", "reality")
                stream.put("realitySettings", realitySettings(profile))
            }
            else -> stream.put("security", "none")
        }

        when (network) {
            "ws", "websocket" -> stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", profile.path.ifBlank { "/" })
                    .apply {
                        if (profile.host.isNotBlank()) {
                            put("headers", JSONObject().put("Host", profile.host))
                        }
                    }
            )
            "grpc", "gun" -> stream.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", profile.serviceName)
                    .put("multiMode", profile.mode.equals("multi", true))
            )
            "http", "h2" -> stream.put(
                "httpSettings",
                JSONObject()
                    .put("path", profile.path.ifBlank { "/" })
                    .put("host", JSONArray().put(profile.host.ifBlank { profile.server }))
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("path", profile.path.ifBlank { "/" })
                    .put("host", profile.host.ifBlank { profile.sni.ifBlank { profile.server } })
            )
            "xhttp", "splithttp" -> stream.put(
                "xhttpSettings",
                JSONObject()
                    .put("path", profile.path.ifBlank { "/" })
                    .put("host", profile.host.ifBlank { profile.sni.ifBlank { profile.server } })
                    .apply { if (profile.mode.isNotBlank()) put("mode", profile.mode) }
            )
            "kcp", "mkcp" -> stream.put(
                "kcpSettings",
                JSONObject()
                    .put("mtu", 1350)
                    .put("tti", 50)
                    .put("uplinkCapacity", 5)
                    .put("downlinkCapacity", 20)
                    .put("congestion", false)
                    .put("readBufferSize", 1)
                    .put("writeBufferSize", 1)
                    .put("header", JSONObject().put("type", profile.headerType.ifBlank { "none" }))
            )
            "quic" -> stream.put(
                "quicSettings",
                JSONObject()
                    .put("security", "none")
                    .put("key", "")
                    .put("header", JSONObject().put("type", "none"))
            )
            "tcp" -> {
                if (profile.headerType.equals("http", true)) {
                    stream.put(
                        "tcpSettings",
                        JSONObject().put(
                            "header",
                            JSONObject()
                                .put("type", "http")
                                .put(
                                    "request",
                                    JSONObject()
                                        .put("path", JSONArray().put(profile.path.ifBlank { "/" }))
                                        .put(
                                            "headers",
                                            JSONObject().put(
                                                "Host",
                                                JSONArray().put(profile.host.ifBlank { profile.server }),
                                            ),
                                        ),
                                ),
                        ),
                    )
                }
            }
        }
        return stream
    }

    private fun tlsSettings(profile: Profile): JSONObject {
        val sni = profile.sni.ifBlank { profile.host.ifBlank { profile.server } }
        val tls = JSONObject()
            .put("serverName", sni)
            .put("allowInsecure", profile.allowInsecure)
            .put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
        val alpn = profile.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn))
        return tls
    }

    private fun realitySettings(profile: Profile): JSONObject {
        if (profile.publicKey.isBlank()) error("${profile.name}: REALITY без public key")
        return JSONObject()
            .put("serverName", profile.sni.ifBlank { profile.host.ifBlank { profile.server } })
            .put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
            .put("publicKey", profile.publicKey)
            .put("shortId", profile.shortId)
            .put("spiderX", profile.spiderX.ifBlank { "/" })
    }

    private fun addDialerProxy(outbound: JSONObject, proxyTag: String) {
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject().also {
            outbound.put("streamSettings", it)
        }
        val sockopt = stream.optJSONObject("sockopt") ?: JSONObject().also {
            stream.put("sockopt", it)
        }
        sockopt.put("dialerProxy", proxyTag)
    }

    private fun applyFragment(outbound: JSONObject, profile: Profile) {
        if (!settings.fragmentEnabled) return
        val security = profile.security.lowercase()
        if (security != "tls" && security != "reality" && security != "xtls") return
        val stream = outbound.optJSONObject("streamSettings") ?: return
        val dialer = stream.optJSONObject("sockopt")?.optString("dialerProxy").orEmpty()
        if (dialer.isNotBlank()) return

        var packets = settings.fragmentPackets.ifBlank { "tlshello" }
        if (security == "reality" && packets == "tlshello") packets = "1-3"
        val fragment = JSONObject()
            .put("type", "fragment")
            .put(
                "settings",
                JSONObject()
                    .put("packets", packets)
                    .put("length", settings.fragmentLength.ifBlank { "50-100" })
                    .put("delay", settings.fragmentDelay.ifBlank { "10-20" })
                    .put("maxSplit", settings.fragmentMaxSplit.ifBlank { "10" }),
            )
        val noise = JSONObject()
            .put("type", "noise")
            .put(
                "settings",
                JSONObject().put(
                    "noise",
                    JSONArray().put(JSONObject().put("rand", "10-20").put("delay", "10-16")),
                ),
            )
        val finalMask = stream.optJSONObject("finalmask") ?: JSONObject().also {
            stream.put("finalmask", it)
        }
        if (finalMask.optJSONArray("tcp") == null) {
            finalMask.put("tcp", JSONArray().put(fragment))
        }
        if (finalMask.optJSONArray("udp") == null) {
            finalMask.put("udp", JSONArray().put(noise))
        }
    }

    private fun tunInbound(): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings",
            JSONObject()
                .put("name", "xray0")
                .put("MTU", settings.mtu)
                .put("userLevel", 8),
        )
        .put(
            "sniffing",
            JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                .put("routeOnly", false),
        )

    private fun policy(): JSONObject = JSONObject()
        .put(
            "levels",
            JSONObject().put(
                "8",
                JSONObject()
                    .put("handshake", 4)
                    .put("connIdle", 300)
                    .put("uplinkOnly", 1)
                    .put("downlinkOnly", 1),
            ),
        )
        .put(
            "system",
            JSONObject()
                .put("statsOutboundUplink", true)
                .put("statsOutboundDownlink", true),
        )

    private fun routing(
        selected: Profile,
        mainEntry: String,
        entryTags: List<String>,
    ): JSONObject {
        val rules = JSONArray()
        if (settings.bypassLan) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("outboundTag", "direct"),
            )
        }
        if (settings.blockAds) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:category-ads-all"))
                    .put("outboundTag", "block"),
            )
        }
        when (settings.routingMode) {
            "bypass_ru" -> {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", JSONArray().put("geosite:category-ru"))
                        .put("outboundTag", "direct"),
                )
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:ru"))
                        .put("outboundTag", "direct"),
                )
            }
            "bypass_ir" -> {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", JSONArray().put("geosite:category-ir"))
                        .put("outboundTag", "direct"),
                )
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:ir"))
                        .put("outboundTag", "direct"),
                )
            }
        }
        if (settings.customRules.isNotBlank()) {
            runCatching { JSONArray(settings.customRules) }.getOrNull()?.let { custom ->
                for (i in 0 until custom.length()) {
                    val item = custom.optJSONObject(i) ?: continue
                    rules.put(item)
                }
            }
        }

        val routing = JSONObject()
            .put("domainStrategy", "AsIs")
            .put("rules", rules)
        if (selected.isGroup) {
            val strategy = when (selected.groupStrategy) {
                GroupStrategy.LEAST_PING -> "leastPing"
                GroupStrategy.LEAST_LOAD -> "leastLoad"
                GroupStrategy.RANDOM -> "random"
            }
            val balancer = JSONObject()
                .put("tag", "main-balancer")
                .put("selector", JSONArray(entryTags))
                .put("strategy", JSONObject().put("type", strategy))
            if (entryTags.isNotEmpty()) balancer.put("fallbackTag", entryTags.first())
            routing.put("balancers", JSONArray().put(balancer))
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("balancerTag", "main-balancer"),
            )
        } else {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", mainEntry),
            )
        }
        return routing
    }

    private fun observatory(entryTags: List<String>): JSONObject = JSONObject()
        .put("subjectSelector", JSONArray(entryTags))
        .put("probeUrl", settings.delayUrl)
        .put("probeInterval", "60s")
        .put("enableConcurrency", true)

    private fun burstObservatory(entryTags: List<String>): JSONObject = JSONObject()
        .put("subjectSelector", JSONArray(entryTags))
        .put(
            "pingConfig",
            JSONObject()
                .put("destination", settings.delayUrl)
                .put("interval", "30s")
                .put("sampling", 3)
                .put("timeout", "5s"),
        )
}
