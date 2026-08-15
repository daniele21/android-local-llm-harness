package io.github.daniele21.localllm.console.application

import java.io.IOException

internal enum class OmbraDocumentExtractionFailureCode {
    SOURCE_NOT_FOUND,
    SOURCE_UNREADABLE,
    ENCRYPTED_PDF,
    IMAGE_ONLY_PDF,
    EMPTY_PDF,
    LIMIT_EXCEEDED,
    PARSER_FAILED,
}

/** Content-free failure surfaced by the production OMBRA document extractor boundary. */
internal class OmbraDocumentExtractionException(val code: OmbraDocumentExtractionFailureCode) :
    IOException("OMBRA document extraction failed: $code") {
    override fun toString(): String = "OmbraDocumentExtractionException(code=$code)"
}
