package io.github.daniele21.localllm.phonetest

import android.content.ComponentName
import android.content.pm.PermissionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
