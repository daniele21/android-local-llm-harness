package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity :
    Activity(),
    PhoneTestListener {
    private lateinit var controller: PhoneTestController
    private lateinit var architectureInput: EditText
    private lateinit var quantizationInput: EditText
    private lateinit var selectButton: Button
    private lateinit var runButton: Button
    private lateinit var removeButton: Button
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var modelStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var resultText: TextView
    private lateinit var scrollView: ScrollView

    private var importedModel: ImportedPhoneModel? = null
    private var latestReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildContent())
        latestReport = savedInstanceState?.getString(STATE_REPORT).orEmpty()
        if (latestReport.isNotBlank()) {
            resultText.text = latestReport
            copyButton.isEnabled = true
            shareButton.isEnabled = true
        }
        controller = PhoneTestController(this, this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_REPORT, latestReport)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        controller.close()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android framework but retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL_DOCUMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        controller.importModel(
            uri = uri,
            architecture = architectureInput.text.toString(),
            quantization = quantizationInput.text.toString(),
        )
    }

    override fun onBusyChanged(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        selectButton.isEnabled = !busy
        runButton.isEnabled = !busy && importedModel != null
        removeButton.isEnabled = !busy && importedModel != null
        architectureInput.isEnabled = !busy
        quantizationInput.isEnabled = !busy
    }

    override fun onProgress(message: String) {
        operationStatus.text = message
        val existing = resultText.text.toString()
        if (existing.isBlank() || existing == latestReport) {
            resultText.text = message
        } else {
            resultText.append("\n$message")
        }
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onModelChanged(model: ImportedPhoneModel?) {
        importedModel = model
        if (model == null) {
            modelStatus.text = "No model imported"
        } else {
            architectureInput.setText(model.architecture)
            quantizationInput.setText(model.quantization)
            modelStatus.text = buildString {
                appendLine(model.fileName)
                append("${formatBytes(model.sizeBytes)} · ${model.digest.sha256.take(16)}…")
            }
        }
        runButton.isEnabled = model != null
        removeButton.isEnabled = model != null
    }

    override fun onReport(report: String) {
        latestReport = report
        resultText.text = report
        copyButton.isEnabled = true
        shareButton.isEnabled = true
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildContent(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        container.addView(label("Local LLM Phone Test", 28f, bold = true))
        container.addView(
            label(
                "Installable through Google Play internal testing. The GGUF stays on this device.",
                15f,
            ),
        )
        container.addView(section("Model profile"))
        architectureInput = input("Architecture", DEFAULT_ARCHITECTURE)
        quantizationInput = input("Quantization", DEFAULT_QUANTIZATION)
        container.addView(architectureInput)
        container.addView(quantizationInput)

        selectButton = actionButton("Select and import GGUF") { openModelDocument() }
        container.addView(selectButton)
        modelStatus = label("No model imported", 14f)
        modelStatus.setPadding(0, dp(12), 0, 0)
        container.addView(modelStatus)

        container.addView(section("Physical-device validation"))
        container.addView(
            label(
                "Runs generation, active cancellation and five load/generate/unload memory cycles. " +
                    "Keep the app open until it finishes.",
                14f,
            ),
        )
        runButton = actionButton("Run full validation") {
            latestReport = ""
            resultText.text = "Starting validation…"
            copyButton.isEnabled = false
            shareButton.isEnabled = false
            controller.runFullValidation()
        }.apply { isEnabled = false }
        removeButton = actionButton("Remove imported model") { controller.removeModel() }.apply {
            isEnabled = false
        }
        container.addView(runButton)
        container.addView(removeButton)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        container.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
                setMargins(0, dp(16), 0, dp(8))
            },
        )
        operationStatus = label("Ready", 14f, bold = true)
        container.addView(operationStatus)

        container.addView(section("Privacy-safe result"))
        resultText = label("No validation report yet", 13f).apply {
            setTypeface(Typeface.MONOSPACE)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xffeeeeee.toInt())
        }
        container.addView(resultText)

        copyButton = actionButton("Copy report") { copyReport() }.apply { isEnabled = false }
        shareButton = actionButton("Share report") { shareReport() }.apply { isEnabled = false }
        container.addView(copyButton)
        container.addView(shareButton)

        scrollView = ScrollView(this).apply { addView(container) }
        return scrollView
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

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Local LLM phone test", latestReport))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Local LLM physical-device validation")
                    putExtra(Intent.EXTRA_TEXT, latestReport)
                },
                "Share validation report",
            ),
        )
    }

    private fun section(text: String): TextView = label(text, 20f, bold = true).apply {
        setPadding(0, dp(28), 0, dp(10))
    }

    private fun input(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(0, dp(12), 0, 0)
        }
    }

    private fun label(text: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_MODEL_DOCUMENT = 7001
        const val STATE_REPORT = "report"
        const val DEFAULT_ARCHITECTURE = "qwen3"
        const val DEFAULT_QUANTIZATION = "Q4_K_M"
    }
}
