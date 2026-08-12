package io.github.daniele21.localllm.transport.binder.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedRuntimeHostConfigTest {
    @Test
    fun `preserves fully qualified service class`() {
        val config = SharedRuntimeHostConfig.create(
            packageName = "io.github.example.host",
            serviceClassName = "io.github.example.host.SharedRuntimeService",
        )

        assertEquals("io.github.example.host", config.packageName)
        assertEquals("io.github.example.host.SharedRuntimeService", config.serviceClassName)
    }

    @Test
    fun `qualifies leading-dot service class against exact package`() {
        val config = SharedRuntimeHostConfig.create(
            packageName = "io.github.example.host.debug",
            serviceClassName = ".SharedRuntimeService",
        )

        assertEquals("io.github.example.host.debug.SharedRuntimeService", config.serviceClassName)
    }

    @Test
    fun `qualifies bare service class against exact package`() {
        val config = SharedRuntimeHostConfig.create(
            packageName = "io.github.example.host",
            serviceClassName = "SharedRuntimeService",
        )

        assertEquals("io.github.example.host.SharedRuntimeService", config.serviceClassName)
    }

    @Test
    fun `rejects blank or non-canonical host identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedRuntimeHostConfig.create("", "SharedRuntimeService")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SharedRuntimeHostConfig.create(" io.github.example.host", "SharedRuntimeService")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SharedRuntimeHostConfig.create("io.github.example.host", "Shared Runtime Service")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SharedRuntimeHostConfig.create("io.github.example.host/other", "SharedRuntimeService")
        }
    }
}
