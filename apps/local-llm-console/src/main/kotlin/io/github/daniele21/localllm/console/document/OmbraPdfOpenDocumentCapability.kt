package io.github.daniele21.localllm.console.document

import android.content.Intent
import android.net.Uri
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceRef

/**
 * PDF-only Storage Access Framework entry point for OMBRA imports.
 *
 * The picker requests only a transient read capability. Persistable and write permissions are
 * deliberately absent; the selected Uri is immediately wrapped in an opaque process-local source
 * reference and never enters workflow state.
 */
internal class OmbraPdfOpenDocumentCapability(private val sourceRegistry: OmbraDocumentSourceRegistry) {
    fun createIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = OmbraDocumentSourceRegistry.PDF_MIME_TYPE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun registerResult(uri: Uri?): OmbraDocumentSourceRef? {
        if (uri == null) return null
        require(uri.scheme == "content") { "OpenDocument must return a content Uri" }
        return sourceRegistry.register(uri)
    }
}
