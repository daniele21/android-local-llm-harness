package io.github.daniele21.localllm.console.document

import android.content.Context
import android.net.Uri
import io.github.daniele21.localllm.console.application.OmbraExportDestinationRef
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class OmbraExportDestination(val uri: Uri) {
    override fun toString(): String = "OmbraExportDestination(uri=<redacted>)"
}

internal fun interface OmbraExportDestinationResolver {
    fun resolve(destinationRef: OmbraExportDestinationRef): OmbraExportDestination?
}

/** Process-local destination capabilities; raw output Uri values never enter workflow state. */
internal class OmbraExportDestinationRegistry(context: Context) : OmbraExportDestinationResolver {
    private val applicationContext = context.applicationContext
    private val nextOrdinal = AtomicLong(1)
    private val destinations = ConcurrentHashMap<Long, OmbraExportDestination>()

    fun register(uri: Uri): OmbraExportDestinationRef {
        require(uri.scheme == "content" || uri.scheme == "file") { "Only content/file export destinations are supported" }
        val destinationRef = OmbraExportDestinationRef(nextOrdinal.getAndIncrement())
        check(destinations.putIfAbsent(destinationRef.value, OmbraExportDestination(uri)) == null) {
            "Duplicate export destination capability"
        }
        return destinationRef
    }

    override fun resolve(destinationRef: OmbraExportDestinationRef): OmbraExportDestination? = destinations[destinationRef.value]

    fun release(destinationRef: OmbraExportDestinationRef): Boolean = destinations.remove(destinationRef.value) != null

    fun deleteBestEffort(destinationRef: OmbraExportDestinationRef) {
        val destination = destinations[destinationRef.value] ?: return
        if (destination.uri.scheme == "content") {
            runCatching { applicationContext.contentResolver.delete(destination.uri, null, null) }
        } else if (destination.uri.scheme == "file") {
            destination.uri.path?.let { path -> runCatching { java.io.File(path).delete() } }
        }
    }

    fun clear() = destinations.clear()
}
