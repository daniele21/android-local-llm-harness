package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId

enum class ApplicationRegistrationState {
    PENDING,
    AUTHORIZED,
    DISABLED,
    SIGNATURE_CHANGED,
    UNAVAILABLE,
}

data class RegisteredApplication(
    val applicationId: ApplicationId,
    val packageName: String,
    val signerSha256: String,
    val displayName: String,
    val state: ApplicationRegistrationState,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
) {
    init {
        require(packageName.isNotBlank()) { "Application package name must not be blank" }
        require(displayName.isNotBlank()) { "Application display name must not be blank" }
        require(SHA256_PATTERN.matches(signerSha256)) { "Application signer identity must be SHA-256" }
        require(firstSeenAtEpochMs >= 0) { "Application first-seen timestamp must not be negative" }
        require(lastSeenAtEpochMs >= firstSeenAtEpochMs) {
            "Application last-seen timestamp must not precede first-seen"
        }
    }
}

enum class UseCaseDefinitionState {
    DRAFT,
    ACTIVE,
    DISABLED,
}

data class UseCaseRequirements(
    val outputMode: OutputMode,
    val sessionKind: SessionKind,
    val reasoningSupported: Boolean,
    val minimumContextTokens: Int,
    val maxInputCharacters: Int? = null,
    val maxJsonSchemaCharacters: Int? = null,
) {
    init {
        require(minimumContextTokens > 0) { "Minimum context tokens must be positive" }
        require(maxInputCharacters == null || maxInputCharacters > 0) { "Maximum input characters must be positive" }
        require(maxJsonSchemaCharacters == null || maxJsonSchemaCharacters > 0) {
            "Maximum JSON-schema characters must be positive"
        }
        require(outputMode == OutputMode.JSON_SCHEMA || maxJsonSchemaCharacters == null) {
            "JSON-schema character limit requires JSON_SCHEMA output mode"
        }
    }
}

data class UseCaseDefinition(
    val useCaseId: UseCaseId,
    val displayName: String,
    val description: String,
    val requirements: UseCaseRequirements,
    val state: UseCaseDefinitionState,
    val revision: Int,
) {
    init {
        require(displayName.isNotBlank()) { "Use-case display name must not be blank" }
        require(description.isNotBlank()) { "Use-case description must not be blank" }
        require(revision > 0) { "Use-case revision must be positive" }
    }
}

enum class PresetCreationSource {
    SUGGESTED,
    CUSTOM,
}

enum class PresetLifecycleState {
    DRAFT,
    PUBLISHED,
    DEPRECATED,
    DISABLED,
}

data class PresetConsumerMetadata(
    val presetId: String,
    val revision: Int,
    val displayName: String,
    val description: String,
) {
    init {
        require(presetId.isNotBlank()) { "Preset ID must not be blank" }
        require(revision > 0) { "Preset revision must be positive" }
        require(displayName.isNotBlank()) { "Preset display name must not be blank" }
        require(description.isNotBlank()) { "Preset description must not be blank" }
    }
}

data class PresetExecutionPolicy(
    val modelProfileId: String?,
    val inferencePreset: InferencePresetRef,
    val contextTokens: Int?,
    val cachePolicy: UseCaseCachePolicy,
) {
    init {
        require(modelProfileId == null || modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
        require(contextTokens == null || contextTokens > 0) { "Preset context tokens must be positive" }
    }
}

data class UseCasePresetDefinition(
    val useCaseId: UseCaseId,
    val metadata: PresetConsumerMetadata,
    val creationSource: PresetCreationSource,
    val state: PresetLifecycleState,
    val execution: PresetExecutionPolicy,
) {
    val isConsumerVisible: Boolean
        get() = state == PresetLifecycleState.PUBLISHED
}

data class ApplicationUseCaseBinding(
    val bindingId: String,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val revision: Int,
    val enabled: Boolean = true,
) {
    init {
        require(bindingId.isNotBlank()) { "Binding ID must not be blank" }
        require(revision > 0) { "Binding revision must be positive" }
    }
}

data class PresetExposure(
    val bindingId: String,
    val presetId: String,
    val presetRevision: Int,
    val isDefault: Boolean = false,
) {
    init {
        require(bindingId.isNotBlank()) { "Binding ID must not be blank" }
        require(presetId.isNotBlank()) { "Preset ID must not be blank" }
        require(presetRevision > 0) { "Preset revision must be positive" }
    }
}

data class HostControlPlaneConfiguration(
    val application: RegisteredApplication,
    val useCase: UseCaseDefinition,
    val binding: ApplicationUseCaseBinding,
    val presets: List<UseCasePresetDefinition>,
    val exposures: List<PresetExposure>,
) {
    init {
        require(binding.applicationId == application.applicationId) {
            "Binding application does not match registered application"
        }
        require(binding.useCaseId == useCase.useCaseId) {
            "Binding use case does not match use-case definition"
        }
        require(presets.all { it.useCaseId == useCase.useCaseId }) { "Preset belongs to another use case" }
        require(exposures.all { it.bindingId == binding.bindingId }) { "Preset exposure belongs to another binding" }
        require(exposures.map { it.presetId to it.presetRevision }.distinct().size == exposures.size) {
            "Preset exposures must be unique by preset revision"
        }
        require(exposures.count(PresetExposure::isDefault) <= 1) { "At most one exposed preset may be default" }
        val available = presets.associateBy { it.metadata.presetId to it.metadata.revision }
        require(exposures.all { exposure -> available[exposure.presetId to exposure.presetRevision]?.isConsumerVisible == true }) {
            "Every exposed preset revision must exist and be published"
        }
    }

    fun consumerPresets(): List<PresetConsumerMetadata> = exposures.map { exposure ->
        requireNotNull(
            presets.firstOrNull {
                it.metadata.presetId == exposure.presetId && it.metadata.revision == exposure.presetRevision
            },
        ).metadata
    }

    fun defaultConsumerPreset(): PresetConsumerMetadata? = exposures.singleOrNull(PresetExposure::isDefault)?.let { exposure ->
        presets.first {
            it.metadata.presetId == exposure.presetId && it.metadata.revision == exposure.presetRevision
        }.metadata
    }
}

private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
