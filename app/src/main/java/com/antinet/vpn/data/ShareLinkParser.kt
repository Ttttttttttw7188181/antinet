package com.antinet.vpn.data

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses the share links people actually paste: vless, vmess, trojan, shadowsocks,
 * socks and http. Everything is converted into [Profile]; the original link is kept
 * so a profile can be shared again without lossy re-encoding.
 */
object ShareLinkParser {

    fun parseMany(raw: String): ImportOutcome {
        val text = raw.trim()
        if (text.isEmpty()) return ImportOutcome(errors = listOf("Пустой текст"))
        val lines = if (text.contains("://")) {
            text.lines()
        } else {
            runCatching { decodeBase64(text) }.getOrNull()?.lines() ?: text.lines()
        }
        val added = mutableListOf<Profile>()
        val errors = mutableListOf<String>()
        lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { line ->
                val profile = parse(line)
                if (profile != null) added += profile else errors += "Не распознано: ${line.take(24)}…"
            }
        return ImportOutcome(added, errors)
    }

    fun parse(link: String): Profile? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
            trimmed.startsWith("socks://", ignoreCase = true) ||
                trimmed.startsWith("socks5://", ignoreCase = true) -> parseSocks(trimmed)
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> parseHttp(trimmed)
            else -> null
        }
    }

    private fun parseVmess(link: String): Profile? = runCatching {
        val payload = link.substringAfter("://")
        val json = JSONObject(decodeBase64(payload))
        val server = json.optString("add").ifBlank { return null }
        val port = json.optInt("port", 443)
        val network = json.optString("net", "tcp").ifBlank { "tcp" }
        val tls = json.optString("tls").equals("tls", ignoreCase = true)
        val name = json.optString("ps").ifBlank { "VMess $server" }
        Profile(
            name = name,
            protocol = ProxyProtocol.VMESS,
            server = server,
            port = port,
            password = json.optString("id"),
            alterId = json.optInt("aid", 0),
            method = json.optString("scy", "auto").ifBlank { "auto" },
            security = if (tls) "tls" else "none",
            sni = json.optString("sni").ifBlank { json.optString("host") },
            fingerprint = json.optString("fp", "chrome").ifBlank { "chrome" },
            alpn = json.optString("alpn"),
            network = network,
            headerType = json.optString("type"),
            host = json.optString("host"),
            path = json.optString("path", "/").ifBlank { "/" },
            serviceName = json.optString("path"),
            rawLink = link,
        )
    }.getOrNull()

    private fun parseVless(link: String): Profile? = runCatching {
        val uri = uriParts(link) ?: return null
        val id = uri.userInfo.ifBlank { return null }
        val security = uri.query["security"].orEmpty().ifBlank { "none" }
        val network = uri.query["type"].orEmpty().ifBlank { "tcp" }
        val name = uri.fragment.ifBlank { "VLESS ${uri.host}" }
        Profile(
            name = name,
            protocol = ProxyProtocol.VLESS,
            server = uri.host,
            port = uri.port,
            password = id,
            encryption = uri.query["encryption"].orEmpty().ifBlank { "none" },
            flow = uri.query["flow"].orEmpty(),
            security = security,
            sni = uri.query["sni"].orEmpty().ifBlank { uri.query["host"].orEmpty() },
            fingerprint = uri.query["fp"].orEmpty().ifBlank { "chrome" },
            publicKey = uri.query["pbk"].orEmpty(),
            shortId = uri.query["sid"].orEmpty(),
            spiderX = uri.query["spx"].orEmpty().ifBlank { "/" },
            allowInsecure = uri.query["allowInsecure"] == "1" || uri.query["insecure"] == "1",
            alpn = uri.query["alpn"].orEmpty(),
            network = network,
            headerType = uri.query["headerType"].orEmpty(),
            host = uri.query["host"].orEmpty(),
            path = uri.query["path"].orEmpty().ifBlank { "/" },
            serviceName = uri.query["serviceName"].orEmpty(),
            mode = uri.query["mode"].orEmpty(),
            rawLink = link,
        )
    }.getOrNull()

    private fun parseTrojan(link: String): Profile? = runCatching {
        val uri = uriParts(link) ?: return null
        val password = uri.userInfo.ifBlank { return null }
        val network = uri.query["type"].orEmpty().ifBlank { "tcp" }
        val security = uri.query["security"].orEmpty().ifBlank { "tls" }
        val name = uri.fragment.ifBlank { "Trojan ${uri.host}" }
        Profile(
            name = name,
            protocol = ProxyProtocol.TROJAN,
            server = uri.host,
            port = uri.port,
            password = password,
            security = security,
            sni = uri.query["sni"].orEmpty().ifBlank { uri.query["peer"].orEmpty() },
            fingerprint = uri.query["fp"].orEmpty().ifBlank { "chrome" },
            publicKey = uri.query["pbk"].orEmpty(),
            shortId = uri.query["sid"].orEmpty(),
            spiderX = uri.query["spx"].orEmpty().ifBlank { "/" },
            allowInsecure = uri.query["allowInsecure"] == "1" || uri.query["insecure"] == "1",
            alpn = uri.query["alpn"].orEmpty(),
            network = network,
            headerType = uri.query["headerType"].orEmpty(),
            host = uri.query["host"].orEmpty(),
            path = uri.query["path"].orEmpty().ifBlank { "/" },
            serviceName = uri.query["serviceName"].orEmpty(),
            mode = uri.query["mode"].orEmpty(),
            rawLink = link,
        )
    }.getOrNull()

    private fun parseShadowsocks(link: String): Profile? = runCatching {
        var body = link.substringAfter("://")
        val fragment = body.substringAfter('#', "").let { if (it.isEmpty()) "" else decode(it) }
        body = body.substringBefore('#')
        body = body.substringBefore('?')
        var userInfo: String
        var hostPort: String
        if (body.contains('@')) {
            userInfo = body.substringBeforeLast('@')
            hostPort = body.substringAfterLast('@')
        } else {
            val decoded = decodeBase64(body)
            userInfo = decoded.substringBeforeLast('@')
            hostPort = decoded.substringAfterLast('@')
        }
        val methodPass = if (userInfo.contains(':')) {
            userInfo
        } else {
            decodeBase64(userInfo)
        }
        val method = methodPass.substringBefore(':')
        val password = methodPass.substringAfter(':', "")
        val host = hostPort.substringBeforeLast(':').removeSurrounding("[", "]")
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: 8388
        Profile(
            name = fragment.ifBlank { "Shadowsocks $host" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = host,
            port = port,
            method = method,
            password = password,
            rawLink = link,
        )
    }.getOrNull()

    private fun parseSocks(link: String): Profile? = runCatching {
        val uri = uriParts(link) ?: return null
        val credentials = if (uri.userInfo.contains(':')) {
            uri.userInfo
        } else if (uri.userInfo.isNotBlank()) {
            runCatching { decodeBase64(uri.userInfo) }.getOrDefault(uri.userInfo)
        } else {
            ""
        }
        Profile(
            name = uri.fragment.ifBlank { "SOCKS ${uri.host}" },
            protocol = ProxyProtocol.SOCKS,
            server = uri.host,
            port = uri.port,
            username = credentials.substringBefore(':', ""),
            password = credentials.substringAfter(':', ""),
            rawLink = link,
        )
    }.getOrNull()

    private fun parseHttp(link: String): Profile? = runCatching {
        val uri = uriParts(link) ?: return null
        if (uri.port != 443 && uri.port != 80 && uri.port <= 0) return null
        Profile(
            name = uri.fragment.ifBlank { "HTTP ${uri.host}" },
            protocol = ProxyProtocol.HTTP,
            server = uri.host,
            port = uri.port,
            username = uri.userInfo.substringBefore(':', ""),
            password = uri.userInfo.substringAfter(':', ""),
            security = if (link.startsWith("https://", ignoreCase = true) && uri.port != 80) "tls" else "none",
            sni = uri.host,
            rawLink = link,
        )
    }.getOrNull()

    data class UriParts(
        val host: String,
        val port: Int,
        val userInfo: String,
        val fragment: String,
        val query: Map<String, String>,
    )

    private fun uriParts(link: String): UriParts? {
        val fragment = link.substringAfter('#', "").let { if (it.isEmpty()) "" else decode(it) }
        val withoutFragment = link.substringBefore('#')
        val queryPart = withoutFragment.substringAfter('?', "")
        val query = if (queryPart.isEmpty()) emptyMap() else queryPart.split('&').associate { pair ->
            val key = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            key to value
        }
        val base = withoutFragment.substringBefore('?')
        val authority = base.substringAfter("://")
        val userInfo = if (authority.contains('@')) decode(authority.substringBeforeLast('@')) else ""
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBeforeLast(':', hostPort)
        }.ifBlank { return null }
        val port = hostPort.substringAfterLast(':', "").toIntOrNull()
            ?: when {
                link.startsWith("https://", true) -> 443
                link.startsWith("http://", true) -> 80
                else -> 443
            }
        val normalizedHost = runCatching { URI("http://$host").host }.getOrNull() ?: host
        return UriParts(normalizedHost, port, userInfo, fragment, query)
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    fun decodeBase64(value: String): String {
        var text = value.trim()
            .replace('-', '+')
            .replace('_', '/')
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
        while (text.length % 4 != 0) text += "="
        return String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    fun encodeBase64(value: String): String =
        Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
}
