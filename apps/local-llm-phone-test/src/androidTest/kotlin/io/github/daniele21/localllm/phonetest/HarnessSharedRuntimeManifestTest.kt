package io.github.daniele21.localllm.phonetest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HarnessSharedRuntimeManifestTest {
    @Test
    fun sharedRuntimeServiceUsesBinderAuthorizationWithoutCustomBindPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, HarnessSharedRuntimeService::class.java),
            0,
        )

        assertTrue(serviceInfo.exported)
        assertEquals(context.packageName, serviceInfo.packageName)
        assertNull(serviceInfo.permission)
        listOf(RELEASE_BIND_PERMISSION, DEBUG_BIND_PERMISSION).forEach { permissionName ->
            try {
                packageManager.getPermissionInfo(permissionName, 0)
                fail("Shared-runtime bind permission must not be declared: $permissionName")
            } catch (_: PackageManager.NameNotFoundException) {
                // Expected: public reachability is install-order safe and Binder policy owns authority.
            }
        }
    }

    @Test
    fun bindingProofServiceDoesNotCreateRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = HarnessRuntimeGraph.from(context)
        graph.close()
        assertNull(graph.runtimeSnapshot())

        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent(context, HarnessSharedRuntimeService::class.java)

        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertNull(graph.runtimeSnapshot())
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 5L
        const val RELEASE_BIND_PERMISSION = "io.github.daniele21.localllm.permission.BIND_LOCAL_LLM"
        const val DEBUG_BIND_PERMISSION = "io.github.daniele21.localllm.debug.permission.BIND_LOCAL_LLM"
    }
}
