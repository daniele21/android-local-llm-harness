package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Wire tags for protocol-minor-5 detached Consumer generation jobs. */
object ConsumerLogicalJobWireTags {
    const val STATE_QUEUED = "QUEUED"
    const val STATE_PREPARING = "PREPARING"
    const val STATE_RUNNING = "RUNNING"
    const val STATE_SUCCEEDED = "SUCCEEDED"
    const val STATE_CANCEL_REQUESTED = "CANCEL_REQUESTED"
    const val STATE_CANCELLED = "CANCELLED"
    const val STATE_FAILED_RETRYABLE = "FAILED_RETRYABLE"
    const val STATE_RECOVERING = "RECOVERING"
    const val STATE_INTERRUPTED = "INTERRUPTED"
    const val STATE_FAILED_FINAL = "FAILED_FINAL"
}

/**
 * Minor-v5 submit envelope for a detached logical generation.
 *
 * The host owns the runtime session created from [preparedId]. The caller supplies a stable,
 * privacy-safe [clientRequestId] so a retry after Binder loss converges on the same logical job.
 */
@Parcelize
data class ConsumerLogicalJobSubmitParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val clientRequestId: String,
    val useCaseId: String,
    val preparedId: String,
    val input: ConsumerGenerationInputParcel,
    val outputConstraint: ConsumerOutputConstraintParcel,
    val taskDefinitions: List<TaskDefinitionParcel> = emptyList(),
) : Parcelable

/** Query/cancel identity. Job ownership is still derived from the authenticated Binder caller. */
@Parcelize
data class ConsumerLogicalJobQueryParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val jobId: String,
    val useCaseId: String,
) : Parcelable

/** Privacy-safe authoritative state; prompt and generated content are deliberately absent. */
@Parcelize
data class ConsumerLogicalJobSnapshotParcel(
    val jobId: String,
    val clientRequestId: String,
    val useCaseId: String,
    val stateTag: String,
    val revision: Long,
    val attempt: Int,
    val runtimeSessionId: String,
    val resultAvailable: Boolean,
    val errorCode: String? = null,
) : Parcelable

/**
 * Response for submit/query/result operations.
 *
 * [answerText] and [reasoningText] are process-local replay data only. The host must never persist
 * them as logical-job metadata; after host process death they can legitimately be unavailable.
 */
@Parcelize
data class ConsumerLogicalJobResultParcel(
    val operationId: String,
    val snapshot: ConsumerLogicalJobSnapshotParcel? = null,
    val answerText: String? = null,
    val reasoningText: String? = null,
    val metrics: ConsumerInferenceMetricsParcel? = null,
    val error: WireErrorParcel? = null,
) : Parcelable
