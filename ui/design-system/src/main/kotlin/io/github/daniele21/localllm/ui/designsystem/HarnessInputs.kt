@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Numeric input modes supported by the shared Harness form field. */
enum class HarnessNumberInputMode {
    INTEGER,
    DECIMAL,
}

/** Optional validation and supporting copy shown by [HarnessNumberField]. */
data class HarnessNumberFieldValidation(val isError: Boolean = false, val supportingText: String? = null)

/**
 * Shared numeric editor for Harness forms.
 *
 * Screens must use this component instead of a generic text field whenever the domain value is numeric. The
 * field selects the matching Android numeric keyboard, keeps the raw editable value source-backed, accepts
 * locale comma input for decimal values by normalizing it to '.', and rejects non-numeric edits before they
 * reach screen state.
 */
@Composable
fun HarnessNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mode: HarnessNumberInputMode = HarnessNumberInputMode.INTEGER,
    enabled: Boolean = true,
    validation: HarnessNumberFieldValidation = HarnessNumberFieldValidation(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            normalizeHarnessNumberInput(candidate, mode)?.let(onValueChange)
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        isError = validation.isError,
        label = { Text(label) },
        supportingText = validation.supportingText?.let { detail -> { Text(detail) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (mode) {
                HarnessNumberInputMode.INTEGER -> KeyboardType.Number
                HarnessNumberInputMode.DECIMAL -> KeyboardType.Decimal
            },
        ),
    )
}

fun normalizeHarnessNumberInput(candidate: String, mode: HarnessNumberInputMode): String? {
    if (candidate.isEmpty()) return ""
    return when (mode) {
        HarnessNumberInputMode.INTEGER -> candidate.takeIf { value -> value.all(Char::isDigit) }

        HarnessNumberInputMode.DECIMAL -> {
            val normalized = candidate.replace(',', '.')
            normalized.takeIf { value ->
                value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }
            }
        }
    }
}
