package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal sealed interface HarnessApplicationsReadState {
    data object Loading : HarnessApplicationsReadState

    data class Loaded(val snapshot: HarnessApplicationsSnapshot) : HarnessApplicationsReadState

    data class Failed(val message: String) : HarnessApplicationsReadState
}

internal sealed interface HarnessApplicationsMutationState {
    data object Idle : HarnessApplicationsMutationState

    data object Saving : HarnessApplicationsMutationState

    data class Saved(val message: String, val presetId: String? = null, val presetRevision: Int? = null) : HarnessApplicationsMutationState

    data class Conflict(val message: String) : HarnessApplicationsMutationState

    data class Failed(val message: String) : HarnessApplicationsMutationState
}

internal class HarnessApplicationsReadViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<HarnessApplicationsReadState>(HarnessApplicationsReadState.Loading)
    private val mutableMutationState = MutableStateFlow<HarnessApplicationsMutationState>(HarnessApplicationsMutationState.Idle)
    private val generation = java.util.concurrent.atomic.AtomicLong(0)
    private var gateway: HarnessApplicationsGateway? = null

    val state: StateFlow<HarnessApplicationsReadState> = mutableState.asStateFlow()
    val mutationState: StateFlow<HarnessApplicationsMutationState> = mutableMutationState.asStateFlow()

    fun attach(gateway: HarnessApplicationsGateway) {
        if (this.gateway === gateway) return
        this.gateway = gateway
        refresh()
    }

    fun detach(gateway: HarnessApplicationsGateway) {
        if (this.gateway !== gateway) return
        this.gateway = null
        generation.incrementAndGet()
        mutableMutationState.value = HarnessApplicationsMutationState.Idle
    }

    fun refresh() {
        val attached = gateway
        if (attached == null) {
            mutableState.value = HarnessApplicationsReadState.Failed("Applications source is unavailable")
            return
        }
        val token = generation.incrementAndGet()
        mutableMutationState.value = HarnessApplicationsMutationState.Idle
        mutableState.value = HarnessApplicationsReadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching(attached::snapshot)
            if (!isCurrent(token, attached)) return@launch
            mutableState.value = result.fold(
                onSuccess = HarnessApplicationsReadState::Loaded,
                onFailure = { HarnessApplicationsReadState.Failed("Applications could not be loaded") },
            )
        }
    }

    fun setDefaultPreset(applicationId: String, assignment: HarnessAssignmentSummary, preset: HarnessPresetSummary) {
        val attached = gateway
        if (attached == null) {
            mutableMutationState.value = HarnessApplicationsMutationState.Failed("Applications source is unavailable")
            return
        }
        val token = generation.incrementAndGet()
        mutableMutationState.value = HarnessApplicationsMutationState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                attached.setDefaultPreset(
                    HarnessSetDefaultPresetCommand(
                        applicationId = applicationId,
                        useCaseId = assignment.useCaseId,
                        expectedBindingRevision = assignment.bindingRevision,
                        presetId = preset.presetId,
                        presetRevision = preset.revision,
                    ),
                )
            }.getOrElse {
                if (isCurrent(token, attached)) {
                    mutableMutationState.value = HarnessApplicationsMutationState.Failed(
                        "Configuration could not be updated. Nothing was retried automatically.",
                    )
                }
                return@launch
            }
            val canonical = runCatching(attached::snapshot).getOrNull()
            if (!isCurrent(token, attached)) return@launch
            canonical?.let { mutableState.value = HarnessApplicationsReadState.Loaded(it) }
            mutableMutationState.value = result.toMutationState(canonical != null)
        }
    }

    fun createCustomPreset(
        applicationId: String,
        assignment: HarnessAssignmentSummary,
        basePreset: HarnessPresetSummary,
        displayName: String,
        automaticModelSelection: Boolean,
        contextTokens: Int?,
    ) {
        val attached = gateway as? HarnessCustomPresetGateway
        if (attached == null) {
            mutableMutationState.value = HarnessApplicationsMutationState.Failed(
                "Custom preset creation is unavailable in this build.",
            )
            return
        }
        val token = generation.incrementAndGet()
        mutableMutationState.value = HarnessApplicationsMutationState.Saving
        val presetId = "custom-${UUID.randomUUID()}"
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                attached.createCustomPreset(
                    HarnessCreateCustomPresetCommand(
                        applicationId = applicationId,
                        useCaseId = assignment.useCaseId,
                        expectedBindingRevision = assignment.bindingRevision,
                        presetId = presetId,
                        basePresetId = basePreset.presetId,
                        basePresetRevision = basePreset.revision,
                        displayName = displayName,
                        modelProfileId = if (automaticModelSelection) null else basePreset.modelProfileId,
                        contextTokens = contextTokens,
                    ),
                )
            }.getOrElse {
                if (isCurrent(token, attached)) {
                    mutableMutationState.value = HarnessApplicationsMutationState.Failed(
                        "Custom preset could not be saved. Nothing was retried automatically.",
                    )
                }
                return@launch
            }
            val canonical = runCatching(attached::snapshot).getOrNull()
            if (!isCurrent(token, attached)) return@launch
            canonical?.let { mutableState.value = HarnessApplicationsReadState.Loaded(it) }
            mutableMutationState.value = result.toMutationState(canonical != null)
        }
    }

    fun clearMutationFeedback() {
        if (mutableMutationState.value != HarnessApplicationsMutationState.Saving) {
            mutableMutationState.value = HarnessApplicationsMutationState.Idle
        }
    }

    private fun isCurrent(token: Long, attached: HarnessApplicationsGateway): Boolean = generation.get() == token && gateway === attached

    override fun onCleared() {
        generation.incrementAndGet()
        gateway = null
        mutableMutationState.value = HarnessApplicationsMutationState.Idle
        super.onCleared()
    }
}

private fun HarnessControlPlaneMutationResult.toMutationState(canonicalReloaded: Boolean): HarnessApplicationsMutationState = when (this) {
    is HarnessControlPlaneMutationResult.Success -> if (canonicalReloaded) {
        HarnessApplicationsMutationState.Saved("Default preset updated and reloaded from the control plane.")
    } else {
        HarnessApplicationsMutationState.Failed(
            "The default preset was updated, but the canonical state could not be reloaded. Reload Applications before continuing.",
        )
    }

    is HarnessControlPlaneMutationResult.StaleRevision -> HarnessApplicationsMutationState.Conflict(
        "Configuration changed elsewhere. Review the latest assignment before confirming again.",
    )

    is HarnessControlPlaneMutationResult.Rejected -> HarnessApplicationsMutationState.Failed(
        message.ifBlank { "Configuration could not be updated." },
    )
}

private fun HarnessCustomPresetMutationResult.toMutationState(canonicalReloaded: Boolean): HarnessApplicationsMutationState = when (this) {
    is HarnessCustomPresetMutationResult.Success -> if (canonicalReloaded) {
        HarnessApplicationsMutationState.Saved(
            message = "Preset saved and reloaded from the control plane.",
            presetId = presetId,
            presetRevision = presetRevision,
        )
    } else {
        HarnessApplicationsMutationState.Failed(
            "The preset was saved, but canonical state could not be reloaded. Reload Applications before continuing.",
        )
    }

    is HarnessCustomPresetMutationResult.StaleRevision -> HarnessApplicationsMutationState.Conflict(
        "Configuration changed elsewhere. Reload the assignment before saving again.",
    )

    is HarnessCustomPresetMutationResult.Rejected -> HarnessApplicationsMutationState.Failed(
        message.ifBlank { "Custom preset could not be saved." },
    )
}
