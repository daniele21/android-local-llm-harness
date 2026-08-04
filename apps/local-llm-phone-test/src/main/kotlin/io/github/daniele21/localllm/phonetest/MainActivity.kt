package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
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

@Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod")
class MainActivity :
    Activity(),
    PhoneTestListener {
    private lateinit var controller: PhoneTestController
    private lateinit var playgroundController: PhonePlaygroundController
    private lateinit var architectureInput: EditText
    private lateinit var quantizationInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var maxTokensInput: EditText
    private lateinit var temperatureInput: EditText
    private lateinit var seedInput: EditText
    private lateinit var selectButton: Button
    private lateinit var validationButton: Button
    private lateinit var removeButton: Button
    private lateinit var playgroundRunButton: Button
    private lateinit var playgroundCancelButton: Button
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var modelStatus: TextView
    private lateinit var playgroundStatus: TextView
    private lateinit var playgroundOutput: TextView
    private lateinit var playgroundMetrics: TextView
    private lateinit var operationStatus: TextView
    private lateinit var resultText: TextView
    private lateinit var scrollView: ScrollView

    private var importedModel: ImportedPhoneModel? = null
    private var latestReport: String = ""
    private var controllerBusy: Boolean = false
    private var playgroundState: PlaygroundState = PlaygroundState()

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
        playgroundController = PhonePlaygroundController(this, ::onPlaygroundStateChanged)
        onPlaygroundStateChanged(playgroundController.snapshot())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_REPORT, latestReport)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        playgroundController.close()
        controller.close()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android framework but retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL_DOCUMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        afterPlaygroundRuntimeReleased {
            controller.importModel(
                uri = uri,
                architecture = architectureInput.text.toString(),
                quantization = quantizationInput.text.toString(),
            )
        }
    }

    override fun onBusyChanged(busy: Boolean) {
        controllerBusy = busy
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        updateControls()
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
        updateControls()
    }

    override fun onReport(report: String) {
        latestReport = report
        resultText.text = report
        copyButton.isEnabled = true
        shareButton.isEnabled = true
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onPlaygroundStateChanged(state: PlaygroundState) {
        runOnUiThread {
            playgroundState = state
            playgroundStatus.text = state.detail
            playgroundOutput.text = when {
                state.output.isNotBlank() && state.outputTruncated -> state.output + "\n\n[Output truncated]"
                state.output.isNotBlank() -> state.output
                state.phase == PlaygroundPhase.IDLE -> "No playground output yet"
                else -> state.detail
            }
            playgroundMetrics.text = formatPlaygroundMetrics(state)
            updateControls()
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun buildContent(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        container.addView(label("Local LLM Android", 28f, bold = true))
        container.addView(
            label(
                "Import a GGUF, run prompts locally and validate the runtime on this device.",
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

        container.addView(section("Local inference playground"))
        container.addView(
            label(
                "The model remains loaded between prompts, so repeated runs expose cold and warm behavior. " +
                    "Prompts and generated text are not persisted.",
                14f,
            ),
        )
        promptInput = multiLineInput(
            hint = "Prompt",
            value = "Explain in two sentences why local inference improves privacy.",
        )
        container.addView(promptInput)
        maxTokensInput = input(
            hint = "Maximum output tokens",
            value = DEFAULT_MAX_OUTPUT_TOKENS,
            inputType = InputType.TYPE_CLASS_NUMBER,
        )
        temperatureInput = input(
            hint = "Temperature",
            value = DEFAULT_TEMPERATURE,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        seedInput = input(
            hint = "Seed",
            value = DEFAULT_SEED,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED,
        )
        container.addView(maxTokensInput)
        container.addView(temperatureInput)
        container.addView(seedInput)

        playgroundRunButton = actionButton("Run local prompt") { startPlayground() }.apply {
            isEnabled = false
        }
        playgroundCancelButton = actionButton("Cancel generation") {
            if (!playgroundController.cancel()) {
                Toast.makeText(this@MainActivity, "No cancellable generation is active", Toast.LENGTH_SHORT).show()
            }
        }.apply { isEnabled = false }
        container.addView(playgroundRunButton)
        container.addView(playgroundCancelButton)

        playgroundStatus = label("Ready", 14f, bold = true).apply {
            setPadding(0, dp(14), 0, dp(8))
        }
        container.addView(playgroundStatus)
        playgroundOutput = codeBlock("No playground output yet")
        container.addView(playgroundOutput)
        playgroundMetrics = label("No metrics yet", 13f).apply {
            setPadding(0, dp(10), 0, 0)
        }
        container.addView(playgroundMetrics)

        container.addView(section("Physical-device validation"))
        container.addView(
            label(
                "Runs generation, active cancellation and five load/generate/unload memory cycles. " +
                    "Keep the app open until it finishes.",
                14f,
            ),
        )
        validationButton = actionButton("Run full validation") {
            afterPlaygroundRuntimeReleased {
                latestReport = ""
                resultText.text = "Starting validation…"
                copyButton.isEnabled = false
                shareButton.isEnabled = false
                controller.runFullValidation()
            }
        }.apply { isEnabled = false }
        removeButton = actionButton("Remove imported model") {
            afterPlaygroundRuntimeReleased { controller.removeModel() }
        }.apply { isEnabled = false }
        container.addView(validationButton)
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

        container.addView(section("Privacy-safe validation result"))
        resultText = codeBlock("No validation report yet")
        container.addView(resultText)

        copyButton = actionButton("Copy report") { copyReport() }.apply { isEnabled = false }
        shareButton = actionButton("Share report") { shareReport() }.apply { isEnabled = false }
        container.addView(copyButton)
        container.addView(shareButton)

        scrollView = ScrollView(this).apply { addView(container) }
        return scrollView
    }

    private fun startPlayground() {
        val model = importedModel
        if (model == null) {
            Toast.makeText(this, "Import a GGUF model first", Toast.LENGTH_SHORT).show()
            return
        }
        val options = runCatching {
            PlaygroundRequestOptions.parse(
                maxOutputTokens = maxTokensInput.text.toString(),
                temperature = temperatureInput.text.toString(),
                seed = seedInput.text.toString(),
            )
        }.getOrElse {
            Toast.makeText(this, it.message ?: "Invalid generation settings", Toast.LENGTH_LONG).show()
            return
        }
        val started = runCatching {
            playgroundController.start(model, promptInput.text.toString(), options)
        }.getOrElse {
            Toast.makeText(this, it.message ?: "Unable to start local inference", Toast.LENGTH_LONG).show()
            false
        }
        if (!started) {
            Toast.makeText(this, "Another playground operation is still active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun afterPlaygroundRuntimeReleased(action: () -> Unit) {
        val accepted = playgroundController.releaseRuntime {
            runOnUiThread(action)
        }
        if (!accepted) {
            Toast.makeText(this, "Cancel or wait for the active generation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateControls() {
        if (!::playgroundController.isInitialized) return
        val playgroundBusy = playgroundController.active
        val anyBusy = controllerBusy || playgroundBusy
        val hasModel = importedModel != null
        selectButton.isEnabled = !anyBusy
        validationButton.isEnabled = !anyBusy && hasModel
        removeButton.isEnabled = !anyBusy && hasModel
        architectureInput.isEnabled = !anyBusy
        quantizationInput.isEnabled = !anyBusy
        promptInput.isEnabled = !anyBusy
        maxTokensInput.isEnabled = !anyBusy
        temperatureInput.isEnabled = !anyBusy
        seedInput.isEnabled = !anyBusy
        playgroundRunButton.isEnabled = !anyBusy && hasModel
        playgroundCancelButton.isEnabled = playgroundState.cancellationAvailable
    }

    private fun formatPlaygroundMetrics(state: PlaygroundState): String {
        val metrics = state.metrics ?: return state.generatedTokens?.let { "Generated tokens: $it" } ?: "No metrics yet"
        return buildString {
            appendLine("Load: ${metrics.modelLoadKind} · ${metrics.modelLoadMs ?: "n/a"} ms")
            appendLine("TTFT: ${metrics.timeToFirstTokenMs ?: "n/a"} ms · Total: ${metrics.totalMs ?: "n/a"} ms")
            appendLine("Input/output tokens: ${metrics.inputTokens ?: "n/a"}/${metrics.outputTokens ?: "n/a"}")
            append("Decode: ${metrics.decodeTokensPerSecond?.let { "%.2f".format(it) } ?: "n/a"} tok/s")
        }
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

    private fun input(hint: String, value: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        setText(value)
        isSingleLine = true
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun multiLineInput(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 4
        maxLines = 10
        gravity = Gravity.TOP or Gravity.START
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

    private fun codeBlock(text: String): TextView = label(text, 13f).apply {
        setTypeface(Typeface.MONOSPACE)
        setTextIsSelectable(true)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(0xffeeeeee.toInt())
    }

    private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_MODEL_DOCUMENT = 7001
        const val STATE_REPORT = "report"
        const val DEFAULT_ARCHITECTURE = "qwen3"
        const val DEFAULT_QUANTIZATION = "Q4_K_M"
        const val DEFAULT_MAX_OUTPUT_TOKENS = "128"
        const val DEFAULT_TEMPERATURE = "0.2"
        const val DEFAULT_SEED = "42"
    }
}
