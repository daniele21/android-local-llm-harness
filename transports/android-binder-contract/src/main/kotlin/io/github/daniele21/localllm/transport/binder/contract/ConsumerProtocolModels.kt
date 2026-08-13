package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConsumerPresetParcel(val id: String, val version: Int) : Parcelable

@Parcelize
data class ConsumerLimitsParcel(val maxInputCharacters: Int, val maxConversationMessages: Int, val maxJsonSchemaCharacters: Int) : Parcelable

@Parcelize
data class ConsumerCapabilitiesRequestParcel(val clientToken: ClientTokenParcel, val operationId: String, val useCaseId: String) : Parcelable

@Parcelize
data class ConsumerCapabilitiesResultParcel(val operationId: String, val payload: String?, val error: WireErrorParcel?) : Parcelable
