package com.antinet.vpn.ui.screens

import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerValidationTest {
    private val valid = Profile(
        protocol = ProxyProtocol.VLESS,
        server = "example.com",
        port = 443,
        password = "uuid",
    )

    @Test
    fun validProfilePasses() = assertEquals("", validate(valid))

    @Test
    fun blankAddressFails() = assertTrue(validate(valid.copy(server = "")).isNotEmpty())

    @Test
    fun badPortFails() = assertTrue(validate(valid.copy(port = 0)).isNotEmpty())

    @Test
    fun realityNeedsPublicKey() = assertTrue(validate(valid.copy(security = "reality")).isNotEmpty())

    @Test
    fun groupNeedsTwoServers() {
        val group = Profile(protocol = ProxyProtocol.GROUP, groupIds = listOf("a"))
        assertTrue(validate(group).isNotEmpty())
        assertEquals("", validate(group.copy(groupIds = listOf("a", "b"))))
    }

    @Test
    fun socksAllowsEmptyCredentials() {
        assertEquals("", validate(valid.copy(protocol = ProxyProtocol.SOCKS, password = "")))
    }
}
