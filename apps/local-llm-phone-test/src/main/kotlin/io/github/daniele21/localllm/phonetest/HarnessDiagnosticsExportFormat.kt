package io.github.daniele21.localllm.phonetest

import java.time.Instant
import java.util.Locale

private val DIAGNOSTIC_CONTROL_CHARACTERS = Regex("[\\p{Cntrl}&&[^\\n\\t]]|[\\n\\t]+")
private const val diagnosticMaxTokenLength = 128
private const val diagnosticMaxValueLength = 512

internal fun Any?.orUnavailable(): String = this?.toString() ?: "Unavailable"

internal fun Long?.asInstantOrUnavailable(): String = this?.let(Instant::ofEpochMilli)?.toString() ?: "Unavailable"

internal fun Double?.asDecimalOrUnavailable(): String = this?.let { "%.3f".format(Locale.ROOT, it) } ?: "Unavailable"

internal fun String?.asSafeOrUnavailable(): String = this?.safeToken() ?: "Unavailable"

internal fun String?.asSafeOrNone(): String = this?.safeToken() ?: "None"

internal fun String.safeToken(): String =
    replace(DIAGNOSTIC_CONTROL_CHARACTERS, " ")
        .trim()
        .take(diagnosticMaxTokenLength)

internal fun String.safeValue(): String =
    replace(DIAGNOSTIC_CONTROL_CHARACTERS, " ")
        .trim()
        .take(diagnosticMaxValueLength)
