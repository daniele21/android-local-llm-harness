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
    val consoleApplicationId = ApplicationId("local-llm-console")

    /** Legacy surface retained only until OMB-7 removes the raw Console playground. */
    val consoleUseCaseId = UseCaseId("console-inference-playground")

    /** OMB-4 host-owned PII-analysis use case. Consumers never provide model identity. */
    val ombraUseCaseId = UseCaseId("document-pii-detection")
    val ombraDefaultPreset =
        InferencePresetRef(InferencePresetId("qwen35-json"), PHONE_INFERENCE_PRESET_VERSION)

    const val CONSOLE_RELEASE_PACKAGE = "io.github.daniele21.localllm.console"
    const val CONSOLE_DEBUG_PACKAGE = "io.github.daniele21.localllm.console.debug"
    const val CONSOLE_INTERNAL_PACKAGE = "io.github.daniele21.localllm.console.internal"
    const val SR6_RELEASE_CONSUMER_PACKAGE = "io.github.daniele21.localllm.consumerfixture"

    val consoleUseCases: Set<UseCaseId> = setOf(consoleUseCaseId, ombraUseCaseId)

    fun consolePackages(debugHost: Boolean): Set<String> = if (debugHost) {
        setOf(CONSOLE_DEBUG_PACKAGE, CONSOLE_INTERNAL_PACKAGE)
    } else {
        setOf(CONSOLE_RELEASE_PACKAGE)
    }

    fun externalClientPackages(debugHost: Boolean): Set<String> =
        consolePackages(debugHost) + if (debugHost) emptySet() else setOf(SR6_RELEASE_CONSUMER_PACKAGE)

    fun resolveConsole(model: ImportedPhoneModel): ResolvedUseCase {
        val resolved =
            resolvedPhoneUseCase(
                model = model,
                maxOutputTokens = CONSOLE_DEFAULT_MAX_OUTPUT_TOKENS,
                useCaseValue = consoleUseCaseId.value,
                profileSuffix = "shared-console",
                contextSize = CONSOLE_CONTEXT_SIZE,
            )
        return resolved.copy(
            binding =
                AppModelBinding(
                    applicationId = consoleApplicationId,
                    useCaseId = consoleUseCaseId,
                    useCaseProfileId = resolved.useCase.id,
                ),
        )
    }

    fun resolveOmbra(model: ImportedPhoneModel): ResolvedUseCase {
        val resolved =
            resolvedPhoneUseCase(
                model = model,
                maxOutputTokens = OMBRA_DEFAULT_MAX_OUTPUT_TOKENS,
                useCaseValue = ombraUseCaseId.value,
                profileSuffix = "ombra-pii",
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
            binding =
                AppModelBinding(
                    applicationId = consoleApplicationId,
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
}
