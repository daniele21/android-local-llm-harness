package io.github.daniele21.localllm.console.pii

/** Versioned v1 built-in PII definitions owned by OMBRA, not by Harness. */
internal object OmbraBuiltInPiiDefinitions {
    const val VERSION = 1

    val all: List<PiiDefinition> =
        listOf(
            builtIn(
                id = "full-name",
                label = "Nome completo",
                definition = "Nome e cognome, o altro nome completo, riferibile a una persona fisica.",
            ),
            builtIn(
                id = "email",
                label = "Email",
                definition = "Indirizzo email riferibile a una persona fisica.",
            ),
            builtIn(
                id = "telephone",
                label = "Telefono",
                definition = "Numero di telefono fisso o mobile riferibile a una persona fisica.",
            ),
            builtIn(
                id = "postal-address",
                label = "Indirizzo postale",
                definition =
                "Indirizzo di residenza, domicilio o recapito postale riferibile a una persona fisica.",
            ),
            builtIn(
                id = "italian-tax-code",
                label = "Codice fiscale",
                definition = "Codice fiscale italiano riferibile a una persona fisica.",
            ),
            builtIn(
                id = "iban",
                label = "IBAN",
                definition = "Codice IBAN di un conto riferibile a una persona fisica.",
            ),
        )

    init {
        check(PiiDefinitionSet.create(all).isSuccess) { "Built-in PII definitions must form a valid set" }
    }

    private fun builtIn(id: String, label: String, definition: String): PiiDefinition = PiiDefinition(
        id = PiiTypeId.parse(id),
        label = label,
        definition = definition,
        source = PiiDefinitionSource.BUILT_IN,
    )
}
