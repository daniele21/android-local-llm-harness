package io.github.daniele21.localllm.console.application

import java.io.IOException

internal enum class OmbraDocumentExportFailureCode {
    DESTINATION_NOT_FOUND,
    DESTINATION_UNWRITABLE,
    REVIEW_INCOMPLETE,
    REDACTION_CONFLICT,
    SOURCE_MISMATCH,
    OUTPUT_LIMIT_EXCEEDED,
    WRITER_FAILED,
}

/** Content-free export failure; source text, placeholders and destination Uri are intentionally absent. */
internal class OmbraDocumentExportException(val code: OmbraDocumentExportFailureCode) :
    IOException("OMBRA document export failed: $code") {
    override fun toString(): String = "OmbraDocumentExportException(code=$code)"
}
