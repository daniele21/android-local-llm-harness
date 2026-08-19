package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConsumerControlPlaneRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val useCaseId: String? = null,
    val useCaseRevision: Int? = null,
    val bindingRevision: Int? = null,
    val preset: ConsumerPresetParcel? = null,
    val activationId: String? = null,
) : Parcelable

@Parcelize
data class ConsumerAssignedUseCaseParcel(
    val useCaseId: String,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
) : Parcelable

@Parcelize
data class ConsumerPublishedPresetMetadataParcel(
    val preset: ConsumerPresetParcel,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
) : Parcelable

@Parcelize
data class ConsumerActivationParcel(
    val activationId: String,
    val useCaseId: String,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val preset: ConsumerPresetParcel,
) : Parcelable

@Parcelize
data class ConsumerControlPlaneResultParcel(
    val operationId: String,
    val assignments: List<ConsumerAssignedUseCaseParcel> = emptyList(),
    val useCaseId: String? = null,
    val bindingRevision: Int? = null,
    val presets: List<ConsumerPublishedPresetMetadataParcel> = emptyList(),
    val activation: ConsumerActivationParcel? = null,
    val releasedActivationId: String? = null,
    val error: WireErrorParcel? = null,
) : Parcelable
