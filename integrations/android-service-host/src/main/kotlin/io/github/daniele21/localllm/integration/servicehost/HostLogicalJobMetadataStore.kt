package io.github.daniele21.localllm.integration.servicehost

import android.content.Context
import android.content.SharedPreferences
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId

/**
 * Privacy-safe durable metadata only. Implementations must never persist inference input, prompt,
 * document text, generated output, reasoning or private runtime/model paths.
 */
internal interface HostLogicalJobMetadataStore {
    fun load(maxJobs: Int): List<HostLogicalJobSnapshot>

    /** Replaces [snapshot] and optionally evicts [evictedJobId] in one durable write. */
    fun replace(snapshot: HostLogicalJobSnapshot, evictedJobId: HostLogicalJobId? = null): Boolean
}

internal object NoOpHostLogicalJobMetadataStore : HostLogicalJobMetadataStore {
    override fun load(maxJobs: Int): List<HostLogicalJobSnapshot> = emptyList()

    override fun replace(snapshot: HostLogicalJobSnapshot, evictedJobId: HostLogicalJobId?): Boolean = true
}

internal data class HostLogicalJobMetadataRecord(
    val jobId: String,
    val clientRequestId: String,
    val applicationId: String,
    val useCaseId: String,
    val capabilityRevision: String,
    val presetId: String?,
    val presetVersion: Int?,
    val reasoningMode: String,
    val outputConstraint: String,
    val sessionKind: String,
    val state: String,
    val revision: Long,
    val attempt: Int,
    val runtimeSessionId: String,
)

internal fun HostLogicalJobSnapshot.toMetadataRecord(): HostLogicalJobMetadataRecord = HostLogicalJobMetadataRecord(
    jobId = jobId.value,
    clientRequestId = clientRequestId.value,
    applicationId = scope.applicationId.value,
    useCaseId = scope.useCaseId.value,
    capabilityRevision = execution.capabilityRevision,
    presetId = execution.preset?.id?.value,
    presetVersion = execution.preset?.version,
    reasoningMode = execution.reasoningMode.name,
    outputConstraint = execution.outputConstraint.name,
    sessionKind = execution.sessionKind.name,
    state = state.name,
    revision = revision,
    attempt = attempt,
    runtimeSessionId = runtimeSessionId.value,
)

internal fun HostLogicalJobMetadataRecord.toSnapshot(): HostLogicalJobSnapshot {
    require((presetId == null) == (presetVersion == null)) { "Logical job preset metadata must be complete or absent" }
    val useCase = UseCaseId(useCaseId)
    return HostLogicalJobSnapshot(
        jobId = HostLogicalJobId(jobId),
        clientRequestId = HostClientRequestId(clientRequestId),
        scope = HostLogicalJobScope(ApplicationId(applicationId), useCase),
        execution =
        ConsumerExecutionIdentity(
            useCaseId = useCase,
            capabilityRevision = capabilityRevision,
            preset =
            presetId?.let { id ->
                InferencePresetRef(
                    InferencePresetId(id),
                    requireNotNull(presetVersion),
                )
            },
            reasoningMode = enumValueOf<EffectiveConsumerReasoningMode>(reasoningMode),
            outputConstraint = enumValueOf<ConsumerOutputConstraintKind>(outputConstraint),
            sessionKind = enumValueOf<SessionKind>(sessionKind),
        ),
        state = enumValueOf<HostLogicalJobState>(state),
        revision = revision,
        attempt = attempt,
        runtimeSessionId = HostRuntimeSessionId(runtimeSessionId),
    )
}

