package io.github.daniele21.localllm.console.document

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmbraPdfOpenDocumentCapabilityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun pickerIsPdfOnlyAndRequestsOnlyTransientReadAccess() {
        val capability = OmbraPdfOpenDocumentCapability(OmbraDocumentSourceRegistry(context))

        val intent = capability.createIntent()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(OmbraDocumentSourceRegistry.PDF_MIME_TYPE, intent.type)
        assertTrue(Intent.CATEGORY_OPENABLE in intent.categories.orEmpty())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    }

    @Test
    fun selectedContentUriBecomesOpaqueCapabilityAndResetCleanupCanReleaseIt() {
        val registry = OmbraDocumentSourceRegistry(context)
        val capability = OmbraPdfOpenDocumentCapability(registry)
        val uri = Uri.parse("content://ombra.test/documents/42")

        val sourceRef = capability.registerResult(uri)

        assertNotNull(sourceRef)
        assertEquals(uri, registry.resolve(requireNotNull(sourceRef))?.uri)
        registry.releaseAll()
        assertNull(registry.resolve(sourceRef))
    }

    @Test
    fun cancelledPickerDoesNotCreateSourceCapability() {
        val capability = OmbraPdfOpenDocumentCapability(OmbraDocumentSourceRegistry(context))

        assertNull(capability.registerResult(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pickerResultRejectsNonContentUri() {
        val capability = OmbraPdfOpenDocumentCapability(OmbraDocumentSourceRegistry(context))

        capability.registerResult(Uri.parse("file:///tmp/source.pdf"))
    }
}
