package com.anjo.validation

object IpValidation {
    fun isValidPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val octets = try {
            parts.map { it.toInt() }
        } catch (_: NumberFormatException) {
            return false
        }
        if (octets.any { it < 0 || it > 255 }) return false

        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }
}
