package io.github.daniele21.localllm.runtime

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration

fun interface SystemLowMemoryProbe {
    fun isLowMemory(): Boolean
}

class ActivityManagerLowMemoryProbe(
    context: Context,
) : SystemLowMemoryProbe {
    private val activityManager = context.applicationContext.getSystemService(ActivityManager::class.java)

    override fun isLowMemory(): Boolean {
        val manager = activityManager ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }
}

object AndroidTrimMemoryMapper {
    fun map(level: Int): RuntimeMemoryPressure? = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> RuntimeMemoryPressure.BACKGROUND
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> RuntimeMemoryPressure.UI_HIDDEN
        else -> null
    }
}

class AndroidMemoryPressureCallbacks(
    private val lowMemoryProbe: SystemLowMemoryProbe,
    private val onPressure: (RuntimeMemoryPressure) -> Unit,
) : ComponentCallbacks2 {
    constructor(
        context: Context,
        runtime: RuntimeOrchestrator,
    ) : this(
        lowMemoryProbe = ActivityManagerLowMemoryProbe(context),
        onPressure = { pressure -> runtime.handleMemoryPressure(pressure) },
    )

    override fun onTrimMemory(level: Int) {
        val pressure = if (lowMemoryProbe.isLowMemory()) {
            RuntimeMemoryPressure.LOW_MEMORY
        } else {
            AndroidTrimMemoryMapper.map(level)
        }
        pressure?.let(onPressure)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        onPressure(RuntimeMemoryPressure.LOW_MEMORY)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
