package com.antinet.vpn.engine

import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {
    private val server = Profile(
        id = "main",
        name = "main",
        protocol = ProxyProtocol.VLESS,
        server = "example.com",
        port = 443,
        password = "11111111-1111-1111-1111-111111111111",
        security = "tls",
        sni = "example.com",
    )

    private val hop = server.copy(id = "hop", name = "hop", server = "hop.example.com")

    private fun outbounds(json: String): JSONArray = JSONObject(json).getJSONArray("outbounds")

    private fun outbound(json: String, tag: String): JSONObject? {
        val list = outbounds(json)
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            if (item.optString("tag") == tag) return item
        }
        return null
    }

    private fun dialer(outbound: JSONObject?): String =
        outbound?.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.optString("dialerProxy").orEmpty()

    @Test
    fun buildsTunInboundAndDirectOutbound() {
        val settings = AppSettings(mtu = 1400)
        val json = XrayConfigBuilder(settings, listOf(server)).build("main").json
        val inbound = JSONObject(json).getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("tag"))
        assertEquals("tun", inbound.getString("protocol"))
        assertEquals(1400, inbound.getJSONObject("settings").getInt("MTU"))

        val direct = outbound(json, "direct")
        assertNotNull(direct)
        assertEquals("freedom", direct!!.getString("protocol"))
        assertNull(outbound(json, "aggregation"))
        assertEquals("", dialer(outbound(json, "proxy")))
    }

    @Test
    fun chainUsesDialerProxyForNextHop() {
        val chained = server.copy(chainIds = listOf("hop"))
        val json = XrayConfigBuilder(AppSettings(), listOf(chained, hop)).build("main").json

        assertEquals("proxy-c1", dialer(outbound(json, "proxy")))
        assertEquals("", dialer(outbound(json, "proxy-c1")))
    }

    @Test
    fun groupUsesBalancerWithEntryTags() {
        val group = Profile(
            id = "group",
            name = "group",
            protocol = ProxyProtocol.GROUP,
            groupIds = listOf("main", "hop"),
        )
        val json = XrayConfigBuilder(AppSettings(), listOf(group, server, hop)).build("group").json
        val routing = JSONObject(json).getJSONObject("routing")
        assertTrue(routing.has("balancers"))
    }

    @Test
    fun mtuIsClampedByNormalization() {
        assertEquals(9000, AppSettings(mtu = 20000).normalized().mtu)
        assertEquals(1200, AppSettings(mtu = 100).normalized().mtu)
    }

    @Test
    fun customRulesAreAppended() {
        val rules = """[{"type":"field","domain":["example.org"],"outboundTag":"direct"}]"""
        val json = XrayConfigBuilder(AppSettings(customRules = rules), listOf(server)).build("main").json
        val array = JSONObject(json).getJSONObject("routing").getJSONArray("rules")
        val last = array.getJSONObject(array.length() - 1)
        assertEquals("example.org", last.getJSONArray("domain").getString(0))
    }
}
