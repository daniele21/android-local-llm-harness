package io.github.daniele21.localllm.console.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceRef
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class OmbraDocumentSource(val uri: Uri, val displayName: String) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }

    override fun toString(): String = "OmbraDocumentSource(uri=<redacted>, displayName=<redacted>)"
}

internal fun interface OmbraDocumentSourceResolver {
    fun resolve(sourceRef: OmbraDocumentSourceRef): OmbraDocumentSource?
}

/**
 * Process-local capability registry for one-shot document picker results.
 *
 * Raw Uri values never enter workflow state. The registry intentionally does not request or retain
 * persistable URI permissions; callers release capabilities after reset/process completion.
 */
internal class OmbraDocumentSourceRegistry(context: Context) : OmbraDocumentSourceResolver {
    private val applicationContext = context.applicationContext
    private val nextOrdinal = AtomicLong(1)
    private val sources = ConcurrentHashMap<Long, OmbraDocumentSource>()

    fun register(uri: Uri): OmbraDocumentSourceRef {
        require(uri.scheme == "content" || uri.scheme == "file") { "Only content/file document sources are supported" }
        val sourceRef = OmbraDocumentSourceRef(nextOrdinal.getAndIncrement())
        val source = OmbraDocumentSource(uri = uri, displayName = resolveDisplayName(uri))
        check(sources.putIfAbsent(sourceRef.value, source) == null) { "Duplicate document source capability" }
        return sourceRef
    }

    override fun resolve(sourceRef: OmbraDocumentSourceRef): OmbraDocumentSource? = sources[sourceRef.value]

    fun release(sourceRef: OmbraDocumentSourceRef): Boolean = sources.remove(sourceRef.value) != null

    fun clear() = sources.clear()

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: DEFAULT_DISPLAY_NAME
    }

    companion object {
        val OPEN_DOCUMENT_MIME_TYPES: Array<String>
            get() = arrayOf(PDF_MIME_TYPE)

        const val PDF_MIME_TYPE = "application/pdf"
        private const val DEFAULT_DISPLAY_NAME = "document.pdf"
    }
}