internal class AndroidHostLogicalJobMetadataStore(
    context: Context,
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
) : HostLogicalJobMetadataStore {
    override fun load(maxJobs: Int): List<HostLogicalJobSnapshot> {
        require(maxJobs > 0) { "Logical job metadata capacity must be positive" }
        val ids = preferences.getStringSet(INDEX_KEY, emptySet()).orEmpty().toList().sorted().take(maxJobs)
        return ids.mapNotNull { jobId -> readRecord(jobId)?.let { record -> runCatching(record::toSnapshot).getOrNull() } }
    }

    override fun replace(snapshot: HostLogicalJobSnapshot, evictedJobId: HostLogicalJobId?): Boolean {
        val record = snapshot.toMetadataRecord()
        val ids = preferences.getStringSet(INDEX_KEY, emptySet()).orEmpty().toMutableSet()
        evictedJobId?.let { ids.remove(it.value) }
        ids.add(record.jobId)

        val editor = preferences.edit()
        evictedJobId?.let { removeRecord(editor, it.value) }
        writeRecord(editor, record)
        editor.putStringSet(INDEX_KEY, ids)
        return editor.commit()
    }

    private fun readRecord(jobId: String): HostLogicalJobMetadataRecord? {
        val prefix = recordPrefix(jobId)
        val requiredStrings =
            REQUIRED_STRING_FIELDS.associateWith { field -> preferences.getString(prefix + field, null) }
        if (requiredStrings.values.any { value -> value == null }) return null

        val revision = preferences.getLong(prefix + FIELD_REVISION, INVALID_LONG)
        val attempt = preferences.getInt(prefix + FIELD_ATTEMPT, INVALID_INT)
        if (revision < 0 || attempt < 1) return null

        val presetId = preferences.getString(prefix + FIELD_PRESET_ID, null)
        val presetVersion = preferences.getInt(prefix + FIELD_PRESET_VERSION, NO_PRESET_VERSION).takeIf { it != NO_PRESET_VERSION }
        return HostLogicalJobMetadataRecord(
            jobId = jobId,
            clientRequestId = requireNotNull(requiredStrings[FIELD_CLIENT_REQUEST_ID]),
            applicationId = requireNotNull(requiredStrings[FIELD_APPLICATION_ID]),
            useCaseId = requireNotNull(requiredStrings[FIELD_USE_CASE_ID]),
            capabilityRevision = requireNotNull(requiredStrings[FIELD_CAPABILITY_REVISION]),
            presetId = presetId,
            presetVersion = presetVersion,
            reasoningMode = requireNotNull(requiredStrings[FIELD_REASONING_MODE]),
            outputConstraint = requireNotNull(requiredStrings[FIELD_OUTPUT_CONSTRAINT]),
            sessionKind = requireNotNull(requiredStrings[FIELD_SESSION_KIND]),
            state = requireNotNull(requiredStrings[FIELD_STATE]),
            revision = revision,
            attempt = attempt,
            runtimeSessionId = requireNotNull(requiredStrings[FIELD_RUNTIME_SESSION_ID]),
        )
    }

    private fun writeRecord(editor: SharedPreferences.Editor, record: HostLogicalJobMetadataRecord) {
        val prefix = recordPrefix(record.jobId)
        editor
            .putString(prefix + FIELD_CLIENT_REQUEST_ID, record.clientRequestId)
            .putString(prefix + FIELD_APPLICATION_ID, record.applicationId)
            .putString(prefix + FIELD_USE_CASE_ID, record.useCaseId)
            .putString(prefix + FIELD_CAPABILITY_REVISION, record.capabilityRevision)
            .putString(prefix + FIELD_REASONING_MODE, record.reasoningMode)
            .putString(prefix + FIELD_OUTPUT_CONSTRAINT, record.outputConstraint)
            .putString(prefix + FIELD_SESSION_KIND, record.sessionKind)
            .putString(prefix + FIELD_STATE, record.state)
            .putLong(prefix + FIELD_REVISION, record.revision)
            .putInt(prefix + FIELD_ATTEMPT, record.attempt)
            .putString(prefix + FIELD_RUNTIME_SESSION_ID, record.runtimeSessionId)
        if (record.presetId == null) {
            editor.remove(prefix + FIELD_PRESET_ID).remove(prefix + FIELD_PRESET_VERSION)
        } else {
            editor
                .putString(prefix + FIELD_PRESET_ID, record.presetId)
                .putInt(prefix + FIELD_PRESET_VERSION, requireNotNull(record.presetVersion))
        }
    }

    private fun removeRecord(editor: SharedPreferences.Editor, jobId: String) {
        val prefix = recordPrefix(jobId)
        RECORD_FIELDS.forEach { field -> editor.remove(prefix + field) }
    }

    private companion object {
        const val PREFERENCES_NAME = "harnex_consumer_logical_jobs_v1"
        const val INDEX_KEY = "jobs"
        const val FIELD_CLIENT_REQUEST_ID = "client_request_id"
        const val FIELD_APPLICATION_ID = "application_id"
        const val FIELD_USE_CASE_ID = "use_case_id"
        const val FIELD_CAPABILITY_REVISION = "capability_revision"
        const val FIELD_PRESET_ID = "preset_id"
        const val FIELD_PRESET_VERSION = "preset_version"
        const val FIELD_REASONING_MODE = "reasoning_mode"
        const val FIELD_OUTPUT_CONSTRAINT = "output_constraint"
        const val FIELD_SESSION_KIND = "session_kind"
        const val FIELD_STATE = "state"
        const val FIELD_REVISION = "revision"
        const val FIELD_ATTEMPT = "attempt"
        const val FIELD_RUNTIME_SESSION_ID = "runtime_session_id"
        const val INVALID_LONG = -1L
        const val INVALID_INT = -1
        const val NO_PRESET_VERSION = Int.MIN_VALUE
        val REQUIRED_STRING_FIELDS =
            listOf(
                FIELD_CLIENT_REQUEST_ID,
                FIELD_APPLICATION_ID,
                FIELD_USE_CASE_ID,
                FIELD_CAPABILITY_REVISION,
                FIELD_REASONING_MODE,
                FIELD_OUTPUT_CONSTRAINT,
                FIELD_SESSION_KIND,
                FIELD_STATE,
                FIELD_RUNTIME_SESSION_ID,
            )
        val RECORD_FIELDS =
            listOf(
                FIELD_CLIENT_REQUEST_ID,
                FIELD_APPLICATION_ID,
                FIELD_USE_CASE_ID,
                FIELD_CAPABILITY_REVISION,
                FIELD_PRESET_ID,
                FIELD_PRESET_VERSION,
                FIELD_REASONING_MODE,
                FIELD_OUTPUT_CONSTRAINT,
                FIELD_SESSION_KIND,
                FIELD_STATE,
                FIELD_REVISION,
                FIELD_ATTEMPT,
                FIELD_RUNTIME_SESSION_ID,
            )

        fun recordPrefix(jobId: String): String = "job.$jobId."
    }
}
