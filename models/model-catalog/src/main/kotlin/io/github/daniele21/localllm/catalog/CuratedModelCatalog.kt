package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import java.net.URI

/** Administrator-curated bootstrap releases for the phone-test catalog. */
object CuratedModelCatalog {
    val catalogId = CatalogId("android-local-llm-curated")
    const val REVISION: Long = 2

    private val phoneTestApplicationId = ApplicationId("play-internal-phone-test")
    private val phoneTestUseCases =
        setOf(
            "manual-inference-playground",
            "physical-device-validation",
        )

    val releases: List<CatalogModelRelease> =
        listOf(
            lfmTwoPointFiveOnePointTwoB(),
            smolLmTwoThreeHundredSixtyM(),
            qwenThreeEightBUdIqOneS(),
            qwenThreeEightBUdIqOneM(),
            qwenThreeEightBQTwoK(),
            qwenThreeEightBQThreeKM(),
            qwenThreeEightBQFourKM(),
            qwenThreeEightBQFiveKM(),
            qwenThreePointFiveZeroPointEightBQFourKM(),
            qwenThreePointFiveZeroPointEightBQFiveKM(),
            qwenThreePointFiveZeroPointEightBQEightZero(),
            qwenThreePointFiveTwoBQFourKM(),
            qwenThreePointFiveTwoBQFiveKM(),
            qwenThreePointFiveFourBQFourKM(),
            qwenThreePointFiveFourBQFiveKM(),
        )

    fun document(generatedAtEpochMs: Long, expiresAtEpochMs: Long): CatalogModelDocument = CatalogModelDocument(
        schemaVersion = 1,
        catalogId = catalogId,
        revision = REVISION,
        generatedAtEpochMs = generatedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        entries = releases,
    )

