package io.github.daniele21.localllm.phonetest

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface PhoneTestListener {
    fun onBusyChanged(busy: Boolean)

    fun onProgress(message: String)

    fun onModelChanged(model: ImportedPhoneModel?)

    fun onReport(report: String)
}

@Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
internal class PhoneTestController(
    context: Context,
    private val runtimeGraph: HarnessRuntimeGraph,
    private val listener: PhoneTestListener,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val modelStore = runtimeGraph.modelStore

    @Volatile
    private var currentModel: ImportedPhoneModel? = restoreModel()

    init {
        post { listener.onModelChanged(currentModel) }
    }

    fun snapshotModel(): ImportedPhoneModel? = currentModel

    fun importModel(uri: Uri, architecture: String, quantization: String) {
        runExclusive {
            require(architecture.isNotBlank()) { "Model architecture must not be blank" }
            require(quantization.isNotBlank()) { "Model quantization must not be blank" }
            val metadata = queryDocumentMetadata(uri)
            require(metadata.fileName.lowercase().endsWith(".gguf")) {
                "Select a .gguf model file"
            }
            progress("Copying ${metadata.fileName} into a private staging area")
            val staged = copyAndDigest(uri, metadata)
            try {
                val model = ImportedPhoneModel(
                    digest = staged.digest,
                    fileName = metadata.fileName,
                    sizeBytes = staged.sizeBytes,
                    architecture = architecture.trim(),
                    quantization = quantization.trim(),
                )
                progress("Importing and verifying SHA-256 ${model.digest.sha256.take(12)}…")
                val stored = modelStore.import(staged.file, model.artifact())
                check(stored.verified) { "Imported model was not verified" }
                persist(model)
                currentModel = model
                post { listener.onModelChanged(model) }
                progress("Model import complete (${formatBytes(model.sizeBytes)})")
            } finally {
                staged.file.delete()
            }
        }
    }

    fun selectInstalledModel(model: ImportedPhoneModel) {
        runExclusive {
            progress("Verifying installed model before selection")
            val stored = requireNotNull(modelStore.find(model.digest)) {
                "Installed model is no longer available"
            }
            check(stored.verified) { "Installed model is not marked as verified" }
            val verification = modelStore.verify(model.digest)
            check(verification.valid) { verification.detail }
            persist(model)
            currentModel = model
            post { listener.onModelChanged(model) }
            progress("${model.fileName} selected for local inference")
        }
    }

    fun removeModel() {
        runExclusive {
            val model = currentModel ?: return@runExclusive
            progress("Removing the imported model")
            runtimeGraph.releaseModel(model.digest)
            modelStore.remove(model.digest)
            preferences.edit().clear().apply()
            currentModel = null
            post { listener.onModelChanged(null) }
            progress("Model removed")
        }
    }

    fun runFullValidation() {
        runExclusive(reportFailure = true) {
            val model = requireNotNull(currentModel) { "Import a GGUF model before running validation" }
            val startedAt = System.currentTimeMillis()
            val startThermal = thermalStatus()
            progress("Verifying stored model integrity")
            val verification = modelStore.verify(model.digest)
            check(verification.valid) { verification.detail }

            progress("Running lifecycle and generation test")
            val generation = runGeneration(model, GENERATION_OUTPUT_TOKENS)
            progress("Generation passed: ${generation.outputTokens} output tokens")

            progress("Running active-generation cancellation test")
            runCancellation(model)
            progress("Cancellation passed")

            progress("Running $MEMORY_REPEAT_COUNT repeated load/generate/unload cycles")
            val memory = runMemoryCycles(model)
            progress("Memory cycles passed: growth ${memory.growthKb} KB")

            val report = buildSuccessReport(
                model = model,
                generation = generation,
                memory = memory,
                startThermal = startThermal,
                endThermal = thermalStatus(),
                durationMs = System.currentTimeMillis() - startedAt,
            )
            post { listener.onReport(report) }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun runExclusive(reportFailure: Boolean = false, action: () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            progress("Another operation is already running")
            return
        }
        post { listener.onBusyChanged(true) }
        executor.execute {
            try {
                action()
            } catch (error: Throwable) {
                val detail = sanitize(error.message ?: error.javaClass.simpleName)
                progress("Failed: $detail")
                if (reportFailure) {
                    post {
                        listener.onReport(
                            buildFailureReport(currentModel, detail),
                        )
                    }
                }
            } finally {
                busy.set(false)
                post { listener.onBusyChanged(false) }
            }
        }
    }

    private fun runGeneration(model: ImportedPhoneModel, maxOutputTokens: Int): GenerationSummary {
        val harness = buildHarness(model)
        val runtime = harness.runtime
        val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
        check(prepared.ready) { "Prepare failed: ${prepared.detail}" }
        val session = runtime.createSession(harness.applicationId, harness.useCaseId)
        val completed = generateAndAwait(runtime, harness, session, GENERATION_PROMPT, maxOutputTokens)
        check(completed.output.isNotBlank()) { "Generation returned an empty output" }
        closeSessionAndUnload(runtime, session)
        return GenerationSummary.from(completed.metrics)
    }

    private fun runCancellation(model: ImportedPhoneModel) {
        val harness = buildHarness(model)
        val runtime = harness.runtime
        val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
        check(prepared.ready) { "Prepare failed: ${prepared.detail}" }
        val session = runtime.createSession(harness.applicationId, harness.useCaseId)
        val firstDelta = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        val handle = runtime.generate(
            request(harness, session, CANCELLATION_PROMPT, CANCELLATION_OUTPUT_TOKENS),
            GenerationListener { event ->
                if (event is GenerationEvent.TextDelta) firstDelta.countDown()
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent.compareAndSet(null, event)
                    terminal.countDown()
                }
            },
        )
        check(firstDelta.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "No streaming delta arrived before the cancellation timeout"
        }
        handle.cancel()
        check(terminal.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Cancellation did not reach a terminal event"
        }
        val result = terminalEvent.get()
        check(result is GenerationEvent.Failed && result.error is LocalLlmError.Cancelled) {
            "Expected a cancelled terminal event"
        }
        closeSessionAndUnload(runtime, session)
    }

    private fun runMemoryCycles(model: ImportedPhoneModel): MemorySummary {
        val samples = mutableListOf<Int>()
        repeat(MEMORY_REPEAT_COUNT) { index ->
            progress("Memory cycle ${index + 1}/$MEMORY_REPEAT_COUNT")
            runGeneration(model, MEMORY_OUTPUT_TOKENS)
            Runtime.getRuntime().gc()
            Thread.sleep(MEMORY_SETTLE_MILLIS)
            samples += totalPssKb()
        }
        val growth = samples.last() - samples.first()
        check(growth <= MAX_PSS_GROWTH_KB) {
            "PSS grew by $growth KB; budget=$MAX_PSS_GROWTH_KB KB, samples=$samples"
        }
        return MemorySummary(samples, growth)
    }

    private fun buildHarness(model: ImportedPhoneModel): PhoneHarness =
        runtimeGraph.harnessFor(model, HarnessRuntimePurpose.PHYSICAL_VALIDATION)

    private fun generateAndAwait(
        runtime: RuntimeOrchestrator,
        harness: PhoneHarness,
        session: SessionId,
        prompt: String,
        maxOutputTokens: Int,
    ): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        runtime.generate(
            request(harness, session, prompt, maxOutputTokens),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent.compareAndSet(null, event)
                    terminal.countDown()
                }
            },
        )
        check(terminal.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Generation did not complete within $OPERATION_TIMEOUT_SECONDS seconds"
        }
        return when (val result = terminalEvent.get()) {
            is GenerationEvent.Completed -> result
            is GenerationEvent.Failed -> error("Generation failed: ${result.error}")
            else -> error("Generation completed without a terminal event")
        }
    }

    private fun request(harness: PhoneHarness, session: SessionId, prompt: String, maxOutputTokens: Int): GenerationRequest =
        GenerationRequest(
            requestId = RequestId(UUID.randomUUID().toString()),
            sessionId = session,
            applicationId = harness.applicationId,
            useCaseId = harness.useCaseId,
            input = prompt,
            overrides = GenerationOverrides(maxOutputTokens = maxOutputTokens),
        )

    private fun closeSessionAndUnload(runtime: RuntimeOrchestrator, session: SessionId) {
        runtime.closeSession(session)
        check(eventually { runtime.runtimeSnapshot().activeSessions == 0 }) {
            "Session context was not released"
        }
        check(runtimeGraph.unloadIdleModel()) { "Idle model was not unloaded" }
    }

    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OPERATION_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    private fun queryDocumentMetadata(uri: Uri): DocumentMetadata {
        var fileName = "model.gguf"
        var sizeBytes: Long? = null
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }
        return DocumentMetadata(fileName, sizeBytes)
    }

    private fun copyAndDigest(uri: Uri, metadata: DocumentMetadata): StagedModel {
        val directory = File(appContext.cacheDir, IMPORT_DIRECTORY).apply { mkdirs() }
        val temporaryFile = File.createTempFile("phone-model-", ".gguf", directory)
        val digest = MessageDigest.getInstance(SHA_256)
        var copied = 0L
        var nextProgress = PROGRESS_INTERVAL_BYTES
        try {
            val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
                "Unable to open the selected model"
            }
            input.buffered(COPY_BUFFER_BYTES).use { source ->
                temporaryFile.outputStream().buffered(COPY_BUFFER_BYTES).use { destination ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var read = source.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read)
                            destination.write(buffer, 0, read)
                            copied += read
                            if (copied >= nextProgress) {
                                progress(copyProgress(copied, metadata.sizeBytes))
                                nextProgress += PROGRESS_INTERVAL_BYTES
                            }
                        }
                        read = source.read(buffer)
                    }
                }
            }
            return StagedModel(
                file = temporaryFile,
                digest = ModelDigest(digest.digest().toHex()),
                sizeBytes = copied,
            )
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    private fun persist(model: ImportedPhoneModel) {
        preferences.edit()
            .putString(KEY_DIGEST, model.digest.sha256)
            .putString(KEY_FILE_NAME, model.fileName)
            .putLong(KEY_SIZE_BYTES, model.sizeBytes)
            .putString(KEY_ARCHITECTURE, model.architecture)
            .putString(KEY_QUANTIZATION, model.quantization)
            .apply()
    }

    private fun restoreModel(): ImportedPhoneModel? {
        val digest = preferences.getString(KEY_DIGEST, null) ?: return null
        return runCatching {
            val model = ImportedPhoneModel(
                digest = ModelDigest(digest),
                fileName = preferences.getString(KEY_FILE_NAME, "model.gguf") ?: "model.gguf",
                sizeBytes = preferences.getLong(KEY_SIZE_BYTES, -1L),
                architecture = preferences.getString(KEY_ARCHITECTURE, DEFAULT_ARCHITECTURE) ?: DEFAULT_ARCHITECTURE,
                quantization = preferences.getString(KEY_QUANTIZATION, DEFAULT_QUANTIZATION) ?: DEFAULT_QUANTIZATION,
            )
            require(model.sizeBytes >= 0 && modelStore.find(model.digest) != null)
            model
        }.getOrElse {
            preferences.edit().clear().apply()
            null
        }
    }

    private fun buildSuccessReport(
        model: ImportedPhoneModel,
        generation: GenerationSummary,
        memory: MemorySummary,
        startThermal: String,
        endThermal: String,
        durationMs: Long,
    ): String = buildString {
        appendLine("LOCAL_LLM_PHONE_TEST result=PASS")
        appendLine(deviceSummary())
        appendLine("modelDigest=${model.digest.sha256}")
        appendLine("modelBytes=${model.sizeBytes}")
        appendLine("architecture=${model.architecture}")
        appendLine("quantization=${model.quantization}")
        appendLine("inputTokens=${generation.inputTokens}")
        appendLine("outputTokens=${generation.outputTokens}")
        appendLine("ttftMs=${generation.ttftMs}")
        appendLine("totalMs=${generation.totalMs}")
        appendLine("decodeTokensPerSecond=${generation.decodeTokensPerSecond}")
        appendLine("cancellation=cancelled")
        appendLine("pssSamplesKb=${memory.samplesKb}")
        appendLine("pssGrowthKb=${memory.growthKb}")
        appendLine("thermalStart=$startThermal")
        appendLine("thermalEnd=$endThermal")
        appendLine("validationDurationMs=$durationMs")
        append("privacy=prompts-and-generated-output-not-recorded")
    }

    private fun buildFailureReport(model: ImportedPhoneModel?, detail: String): String = buildString {
        appendLine("LOCAL_LLM_PHONE_TEST result=FAIL")
        appendLine(deviceSummary())
        model?.let {
            appendLine("modelDigest=${it.digest.sha256}")
            appendLine("modelBytes=${it.sizeBytes}")
            appendLine("architecture=${it.architecture}")
            appendLine("quantization=${it.quantization}")
        }
        appendLine("error=$detail")
        append("privacy=prompts-and-generated-output-not-recorded")
    }

    private fun deviceSummary(): String {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return "device=${Build.MANUFACTURER}/${Build.MODEL} " +
            "android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} " +
            "abis=${Build.SUPPORTED_ABIS.joinToString(",")} " +
            "ramBytes=${memoryInfo.totalMem} cpuThreads=${Runtime.getRuntime().availableProcessors()}"
    }

    private fun thermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unavailable"
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.currentThermalStatus.toString()
    }

    private fun totalPssKb(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss
    }

    private fun sanitize(detail: String): String = detail
        .replace(appContext.dataDir.absolutePath, "<app-data>")
        .replace(appContext.cacheDir.absolutePath, "<cache>")
        .replace('\n', ' ')
        .take(MAX_ERROR_LENGTH)

    private fun copyProgress(copied: Long, total: Long?): String = if (total != null && total > 0) {
        val percent = (copied * 100 / total).coerceIn(0, 100)
        "Copying model: $percent% (${formatBytes(copied)} / ${formatBytes(total)})"
    } else {
        "Copying model: ${formatBytes(copied)}"
    }

    private fun progress(message: String) {
        post { listener.onProgress(message) }
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun ByteArray.toHex(): String {
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = HEX_DIGITS[value ushr 4]
            result[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return String(result)
    }

    private fun formatBytes(bytes: Long): String = "%.1f MB".format(Locale.ROOT, bytes / 1_048_576.0)

    private data class DocumentMetadata(val fileName: String, val sizeBytes: Long?)

    private data class StagedModel(val file: File, val digest: ModelDigest, val sizeBytes: Long)

    private data class GenerationSummary(
        val inputTokens: String,
        val outputTokens: String,
        val ttftMs: String,
        val totalMs: String,
        val decodeTokensPerSecond: String,
    ) {
        companion object {
            fun from(metrics: GenerationMetrics): GenerationSummary = GenerationSummary(
                inputTokens = metrics.inputTokens.toString(),
                outputTokens = metrics.outputTokens.toString(),
                ttftMs = metrics.timeToFirstTokenMs.toString(),
                totalMs = metrics.totalMs.toString(),
                decodeTokensPerSecond = metrics.decodeTokensPerSecond.toString(),
            )
        }
    }

    private data class MemorySummary(val samplesKb: List<Int>, val growthKb: Int)

    private companion object {
        const val PREFERENCES_NAME = "phone-test-model"
        const val IMPORT_DIRECTORY = "phone-test-import"
        const val KEY_DIGEST = "digest"
        const val KEY_FILE_NAME = "file-name"
        const val KEY_SIZE_BYTES = "size-bytes"
        const val KEY_ARCHITECTURE = "architecture"
        const val KEY_QUANTIZATION = "quantization"
        const val DEFAULT_ARCHITECTURE = "qwen3"
        const val DEFAULT_QUANTIZATION = "Q4_K_M"
        const val SHA_256 = "SHA-256"
        const val HEX_DIGITS = "0123456789abcdef"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val PROGRESS_INTERVAL_BYTES = 8L * 1024 * 1024
        const val GENERATION_OUTPUT_TOKENS = 32
        const val CANCELLATION_OUTPUT_TOKENS = 256
        const val MEMORY_OUTPUT_TOKENS = 16
        const val MEMORY_REPEAT_COUNT = 5
        const val MEMORY_SETTLE_MILLIS = 750L
        const val MAX_PSS_GROWTH_KB = 131_072
        const val OPERATION_TIMEOUT_SECONDS = 180L
        const val MAX_ERROR_LENGTH = 500
        const val GENERATION_PROMPT = "Reply with the single word READY."
        const val CANCELLATION_PROMPT =
            "Write a numbered list from 1 to 1000. Continue until every number is written."
    }
}
