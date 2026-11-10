package io.github.pauloestefani.javaworld.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

enum class AddressKind { WIFI, HOTSPOT, CABLE, VPN, IPV6 }

data class NetAddress(val kind: AddressKind, val label: String, val address: String, val hint: String)

object NetInfo {
    fun addresses(): List<NetAddress> {
        val out = mutableListOf<NetAddress>()
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        for (ni in ifaces) {
            val up = runCatching { ni.isUp && !ni.isLoopback }.getOrDefault(false)
            if (!up) continue
            val name = ni.name.lowercase()
            for (addr in ni.inetAddresses.toList()) {
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                val host = addr.hostAddress?.substringBefore('%') ?: continue
                if (addr is Inet4Address) {
                    val kind = when {
                        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") ||
                            (name.startsWith("wlan") && host.startsWith("192.168.43.")) -> AddressKind.HOTSPOT
                        name.startsWith("wlan") -> AddressKind.WIFI
                        name.startsWith("eth") -> AddressKind.CABLE
                        name.startsWith("tun") || name.startsWith("tailscale") || name.startsWith("zt") -> AddressKind.VPN
                        else -> null
                    } ?: continue
                    out += when (kind) {
                        AddressKind.WIFI -> NetAddress(kind, "Mesmo Wi-Fi", host, "Para quem está conectado no mesmo Wi-Fi que você")
                        AddressKind.HOTSPOT -> NetAddress(kind, "Roteador do celular", host, "Para quem conectou no hotspot do seu celular")
                        AddressKind.CABLE -> NetAddress(kind, "Rede cabeada", host, "Para quem está na mesma rede")
                        AddressKind.VPN -> NetAddress(kind, "Rede virtual (VPN)", host, "Tailscale/ZeroTier: para quem está na mesma rede virtual")
                        AddressKind.IPV6 -> error("unreachable")
                    }
                } else if (addr is Inet6Address) {
                    val first = addr.address[0].toInt() and 0xff
                    val isGlobal = (first and 0xe0) == 0x20 // 2000::/3
                    if (isGlobal && !name.startsWith("tun")) {
                        out += NetAddress(
                            AddressKind.IPV6, "IPv6 (experimental)", "[$host]",
                            "Só funciona se a internet do seu amigo também tiver IPv6 e o provedor não bloquear"
                        )
                    }
                }
            }
        }
        return out.distinctBy { it.address }.sortedBy { it.kind.ordinal }
    }
}
