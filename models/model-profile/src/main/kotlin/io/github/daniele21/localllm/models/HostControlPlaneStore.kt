package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId

data class StoredPresetExposure(
    val bindingId: String,
    val bindingRevision: Int,
    val presetId: String,
    val presetRevision: Int,
    val isDefault: Boolean = false,
) {
    init {
        require(bindingId.isNotBlank()) { "Binding ID must not be blank" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
        require(presetId.isNotBlank()) { "Preset ID must not be blank" }
        require(presetRevision > 0) { "Preset revision must be positive" }
    }
}

data class HostControlPlaneState(
    val applications: List<RegisteredApplication> = emptyList(),
    val useCases: List<UseCaseDefinition> = emptyList(),
    val presets: List<UseCasePresetDefinition> = emptyList(),
    val bindings: List<ApplicationUseCaseBinding> = emptyList(),
    val exposures: List<StoredPresetExposure> = emptyList(),
) {
    init {
        require(applications.size <= MAX_APPLICATIONS) { "Too many registered applications" }
        require(useCases.size <= MAX_USE_CASE_REVISIONS) { "Too many use-case revisions" }
        require(presets.size <= MAX_PRESET_REVISIONS) { "Too many preset revisions" }
        require(bindings.size <= MAX_BINDING_REVISIONS) { "Too many binding revisions" }
        require(exposures.size <= MAX_EXPOSURES) { "Too many preset exposures" }

        require(applications.distinctBy { it.applicationId }.size == applications.size) {
            "Registered applications must be unique by application ID"
        }
        require(useCases.distinctBy { it.useCaseId to it.revision }.size == useCases.size) {
            "Use-case revisions must be unique"
        }
        require(
            presets.distinctBy { preset ->
                Triple(preset.useCaseId, preset.metadata.presetId, preset.metadata.revision)
            }.size == presets.size,
        ) {
            "Preset revisions must be unique"
        }
        require(bindings.distinctBy { it.bindingId to it.revision }.size == bindings.size) {
            "Binding revisions must be unique"
        }
        require(
            exposures.distinctBy { exposure ->
                listOf(
                    exposure.bindingId,
                    exposure.bindingRevision.toString(),
                    exposure.presetId,
                    exposure.presetRevision.toString(),
                )
            }.size == exposures.size,
        ) {
            "Preset exposures must be unique by binding and preset revision"
        }

        val applicationIds = applications.map { it.applicationId }.toSet()
        val useCaseIds = useCases.map { it.useCaseId }.toSet()
        val bindingKeys = bindings.associateBy { it.bindingId to it.revision }
        val presetKeys = presets.associateBy { preset ->
            Triple(preset.useCaseId, preset.metadata.presetId, preset.metadata.revision)
        }

        require(bindings.all { it.applicationId in applicationIds }) {
            "Every binding must reference a registered application"
        }
        require(bindings.all { it.useCaseId in useCaseIds }) {
            "Every binding must reference a known use case"
        }
        require(presets.all { it.useCaseId in useCaseIds }) {
            "Every preset must reference a known use case"
        }
        require(exposures.all { exposure -> bindingKeys.containsKey(exposure.bindingId to exposure.bindingRevision) }) {
            "Every exposure must reference an existing binding revision"
        }
        require(exposures.all { exposure ->
            val binding = bindingKeys.getValue(exposure.bindingId to exposure.bindingRevision)
            presetKeys[
                Triple(binding.useCaseId, exposure.presetId, exposure.presetRevision)
            ]?.isConsumerVisible == true
        }) {
            "Every exposure must reference a published preset revision for the binding use case"
        }
        require(
            exposures.groupBy { it.bindingId to it.bindingRevision }.values.all { group ->
                group.count(StoredPresetExposure::isDefault) <= 1
            },
        ) {
            "At most one preset may be default for each binding revision"
        }
    }

    fun canonical(): HostControlPlaneState = copy(
        applications = applications.sortedBy { it.applicationId.value }.toList(),
        useCases = useCases.sortedWith(compareBy({ it.useCaseId.value }, UseCaseDefinition::revision)).toList(),
        presets = presets.sortedWith(
            compareBy(
                { it.useCaseId.value },
                { it.metadata.presetId },
                { it.metadata.revision },
            ),
        ).toList(),
        bindings = bindings.sortedWith(compareBy(ApplicationUseCaseBinding::bindingId, ApplicationUseCaseBinding::revision)).toList(),
        exposures = exposures.sortedWith(
            compareBy(
                StoredPresetExposure::bindingId,
                StoredPresetExposure::bindingRevision,
                StoredPresetExposure::presetId,
                StoredPresetExposure::presetRevision,
            ),
        ).toList(),
    )

    fun latestUseCase(useCaseId: UseCaseId): UseCaseDefinition? =
        useCases.filter { it.useCaseId == useCaseId }.maxByOrNull(UseCaseDefinition::revision)

    fun latestBinding(applicationId: ApplicationId, useCaseId: UseCaseId): ApplicationUseCaseBinding? =
        bindings
            .filter { it.applicationId == applicationId && it.useCaseId == useCaseId }
            .maxByOrNull(ApplicationUseCaseBinding::revision)

    fun preset(useCaseId: UseCaseId, presetId: String, revision: Int): UseCasePresetDefinition? =
        presets.firstOrNull {
            it.useCaseId == useCaseId &&
                it.metadata.presetId == presetId &&
                it.metadata.revision == revision
        }

    companion object {
        const val MAX_APPLICATIONS = 128
        const val MAX_USE_CASE_REVISIONS = 512
        const val MAX_PRESET_REVISIONS = 2_048
        const val MAX_BINDING_REVISIONS = 2_048
        const val MAX_EXPOSURES = 4_096
    }
}

fun interface HostControlPlaneTransaction {
    fun apply(current: HostControlPlaneState): HostControlPlaneState
}

interface HostControlPlaneStore {
    fun snapshot(): HostControlPlaneState

    fun replace(state: HostControlPlaneState)

    fun transact(transaction: HostControlPlaneTransaction): HostControlPlaneState
}

class InMemoryHostControlPlaneStore(initialState: HostControlPlaneState = HostControlPlaneState()) : HostControlPlaneStore {
    private var state = initialState.canonical()

    @Synchronized
    override fun snapshot(): HostControlPlaneState = state.canonical()

    @Synchronized
    override fun replace(state: HostControlPlaneState) {
        this.state = state.canonical()
    }

    @Synchronized
    override fun transact(transaction: HostControlPlaneTransaction): HostControlPlaneState {
        val next = transaction.apply(state.canonical()).canonical()
        state = next
        return next
    }
}
