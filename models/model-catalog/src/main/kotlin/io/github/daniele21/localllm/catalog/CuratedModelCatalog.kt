package io.github.daniele21.localllm.catalog

/** Administrator-curated Qwen3.5 releases available to the product. */
object CuratedModelCatalog {
    val catalogId = CatalogId("android-local-llm-curated")
    const val REVISION: Long = 4

    val releases: List<CatalogModelRelease> = Qwen35CuratedModelReleases.releases

    fun document(generatedAtEpochMs: Long, expiresAtEpochMs: Long): CatalogModelDocument = CatalogModelDocument(
        schemaVersion = 1,
        catalogId = catalogId,
        revision = REVISION,
        generatedAtEpochMs = generatedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        entries = releases,
    )
}
