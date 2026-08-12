package io.github.daniele21.localllm.phonetest

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.daniele21.localllm.integration.servicehost.SharedRuntimeHostComposition

/** Bound-only proof host for the shared local-LLM runtime. */
class HarnessSharedRuntimeService : Service() {
    private lateinit var hostComposition: SharedRuntimeHostComposition

    override fun onCreate() {
        super.onCreate()
        val graph = HarnessRuntimeGraph.from(this)
        hostComposition = SharedRuntimeHostComposition(
            context = this,
            client = graph.sharedRuntimeClient(),
            permissionName = BuildConfig.SHARED_RUNTIME_PERMISSION,
            policies = HarnessSharedRuntimePolicy.authorizedClients(this),
            hostBuildId = "phone-test-${BuildConfig.VERSION_NAME}",
        )
    }

    override fun onBind(intent: Intent?): IBinder = hostComposition.binder
}
