package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase

/** Host-owned identities and fixed use-case bindings for external shared-runtime clients. */
internal object HarnessSharedRuntimeBindings {
    /** Temporary legacy identity retained only through the RedactGuard cross-repository cutover. */
    val consoleApplicationId = ApplicationId("local-llm-console")

    /** Independent product identity used by daniele21/redactguard-android. */
    val redactGuardApplicationId = ApplicationId("redactguard")

    /** Legacy surface retained only until the in-repo Console/OMBRA consumer is removed. */
    val consoleUseCaseId = UseCaseId("console-inference-playground")

    /** Host-owned PII-analysis use case. Consumers never provide model identity. */
    val ombraUseCaseId = UseCaseId("document-pii-detection")
    val ombraDefaultPreset =
        InferencePresetRef(InferencePresetId("qwen35-json"), PHONE_INFERENCE_PRESET_VERSION)

    const val CONSOLE_RELEASE_PACKAGE = "io.github.daniele21.localllm.console"
    const val CONSOLE_DEBUG_PACKAGE = "io.github.daniele21.localllm.console.debug"
    const val CONSOLE_INTERNAL_PACKAGE = "io.github.daniele21.localllm.console.internal"
    const val REDACTGUARD_RELEASE_PACKAGE = "io.github.daniele21.redactguard"
    const val REDACTGUARD_DEBUG_PACKAGE = "io.github.daniele21.redactguard.debug"
    const val SR6_RELEASE_CONSUMER_PACKAGE = "io.github.daniele21.localllm.consumerfixture"

    val consoleUseCases: Set<UseCaseId> = setOf(consoleUseCaseId, ombraUseCaseId)
    val redactGuardUseCases: Set<UseCaseId> = setOf(ombraUseCaseId)
    val piiConsumerApplicationIds: Set<ApplicationId> = setOf(consoleApplicationId, redactGuardApplicationId)

    fun consolePackages(debugHost: Boolean): Set<String> = if (debugHost) {
        setOf(CONSOLE_DEBUG_PACKAGE, CONSOLE_INTERNAL_PACKAGE)
    } else {
        setOf(CONSOLE_RELEASE_PACKAGE)
    }

    fun redactGuardPackages(debugHost: Boolean): Set<String> = if (debugHost) {
        setOf(REDACTGUARD_DEBUG_PACKAGE)
    } else {
        setOf(REDACTGUARD_RELEASE_PACKAGE)
    }

    fun externalClientPackages(debugHost: Boolean): Set<String> = consolePackages(debugHost) +
        redactGuardPackages(debugHost) +
        if (debugHost) emptySet() else setOf(SR6_RELEASE_CONSUMER_PACKAGE)

    fun modelProfileId(useCaseId: String, catalogProfileKey: String): String? {
        require(catalogProfileKey.isNotBlank()) { "Catalog profile key must not be blank" }
        val suffix = when (useCaseId) {
            consoleUseCaseId.value -> CONSOLE_PROFILE_SUFFIX
            ombraUseCaseId.value -> OMBRA_PROFILE_SUFFIX
            else -> return null
        }
        return "$catalogProfileKey-$suffix"
    }

    /** Exact model-profile identity exposed by the current document-PII runtime environment. */
    fun ombraModelProfileId(catalogProfileKey: String): String = requireNotNull(modelProfileId(ombraUseCaseId.value, catalogProfileKey))

    fun resolveConsole(model: ImportedPhoneModel): ResolvedUseCase {
        val resolved =
            resolvedPhoneUseCase(
                model = model,
                maxOutputTokens = CONSOLE_DEFAULT_MAX_OUTPUT_TOKENS,
                useCaseValue = consoleUseCaseId.value,
                profileSuffix = CONSOLE_PROFILE_SUFFIX,
                contextSize = CONSOLE_CONTEXT_SIZE,
            )
        return resolved.copy(
            binding = AppModelBinding(
                applicationId = consoleApplicationId,
                useCaseId = consoleUseCaseId,
                useCaseProfileId = resolved.useCase.id,
            ),
        )
    }

    /**
     * Resolves the document-PII runtime for any application identity already authorized and assigned by the
     * control plane. Built-in and user-created applications share the same host-owned runtime contract.
     */
    fun resolveOmbra(model: ImportedPhoneModel, applicationId: ApplicationId = consoleApplicationId): ResolvedUseCase {
        val resolved =
            resolvedPhoneUseCase(
                model = model,
                maxOutputTokens = OMBRA_DEFAULT_MAX_OUTPUT_TOKENS,
                useCaseValue = ombraUseCaseId.value,
                profileSuffix = OMBRA_PROFILE_SUFFIX,
                contextSize = OMBRA_CONTEXT_SIZE,
            )
        val useCase =
            resolved.useCase.copy(
                outputMode = OutputMode.JSON_SCHEMA,
                defaultPreset = ombraDefaultPreset,
            )
        check(useCase.presets.any { it.ref == ombraDefaultPreset && OutputMode.JSON_SCHEMA in it.allowedOutputModes }) {
            "OMBRA default preset must support JSON_SCHEMA"
        }
        return resolved.copy(
            binding = AppModelBinding(
                applicationId = applicationId,
                useCaseId = ombraUseCaseId,
                useCaseProfileId = useCase.id,
            ),
            useCase = useCase,
        )
    }

    private const val CONSOLE_DEFAULT_MAX_OUTPUT_TOKENS = 512
    private const val CONSOLE_CONTEXT_SIZE = 4_096
    private const val OMBRA_DEFAULT_MAX_OUTPUT_TOKENS = 512
    private const val OMBRA_CONTEXT_SIZE = 4_096
    private const val CONSOLE_PROFILE_SUFFIX = "shared-console"
    private const val OMBRA_PROFILE_SUFFIX = "ombra-pii"
}
