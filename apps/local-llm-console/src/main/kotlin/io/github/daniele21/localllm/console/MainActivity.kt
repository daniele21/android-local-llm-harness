package io.github.daniele21.localllm.console

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildDashboardShell())
    }

    private fun buildDashboardShell(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
        }

        container.addView(label("Local LLM Console", 30f, bold = true))
        container.addView(label("Embedded runtime developer control plane", 16f))
        container.addView(section("Runtime overview"))
        container.addView(row("Status", "Control plane shell"))
        container.addView(row("Backend", "Runtime integration not connected"))
        container.addView(row("Loaded model", "Unavailable"))
        container.addView(row("Active sessions", "Unavailable"))
        container.addView(row("Queue", "Unavailable"))
        container.addView(section("Planned views"))
        container.addView(label("Apps · Models · Runs · Logs · Cache · Health · Benchmarks · Device", 16f))
        container.addView(section("Current implementation focus"))
        container.addView(
            label(
                "Connect Phase 2 telemetry, health and runtime-state sources. " +
                    "Physical-device GGUF validation remains a release gate.",
                16f,
            ),
        )

        return ScrollView(this).apply { addView(container) }
    }

    private fun section(text: String): TextView = label(text, 20f, bold = true).apply {
        setPadding(0, 40, 0, 12)
    }

    private fun row(name: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(label(name, 16f, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(value, 16f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setPadding(0, 8, 0, 8)
    }

    private fun label(text: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
}
