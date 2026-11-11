package com.antinet.vpn.engine

import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.data.GroupStrategy
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigBuilderTest {
    private val server = Profile(
        id = "main",
        name = "main",
        protocol = ProxyProtocol.VLESS,
        server = "example.com",
        port = 443,
        password = "11111111-1111-1111-1111-111111111111",
        security = "tls",
        sni = "example.com",
        network = "ws",
        path = "/ws",
        host = "cdn.example.com",
    )

    private val hop = server.copy(id = "hop", name = "hop", server = "hop.example.com")

    private fun build(settings: AppSettings = AppSettings(coreType = CoreType.SING_BOX)): JSONObject {
        val json = SingBoxConfigBuilder(settings, listOf(server)).build("main").json
        return JSONObject(json)
    }

    @Test
    fun buildsTunInboundWithAddressAndStack() {
        val root = build(AppSettings(coreType = CoreType.SING_BOX, singBoxStack = "mixed", mtu = 1400))
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("type"))
        assertEquals("10.10.10.1/30", inbound.getJSONArray("address").getString(0))
        assertEquals(1400, inbound.getInt("mtu"))
        assertEquals("mixed", inbound.getString("stack"))
        assertFalse(inbound.getBoolean("auto_route"))
    }

    @Test
    fun vlessTlsAndWebSocketTransport() {
        val root = build()
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", proxy.getString("type"))
        assertEquals("11111111-1111-1111-1111-111111111111", proxy.getString("uuid"))
        assertTrue(proxy.getJSONObject("tls").getBoolean("enabled"))
        assertEquals("example.com", proxy.getJSONObject("tls").getString("server_name"))
        val transport = proxy.getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/ws", transport.getString("path"))
    }

    @Test
    fun groupBecomesUrlTestWhenLeastPing() {
        val group = Profile(
            id = "group",
            name = "group",
            protocol = ProxyProtocol.GROUP,
            groupIds = listOf("main", "hop"),
            groupStrategy = GroupStrategy.LEAST_PING,
        )
        val root = JSONObject(
            SingBoxConfigBuilder(AppSettings(coreType = CoreType.SING_BOX), listOf(group, server, hop))
                .build("group")
                .json,
        )
        val outbounds = root.getJSONArray("outbounds")
        val groupOutbound = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "proxy-group" }
        assertEquals("urltest", groupOutbound.getString("type"))
        assertEquals(2, groupOutbound.getJSONArray("outbounds").length())
    }

    @Test
    fun dnsUsesNewServerFormat() {
        val root = build()
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val remote = servers.getJSONObject(0)
        assertEquals("udp", remote.getString("type"))
        assertTrue(remote.has("detour"))
    }

    @Test
    fun routeHasDefaultDomainResolverAndSniffAction() {
        val root = build()
        val route = root.getJSONObject("route")
        assertTrue(route.has("default_domain_resolver"))
        val rules = route.getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("action") == "sniff" })
    }

    @Test
    fun customOutboundMustHaveSingBoxType() {
        val custom = server.copy(
            id = "custom",
            name = "custom",
            protocol = ProxyProtocol.CUSTOM,
            customOutbound = """{"protocol":"vless"}""",
        )
        val error = runCatching {
            SingBoxConfigBuilder(AppSettings(coreType = CoreType.SING_BOX), listOf(custom)).build("custom")
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
