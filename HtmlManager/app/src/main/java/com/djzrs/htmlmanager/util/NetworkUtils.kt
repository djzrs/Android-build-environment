package com.djzrs.htmlmanager.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /** 取第一个可用的局域网 IPv4，取不到返回 null。 */
    fun localIpv4Address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
