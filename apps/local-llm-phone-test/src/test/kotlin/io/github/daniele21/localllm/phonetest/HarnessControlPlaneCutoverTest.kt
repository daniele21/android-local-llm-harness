package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessControlPlaneCutoverTest {
    @Test
    fun `external consumer cannot fall back to phone selected model`() {
        val registry = HarnessPhoneBindingRegistry()
        val failure = runCatching {
            registry.resolve(ApplicationId("external-consumer"), UseCaseId("document-pii-detection"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("control-plane activation") == true)
    }
}
