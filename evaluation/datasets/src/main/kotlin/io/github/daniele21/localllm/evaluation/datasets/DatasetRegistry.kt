package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

data class EvaluationDatasetRegistryFilter(
    val datasetId: EvaluationDatasetId? = null,
    val version: EvaluationDatasetVersion? = null,
    val origin: EvaluationDatasetOrigin? = null,
    val categoryId: EvaluationCategoryId? = null,
)

data class InstalledEvaluationDatasetPack(val manifest: EvaluationDatasetManifestV1, val directory: File)

class EvaluationDatasetRegistry(private val rootDirectory: File) {
    fun discover(filter: EvaluationDatasetRegistryFilter = EvaluationDatasetRegistryFilter()): List<InstalledEvaluationDatasetPack> =
        rootDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { directory -> directory.isDirectory && directory.name != STAGING_DIRECTORY_NAME }
            .flatMap { datasetDirectory -> datasetDirectory.listFiles().orEmpty().asSequence() }
            .filter(File::isDirectory)
            .mapNotNull(::readPublishedPack)
            .filter { pack -> filter.matches(pack.manifest) }
            .sortedWith(
                compareBy<InstalledEvaluationDatasetPack>(
                    { pack -> pack.manifest.datasetId.value },
                    { pack -> pack.manifest.version.value },
                ),
            )
            .toList()

    fun find(datasetId: EvaluationDatasetId, version: EvaluationDatasetVersion): InstalledEvaluationDatasetPack? =
        discover(EvaluationDatasetRegistryFilter(datasetId = datasetId, version = version)).singleOrNull()

    private fun readPublishedPack(versionDirectory: File): InstalledEvaluationDatasetPack? {
        val manifestFile = File(versionDirectory, MANIFEST_FILE_NAME)
        val casesFile = File(versionDirectory, CASES_FILE_NAME)
        if (!manifestFile.isFile || !casesFile.isFile) return null

        val manifest = runCatching {
            EvaluationDatasetManifestDecoder.decode(manifestFile.readText(StandardCharsets.UTF_8))
        }.getOrNull() ?: return null

        val datasetDirectory = versionDirectory.parentFile ?: return null
        if (datasetDirectory.name != storageSegment(manifest.datasetId.value)) return null
        if (versionDirectory.name != storageSegment(manifest.version.value)) return null

        return InstalledEvaluationDatasetPack(manifest = manifest, directory = versionDirectory)
    }

    private fun EvaluationDatasetRegistryFilter.matches(manifest: EvaluationDatasetManifestV1): Boolean =
        (datasetId == null || datasetId == manifest.datasetId) &&
            (version == null || version == manifest.version) &&
            (origin == null || origin == manifest.origin) &&
            (categoryId == null || manifest.categories.any { category -> category.id == categoryId })

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val CASES_FILE_NAME = "cases.jsonl"
        const val STAGING_DIRECTORY_NAME = ".staging"
    }
}

private fun storageSegment(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
