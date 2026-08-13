package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ResolvedUseCase

/** Host-owned identities and fixed use-case binding for external shared-runtime proof clients. */
internal object HarnessSharedRuntimeBindings {
    val consoleApplicationId = ApplicationId("local-llm-console")
    val consoleUseCaseId = UseCaseId("console-inference-playground")

    const val CONSOLE_RELEASE_PACKAGE = "io.github.daniele21.localllm.console"
    const val CONSOLE_DEBUG_PACKAGE = "io.github.daniele21.localllm.console.debug"
    const val CONSOLE_INTERNAL_PACKAGE = "io.github.daniele21.localllm.console.internal"
    const val SR6_RELEASE_CONSUMER_PACKAGE = "io.github.daniele21.localllm.consumerfixture"

    fun consolePackages(debugHost: Boolean): Set<String> = if (debugHost) {
        setOf(CONSOLE_DEBUG_PACKAGE, CONSOLE_INTERNAL_PACKAGE)
    } else {
        setOf(CONSOLE_RELEASE_PACKAGE)
    }

    fun externalClientPackages(debugHost: Boolean): Set<String> =
        consolePackages(debugHost) + if (debugHost) emptySet() else setOf(SR6_RELEASE_CONSUMER_PACKAGE)

    fun resolveConsole(model: ImportedPhoneModel): ResolvedUseCase {
        val resolved = resolvedPhoneUseCase(
            model = model,
            maxOutputTokens = CONSOLE_DEFAULT_MAX_OUTPUT_TOKENS,
            useCaseValue = consoleUseCaseId.value,
            profileSuffix = "shared-console",
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

    private const val CONSOLE_DEFAULT_MAX_OUTPUT_TOKENS = 512
    private const val CONSOLE_CONTEXT_SIZE = 4_096
}
