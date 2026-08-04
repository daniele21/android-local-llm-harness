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
    private val healthControl: ConsoleHealthControl by lazy {
        HealthEngineConsoleHealthControl(
            healthEngine = HealthEngine(
                checks = listOf(ModelIntegrityHealthCheck(modelStore)),
                telemetryRepository = telemetryRepository,
            ),
            source = "Local console sandbox",
        )
    }
    private val dataSource: ConsoleDataSource by lazy {
        TelemetryConsoleDataSource(
            telemetryRepository = telemetryRepository,
            modelInventoryProvider = ModelStoreInventoryProvider(
                modelStore = modelStore,
                source = "Local console sandbox",
            ),
            healthControl = healthControl,
        )
    }
    private val healthExecutor = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var updatedAt: TextView
    private lateinit var backButton: Button
    private var selectedTab: ConsoleTab = ConsoleTab.OVERVIEW
    private var snapshot: ConsoleSnapshot? = null
    private var requestDetail: ConsoleRequestDetail? = null
    private var healthExecutionInProgress = false
    private var healthExecutionError: String? = null

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
        healthExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildConsole(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 40, 32, 24)

        addView(label("Local LLM Console", 30f, bold = true))
        addView(label("Android local inference control plane", 16f))
        addView(
            label(
                "Read-only Phase 2 observability, local model inventory and explicit sandbox health controls. " +
                    "Cross-application access remains disconnected until the signature-protected diagnostics " +
                    "bridge is implemented.",
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
        val currentSnapshot = snapshot ?: return
        val displaySnapshot = currentSnapshot.copy(
            healthControl = currentSnapshot.healthControl.copy(
                executionInProgress = healthExecutionInProgress,
                sourceError = healthExecutionError ?: currentSnapshot.healthControl.sourceError,
            ),
        )
        val currentDetail = requestDetail
        val baseScreen = when {
            currentDetail != null -> presenter.presentRequestDetail(currentDetail)
            selectedTab == ConsoleTab.HEALTH -> healthPresenter.present(displaySnapshot)
            else -> presenter.present(selectedTab, displaySnapshot)
        }
        val screen = if (currentDetail == null && selectedTab == ConsoleTab.RESOURCES) {
            baseScreen.copy(
                subtitle = "Persisted memory and thermal trends from explicit resource captures",
                charts = resourceChartPresenter.charts(displaySnapshot.resources),
            )
        } else {
            baseScreen
        }
        backButton.visibility = if (currentDetail == null) View.GONE else View.VISIBLE
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

    private fun actionButton(action: ConsoleAction): Button = Button(this).apply {
        text = action.label
        isAllCaps = false
        isEnabled = action.enabled
        setOnClickListener { executeHealthAction(action) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, 12) }
    }

    private fun executeHealthAction(action: ConsoleAction) {
        if (healthExecutionInProgress || !action.enabled) return
        healthExecutionInProgress = true
        healthExecutionError = null
        render()
        healthExecutor.execute {
            val outcome = when (action.type) {
                ConsoleActionType.RUN_ALL_HEALTH_CHECKS -> healthControl.runAll()
                ConsoleActionType.RUN_HEALTH_CHECKS -> healthControl.run(action.healthCheckIds)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                healthExecutionInProgress = false
                healthExecutionError = outcome.sourceError
                refresh()
            }
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
}

private val ConsoleEmphasis.label: String
    get() = when (this) {
        ConsoleEmphasis.NEUTRAL -> "[INFO]"
        ConsoleEmphasis.POSITIVE -> "[PASS]"
        ConsoleEmphasis.WARNING -> "[WARN]"
        ConsoleEmphasis.NEGATIVE -> "[FAIL]"
    }
