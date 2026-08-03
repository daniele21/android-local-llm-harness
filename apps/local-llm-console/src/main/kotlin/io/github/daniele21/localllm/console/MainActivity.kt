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
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository

@Suppress("MagicNumber")
class MainActivity : Activity() {
    private val presenter = ConsolePresenter()
    private val dataSource: ConsoleDataSource = TelemetryConsoleDataSource(
        telemetryRepository = InMemoryTelemetryRepository(),
    )
    private lateinit var content: LinearLayout
    private lateinit var updatedAt: TextView
    private lateinit var backButton: Button
    private var selectedTab: ConsoleTab = ConsoleTab.OVERVIEW
    private var snapshot: ConsoleSnapshot? = null
    private var requestDetail: ConsoleRequestDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsole())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) refresh()
    }

    private fun buildConsole(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 40, 32, 24)

        addView(label("Local LLM Console", 30f, bold = true))
        addView(label("Android local inference control plane", 16f))
        addView(
            label(
                "Read-only Phase 2 observability. Cross-application access remains disconnected " +
                    "until the signature-protected diagnostics bridge is implemented.",
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
        val currentDetail = requestDetail
        val screen = currentDetail?.let(presenter::presentRequestDetail)
            ?: presenter.present(selectedTab, currentSnapshot)
        backButton.visibility = if (currentDetail == null) View.GONE else View.VISIBLE
        updatedAt.text = "Captured ${currentSnapshot.capturedAtEpochMs}"
        content.removeAllViews()
        content.addView(section(screen.title))
        content.addView(label(screen.subtitle, 14f).apply { setPadding(0, 0, 0, 12) })
        screen.cards.forEach { card -> content.addView(card(card)) }
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

    private fun section(text: String): TextView = label(text, 22f, bold = true).apply {
        setPadding(0, 20, 0, 10)
    }

    private fun label(text: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private val ConsoleEmphasis.label: String
        get() = when (this) {
            ConsoleEmphasis.NEUTRAL -> "[INFO]"
            ConsoleEmphasis.POSITIVE -> "[PASS]"
            ConsoleEmphasis.WARNING -> "[WARN]"
            ConsoleEmphasis.NEGATIVE -> "[FAIL]"
        }
}
