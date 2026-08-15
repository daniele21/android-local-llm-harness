package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

enum class DatasetInstallRejectionCode {
    ALREADY_INSTALLED,
    PARSE_FAILURE,
    VALIDATION_FAILURE,
    DIGEST_MISMATCH,
    ATOMIC_PUBLICATION_UNAVAILABLE,
    IO_FAILURE,
}

sealed interface DatasetInstallResult {
    data class Installed(val directory: File) : DatasetInstallResult

    data class Rejected(
        val code: DatasetInstallRejectionCode,
        val parseLineNumber: Int? = null,
        val parseCode: DatasetParseErrorCode? = null,
        val validationIssues: List<DatasetValidationIssue> = emptyList(),
    ) : DatasetInstallResult
}

private sealed interface ParsedDatasetCases {
    data class Success(val cases: List<EvaluationDatasetCaseV1>) : ParsedDatasetCases

    data class Rejected(val result: DatasetInstallResult.Rejected) : ParsedDatasetCases
}

class EvaluationDatasetInstaller(
    private val rootDirectory: File,
    private val parser: EvaluationDatasetJsonlParser,
    private val validator: EvaluationDatasetPackValidator,
) {
    fun install(
        manifest: EvaluationDatasetManifestV1,
        casesInput: InputStream,
    ): DatasetInstallResult {
        val finalDirectory = finalDirectory(manifest)
        if (finalDirectory.exists()) {
            return DatasetInstallResult.Rejected(DatasetInstallRejectionCode.ALREADY_INSTALLED)
        }

        val parsed = parseCases(casesInput)
        if (parsed is ParsedDatasetCases.Rejected) return parsed.result
        val cases = (parsed as ParsedDatasetCases.Success).cases

        validate(manifest, cases)?.let { rejection -> return rejection }

        val stagingDirectory = File(stagingRoot(), UUID.randomUUID().toString())
        return publish(manifest, cases, stagingDirectory, finalDirectory)
    }

    fun installedDirectory(manifest: EvaluationDatasetManifestV1): File = finalDirectory(manifest)

    internal fun stagingRoot(): File = File(rootDirectory, STAGING_DIRECTORY_NAME)

    private fun parseCases(casesInput: InputStream): ParsedDatasetCases = try {
        ParsedDatasetCases.Success(parser.parse(casesInput))
    } catch (failure: EvaluationDatasetParseException) {
        ParsedDatasetCases.Rejected(
            DatasetInstallResult.Rejected(
                code = DatasetInstallRejectionCode.PARSE_FAILURE,
                parseLineNumber = failure.lineNumber,
                parseCode = failure.code,
            ),
        )
    }

    private fun validate(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
    ): DatasetInstallResult.Rejected? {
        val validation = validator.validate(manifest, cases)
        if (validation is DatasetValidationResult.Invalid) {
            return DatasetInstallResult.Rejected(
                code = DatasetInstallRejectionCode.VALIDATION_FAILURE,
                validationIssues = validation.issues,
            )
        }
        if (EvaluationDatasetContentDigester.verify(manifest, cases) !is DatasetDigestVerification.Match) {
            return DatasetInstallResult.Rejected(DatasetInstallRejectionCode.DIGEST_MISMATCH)
        }
        return null
    }

    private fun publish(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
        stagingDirectory: File,
        finalDirectory: File,
    ): DatasetInstallResult = try {
        requireDirectory(stagingDirectory)
        writeUtf8AndSync(
            File(stagingDirectory, MANIFEST_FILE_NAME),
            EvaluationDatasetManifestCanonicalJson.encode(manifest) + "\n",
        )
        writeCasesAndSync(File(stagingDirectory, CASES_FILE_NAME), cases)
        publishAtomically(stagingDirectory, finalDirectory)
        DatasetInstallResult.Installed(finalDirectory)
    } catch (_: AtomicMoveNotSupportedException) {
        stagingDirectory.deleteRecursively()
        DatasetInstallResult.Rejected(DatasetInstallRejectionCode.ATOMIC_PUBLICATION_UNAVAILABLE)
    } catch (_: Exception) {
        stagingDirectory.deleteRecursively()
        DatasetInstallResult.Rejected(DatasetInstallRejectionCode.IO_FAILURE)
    }

    private fun finalDirectory(manifest: EvaluationDatasetManifestV1): File = File(
        File(rootDirectory, storageSegment(manifest.datasetId.value)),
        storageSegment(manifest.version.value),
    )

    private fun publishAtomically(stagingDirectory: File, finalDirectory: File) {
        if (finalDirectory.exists()) {
            error("Dataset installation target already exists")
        }
        requireDirectory(requireNotNull(finalDirectory.parentFile))
        Files.move(
            stagingDirectory.toPath(),
            finalDirectory.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun writeCasesAndSync(
        file: File,
        cases: List<EvaluationDatasetCaseV1>,
    ) {
        FileOutputStream(file).use { output ->
            BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
                cases.forEach { case ->
                    writer.write(EvaluationDatasetCanonicalJson.encodeCase(case))
                    writer.newLine()
                }
                writer.flush()
                output.fd.sync()
            }
        }
    }

    private fun writeUtf8AndSync(file: File, content: String) {
        FileOutputStream(file).use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun requireDirectory(directory: File) {
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create dataset directory" }
    }

    private fun storageSegment(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val CASES_FILE_NAME = "cases.jsonl"
        const val STAGING_DIRECTORY_NAME = ".staging"
    }
}

internal object EvaluationDatasetManifestCanonicalJson {
    fun encode(manifest: EvaluationDatasetManifestV1): String = buildString {
        append('{')
        field("schemaVersion", manifest.schemaVersion.toString())
        append(',')
        field("caseSchemaVersion", manifest.caseSchemaVersion.toString())
        append(',')
        stringField("datasetId", manifest.datasetId.value)
        append(',')
        stringField("version", manifest.version.value)
        append(',')
        stringField("displayName", manifest.displayName)
        manifest.description?.let { description ->
            append(',')
            stringField("description", description)
        }
        append(',')
        stringField("origin", manifest.origin.name)
        append(',')
        field("caseCount", manifest.caseCount.toString())
        append(',')
        stringField("contentDigest", manifest.contentDigest.sha256)
        append(',')
        appendJsonString("categories")
        append(':')
        append('[')
        manifest.categories.forEachIndexed { index, category ->
            if (index > 0) append(',')
            append('{')
            stringField("id", category.id.value)
            append(',')
            stringField("displayName", category.displayName)
            category.weight?.let { weight ->
                append(',')
                field("weight", weight.toString())
            }
            append('}')
        }
        append(']')
        if (manifest.presets.isNotEmpty()) {
            append(',')
            appendJsonString("presets")
            append(':')
            append('[')
            manifest.presets.forEachIndexed { index, preset ->
                if (index > 0) append(',')
                append('{')
                stringField("id", preset.id)
                append(',')
                appendJsonString("orderedCaseIds")
                append(':')
                append('[')
                preset.orderedCaseIds.forEachIndexed { caseIndex, caseId ->
                    if (caseIndex > 0) append(',')
                    appendJsonString(caseId.value)
                }
                append(']')
                append('}')
            }
            append(']')
        }
        append('}')
    }
}

private fun StringBuilder.field(name: String, encodedValue: String) {
    appendJsonString(name)
    append(':')
    append(encodedValue)
}

private fun StringBuilder.stringField(name: String, value: String) {
    appendJsonString(name)
    append(':')
    appendJsonString(value)
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}
