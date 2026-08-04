package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository

internal data class DiagnosticsLogFilter(
    val level: LogLevel? = null,
    val componentQuery: String = "",
    val eventQuery: String = "",
    val requestQuery: String = "",
    val searchQuery: String = "",
) {
    val active: Boolean
        get() = level != null ||
            componentQuery.isNotBlank() ||
            eventQuery.isNotBlank() ||
            requestQuery.isNotBlank() ||
            searchQuery.isNotBlank()
}

internal data class DiagnosticsLogFieldUi(val name: String, val value: String)

internal data class DiagnosticsLogUi(
    val stableId: String,
    val timestampEpochMs: Long,
    val level: String,
    val component: String,
    val event: String,
    val requestId: String?,
    val requestIdPrefix: String,
    val fields: List<DiagnosticsLogFieldUi>,
    val offsetMs: Long? = null,
)

internal data class DiagnosticsLogUiState(
    val logs: List<DiagnosticsLogUi> = emptyList(),
    val totalCount: Int = 0,
    val filterActive: Boolean = false,
    val availableComponents: List<String> = emptyList(),
    val availableEvents: List<String> = emptyList(),
    val sourceError: String? = null,
)

internal data class DiagnosticsRequestTimelineUi(
    val requestId: String,
    val requestIdPrefix: String,
    val runStatus: String,
    val events: List<DiagnosticsLogUi> = emptyList(),
    val sourceError: String? = null,
)

internal class HarnessLogSource(private val repository: TelemetryRepository) {
    fun snapshot(filter: DiagnosticsLogFilter = DiagnosticsLogFilter(), limit: Int = DEFAULT_LOG_LIMIT): DiagnosticsLogUiState =
        runCatching {
            val mapped = repository.recentLogs(limit).mapIndexed(::toUi)
            DiagnosticsLogUiState(
                logs = mapped.filter { it.matches(filter) },
                totalCount = mapped.size,
                filterActive = filter.active,
                availableComponents = mapped.map(DiagnosticsLogUi::component).distinct().sorted(),
                availableEvents = mapped.map(DiagnosticsLogUi::event).distinct().sorted(),
            )
        }.getOrElse {
            DiagnosticsLogUiState(filterActive = filter.active, sourceError = SOURCE_ERROR)
        }

    fun requestTimeline(requestId: String, limit: Int = DEFAULT_TIMELINE_LIMIT): DiagnosticsRequestTimelineUi = runCatching {
        val typedRequestId = RequestId(requestId)
        val run = repository.findRun(typedRequestId)
        val ordered = repository.recentLogs(limit, typedRequestId)
            .mapIndexed(::IndexedLog)
            .sortedWith(compareBy<IndexedLog> { it.log.timestampEpochMs }.thenBy(IndexedLog::index))
        val origin = run?.startedAtEpochMs ?: ordered.firstOrNull()?.log?.timestampEpochMs
        DiagnosticsRequestTimelineUi(
            requestId = requestId,
            requestIdPrefix = requestId.safePrefix(),
            runStatus = run?.status?.name ?: "Unavailable",
            events = ordered.map { indexed ->
                toUi(indexed.index, indexed.log).copy(
                    offsetMs = origin?.let { indexed.log.timestampEpochMs - it },
                )
            },
        )
    }.getOrElse {
        DiagnosticsRequestTimelineUi(
            requestId = requestId,
            requestIdPrefix = requestId.safePrefix(),
            runStatus = "Unavailable",
            sourceError = SOURCE_ERROR,
        )
    }

    private fun toUi(index: Int, log: StructuredLog): DiagnosticsLogUi = DiagnosticsLogUi(
        stableId = "${log.timestampEpochMs}:$index:${log.event}",
        timestampEpochMs = log.timestampEpochMs,
        level = log.level.name,
        component = log.component.safeToken(),
        event = log.event.safeToken(),
        requestId = log.requestId?.value,
        requestIdPrefix = log.requestId?.value?.safePrefix() ?: "None",
        fields = log.fields.entries
            .asSequence()
            .filter { it.key in SAFE_FIELD_KEYS }
            .sortedBy { it.key }
            .map { (name, value) -> DiagnosticsLogFieldUi(name, value.safeFieldValue(name)) }
            .toList(),
    )

    private fun DiagnosticsLogUi.matches(filter: DiagnosticsLogFilter): Boolean {
        val levelMatches = filter.level == null || level == filter.level.name
        val componentMatches = component.containsQuery(filter.componentQuery)
        val eventMatches = event.containsQuery(filter.eventQuery)
        val requestMatches = requestId.orEmpty().containsQuery(filter.requestQuery)
        val searchMatches = filter.searchQuery.isBlank() || sequenceOf(component, event, requestId.orEmpty())
            .plus(fields.asSequence().flatMap { sequenceOf(it.name, it.value) })
            .any { it.containsQuery(filter.searchQuery) }
        return levelMatches && componentMatches && eventMatches && requestMatches && searchMatches
    }

    private fun String.containsQuery(query: String): Boolean = query.isBlank() || contains(query.trim(), ignoreCase = true)

    private fun String.safeToken(): String = replace(CONTROL_CHARACTERS, " ").trim().take(MAX_TOKEN_LENGTH)

    private fun String.safeFieldValue(name: String): String = when (name) {
        MODEL_DIGEST_FIELD -> take(DIGEST_PREFIX_LENGTH) + "…"
        else -> replace(CONTROL_CHARACTERS, " ").trim().take(MAX_FIELD_VALUE_LENGTH)
    }

    private fun String.safePrefix(): String = take(REQUEST_PREFIX_LENGTH) + if (length > REQUEST_PREFIX_LENGTH) "…" else ""

    private data class IndexedLog(val index: Int, val log: StructuredLog)

    private companion object {
        const val DEFAULT_LOG_LIMIT = 250
        const val DEFAULT_TIMELINE_LIMIT = 250
        const val REQUEST_PREFIX_LENGTH = 12
        const val DIGEST_PREFIX_LENGTH = 12
        const val MAX_TOKEN_LENGTH = 96
        const val MAX_FIELD_VALUE_LENGTH = 160
        const val MODEL_DIGEST_FIELD = "modelDigest"
        const val SOURCE_ERROR = "Structured diagnostics logs are temporarily unavailable."
        val CONTROL_CHARACTERS = Regex("[\\p{Cntrl}&&[^\\n\\t]]|[\\n\\t]+")
        val SAFE_FIELD_KEYS = setOf(
            "applicationId",
            "useCaseId",
            MODEL_DIGEST_FIELD,
            "position",
            "errorCode",
            "queueMs",
            "totalMs",
            "modelLoadKind",
            "modelLoadMs",
            "timeToFirstTokenMs",
            "prefillMs",
            "decodeMs",
            "inputTokens",
            "outputTokens",
            "decodeTokensPerSecond",
        )
    }
}
