package io.github.daniele21.localllm.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ArtifactSourcePolicyTest {
    private val policy = AllowlistedHttpsSourcePolicy(
        allowedHosts = setOf(
            AllowedSourceHost("huggingface.co"),
            AllowedSourceHost("cdn.example.com", includeSubdomains = true),
        ),
    )

    @Test
    fun acceptsExactAndApprovedSubdomainHosts() {
        assertTrue(policy.validate(URI("https://huggingface.co/model.gguf?download=true")) is SourcePolicyResult.Allowed)
        assertTrue(policy.validate(URI("https://edge.cdn.example.com/model.gguf")) is SourcePolicyResult.Allowed)
    }

    @Test
    fun rejectsUnsafeSchemesCredentialsPortsAndHosts() {
        assertRejected("http://huggingface.co/model.gguf", SourcePolicyRejection.HTTPS_REQUIRED)
        assertRejected("https://user:secret@huggingface.co/model.gguf", SourcePolicyRejection.USER_INFO_REJECTED)
        assertRejected("https://huggingface.co:8443/model.gguf", SourcePolicyRejection.PORT_REJECTED)
        assertRejected("https://evil.example/model.gguf", SourcePolicyRejection.HOST_NOT_ALLOWED)
        assertRejected("https://127.0.0.1/model.gguf", SourcePolicyRejection.IP_LITERAL_REJECTED)
        assertRejected("https://localhost/model.gguf", SourcePolicyRejection.LOCAL_HOST_REJECTED)
        assertRejected("https://printer.local/model.gguf", SourcePolicyRejection.LOCAL_HOST_REJECTED)
    }

    @Test
    fun rejectionDetailsDoNotContainSignedUrlQuery() {
        val result = policy.validate(URI("https://evil.example/model.gguf?token=private"))
        assertTrue(result is SourcePolicyResult.Rejected)
        result as SourcePolicyResult.Rejected
        assertEquals("evil.example", result.detail)
        assertTrue("token" !in result.detail)
        assertTrue("private" !in result.detail)
    }

    private fun assertRejected(uri: String, expected: SourcePolicyRejection) {
        val result = policy.validate(URI(uri))
        assertTrue(result is SourcePolicyResult.Rejected)
        assertEquals(expected, (result as SourcePolicyResult.Rejected).reason)
    }
}
