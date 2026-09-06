package io.github.daniele21.localllm.catalog

/** Curated Unsloth Qwen3.5 4B artifacts restricted to the repository's 4-bit product envelope. */
internal object Qwen35FourBitModelReleases {
    private const val SOURCE_REVISION = "e87f176479d0855a907a41277aca2f8ee7a09523"
    private const val SOURCE_URL = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF"

    val releases: List<CatalogModelRelease> =
        listOf(
            release(
                idSuffix = "ud-q4-k-xl",
                quantization = "UD-Q4_K_XL",
                sha256 = "b252c5610a42ca82d20fe2a12813e9d069eed89292907e26c783eeb0bc961bc7",
                sizeBytes = 2_912_109_728,
                recommended = true,
            ),
            release(
                idSuffix = "q4-k-m",
                quantization = "Q4_K_M",
                sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
                sizeBytes = 2_740_937_888,
            ),
            release(
                idSuffix = "q4-k-s",
                quantization = "Q4_K_S",
                sha256 = "27caeb0e4b999d92ce0a9fdbdd1a7ba5112908d9de125645883732274be2ea77",
                sizeBytes = 2_590_430_368,
            ),
            release(
                idSuffix = "iq4-xs",
                quantization = "IQ4_XS",
                sha256 = "658a9e7e406deb06d0179755e3c14f6a82915a4be4962a2f92a64d948d2e572f",
                sizeBytes = 2_477_053_088,
            ),
            release(
                idSuffix = "iq4-nl",
                quantization = "IQ4_NL",
                sha256 = "ff5c3e9740a5aa53f04fdf3b0b8cc75da556bf8948cdb19d61c512d3a43465d9",
                sizeBytes = 2_579_944_608,
            ),
            release(
                idSuffix = "q4-0",
                quantization = "Q4_0",
                sha256 = "298fcb5fe7a77ccc79745ae24751560c5ac56874caff4bb39b1f2055bd72b8bb",
                sizeBytes = 2_583_221_408,
            ),
            release(
                idSuffix = "q4-1",
                quantization = "Q4_1",
                sha256 = "af1fa652b5c78980b105a2ffef954bfa724bc4d69d2d44463e27c4f3c2953bbd",
                sizeBytes = 2_784_416_928,
            ),
        )

    private fun release(
        idSuffix: String,
        quantization: String,
        sha256: String,
        sizeBytes: Long,
        recommended: Boolean = false,
    ): CatalogModelRelease {
        val fileName = "Qwen3.5-4B-$quantization.gguf"
        val recommendation =
            if (recommended) {
                " Variante 4-bit raccomandata da Unsloth negli esempi llama.cpp e preferita per la validazione Harnex."
            } else {
                " Variante 4-bit alternativa per confrontare qualità, memoria e velocità sul dispositivo."
            }
        return CuratedModelReleaseFactory.release(
            modelId = "qwen35-4b-$idSuffix",
            displayName = "Qwen 3.5 4B $quantization",
            description = "Qwen3.5 4B di Unsloth per inferenza Android locale.$recommendation",
            downloadUrl = "$SOURCE_URL/resolve/$SOURCE_REVISION/$fileName?download=true",
            sha256 = sha256,
            sizeBytes = sizeBytes,
            fileName = fileName,
            architecture = "qwen35",
            quantization = quantization,
            minRamBytes = 8_000_000_000,
            recommendedRamBytes = 12_000_000_000,
            profileKey = "qwen35-4b-$idSuffix-ctx4096",
            licenseId = "Apache-2.0",
            sourceUrl = SOURCE_URL,
            useCases =
            setOf(
                "transaction-classification",
                "structured-extraction",
                "summarization",
                "local-assistant",
                "local-rag",
            ),
        )
    }
}
