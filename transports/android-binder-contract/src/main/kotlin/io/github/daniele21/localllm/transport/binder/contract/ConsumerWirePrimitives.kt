package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef

internal fun InferencePresetRef.toConsumerWire() = ConsumerPresetParcel(id.value, version)

internal fun ConsumerPresetParcel.toCorePreset() = InferencePresetRef(InferencePresetId(id), version)

internal fun ConsumerLimits.toConsumerWire() = ConsumerLimitsParcel(maxInputCharacters, maxConversationMessages, maxJsonSchemaCharacters)

internal fun ConsumerLimitsParcel.toCoreLimits() = ConsumerLimits(maxInputCharacters, maxConversationMessages, maxJsonSchemaCharacters)

internal inline fun <reified T : Enum<T>> enumTag(value: String, label: String): T =
    enumTagOrNull<T>(value) ?: throw invalidWireTag(label, value)

internal inline fun <reified T : Enum<T>> enumTagOrNull(value: String): T? = enumValues<T>().firstOrNull { it.name == value }
