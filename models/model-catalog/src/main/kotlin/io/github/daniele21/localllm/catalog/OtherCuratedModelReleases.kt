package io.github.daniele21.localllm.catalog

internal object OtherCuratedModelReleases {
    val releases: List<CatalogModelRelease> =
        listOf(
            lfmTwoPointFiveOnePointTwoB(),
            smolLmTwoThreeHundredSixtyM(),
        )

    private fun lfmTwoPointFiveOnePointTwoB(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "lfm2.5-1.2b-instruct-q4-k-m",
        displayName = "LFM 2.5 1.2B Instruct",
        description =
        "Modello mobile-first per assistenti locali, classificazione, sintesi, estrazione strutturata e piccoli workflow RAG.",
        downloadUrl =
        "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf?download=true",
        sha256 = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5",
        sizeBytes = 730_895_168,
        fileName = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        architecture = "lfm2",
        quantization = "Q4_K_M",
        minRamBytes = 4_000_000_000,
        recommendedRamBytes = 6_000_000_000,
        profileKey = "lfm2.5-1.2b-instruct-q4-k-m-ctx4096",
        licenseId = "LFM-1.0",
        sourceUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
        useCases =
        setOf(
            "local-assistant",
            "text-classification",
            "structured-extraction",
            "summarization",
            "local-rag",
        ),
    )

    private fun smolLmTwoThreeHundredSixtyM(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "smollm2-360m-instruct-q4-k-m",
        displayName = "SmolLM2 360M Instruct",
        description =
        "Modello ultra-compatto per sanity test, benchmark del runtime, classificazioni semplici e generazione breve.",
        downloadUrl =
        "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf?download=true",
        sha256 = "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2",
        sizeBytes = 270_590_880,
        fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
        architecture = "llama",
        quantization = "Q4_K_M",
        minRamBytes = 2_000_000_000,
        recommendedRamBytes = 4_000_000_000,
        profileKey = "smollm2-360m-instruct-q4-k-m-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF",
        useCases =
        setOf(
            "runtime-sanity",
            "runtime-benchmark",
            "simple-classification",
            "intent-detection",
            "short-text-generation",
        ),
    )
}
