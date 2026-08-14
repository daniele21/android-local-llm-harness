package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerPreparedSelection
import io.github.daniele21.localllm.contracts.ConsumerPresetOption
import io.github.daniele21.localllm.contracts.ConsumerSelectionRequest
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId

fun UseCaseCapabilities.toConsumerWire(): ConsumerCapabilitiesParcel = ConsumerCapabilitiesParcel(
    useCaseId = useCaseId.value,
    readinessTag = readiness.name,
    presets = presets.map { ConsumerPresetOptionParcel(it.ref.toConsumerWire(), it.isDefault) },
    defaultPreset = defaultPreset?.toConsumerWire(),
    reasoningTag = reasoning.name,
    outputConstraintTags = outputConstraints.map { it.name }.sorted(),
    defaultOutputConstraintTag = defaultOutputConstraint.name,
    sessionKindTags = sessionKinds.map { it.name }.sorted(),
    defaultSessionKindTag = defaultSessionKind.name,
    limits = limits.toConsumerWire(),
    capabilityRevision = capabilityRevision,
)

fun ConsumerCapabilitiesParcel.toCoreCapabilities(): UseCaseCapabilities = UseCaseCapabilities(
    useCaseId = UseCaseId(useCaseId),
    readiness = enumTag(readinessTag, "consumer readiness"),
    presets = presets.map { ConsumerPresetOption(it.preset.toCorePreset(), it.isDefault) },
    defaultPreset = defaultPreset?.toCorePreset(),
    reasoning = enumTag(reasoningTag, "consumer reasoning capability"),
    outputConstraints =
    outputConstraintTags
        .map { enumTag<ConsumerOutputConstraintKind>(it, "consumer output constraint") }
        .toSet(),
    defaultOutputConstraint = enumTag(defaultOutputConstraintTag, "consumer default output constraint"),
    sessionKinds = sessionKindTags.map { enumTag<SessionKind>(it, "consumer session kind") }.toSet(),
    defaultSessionKind = enumTag(defaultSessionKindTag, "consumer default session kind"),
    limits = limits.toCoreLimits(),
    capabilityRevision = capabilityRevision,
)

fun ConsumerSelectionRequest.toConsumerWire(): ConsumerSelectionParcel = ConsumerSelectionParcel(
    capabilityRevision = capabilityRevision,
    preset = preset?.toConsumerWire(),
    reasoningPreferenceTag = reasoning.name,
    outputConstraintTag = outputConstraint?.name,
    sessionKindTag = sessionKind?.name,
)

fun ConsumerSelectionParcel.toCoreSelection(): ConsumerSelectionRequest = ConsumerSelectionRequest(
    capabilityRevision = capabilityRevision,
    preset = preset?.toCorePreset(),
    reasoning = enumTag(reasoningPreferenceTag, "consumer reasoning preference"),
    outputConstraint = outputConstraintTag?.let { enumTag(it, "consumer output constraint") },
    sessionKind = sessionKindTag?.let { enumTag(it, "consumer session kind") },
)

fun ConsumerPreparedSelection.toConsumerWire(): ConsumerPreparedSelectionParcel = ConsumerPreparedSelectionParcel(
    preparedId = preparedId.value,
    useCaseId = useCaseId.value,
    capabilityRevision = capabilityRevision,
    preset = preset?.toConsumerWire(),
    reasoningModeTag = reasoningMode.name,
    outputConstraintTag = outputConstraint.name,
    sessionKindTag = sessionKind.name,
)

fun ConsumerPreparedSelectionParcel.toCorePreparedSelection(): ConsumerPreparedSelection = ConsumerPreparedSelection(
    preparedId = ConsumerPreparedId(preparedId),
    useCaseId = UseCaseId(useCaseId),
    capabilityRevision = capabilityRevision,
    preset = preset?.toCorePreset(),
    reasoningMode = enumTag(reasoningModeTag, "consumer reasoning mode"),
    outputConstraint = enumTag(outputConstraintTag, "consumer output constraint"),
    sessionKind = enumTag(sessionKindTag, "consumer session kind"),
)
