package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity

internal data class PerformanceModelOption(val identity: EvaluationModelIdentity, val displayName: String, val detail: String)

internal fun performanceModelOptions(distribution: PhoneModelDistributionState): List<PerformanceModelOption> =
    distribution.models
        .mapNotNull { model ->
            val installed = model.installedModel ?: return@mapNotNull null
            if (!model.compatible || model.status != PhoneCatalogModelStatus.INSTALLED) return@mapNotNull null
            PerformanceModelOption(
                identity = EvaluationModelIdentity(
                    artifactDigest = installed.digest,
                    modelProfileId = installed.profileKey,
                    quantization = installed.quantization,
                ),
                displayName = installed.displayName,
                detail = "${installed.quantization} · ${formatPerformanceModelSize(installed.sizeBytes)}",
            )
        }.sortedBy(PerformanceModelOption::displayName)

private fun formatPerformanceModelSize(sizeBytes: Long): String {
    val gib = sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        "%.2f GiB".format(java.util.Locale.ROOT, gib)
    } else {
        "%.0f MiB".format(java.util.Locale.ROOT, sizeBytes.toDouble() / (1024.0 * 1024.0))
    }
}