    private fun lfmTwoPointFiveOnePointTwoB(): CatalogModelRelease = release(
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

    private fun smolLmTwoThreeHundredSixtyM(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBUdIqOneS(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBUdIqOneM(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBQTwoK(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBQThreeKM(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBQFourKM(): CatalogModelRelease = release(
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

    private fun qwenThreeEightBQFiveKM(): CatalogModelRelease = release(
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

    private fun qwenThreePointFiveZeroPointEightBQFourKM(): CatalogModelRelease = release(
        modelId = "qwen35-08b-q4-k-m",
        displayName = "Qwen 3.5 0.8B Q4_K_M",
        description =
        "Modello Qwen ultra-compatto, consigliato come default leggero per classificazione, estrazione strutturata e generazione breve.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/5aea8824cba95d22990acc6ea66c2c1909530650/Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
        sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
        sizeBytes = 532_517_120,
        fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q4_K_M",
        minRamBytes = 3_000_000_000,
        recommendedRamBytes = 4_000_000_000,
        profileKey = "qwen35-08b-q4-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
        useCases =
        setOf(
            "transaction-classification",
            "intent-classification",
            "structured-extraction",
            "short-summarization",
        ),
    )

    private fun qwenThreePointFiveZeroPointEightBQFiveKM(): CatalogModelRelease = release(
        modelId = "qwen35-08b-q5-k-m",
        displayName = "Qwen 3.5 0.8B Q5_K_M",
        description =
        "Versione a maggiore precisione del modello Qwen 0.8B, indicata quando l'affidabilità dell'output è più importante del minimo consumo di memoria.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/c61c105fd9f6919021a9f13e7d98f11edfddc41e/Qwen3.5-0.8B-Q5_K_M.gguf?download=true",
        sha256 = "c3ef5827b322c4be08a3a26ce424460d8b37daf593356c2cc1f4e40a7ab0581b",
        sizeBytes = 590_057_728,
        fileName = "Qwen3.5-0.8B-Q5_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q5_K_M",
        minRamBytes = 3_000_000_000,
        recommendedRamBytes = 4_000_000_000,
        profileKey = "qwen35-08b-q5-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
        useCases =
        setOf(
            "transaction-classification",
            "intent-classification",
            "structured-extraction",
            "short-summarization",
        ),
    )

    private fun qwenThreePointFiveZeroPointEightBQEightZero(): CatalogModelRelease = release(
        modelId = "qwen35-08b-q8-0",
        displayName = "Qwen 3.5 0.8B Q8_0",
        description =
        "Versione ad alta precisione del modello Qwen 0.8B, utile come riferimento qualitativo nei benchmark delle quantizzazioni inferiori.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/e524882462b3f2a9fe83be967c654c4322abb2f6/Qwen3.5-0.8B-Q8_0.gguf?download=true",
        sha256 = "0ad885ffd4bb022fc4f0d33a3308fa108ef8613159d3b3a67e23abca056b7a6c",
        sizeBytes = 811_843_840,
        fileName = "Qwen3.5-0.8B-Q8_0.gguf",
        architecture = "qwen35",
        quantization = "Q8_0",
        minRamBytes = 4_000_000_000,
        recommendedRamBytes = 6_000_000_000,
        profileKey = "qwen35-08b-q8-0-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
        useCases =
        setOf(
            "quantization-benchmark",
            "transaction-classification",
            "structured-extraction",
            "short-summarization",
        ),
    )

    private fun qwenThreePointFiveTwoBQFourKM(): CatalogModelRelease = release(
        modelId = "qwen35-2b-q4-k-m",
        displayName = "Qwen 3.5 2B Q4_K_M",
        description =
        "Modello Qwen di fascia intermedia, consigliato come default di qualità sui dispositivi Android con almeno 6-8 GB di RAM.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/802854bfd388ed92748de119df31327962811548/Qwen3.5-2B-Q4_K_M.gguf?download=true",
        sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
        sizeBytes = 1_280_835_840,
        fileName = "Qwen3.5-2B-Q4_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q4_K_M",
        minRamBytes = 6_000_000_000,
        recommendedRamBytes = 8_000_000_000,
        profileKey = "qwen35-2b-q4-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF",
        useCases =
        setOf(
            "transaction-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
        ),
    )

    private fun qwenThreePointFiveTwoBQFiveKM(): CatalogModelRelease = release(
        modelId = "qwen35-2b-q5-k-m",
        displayName = "Qwen 3.5 2B Q5_K_M",
        description =
        "Versione a maggiore precisione del modello Qwen 2B, indicata per estrazione strutturata e classificazioni in cui è richiesta maggiore affidabilità.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/7938c998e44eb9f079e01a966c2d1431c6b8ef16/Qwen3.5-2B-Q5_K_M.gguf?download=true",
        sha256 = "1885b3a9195f8cc09da9a7a7a75afdc1e8d5cbf9fc4a499c3961dddea37098ac",
        sizeBytes = 1_435_238_656,
        fileName = "Qwen3.5-2B-Q5_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q5_K_M",
        minRamBytes = 6_000_000_000,
        recommendedRamBytes = 8_000_000_000,
        profileKey = "qwen35-2b-q5-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF",
        useCases =
        setOf(
            "transaction-classification",
            "structured-extraction",
            "summarization",
            "local-assistant",
            "local-rag",
        ),
    )

    private fun qwenThreePointFiveFourBQFourKM(): CatalogModelRelease = release(
        modelId = "qwen35-4b-q4-k-m",
        displayName = "Qwen 3.5 4B Q4_K_M",
        description =
        "Modello Qwen ad alta capacità per dispositivi Android high-end, destinato a estrazioni complesse, assistenti locali e piccoli workflow agentici.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/720bb031aae5488eae5d6a78768e6d826662b2ae/Qwen3.5-4B-Q4_K_M.gguf?download=true",
        sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
        sizeBytes = 2_740_937_888,
        fileName = "Qwen3.5-4B-Q4_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q4_K_M",
        minRamBytes = 8_000_000_000,
        recommendedRamBytes = 12_000_000_000,
        profileKey = "qwen35-4b-q4-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF",
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

    private fun qwenThreePointFiveFourBQFiveKM(): CatalogModelRelease = release(
        modelId = "qwen35-4b-q5-k-m",
        displayName = "Qwen 3.5 4B Q5_K_M",
        description =
        "Versione ad alta precisione del modello Qwen 4B, destinata ai dispositivi Android più potenti e ai benchmark di qualità.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/c0a86ba65c426cbe870bf26c2a963ac53cb083ba/Qwen3.5-4B-Q5_K_M.gguf?download=true",
        sha256 = "8814232b85594dcd46c50e5b8b29324a7efe9e746edbe8a3d1df3d3fce7aad39",
        sizeBytes = 3_143_656_608,
        fileName = "Qwen3.5-4B-Q5_K_M.gguf",
        architecture = "qwen35",
        quantization = "Q5_K_M",
        minRamBytes = 12_000_000_000,
        recommendedRamBytes = 16_000_000_000,
        profileKey = "qwen35-4b-q5-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF",
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

    @Suppress("LongParameterList")
    private fun release(
        modelId: String,
        displayName: String,
        description: String,
        downloadUrl: String,
        sha256: String,
        sizeBytes: Long,
        fileName: String,
        architecture: String,
        quantization: String,
        minRamBytes: Long,
        recommendedRamBytes: Long,
        profileKey: String,
        licenseId: String,
        sourceUrl: String,
        useCases: Set<String>,
    ): CatalogModelRelease = CatalogModelRelease(
        id =
        CatalogReleaseId(
            modelId = CatalogModelId(modelId),
            version = CatalogModelVersion(MODEL_VERSION),
        ),
        displayName = displayName,
        description = description,
        artifact =
        CatalogGgufArtifact(
            digest = ModelDigest(sha256),
            sizeBytes = sizeBytes,
            downloadUri = URI(downloadUrl),
            architecture = architecture,
            quantization = quantization,
            fileName = fileName,
        ),
        compatibility =
        CatalogCompatibility(
            minSdk = MIN_ANDROID_API,
            supportedAbis = setOf(ARM64_ABI),
            minRamBytes = minRamBytes,
            recommendedRamBytes = recommendedRamBytes,
            minFreeStorageBytes = 0,
            supportedBackendIds = setOf(LLAMA_CPP_BACKEND),
        ),
        availability = CatalogAvailability.CANDIDATE,
        allowedTargets =
        (useCases + phoneTestUseCases).mapTo(linkedSetOf()) { useCase ->
            CatalogTarget(phoneTestApplicationId, UseCaseId(useCase))
        },
        profileKey = ModelProfileKey(profileKey),
        license =
        CatalogLicense(
            id = licenseId,
            displayName = licenseId,
            sourceUri = URI(sourceUrl),
        ),
    )

    private const val MODEL_VERSION = "1.0.0"
    private const val MIN_ANDROID_API = 26
    private const val ARM64_ABI = "arm64-v8a"
    private const val LLAMA_CPP_BACKEND = "llama.cpp"
}
