package io.github.daniele21.localllm.console.document

import android.content.Intent
import android.net.Uri
import io.github.daniele21.localllm.console.application.OmbraExportDestinationRef

/** PDF-only Storage Access Framework destination capability for newly generated OMBRA output. */
internal class OmbraCreateDocumentCapability(private val destinationRegistry: OmbraExportDestinationRegistry) {
    fun createIntent(suggestedName: String = DEFAULT_FILE_NAME): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = PDF_MIME_TYPE
        putExtra(Intent.EXTRA_TITLE, sanitizeSuggestedName(suggestedName))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun registerResult(uri: Uri?): OmbraExportDestinationRef? {
        if (uri == null) return null
        require(uri.scheme == "content") { "CreateDocument must return a content Uri" }
        return destinationRegistry.register(uri)
    }

    private fun sanitizeSuggestedName(value: String): String {
        val base = value.trim().take(MAX_FILE_NAME_CHARACTERS).ifEmpty { DEFAULT_FILE_NAME }
        val safe = base.map { character -> if (character.isLetterOrDigit() || character in SAFE_FILE_NAME_CHARACTERS) character else '_' }
            .joinToString(separator = "")
            .trim('.')
            .ifEmpty { DEFAULT_FILE_NAME }
        return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val DEFAULT_FILE_NAME = "ombra-redacted.pdf"
        const val MAX_FILE_NAME_CHARACTERS = 96
        val SAFE_FILE_NAME_CHARACTERS = setOf('-', '_', '.', ' ')
    }
}
