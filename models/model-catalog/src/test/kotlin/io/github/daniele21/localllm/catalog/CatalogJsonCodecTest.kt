package io.github.daniele21.localllm.catalog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonCodecTest {
    private val codec = CatalogJsonCodec()

    @Test
    fun roundTripsCanonicalCatalog() {
        val document = validCatalogDocument(expiresAtEpochMs = 10_000)
        val encoded = codec.encode(document) as CatalogEncodeResult.Success
        val decoded = codec.decode(encoded.bytes) as CatalogDecodeResult.Success

        assertEquals(document, decoded.document)
        assertArrayEquals(encoded.bytes, (codec.encode(decoded.document) as CatalogEncodeResult.Success).bytes)
    }

    @Test
    fun encodesSetsInDeterministicOrder() {
        val release = validCatalogRelease { current ->
            current.copy(
                compatibility = current.compatibility.copy(
                    supportedAbis = setOf("x86_64", "arm64-v8a"),
                    supportedBackendIds = setOf("other", "llama.cpp"),
                ),
            )
        }
        val text = encode(validCatalogDocument(listOf(release), expiresAtEpochMs = 10_000))

        assertTrue(text.indexOf("arm64-v8a") < text.indexOf("x86_64"))
        assertTrue(text.indexOf("llama.cpp") < text.indexOf("other"))
    }

    @Test
    fun rejectsDuplicateJsonObjectFields() {
        val failure = codec.decode("{\"schemaVersion\":1,\"schemaVersion\":1}".encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.DUPLICATE_FIELD, failure.error.code)
    }

    @Test
    fun rejectsUnknownFields() {
        val changed = encode(validCatalogDocument(expiresAtEpochMs = 10_000)).replaceFirst("{", "{\"unknown\":true,")
        val failure = codec.decode(changed.encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.UNKNOWN_FIELD, failure.error.code)
        assertEquals("$.unknown", failure.error.path)
    }

    @Test
    fun rejectsMissingFields() {
        val changed = encode(validCatalogDocument(expiresAtEpochMs = 10_000))
            .replace("\"catalogId\":\"harness-models\",", "")
        val failure = codec.decode(changed.encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.MISSING_FIELD, failure.error.code)
    }

    @Test
    fun rejectsDocumentAboveConfiguredLimit() {
        val failure = CatalogJsonCodec(maxDocumentBytes = 8)
            .decode("{\"value\":123456789}".encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.DOCUMENT_TOO_LARGE, failure.error.code)
    }

    @Test
    fun rejectsFractionalIntegerFields() {
        val changed = encode(validCatalogDocument(expiresAtEpochMs = 10_000))
            .replace("\"revision\":7", "\"revision\":7.5")
        val failure = codec.decode(changed.encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.INVALID_NUMBER, failure.error.code)
    }

    @Test
    fun rejectsInvalidUris() {
        val changed = encode(validCatalogDocument(expiresAtEpochMs = 10_000))
            .replace("https://models.example.test/qwen-small.gguf", "not a uri")
        val failure = codec.decode(changed.encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.INVALID_URI, failure.error.code)
    }

    @Test
    fun rejectsDuplicateSetValues() {
        val changed = encode(validCatalogDocument(expiresAtEpochMs = 10_000))
            .replace("[\"arm64-v8a\"]", "[\"arm64-v8a\",\"arm64-v8a\"]")
        val failure = codec.decode(changed.encodeToByteArray()) as CatalogDecodeResult.Failure

        assertEquals(CatalogCodecErrorCode.DUPLICATE_VALUE, failure.error.code)
    }

    @Test
    fun rejectsUnpairedUnicodeDuringEncoding() {
        val release = validCatalogRelease { current -> current.copy(description = "broken-\uD800") }
        val failure = codec.encode(
            validCatalogDocument(listOf(release), expiresAtEpochMs = 10_000),
        ) as CatalogEncodeResult.Failure

        assertEquals(CatalogCodecErrorCode.INVALID_UNICODE, failure.error.code)
    }

    private fun encode(document: CatalogModelDocument): String =
        (codec.encode(document) as CatalogEncodeResult.Success).bytes.decodeToString()
}
