package io.github.daniele21.localllm.console.document

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmbraCreateDocumentCapabilityInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createDocumentIsPdfOnlyAndDoesNotRequestPersistableGrant() {
        val capability = OmbraCreateDocumentCapability(OmbraExportDestinationRegistry(context))

        val intent = capability.createIntent("Report finale")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/pdf", intent.type)
        assertTrue(Intent.CATEGORY_OPENABLE in intent.categories.orEmpty())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertEquals("Report finale.pdf", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun selectedDestinationBecomesOpaqueProcessLocalCapability() {
        val registry = OmbraExportDestinationRegistry(context)
        val capability = OmbraCreateDocumentCapability(registry)
        val uri = Uri.parse("content://ombra.test/output/42")

        val destinationRef = capability.registerResult(uri)

        assertNotNull(destinationRef)
        val registeredDestinationRef = requireNotNull(destinationRef)
        assertEquals(uri, registry.resolve(registeredDestinationRef)?.uri)
        assertTrue(registry.release(registeredDestinationRef))
        assertNull(registry.resolve(registeredDestinationRef))
    }

    @Test
    fun cancelledCreateDocumentDoesNotCreateCapability() {
        val capability = OmbraCreateDocumentCapability(OmbraExportDestinationRegistry(context))

        assertNull(capability.registerResult(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createDocumentRejectsNonContentPickerResult() {
        val capability = OmbraCreateDocumentCapability(OmbraExportDestinationRegistry(context))

        capability.registerResult(Uri.parse("file:///tmp/output.pdf"))
    }
}
