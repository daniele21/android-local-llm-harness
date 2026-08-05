package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
if old in text:
    text = text.replace(old, new, 1)
else:
    anchor = '                        HarnessSecondaryButton("Remove model"'
    start = text.index(anchor)
    end_marker = '                            afterPlaygroundRuntimeReleased { controller.removeModel() }
                        }
'
    end = text.index(end_marker, start) + len(end_marker)
    text = text[:start] + new + text[end:]

main.write_text(text)
import java.io.File

class PhoneModelManagementControlTest {
    @Test
    fun verifiesWithoutExposingStoragePaths() {
        val store = FakeModelStore(mutableSetOf(DIGEST))
        val control = ModelStorePhoneModelManagementControl(store, { null }, { true })

        val outcome = control.verify(DIGEST)

        assertTrue(outcome.success)
        assertEquals("Model integrity verified", outcome.detail)
        assertFalse(outcome.detail.contains(File.separator))
    }

    @Test
    fun blocksRemovalOfSelectedOrLoadedModel() {
        val store = FakeModelStore(mutableSetOf(DIGEST))
        val control = ModelStorePhoneModelManagementControl(store, { DIGEST }, { true })

        val outcome = control.remove(DIGEST)

        assertFalse(outcome.success)
        assertEquals("Selected or loaded model cannot be removed", outcome.detail)
        assertTrue(store.contains(DIGEST))
    }

    @Test
    fun removesUnprotectedModelAndItsMetadata() {
        val store = FakeModelStore(mutableSetOf(DIGEST))
        val cleaned = mutableListOf<ModelDigest>()
        val control = ModelStorePhoneModelManagementControl(
  modelStore = store,
  protectedModelDigest = { null },
  removeMetadata = { digest -> cleaned += digest; true },
        )

        val outcome = control.remove(DIGEST)

        assertTrue(outcome.success)
        assertFalse(store.contains(DIGEST))
        assertEquals(listOf(DIGEST), cleaned)
    }

    private class FakeModelStore(private val digests: MutableSet<ModelDigest>) : ModelStore {
        fun contains(digest: ModelDigest): Boolean = digest in digests

        override fun find(digest: ModelDigest): StoredModel? = null

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

        override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
  valid = digest in digests,
  actualDigest = digest.takeIf(digests::contains),
  detail = "internal-path-must-not-surface",
        )

        override fun remove(digest: ModelDigest): Boolean = digests.remove(digest)

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0L, emptyList())
    }

    private companion object {
        val DIGEST = ModelDigest("b".repeat(64))
    }
}
