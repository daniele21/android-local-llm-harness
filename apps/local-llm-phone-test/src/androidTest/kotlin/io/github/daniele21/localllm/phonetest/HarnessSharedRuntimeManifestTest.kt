package io.github.daniele21.localllm.phonetest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PermissionInfo
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HarnessSharedRuntimeManifestTest {
    @Suppress("DEPRECATION")
    @Test
    fun `shared runtime service is exported behind the variant signature permission`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, HarnessSharedRuntimeService::class.java),
            0,
        )
        val permissionInfo = packageManager.getPermissionInfo(BuildConfig.SHARED_RUNTIME_PERMISSION, 0)

        assertTrue(serviceInfo.exported)
        assertEquals(context.packageName, serviceInfo.packageName)
        assertEquals(BuildConfig.SHARED_RUNTIME_PERMISSION, serviceInfo.permission)
        assertEquals(
            PermissionInfo.PROTECTION_SIGNATURE,
            permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE,
        )
    }

    @Test
    fun `binding proof service does not create a runtime`() {
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
    }
}
