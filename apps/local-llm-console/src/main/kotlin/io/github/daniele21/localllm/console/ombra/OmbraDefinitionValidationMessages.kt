package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.console.pii.PiiDefinitionIssue
import io.github.daniele21.localllm.console.pii.PiiDefinitionValidation

internal fun PiiDefinitionValidation.errorForName(): String? = when {
    PiiDefinitionIssue.BLANK_LABEL in issues -> "Inserisci un nome."
    PiiDefinitionIssue.LABEL_TOO_LONG in issues -> "Il nome è troppo lungo."
    PiiDefinitionIssue.UNSUPPORTED_CONTROL_CHARACTER in issues -> "Il testo contiene caratteri non supportati."
    PiiDefinitionIssue.CUSTOM_DEFINITION_LIMIT_REACHED in issues -> "Hai raggiunto il numero massimo di definizioni personalizzate."
    else -> null
}

internal fun PiiDefinitionValidation.errorForDefinition(): String? = when {
    PiiDefinitionIssue.BLANK_DEFINITION in issues -> "Inserisci una definizione."
    PiiDefinitionIssue.DEFINITION_TOO_LONG in issues -> "La definizione è troppo lunga."
    PiiDefinitionIssue.UNSUPPORTED_CONTROL_CHARACTER in issues -> "Il testo contiene caratteri non supportati."
    else -> null
}

internal fun PiiDefinitionValidation.errorForExample(): String? = when {
    PiiDefinitionIssue.EXAMPLE_TOO_LONG in issues -> "L’esempio è troppo lungo."
    PiiDefinitionIssue.UNSUPPORTED_CONTROL_CHARACTER in issues -> "Il testo contiene caratteri non supportati."
    else -> null
}
