package io.github.daniele21.localllm.console

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.health.ModelIntegrityHealthCheck
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.transport.binder.client.BinderLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig
import java.util.concurrent.Executors

@Suppress("MagicNumber", "TooManyFunctions")
class MainActivity : Activity() {
    private val presenter = ConsolePresenter()
    private val healthPresenter = ConsoleHealthPresenter()
    private val resourceChartPresenter = ConsoleResourceChartPresenter()
    private val telemetryRepository by lazy { InMemoryTelemetryRepository() }
    private val modelStore by lazy { FileSystemModelStore(filesDir) }
    private val healthControl: ConsoleHealthControl by lazy {
        HealthEngineConsoleHealthControl(
            healthEngine = HealthEngine(
                checks = listOf(ModelIntegrityHealthCheck(modelStore)),
                telemetryRepository = telemetryRepository,
            ),
            source = "Local console sandbox",
        )
    }
    private val cacheControl: ConsoleCacheControl = DisconnectedCacheControl
    private val binderClient: BinderLocalLlmClient by lazy {
        BinderLocalLlmClient.create(
            context = this,
            hostConfig = SharedRuntimeHostConfig.create(
                BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
            ),
            applicationId = CONSOLE_APPLICATION_ID,
            clientBuildId = "console-${BuildConfig.VERSION_NAME}",
            observer = SharedRuntimeConnectionObserver { publishSharedRuntimeConnectionState() },
        )
    }
    private val inferenceControl: SharedRuntimeConsoleInferenceControl by lazy {
        SharedRuntimeConsoleInferenceControl(
            client = binderClient,
            targets = listOf(CONSOLE_INFERENCE_TARGET),
        )
    }
    private val inferenceDialog by lazy { ConsoleInferenceRequestDialog(this) }
    private val dataSource: ConsoleDataSource by lazy {
        TelemetryConsoleDataSource(
            telemetryRepository = telemetryRepository,
            modelInventoryProvider = ModelStoreInventoryProvider(
                modelStore = modelStore,
                source = "Local console sandbox",
            ),
            healthControl = healthControl,
            cacheControl = cacheControl,
            inferenceControl = inferenceControl,
        )
    }
    private val diagnosticExecutor = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var updatedAt: TextView
    private lateinit var backButton: Button
    private var selectedTab: ConsoleTab = ConsoleTab.OVERVIEW
    private var snapshot: ConsoleSnapshot? = null
    private var requestDetail: ConsoleRequestDetail? = null
    private var actionExecutionInProgress = false
    private var activeActionType: ConsoleActionType? = null
    private var healthExecutionError: String? = null
    private var cacheExecutionError: String? = null
    private var lastCacheRepair: ConsoleCacheRepairOutcome? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsole())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) refresh()
    }

    override fun onDestroy() {
        inferenceControl.close()
        diagnosticExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildConsole(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 40, 32, 24)

        addView(label("Local LLM Console", 30f, bold = true))
        addView(label("Android local inference control plane", 16f))
        addView(
            label(
                "Local diagnostics remain sandbox-owned. The Playground can explicitly connect to the protected " +
                    "shared Android runtime; opening or refreshing this screen never binds, prepares or loads a model.",
                14f,
            ).apply { setPadding(0, 8, 0, 16) },
        )
        addView(buildActions())
        addView(buildTabs())

        updatedAt = label("Not refreshed", 13f).apply {
            gravity = Gravity.END
            setPadding(0, 12, 0, 8)
        }
        addView(updatedAt)

        content = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 48)
        }
        addView(
            ScrollView(this@MainActivity).apply { addView(content) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
    }

    private fun buildActions(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        backButton = Button(this@MainActivity).apply {
            text = "Back"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                requestDetail = null
                render()
            }
        }
        addView(backButton)
        addView(
            Button(this@MainActivity).apply {
                text = "Refresh"
                isAllCaps = false
                setOnClickListener { refresh() }
            },
        )
    }

    private fun buildTabs(): HorizontalScrollView = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                ConsoleTab.entries.forEach { tab ->
                    addView(
                        Button(this@MainActivity).apply {
                            text = tab.label
                            isAllCaps = false
                            setOnClickListener {
                                requestDetail = null
                                selectedTab = tab
                                render()
                            }
                        },
                    )
                }
            },
        )
    }

    private fun refresh() {
        snapshot = dataSource.load()
        requestDetail = requestDetail?.let { detail -> dataSource.loadRequest(detail.requestId) }
        render()
    }

    private fun render() {
        val displaySnapshot = displaySnapshot() ?: return
        val screen = screenFor(displaySnapshot)
        backButton.visibility = if (requestDetail == null) View.GONE else View.VISIBLE
        updatedAt.text = "Captured ${displaySnapshot.capturedAtEpochMs}"
        content.removeAllViews()
        content.addView(label(screen.title, 22f, bold = true).apply { setPadding(0, 20, 0, 10) })
        content.addView(label(screen.subtitle, 14f).apply { setPadding(0, 0, 0, 12) })
        screen.actions.forEach { action -> content.addView(actionButton(action)) }
        screen.charts.forEach { chart ->
            content.addView(
                ConsoleChartView(this, chart),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 0, 0, 20) },
            )
        }
        screen.cards.forEach { card -> content.addView(card(card)) }
    }

    private fun displaySnapshot(): ConsoleSnapshot? {
        val currentSnapshot = snapshot ?: return null
        val healthActionInProgress = activeActionType?.let(HEALTH_ACTION_TYPES::contains) == true
        return currentSnapshot.copy(
            healthControl = currentSnapshot.healthControl.copy(
                executionInProgress = actionExecutionInProgress && healthActionInProgress,
                sourceError = healthExecutionError ?: currentSnapshot.healthControl.sourceError,
            ),
            cacheControl = currentSnapshot.cacheControl.copy(
                executionInProgress = actionExecutionInProgress && activeActionType == ConsoleActionType.REPAIR_CACHE,
                lastRepair = lastCacheRepair,
                sourceError = cacheExecutionError ?: currentSnapshot.cacheControl.sourceError,
            ),
        )
    }

    private fun screenFor(displaySnapshot: ConsoleSnapshot): ConsoleScreen {
        requestDetail?.let { detail -> return presenter.presentRequestDetail(detail) }
        val baseScreen = if (selectedTab == ConsoleTab.HEALTH) {
            healthPresenter.present(displaySnapshot)
        } else {
            presenter.present(selectedTab, displaySnapshot)
        }
        return if (selectedTab == ConsoleTab.RESOURCES) {
            baseScreen.copy(
                subtitle = "Persisted memory and thermal trends from explicit resource captures",
                charts = resourceChartPresenter.charts(displaySnapshot.resources),
            )
        } else {
            baseScreen
        }
    }

    private fun actionButton(action: ConsoleAction): Button = Button(this).apply {
        text = action.label
        isAllCaps = false
        isEnabled = action.enabled
        setOnClickListener { dispatchAction(action) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, 12) }
    }

    private fun dispatchAction(action: ConsoleAction) {
        if (actionExecutionInProgress || !action.enabled) return
        if (action.type == ConsoleActionType.START_INFERENCE) {
            snapshot?.inference?.let { state ->
                inferenceDialog.show(state, ::executeInferenceStart)
            }
        } else {
            executeAction(action)
        }
    }

    private fun executeInferenceStart(request: ConsoleInferenceRequest) {
        if (actionExecutionInProgress) return
        beginAction(ConsoleActionType.START_INFERENCE)
        diagnosticExecutor.execute {
            val outcome = runCatching {
                inferenceControl.start(
                    request,
                    ConsoleInferenceListener(::publishInferenceState),
                )
            }.getOrElse {
                ConsoleInferenceOperationOutcome(
                    success = false,
                    state = inferenceControl.snapshot().copy(sourceError = INFERENCE_SOURCE_ERROR),
                    sourceError = INFERENCE_SOURCE_ERROR,
                )
            }
            publishCompletion(ConsoleActionCompletion(inferenceOutcome = outcome))
        }
    }

    private fun executeAction(action: ConsoleAction) {
        beginAction(action.type)
        diagnosticExecutor.execute {
            publishCompletion(performAction(action))
        }
    }

    private fun publishCompletion(completion: ConsoleActionCompletion) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            completeAction(completion)
            refresh()
        }
    }

    private fun publishInferenceState(state: ConsoleInferenceState) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            snapshot = snapshot?.copy(
                capturedAtEpochMs = System.currentTimeMillis(),
                inference = state,
            )
            render()
        }
    }

    private fun publishSharedRuntimeConnectionState() {
        runOnUiThread {
            if (isFinishing || isDestroyed || !::content.isInitialized) return@runOnUiThread
            snapshot = snapshot?.copy(
                capturedAtEpochMs = System.currentTimeMillis(),
                inference = inferenceControl.snapshot(),
            )
            render()
        }
    }

    private fun beginAction(type: ConsoleActionType) {
        actionExecutionInProgress = true
        activeActionType = type
        if (type in HEALTH_ACTION_TYPES) healthExecutionError = null
        if (type == ConsoleActionType.REPAIR_CACHE) cacheExecutionError = null
        render()
    }

    private fun performAction(action: ConsoleAction): ConsoleActionCompletion = when (action.type) {
        ConsoleActionType.RUN_ALL_HEALTH_CHECKS -> ConsoleActionCompletion(
            healthError = healthControl.runAll().sourceError,
        )

        ConsoleActionType.RUN_HEALTH_CHECKS -> ConsoleActionCompletion(
            healthError = healthControl.run(action.healthCheckIds).sourceError,
        )

        ConsoleActionType.REPAIR_CACHE -> ConsoleActionCompletion(
            cacheRepair = cacheControl.repair(requireNotNull(action.cacheId)),
        )

        ConsoleActionType.CONNECT_SHARED_RUNTIME -> ConsoleActionCompletion(
            inferenceOutcome = inferenceControl.connect(),
        )

        ConsoleActionType.CANCEL_INFERENCE -> ConsoleActionCompletion(
            inferenceOutcome = inferenceControl.cancel(),
        )

        ConsoleActionType.CLEAR_INFERENCE -> ConsoleActionCompletion(
            inferenceOutcome = inferenceControl.clear(),
        )

        ConsoleActionType.START_INFERENCE -> error("Inference start requires a request dialog")
    }

    private fun completeAction(completion: ConsoleActionCompletion) {
        actionExecutionInProgress = false
        activeActionType = null
        completion.healthError?.let { error -> healthExecutionError = error }
        completion.cacheRepair?.let { outcome ->
            lastCacheRepair = outcome
            cacheExecutionError = outcome.sourceError
        }
        completion.inferenceOutcome?.let { outcome ->
            snapshot = snapshot?.copy(inference = outcome.state)
        }
    }

    private fun card(card: ConsoleCard): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 20, 24, 20)
        addView(label("${card.emphasis.label} ${card.title}", 17f, bold = true))
        card.lines.forEach { line ->
            addView(label(line, 14f).apply { setPadding(0, 5, 0, 0) })
        }
        card.openRequestId?.let { requestId ->
            isClickable = true
            isFocusable = true
            addView(label("Open request timeline", 13f).apply { setPadding(0, 10, 0, 0) })
            setOnClickListener { openRequest(requestId) }
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(0, 0, 0, 16)
        }
    }

    private fun openRequest(requestId: RequestId) {
        requestDetail = dataSource.loadRequest(requestId)
        render()
    }

    private fun label(text: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private companion object {
        val CONSOLE_APPLICATION_ID = ApplicationId("local-llm-console")
        val CONSOLE_INFERENCE_TARGET = ConsoleInferenceTarget(
            applicationId = CONSOLE_APPLICATION_ID,
            useCaseId = UseCaseId("console-inference-playground"),
            label = "Shared host playground",
        )
        const val INFERENCE_SOURCE_ERROR = "Inference playground unavailable"
        val HEALTH_ACTION_TYPES = setOf(
            ConsoleActionType.RUN_ALL_HEALTH_CHECKS,
            ConsoleActionType.RUN_HEALTH_CHECKS,
        )
    }
}

private data class ConsoleActionCompletion(
    val healthError: String? = null,
    val cacheRepair: ConsoleCacheRepairOutcome? = null,
    val inferenceOutcome: ConsoleInferenceOperationOutcome? = null,
)

private val ConsoleEmphasis.label: String
    get() = when (this) {
        ConsoleEmphasis.NEUTRAL -> "[INFO]"
        ConsoleEmphasis.POSITIVE -> "[PASS]"
        ConsoleEmphasis.WARNING -> "[WARN]"
        ConsoleEmphasis.NEGATIVE -> "[FAIL]"
    }
