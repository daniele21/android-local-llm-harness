package io.github.daniele21.localllm.console

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast

class ConsoleInferenceRequestDialog(private val activity: Activity) {
    fun show(state: ConsoleInferenceState, onRequest: (ConsoleInferenceRequest) -> Unit) {
        if (!state.available || state.targets.isEmpty()) return
        val targets = state.targets
        val targetSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                targets.map(ConsoleInferenceTarget::label),
            )
        }
        val promptInput = EditText(activity).apply {
            hint = "Prompt"
            minLines = 4
            maxLines = 10
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val tokenInput = numericInput("Max output tokens", DEFAULT_MAX_OUTPUT_TOKENS.toString())
        val temperatureInput = decimalInput("Temperature", DEFAULT_TEMPERATURE.toString())
        val seedInput = signedNumericInput("Seed", DEFAULT_SEED.toString())
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING, PADDING / 2, PADDING, 0)
            addView(targetSpinner, matchWidth())
            addView(promptInput, matchWidth())
            addView(tokenInput, matchWidth())
            addView(temperatureInput, matchWidth())
            addView(seedInput, matchWidth())
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Run local inference")
            .setMessage("The prompt and generated output remain in memory and are not written to telemetry.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Run", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val request = buildRequest(
                    target = targets[targetSpinner.selectedItemPosition],
                    prompt = promptInput.text.toString(),
                    maxOutputTokens = tokenInput.text.toString(),
                    temperature = temperatureInput.text.toString(),
                    seed = seedInput.text.toString(),
                )
                if (request == null) {
                    Toast.makeText(activity, "Enter a valid prompt and generation settings", Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                    onRequest(request)
                }
            }
        }
        dialog.show()
    }

    private fun buildRequest(
        target: ConsoleInferenceTarget,
        prompt: String,
        maxOutputTokens: String,
        temperature: String,
        seed: String,
    ): ConsoleInferenceRequest? = runCatching {
        ConsoleInferenceRequest(
            targetId = target.id,
            prompt = prompt.trim(),
            maxOutputTokens = maxOutputTokens.toInt(),
            temperature = temperature.toFloat(),
            seed = seed.toLong(),
        )
    }.getOrNull()

    private fun numericInput(hint: String, value: String): EditText = EditText(activity).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun signedNumericInput(hint: String, value: String): EditText = numericInput(hint, value).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    }

    private fun decimalInput(hint: String, value: String): EditText = EditText(activity).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private companion object {
        const val PADDING = 32
        const val DEFAULT_MAX_OUTPUT_TOKENS = 128
        const val DEFAULT_TEMPERATURE = 0.2f
        const val DEFAULT_SEED = 42L
    }
}
