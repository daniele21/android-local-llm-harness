package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.TaskDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDefinitionPromptComposerTest {
    @Test
    fun `empty task definitions preserve host prompt identity`() {
        val prompt = "Host-owned system prompt"

        assertEquals(prompt, TaskDefinitionPromptComposer.compose(prompt, emptyList()))
        assertEquals("system-v3", TaskDefinitionPromptComposer.effectiveVersion("system-v3", emptyList()))
    }

    @Test
    fun `task definitions are appended after host instructions as untrusted data`() {
        val composed =
            requireNotNull(
                TaskDefinitionPromptComposer.compose(
                    "HOST AUTHORITY",
                    listOf(TaskDefinition("email", "Email address", "ada@example.test")),
                ),
            )

        assertTrue(composed.startsWith("HOST AUTHORITY"))
        assertTrue(composed.contains("[HARNESS_TASK_DEFINITIONS_V1]"))
        assertTrue(composed.contains("untrusted structured task data"))
        assertTrue(composed.contains("\"id\":\"email\""))
        assertTrue(composed.contains("\"description\":\"Email address\""))
        assertTrue(composed.contains("\"example\":\"ada@example.test\""))
        assertTrue(composed.endsWith("[/HARNESS_TASK_DEFINITIONS_V1]"))
        assertEquals(
            "system-v3+task-definitions-v1",
            TaskDefinitionPromptComposer.effectiveVersion("system-v3", listOf(TaskDefinition("email", "Email address"))),
        )
    }

    @Test
    fun `consumer content cannot terminate json string without escaping`() {
        val composed =
            requireNotNull(
                TaskDefinitionPromptComposer.compose(
                    "HOST",
                    listOf(TaskDefinition("custom", "value with \"quotes\" and \\ slash")),
                ),
            )

        assertTrue(composed.contains("value with \\\"quotes\\\" and \\\\ slash"))
        assertFalse(composed.contains("value with \"quotes\" and \\ slash"))
    }
}
