package io.github.daniele21.localllm.console

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.health.ModelIntegrityHealthCheck
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.FileSystemModelStore
import java.util.concurrent.Executors

@Suppress("MagicNumber", "TooManyFunctions")
class MainActivity : Activity() {
    private val presenter = ConsolePresenter()
    private val healthPresenter = ConsoleHealthPresenter()
    private val resourceChartPresenter = ConsoleResourceChartPresenter()
    private val telemetryRepository by lazy { InMemoryTelemetryRepository() }
    private val modelStore by lazy { FileSystemModelStore(filesDir) }
    private val modelControl: ConsoleModelControl by lazy {
        ModelStoreConsoleModelControl(
            modelStore = modelStore,
            source = "Local console sandbox",
        )
    }
    private val modelImportStager by lazy { AndroidModelImportStager(this) }
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
    private val dataSource: ConsoleDataSource by lazy {
        TelemetryConsoleDataSource(
            telemetryRepository = telemetryRepository,
            modelInventoryProvider = ModelStoreInventoryProvider(
                modelStore = modelStore,
                source = "Local console sandbox",
            ),
            modelControl = modelControl,
            healthControl = healthControl,
            cacheControl = cacheControl,
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
    private var modelExecutionError: String? = null
    private var lastCacheRepair: ConsoleCacheRepairOutcome? = null
    private var lastModelOperation: ConsoleModelOperationOutcome? = null
    private var pendingImportProfile: PendingModelProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsole())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) refresh()
    }

    @Deprecated("Deprecated in Android framework but retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL_DOCUMENT) return
        val profile = pendingImportProfile
        pendingImportProfile = null
        if (resultCode != RESULT_OK || profile == null) return
        data?.data?.let { uri -> executeModelImport(uri, profile) }
    }

    override fun onDestroy() {
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
                "Phase 2 observability, explicit sandbox model management and health controls. " +
                    "Runtime cache diagnostics and cross-application access remain disconnected until a real " +
                    "embedded source or signature-protected diagnostics bridge is supplied.",
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
        val modelActionInProgress = activeActionType?.let(MODEL_ACTION_TYPES::contains) == true
        return currentSnapshot.copy(
            modelControl = currentSnapshot.modelControl.copy(
                executionInProgress = actionExecutionInProgress && modelActionInProgress,
                lastOperation = lastModelOperation,
                sourceError = modelExecutionError ?: currentSnapshot.modelControl.sourceError,
            ),
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
        when (action.type) {
            ConsoleActionType.IMPORT_MODEL -> showImportProfileDialog()
            ConsoleActionType.REMOVE_MODEL -> confirmModelRemoval(action)
            else -> executeAction(action)
        }
    }

    private fun showImportProfileDialog() {
        val architectureInput = EditText(this).apply {
            hint = "Architecture"
            setText(DEFAULT_MODEL_ARCHITECTURE)
            isSingleLine = true
        }
        val quantizationInput = EditText(this).apply {
            hint = "Quantization"
            setText(DEFAULT_MODEL_QUANTIZATION)
            isSingleLine = true
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(architectureInput)
            addView(quantizationInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Import GGUF")
            .setMessage("Architecture and quantization describe the import artifact but are not persisted by ModelStore.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Select file") { _, _ ->
                pendingImportProfile = PendingModelProfile(
                    architecture = architectureInput.text.toString().trim().ifBlank { DEFAULT_MODEL_ARCHITECTURE },
                    quantization = quantizationInput.text.toString().trim().ifBlank { DEFAULT_MODEL_QUANTIZATION },
                )
                openModelDocument()
            }
            .show()
    }

    private fun openModelDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/octet-stream", "application/gguf", "application/x-gguf"),
            )
        }
        startActivityForResult(intent, REQUEST_MODEL_DOCUMENT)
    }

    private fun confirmModelRemoval(action: ConsoleAction) {
        val digest = requireNotNull(action.modelDigest)
        AlertDialog.Builder(this)
            .setTitle("Remove installed model?")
            .setMessage("This permanently removes ${digest.sha256} from the connected model store.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> executeAction(action) }
            .show()
    }

    private fun executeAction(action: ConsoleAction) {
        beginAction(action.type)
        diagnosticExecutor.execute {
            val completion = performAction(action)
            publishCompletion(completion)
        }
    }

    private fun executeModelImport(uri: Uri, profile: PendingModelProfile) {
        if (actionExecutionInProgress) return
        beginAction(ConsoleActionType.IMPORT_MODEL)
        diagnosticExecutor.execute {
            var request: ConsoleModelImportRequest? = null
            val outcome = try {
                request = modelImportStager.stage(uri, profile.architecture, profile.quantization)
                modelControl.importModel(request)
            } catch (_: RuntimeException) {
                ConsoleModelOperationOutcome(
                    operation = ConsoleModelOperation.IMPORT,
                    digest = request?.digest,
                    success = false,
                    detail = MODEL_MANAGEMENT_ERROR,
                    sourceError = MODEL_MANAGEMENT_ERROR,
                )
            } finally {
                request?.source?.delete()
            }
            publishCompletion(ConsoleActionCompletion(modelOperation = outcome))
        }
    }

    private fun publishCompletion(completion: ConsoleActionCompletion) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            completeAction(completion)
            refresh()
        }
    }

    private fun beginAction(type: ConsoleActionType) {
        actionExecutionInProgress = true
        activeActionType = type
        if (type in HEALTH_ACTION_TYPES) healthExecutionError = null
        if (type == ConsoleActionType.REPAIR_CACHE) cacheExecutionError = null
        if (type in MODEL_ACTION_TYPES) modelExecutionError = null
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

        ConsoleActionType.VERIFY_MODEL -> ConsoleActionCompletion(
            modelOperation = modelControl.verify(requireNotNull(action.modelDigest)),
        )

        ConsoleActionType.REMOVE_MODEL -> ConsoleActionCompletion(
            modelOperation = modelControl.remove(requireNotNull(action.modelDigest)),
        )

        ConsoleActionType.IMPORT_MODEL -> ConsoleActionCompletion(
            modelOperation = ConsoleModelOperationOutcome(
                operation = ConsoleModelOperation.IMPORT,
                digest = null,
                success = false,
                detail = MODEL_MANAGEMENT_ERROR,
                sourceError = MODEL_MANAGEMENT_ERROR,
            ),
        )
    }

    private fun completeAction(completion: ConsoleActionCompletion) {
        actionExecutionInProgress = false
        activeActionType = null
        completion.healthError?.let { error -> healthExecutionError = error }
        completion.cacheRepair?.let { outcome ->
            lastCacheRepair = outcome
            cacheExecutionError = outcome.sourceError
        }
        completion.modelOperation?.let { outcome ->
            lastModelOperation = outcome
            modelExecutionError = outcome.sourceError
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
        const val REQUEST_MODEL_DOCUMENT = 8101
        const val DEFAULT_MODEL_ARCHITECTURE = "unknown"
        const val DEFAULT_MODEL_QUANTIZATION = "unknown"
        const val MODEL_MANAGEMENT_ERROR = "Model management unavailable"
        val HEALTH_ACTION_TYPES = setOf(
            ConsoleActionType.RUN_ALL_HEALTH_CHECKS,
            ConsoleActionType.RUN_HEALTH_CHECKS,
        )
        val MODEL_ACTION_TYPES = setOf(
            ConsoleActionType.IMPORT_MODEL,
            ConsoleActionType.VERIFY_MODEL,
            ConsoleActionType.REMOVE_MODEL,
        )
    }
}

private data class PendingModelProfile(val architecture: String, val quantization: String)

private data class ConsoleActionCompletion(
    val healthError: String? = null,
    val cacheRepair: ConsoleCacheRepairOutcome? = null,
    val modelOperation: ConsoleModelOperationOutcome? = null,
)

private val ConsoleEmphasis.label: String
    get() = when (this) {
        ConsoleEmphasis.NEUTRAL -> "[INFO]"
        ConsoleEmphasis.POSITIVE -> "[PASS]"
        ConsoleEmphasis.WARNING -> "[WARN]"
        ConsoleEmphasis.NEGATIVE -> "[FAIL]"
    }
