package io.github.daniele21.localllm.download

import java.io.IOException
import java.net.InetAddress

sealed interface NetworkAddressPolicyResult {
    data object Allowed : NetworkAddressPolicyResult
    data class Rejected(val reason: NetworkAddressRejection) : NetworkAddressPolicyResult
}

enum class NetworkAddressRejection {
    NO_ADDRESSES,
    NON_PUBLIC_ADDRESS,
}

fun interface HostAddressResolver {
    @Throws(IOException::class)
    fun resolve(host: String): List<InetAddress>
}

fun interface ArtifactNetworkPolicy {
    @Throws(IOException::class)
    fun validate(host: String): NetworkAddressPolicyResult
}

class PublicNetworkAddressPolicy(
    private val resolver: HostAddressResolver = HostAddressResolver { host ->
        InetAddress.getAllByName(host).toList()
    },
) : ArtifactNetworkPolicy {
    override fun validate(host: String): NetworkAddressPolicyResult {
        val addresses = resolver.resolve(host)
        if (addresses.isEmpty()) {
            return NetworkAddressPolicyResult.Rejected(NetworkAddressRejection.NO_ADDRESSES)
        }
        if (addresses.any { !it.isPublicInternetAddress() }) {
            return NetworkAddressPolicyResult.Rejected(NetworkAddressRejection.NON_PUBLIC_ADDRESS)
        }
        return NetworkAddressPolicyResult.Allowed
    }

    private fun InetAddress.isPublicInternetAddress(): Boolean {
        val prohibitedAddressKind = listOf(
            isAnyLocalAddress,
            isLoopbackAddress,
            isLinkLocalAddress,
            isSiteLocalAddress,
            isMulticastAddress,
        ).any { it }
        if (prohibitedAddressKind) return false
        val octets = address.map(Byte::toInt).map { it and BYTE_MASK }
        return when (octets.size) {
            IPV4_BYTES -> isPublicIpv4(octets)
            IPV6_BYTES -> isPublicIpv6(octets)
            else -> false
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun isPublicIpv4(octets: List<Int>): Boolean {
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 0 -> false
            first == 10 -> false
            first == 100 && second in 64..127 -> false
            first == 127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 0 -> false
            first == 192 && second == 168 -> false
            first == 198 && second in 18..19 -> false
            first >= 224 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(octets: List<Int>): Boolean {
        val first = octets[0]
        return first and UNIQUE_LOCAL_MASK != UNIQUE_LOCAL_PREFIX
    }

    private companion object {
        const val BYTE_MASK = 0xff
        const val IPV4_BYTES = 4
        const val IPV6_BYTES = 16
        const val UNIQUE_LOCAL_MASK = 0xfe
        const val UNIQUE_LOCAL_PREFIX = 0xfc
    }
}
