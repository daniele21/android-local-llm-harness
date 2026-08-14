package io.github.daniele21.localllm.console.application

/** Content-free monotonic identity for one long-running application operation. */
@JvmInline
internal value class OmbraOperationId(val value: Long) {
    init {
        require(value > 0) { "Operation ID must be positive" }
    }
}

/** Opaque process-local capability identity; never a URI or filesystem path. */
@JvmInline
internal value class OmbraDocumentSourceRef(val value: Long) {
    init {
        require(value > 0) { "Document source reference must be positive" }
    }
}

/** Opaque process-local export capability identity; never a destination URI or path. */
@JvmInline
internal value class OmbraExportDestinationRef(val value: Long) {
    init {
        require(value > 0) { "Export destination reference must be positive" }
    }
}

/** Privacy-safe completion metadata for a newly generated PDF. */
internal data class OmbraExportReceipt(val pageCount: Int, val byteCount: Long) {
    init {
        require(pageCount > 0) { "Export pageCount must be positive" }
        require(byteCount > 0) { "Export byteCount must be positive" }
    }
}
