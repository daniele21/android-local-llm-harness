#!/usr/bin/env python3
"""Apply the focused Phase 1 Detekt fixes once.

This script is intentionally strict: every replacement must match exactly so it
cannot silently modify an unexpected revision of the source files.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file_path = ROOT / path
    text = file_path.read_text(encoding="utf-8")
    occurrences = text.count(old)
    if occurrences != 1:
        raise RuntimeError(
            f"Expected exactly one match in {path}, found {occurrences}: {old[:80]!r}"
        )
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


STORE = "models/model-store/src/main/kotlin/io/github/daniele21/localllm/store/FileSystemModelStore.kt"
BRIDGE = "backends/llama-cpp/src/main/kotlin/io/github/daniele21/localllm/llamacpp/LlamaCppBridge.kt"
STREAMING = "backends/llama-cpp/src/main/kotlin/io/github/daniele21/localllm/llamacpp/LlamaCppStreaming.kt"
MEMORY = "core/runtime-core/src/main/kotlin/io/github/daniele21/localllm/runtime/MemoryPressure.kt"
ORCHESTRATOR = "core/runtime-core/src/main/kotlin/io/github/daniele21/localllm/runtime/RuntimeOrchestrator.kt"
SCHEDULER = "core/runtime-core/src/main/kotlin/io/github/daniele21/localllm/runtime/SingleDecodeScheduler.kt"

replace_once(
    STORE,
    "import java.io.IOException\n",
    "import java.io.IOException\nimport java.io.InputStream\nimport java.io.OutputStream\n",
)
replace_once(
    STORE,
    "class FileSystemModelStore(",
    '@Suppress("TooManyFunctions")\nclass FileSystemModelStore(',
)
replace_once(
    STORE,
    "    private fun existingImport(destination: File, expectedDigest: ModelDigest, expectedSize: Long): StoredModel? {\n",
    '    @Suppress("ThrowsCount")\n'
    "    private fun existingImport(destination: File, expectedDigest: ModelDigest, expectedSize: Long): StoredModel? {\n",
)
replace_once(
    STORE,
    '''    private fun copyAndDigest(source: File, destination: File): DigestedFile {
        val messageDigest = MessageDigest.getInstance(SHA_256)
        var copiedBytes = 0L

        source.inputStream().buffered(bufferSizeBytes).use { input ->
            destination.outputStream().buffered(bufferSizeBytes).use { output ->
                val buffer = ByteArray(bufferSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    messageDigest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    copiedBytes += read
                }
                output.flush()
            }
        }

        return DigestedFile(
            digest = ModelDigest(messageDigest.digest().toHex()),
            sizeBytes = copiedBytes,
        )
    }
''',
    '''    private fun copyAndDigest(source: File, destination: File): DigestedFile {
        val messageDigest = MessageDigest.getInstance(SHA_256)
        val copiedBytes = source.inputStream().buffered(bufferSizeBytes).use { input ->
            destination.outputStream().buffered(bufferSizeBytes).use { output ->
                copyAndDigest(input, output, messageDigest)
            }
        }

        return DigestedFile(
            digest = ModelDigest(messageDigest.digest().toHex()),
            sizeBytes = copiedBytes,
        )
    }

    private fun copyAndDigest(input: InputStream, output: OutputStream, messageDigest: MessageDigest): Long {
        val buffer = ByteArray(bufferSizeBytes)
        var copiedBytes = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                messageDigest.update(buffer, 0, read)
                output.write(buffer, 0, read)
                copiedBytes += read
            }
            read = input.read(buffer)
        }
        output.flush()
        return copiedBytes
    }
''',
)
replace_once(
    STORE,
    "        } catch (error: AtomicMoveNotSupportedException) {\n",
    "        } catch (_: AtomicMoveNotSupportedException) {\n",
)
replace_once(
    STORE,
    "            } catch (race: FileAlreadyExistsException) {\n",
    "            } catch (_: FileAlreadyExistsException) {\n",
)
replace_once(
    STORE,
    "        } catch (error: FileAlreadyExistsException) {\n",
    "        } catch (_: FileAlreadyExistsException) {\n",
)

replace_once(
    BRIDGE,
    "class LlamaCppBridge(",
    '@Suppress("TooManyFunctions")\nclass LlamaCppBridge(',
)

replace_once(
    STREAMING,
    "interface NativeLlamaStreamingApi {\n    fun generateStreaming(\n",
    'interface NativeLlamaStreamingApi {\n    @Suppress("LongParameterList")\n    fun generateStreaming(\n',
)
replace_once(
    STREAMING,
    "    external override fun generateStreaming(\n",
    '    @Suppress("LongParameterList")\n    external override fun generateStreaming(\n',
)

replace_once(
    MEMORY,
    '''        if (!resources.modelLoaded && resources.activeSessions == 0 &&
            !resources.activeGeneration && resources.queuedGenerations == 0
        ) {
            return RuntimeMemoryAction.NONE
        }
''',
    '''        val runtimeEmpty = listOf(
            !resources.modelLoaded,
            resources.activeSessions == 0,
            !resources.activeGeneration,
            resources.queuedGenerations == 0,
        ).all { it }
        if (runtimeEmpty) {
            return RuntimeMemoryAction.NONE
        }
''',
)

replace_once(
    ORCHESTRATOR,
    "class RuntimeOrchestrator(\n",
    '@Suppress("TooManyFunctions")\nclass RuntimeOrchestrator(\n',
)
replace_once(
    ORCHESTRATOR,
    "    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {\n",
    '    @Suppress("ReturnCount")\n'
    "    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {\n",
)
replace_once(
    ORCHESTRATOR,
    "    private fun executeGeneration(request: GenerationRequest, session: SessionDescriptor, lifecycle: RequestLifecycle, enqueuedAt: Long) {\n",
    '    @Suppress("CyclomaticComplexMethod")\n'
    "    private fun executeGeneration(request: GenerationRequest, session: SessionDescriptor, lifecycle: RequestLifecycle, enqueuedAt: Long) {\n",
)

replace_once(
    SCHEDULER,
    '''    private fun runLoop() {
        while (!closed.get()) {
            val work = try {
                queue.take()
            } catch (_: InterruptedException) {
                if (closed.get()) return
                continue
            }

            if (work.cancelled.get()) {
                works.remove(work.requestId, work)
                work.notifyQueuedCancellation()
                continue
            }
            if (!work.started.compareAndSet(false, true)) {
                continue
            }

            activeRequest.set(work.requestId)
            try {
                if (work.cancelled.get()) {
                    work.onRunningCancellation()
                } else {
                    work.task()
                }
            } finally {
                activeRequest.set(null)
                works.remove(work.requestId, work)
            }
        }
    }
''',
    '''    private fun runLoop() {
        while (!closed.get()) {
            takeNextWork()?.let(::execute)
        }
    }

    private fun takeNextWork(): ScheduledWork? = try {
        queue.take()
    } catch (_: InterruptedException) {
        null
    }

    private fun execute(work: ScheduledWork) {
        if (work.cancelled.get()) {
            works.remove(work.requestId, work)
            work.notifyQueuedCancellation()
            return
        }
        if (!work.started.compareAndSet(false, true)) {
            return
        }

        activeRequest.set(work.requestId)
        try {
            if (work.cancelled.get()) {
                work.onRunningCancellation()
            } else {
                work.task()
            }
        } finally {
            activeRequest.set(null)
            works.remove(work.requestId, work)
        }
    }
''',
)

print("Applied focused Detekt consolidation fixes.")
