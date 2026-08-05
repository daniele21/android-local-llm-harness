package io.github.daniele21.localllm.phonetest

internal data class PhoneModelDistributionActions(
    val download: (String) -> Unit,
    val cancelDownload: (String) -> Unit,
    val install: (String) -> Unit,
    val verifyInstalled: (String) -> Unit,
    val requestRemove: (String) -> Unit,
    val cancelRemove: (String) -> Unit,
    val confirmRemove: (String) -> Unit,
    val selectInstalled: (InstalledCatalogModelMetadata) -> Unit,
)
