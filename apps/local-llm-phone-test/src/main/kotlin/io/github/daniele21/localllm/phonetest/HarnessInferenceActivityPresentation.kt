package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun InferenceAuditStatus.activityDisplayLabel(): String = name.replace('_', ' ')

internal fun InferenceAuditStatus.activityTone(): HarnessStatusTone = when (this) {
    InferenceAuditStatus.COMPLETED -> HarnessStatusTone.SUCCESS

    InferenceAuditStatus.FAILED -> HarnessStatusTone.ERROR

    InferenceAuditStatus.CANCELLED,
    InferenceAuditStatus.INTERRUPTED,
    -> HarnessStatusTone.WARNING

    InferenceAuditStatus.ADMITTED,
    InferenceAuditStatus.PREPARED,
    InferenceAuditStatus.RUNNING,
    -> HarnessStatusTone.INFO
}

internal fun formatActivityTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(epochMs))

internal fun formatActivityThroughput(value: Double): String = String.format(Locale.US, "%.1f", value)
