package com.codeonthego.tictactoe.net

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {

    const val DEFAULT_PORT: Int = 7060

    /** Picks the most likely LAN-facing IPv4 address, preferring WiFi/hotspot interfaces. */
    fun findLanIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { iface -> iface.inetAddresses.asSequence().map { addr -> iface to addr } }
            .mapNotNull { (iface, addr) -> (addr as? Inet4Address)?.let { iface to it } }
            .filterNot { (_, addr) -> addr.isLoopbackAddress || addr.isLinkLocalAddress }
            .maxByOrNull { (iface, addr) -> score(iface.name, addr) }
            ?.second?.hostAddress
    }.getOrNull()

    private fun score(interfaceName: String, address: Inet4Address): Int {
        var score = 0
        if (address.isSiteLocalAddress) score += 100
        score += when {
            interfaceName.startsWith("wlan", ignoreCase = true) -> 50
            interfaceName.startsWith("ap", ignoreCase = true) -> 40
            interfaceName.startsWith("eth", ignoreCase = true) -> 30
            else -> 0
        }
        return score
    }

    /** Parses "host" or "host:port" from what a user types into the join field. */
    fun parseAddress(input: String): Pair<String, Int>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        val host = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return when (parts.size) {
            1 -> host to DEFAULT_PORT
            2 -> host to (parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null)
            else -> null
        }
    }
}
