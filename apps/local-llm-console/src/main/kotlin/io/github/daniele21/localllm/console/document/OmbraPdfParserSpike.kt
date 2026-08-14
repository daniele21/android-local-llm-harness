package io.github.daniele21.localllm.console.document

import android.content.Context
import android.net.Uri
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.SandboxedPdfLoader

/**
 * OMB-0A bounded parser spike.
 *
 * This is intentionally not the final OMBRA document-domain API. It proves that the selected
 * sandboxed AndroidX PDF path can expose page-ordered text without leaking Android/PDF objects
 * into later domain models. OMB-2 will replace this spike with the reviewed extractor boundary.
 */
@OptIn(ExperimentalPdfApi::class)
internal class OmbraPdfParserSpike(context: Context) {
    private val loader = SandboxedPdfLoader(context.applicationContext)

    suspend fun extractText(
        uri: Uri,
        maxPages: Int = DEFAULT_MAX_PAGES,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    ): OmbraPdfParserSpikeResult {
        require(maxPages > 0) { "maxPages must be positive" }
        require(maxCharacters > 0) { "maxCharacters must be positive" }

        val document = loader.openDocument(uri)
        return document.use { openedDocument ->
            val pagesToRead = minOf(openedDocument.pageCount, maxPages)
            val pages = ArrayList<OmbraPdfParserSpikePage>(pagesToRead)
            var remainingCharacters = maxCharacters
            var truncated = openedDocument.pageCount > pagesToRead

            for (pageIndex in 0 until pagesToRead) {
                if (remainingCharacters == 0) {
                    truncated = true
                    break
                }

                val content = openedDocument.getPageContent(pageIndex)
                val pageText =
                    content
                        ?.textContents
                        .orEmpty()
                        .joinToString(separator = "\n") { textContent -> textContent.text }
                val boundedText = pageText.take(remainingCharacters)
                if (boundedText.length < pageText.length) {
                    truncated = true
                }
                pages +=
                    OmbraPdfParserSpikePage(
                        pageIndex = pageIndex,
                        text = boundedText,
                    )
                remainingCharacters -= boundedText.length
            }

            OmbraPdfParserSpikeResult(
                pageCount = openedDocument.pageCount,
                pages = pages,
                truncated = truncated,
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_PAGES = 200
        const val DEFAULT_MAX_CHARACTERS = 1_000_000
    }
}

internal data class OmbraPdfParserSpikeResult(val pageCount: Int, val pages: List<OmbraPdfParserSpikePage>, val truncated: Boolean)

internal data class OmbraPdfParserSpikePage(val pageIndex: Int, val text: String)
