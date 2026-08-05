package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import java.net.URI

/** Administrator-curated bootstrap releases for the phone-test catalog. */
object CuratedModelCatalog {
    val catalogId = CatalogId("android-local-llm-curated")
    const val REVISION: Long = 1

    private val phoneTestApplicationId = ApplicationId("play-internal-phone-test")
    private val phoneTestUseCases =
        setOf(
            "manual-inference-playground",
            "physical-device-validation",
        )

    val releases: List<CatalogModelRelease> =
        listOf(
            qwenThreePointFiveZeroPointEightB(),
            lfmTwoPointFiveOnePointTwoB(),
            smolLmTwoThreeHundredSixtyM(),
            qwenThreePointFiveTwoB(),
        )

    fun document(generatedAtEpochMs: Long, expiresAtEpochMs: Long): CatalogModelDocument = CatalogModelDocument(
        schemaVersion = 1,
        catalogId = catalogId,
        revision = REVISION,
        generatedAtEpochMs = generatedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        entries = releases,
    )

    private fun qwenThreePointFiveZeroPointEightB(): CatalogModelRelease = release(
        modelId = "qwen3.5-0.8b-instruct-q4-k-m",
        displayName = "Qwen 3.5 0.8B Instruct",
        description =
        "Modello compatto general purpose, adatto a classificazione, estrazione strutturata e generazione breve in locale.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/5aea8824cba95d22990acc6ea66c2c1909530650/Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
        sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
        sizeBytes = 532_517_120,
        fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
        architecture = "qwen35",
        minRamBytes = 3_000_000_000,
        recommendedRamBytes = 6_000_000_000,
        profileKey = "qwen3.5-0.8b-instruct-q4-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
        useCases =
        setOf(
            "transaction-classification",
            "intent-classification",
            "structured-extraction",
            "short-summarization",
            "short-text-generation",
        ),
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

    private fun qwenThreePointFiveTwoB(): CatalogModelRelease = release(
        modelId = "qwen3.5-2b-instruct-q4-k-m",
        displayName = "Qwen 3.5 2B Instruct",
        description =
        "Modello di fascia alta per dispositivi Android potenti, adatto a classificazione complessa, estrazione, sintesi e assistenti locali.",
        downloadUrl =
        "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/1c466474d208da1a7c4b8cb87ebcdac78f160e34/Qwen3.5-2B-Q4_K_M.gguf?download=true",
        sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
        sizeBytes = 1_280_835_840,
        fileName = "Qwen3.5-2B-Q4_K_M.gguf",
        architecture = "qwen35",
        minRamBytes = 6_000_000_000,
        recommendedRamBytes = 8_000_000_000,
        profileKey = "qwen3.5-2b-instruct-q4-k-m-ctx4096",
        licenseId = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF",
        useCases =
        setOf(
            "local-assistant",
            "complex-classification",
            "structured-extraction",
            "summarization",
            "local-rag",
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
            quantization = QUANTIZATION,
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
    private const val QUANTIZATION = "Q4_K_M"
    private const val LLAMA_CPP_BACKEND = "llama.cpp"
}
