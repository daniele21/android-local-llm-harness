package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.pii.PiiTypeId

/**
 * Consumer-validated finding. Model output cannot construct this type directly: OMB-3 must first
 * prove that the type was selected, the surface is exact and every occurrence belongs to a
 * submitted normalized segment. The sensitive surface is intentionally omitted from [toString].
 */
internal data class ValidatedFinding(val typeId: PiiTypeId, val surface: String, val occurrences: List<SourceOccurrence>) {
    init {
        require(surface.isNotBlank()) { "Validated finding surface must not be blank" }
        require(occurrences.isNotEmpty()) { "Validated finding must contain a source occurrence" }
        require(surface.codePointCount(0, surface.length) <= MAX_SURFACE_CODE_POINTS) {
            "Validated finding surface is too long"
        }
    }

    override fun toString(): String = "ValidatedFinding(typeId=$typeId, surface=<redacted>, occurrenceCount=${occurrences.size})"

    companion object {
        const val MAX_SURFACE_CODE_POINTS = 512
    }
}
