package io.github.daniele21.localllm.console.analysis

import android.content.Context
import io.github.daniele21.localllm.console.BuildConfig
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionSnapshot
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Production OMBRA analysis composition over the packaged Consumer API Binder client.
 *
 * Connection lifecycle stays outside the pure OMBRA reducer: OMB-7 can drive [connect] as an
 * exactly-once Android effect before analysis. The analysis path itself sees only the
 * application-owned [OmbraAnalysisClient] contract and never receives model identity, raw runtime
 * tuning or AIDL types.
 */
internal class OmbraBinderAnalysisComposition private constructor(
    private val consumerClient: BinderConsumerLocalLlmClient,
    private val lifecycleExecutor: ExecutorService,
) : OmbraAnalysisClient,
    AutoCloseable {
    private val delegate =
        OmbraSequentialAnalysisClient(
            OmbraConsumerAnalysisChunkClient(
                client = consumerClient,
                lifecycleExecutor = lifecycleExecutor,
                transportConnected = {
                    consumerClient.connectionSnapshot.state == SharedRuntimeConnectionState.CONNECTED
                },
            ),
        )

    val connectionSnapshot: SharedRuntimeConnectionSnapshot
        get() = consumerClient.connectionSnapshot

    fun connect() {
        consumerClient.connect()
    }

    override fun analyze(
        operationId: OmbraOperationId,
        request: OmbraAnalysisRequest,
        onResult: (Result<List<ValidatedFinding>>) -> Unit,
    ) {
        delegate.analyze(operationId, request, onResult)
    }

    override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
        delegate.cancel(operationId, onCancelled)
    }

    override fun close() {
        consumerClient.close()
        lifecycleExecutor.shutdownNow()
    }

    companion object {
        fun create(
            context: Context,
            observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
        ): OmbraBinderAnalysisComposition {
            val consumerClient =
                BinderConsumerLocalLlmClient.create(
                    context = context.applicationContext,
                    hostConfig =
                    SharedRuntimeHostConfig.create(
                        BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                        BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
                    ),
                    clientBuildId = "ombra-${BuildConfig.VERSION_NAME}",
                    observer = observer,
                )
            return OmbraBinderAnalysisComposition(
                consumerClient = consumerClient,
                lifecycleExecutor = Executors.newSingleThreadExecutor(),
            )
        }
    }
}
