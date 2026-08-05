package io.github.daniele21.localllm.catalog

internal object Qwen3CuratedModelReleases {
    val releases: List<CatalogModelRelease> =
        listOf(
            qwenThreeEightBUdIqOneS(),
            qwenThreeEightBUdIqOneM(),
            qwenThreeEightBQTwoK(),
            qwenThreeEightBQThreeKM(),
            qwenThreeEightBQFourKM(),
            qwenThreeEightBQFiveKM(),
        )

    private fun qwenThreeEightBUdIqOneS(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-ud-iq1-s",
        displayName = "Qwen 3 8B UD-IQ1_S",
        description =
        "Quantizzazione Unsloth Dynamic ultra-compatta, inferiore ai 2 bit. Indicata principalmente per verificare la fattibilità di un modello 8B su Android, per benchmark estremi e per task brevi e fortemente vincolati. Può degradare sensibilmente ragionamento, instruction following e affidabilità degli output strutturati.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/14d6a0c6f786c9914abcc33c4c95c81439574020/Qwen3-8B-UD-IQ1_S.gguf?download=true",
        sha256 = "210ada67841bb71977b869095daf9ca70e93592eec9857144f7821ce0fce6f5d",
        sizeBytes = 2_275_379_008,
        fileName = "Qwen3-8B-UD-IQ1_S.gguf",
        architecture = "qwen3",
        quantization = "UD-IQ1_S",
        minRamBytes = 8_000_000_000,
        recommendedRamBytes = 12_000_000_000,
        profileKey = "qwen3-8b-ud-iq1-s-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "quantization-benchmark",
            "runtime-sanity",
            "constrained-classification",
            "short-text-generation",
        ),
    )

    private fun qwenThreeEightBUdIqOneM(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-ud-iq1-m",
        displayName = "Qwen 3 8B UD-IQ1_M",
        description =
        "Quantizzazione Unsloth Dynamic inferiore ai 2 bit, leggermente più grande e generalmente più affidabile della variante IQ1_S. Indicata per benchmark Android low-memory, classificazioni vincolate e generazione breve.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/14d6a0c6f786c9914abcc33c4c95c81439574020/Qwen3-8B-UD-IQ1_M.gguf?download=true",
        sha256 = "04d2d05a45283155fabfaa410731dcf20c90fd3c182b1fda5a1ad6abb034cbe6",
        sizeBytes = 2_396_489_536,
        fileName = "Qwen3-8B-UD-IQ1_M.gguf",
        architecture = "qwen3",
        quantization = "UD-IQ1_M",
        minRamBytes = 8_000_000_000,
        recommendedRamBytes = 12_000_000_000,
        profileKey = "qwen3-8b-ud-iq1-m-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "quantization-benchmark",
            "runtime-sanity",
            "constrained-classification",
            "structured-extraction",
            "short-text-generation",
        ),
    )

    private fun qwenThreeEightBQTwoK(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-q2-k",
        displayName = "Qwen 3 8B Q2_K",
        description =
        "Versione fortemente quantizzata di Qwen 3 8B, destinata a dispositivi Android high-end con memoria limitata. Riduce sensibilmente spazio e RAM, ma può degradare qualità, instruction following e affidabilità degli output strutturati.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/e78f43699bde6ce5ac39775d3c69abd49afd8f47/Qwen3-8B-Q2_K.gguf?download=true",
        sha256 = "7226e0183d31dca14d81c6f799ada2944be62160b8b7549a70254fba4124a5cf",
        sizeBytes = 3_281_733_440,
        fileName = "Qwen3-8B-Q2_K.gguf",
        architecture = "qwen3",
        quantization = "Q2_K",
        minRamBytes = 8_000_000_000,
        recommendedRamBytes = 12_000_000_000,
        profileKey = "qwen3-8b-q2-k-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "complex-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
            "quantization-benchmark",
        ),
    )

    private fun qwenThreeEightBQThreeKM(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-q3-k-m",
        displayName = "Qwen 3 8B Q3_K_M",
        description =
        "Versione a 3 bit di Qwen 3 8B, pensata come compromesso per eseguire un modello 8B su dispositivi Android con almeno 12 GB di RAM.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/e78f43699bde6ce5ac39775d3c69abd49afd8f47/Qwen3-8B-Q3_K_M.gguf?download=true",
        sha256 = "4924cf38a3b3c4b27ead5ccb93e27027f9418738506ac50a24a70dfe8581a007",
        sizeBytes = 4_124_161_856,
        fileName = "Qwen3-8B-Q3_K_M.gguf",
        architecture = "qwen3",
        quantization = "Q3_K_M",
        minRamBytes = 12_000_000_000,
        recommendedRamBytes = 16_000_000_000,
        profileKey = "qwen3-8b-q3-k-m-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "complex-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
            "tool-calling",
        ),
    )

    private fun qwenThreeEightBQFourKM(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-q4-k-m",
        displayName = "Qwen 3 8B Q4_K_M",
        description =
        "Quantizzazione consigliata per utilizzare Qwen 3 8B sui dispositivi Android più potenti, con un migliore equilibrio tra qualità e memoria rispetto alle versioni Q2 e Q3.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/14d6a0c6f786c9914abcc33c4c95c81439574020/Qwen3-8B-Q4_K_M.gguf?download=true",
        sha256 = "120307ba529eb2439d6c430d94104dabd578497bc7bfe7e322b5d9933b449bd4",
        sizeBytes = 5_027_784_512,
        fileName = "Qwen3-8B-Q4_K_M.gguf",
        architecture = "qwen3",
        quantization = "Q4_K_M",
        minRamBytes = 12_000_000_000,
        recommendedRamBytes = 16_000_000_000,
        profileKey = "qwen3-8b-q4-k-m-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "complex-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
            "tool-calling",
        ),
    )

    private fun qwenThreeEightBQFiveKM(): CatalogModelRelease = CuratedModelReleaseFactory.release(
        modelId = "qwen3-8b-q5-k-m",
        displayName = "Qwen 3 8B Q5_K_M",
        description =
        "Versione ad alta precisione di Qwen 3 8B, destinata a dispositivi Android con molta RAM e ai benchmark qualitativi rispetto alle quantizzazioni inferiori.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/9850e1ae6e6be7fdf4746b47938e92d01d68df9d/Qwen3-8B-Q5_K_M.gguf?download=true",
        sha256 = "159c694b93271e4edc1dc2a305b10cf981032c8f3035a7da00973312f0331504",
        sizeBytes = 5_851_113_280,
        fileName = "Qwen3-8B-Q5_K_M.gguf",
        architecture = "qwen3",
        quantization = "Q5_K_M",
        minRamBytes = 16_000_000_000,
        recommendedRamBytes = 24_000_000_000,
        profileKey = "qwen3-8b-q5-k-m-ctx2048",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF",
        useCases =
        setOf(
            "complex-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
            "tool-calling",
            "quantization-benchmark",
        ),
    )
}
