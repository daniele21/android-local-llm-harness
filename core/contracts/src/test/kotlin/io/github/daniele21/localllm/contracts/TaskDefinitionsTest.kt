package io.github.daniele21.localllm.contracts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDefinitionsTest {
    @Test
    fun `task definition string representation does not expose consumer content`() {
        val definition =
            TaskDefinition(
                id = "private-person",
                description = "A person's full identifying name",
                example = "Ada Example",
            )

        val rendered = definition.toString()

        assertTrue(rendered.contains("private-person"))
        assertFalse(rendered.contains("full identifying name"))
        assertFalse(rendered.contains("Ada Example"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate task definition ids are rejected`() {
        val definition = TaskDefinition("email", "An email address")
        TaskDefinitionLimits.validate(listOf(definition, definition))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported control characters are rejected`() {
        TaskDefinition("email", "An email\u0000address")
    }

    @Test
    fun `generation request string does not expose input or task definition content`() {
        val request =
            ConsumerGenerationRequest(
                requestId = RequestId("request-1"),
                sessionId = SessionId("session-1"),
                input = ConsumerGenerationInput.Text("Secret source text"),
                outputConstraint = ConsumerOutputConstraint.Json,
                taskDefinitions = listOf(TaskDefinition("email", "An email address", "ada@example.test")),
            )

        val rendered = request.toString()

        assertFalse(rendered.contains("Secret source text"))
        assertFalse(rendered.contains("An email address"))
        assertFalse(rendered.contains("ada@example.test"))
        assertTrue(rendered.contains("taskDefinitionCount=1"))
    }
}
