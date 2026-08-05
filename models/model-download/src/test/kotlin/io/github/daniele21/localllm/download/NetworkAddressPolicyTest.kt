package io.github.daniele21.localllm.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkAddressPolicyTest {
    @Test
    fun acceptsOnlyPublicResolvedAddresses() {
        val policy = policy("8.8.8.8", "1.1.1.1")

        assertEquals(NetworkAddressPolicyResult.Allowed, policy.validate("cdn.example.com"))
    }

    @Test
    fun rejectsPrivateLoopbackLinkLocalAndUniqueLocalAddresses() {
        listOf(
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.10.10",
            "100.64.0.1",
            "fc00::1",
            "fe80::1",
            "::1",
        ).forEach { address ->
            val result = policy(address).validate("cdn.example.com")
            assertTrue(result is NetworkAddressPolicyResult.Rejected)
            assertEquals(
                NetworkAddressRejection.NON_PUBLIC_ADDRESS,
                (result as NetworkAddressPolicyResult.Rejected).reason,
            )
        }
    }

    @Test
    fun rejectsMixedPublicAndPrivateResolution() {
        val result = policy("8.8.8.8", "127.0.0.1").validate("cdn.example.com")

        assertTrue(result is NetworkAddressPolicyResult.Rejected)
        assertEquals(
            NetworkAddressRejection.NON_PUBLIC_ADDRESS,
            (result as NetworkAddressPolicyResult.Rejected).reason,
        )
    }

    @Test
    fun rejectsEmptyResolution() {
        val result = PublicNetworkAddressPolicy(HostAddressResolver { emptyList() })
            .validate("cdn.example.com")

        assertEquals(
            NetworkAddressPolicyResult.Rejected(NetworkAddressRejection.NO_ADDRESSES),
            result,
        )
    }

    private fun policy(vararg addresses: String): PublicNetworkAddressPolicy = PublicNetworkAddressPolicy(
        HostAddressResolver {
            addresses.map(InetAddress::getByName)
        },
    )
}
