package io.github.daniele21.localllm.catalog

/** Administrator-curated bootstrap releases for the phone-test catalog. */
object CuratedModelCatalog {
    val catalogId = CatalogId("android-local-llm-curated")
    const val REVISION: Long = 3

    val releases: List<CatalogModelRelease> =
        OtherCuratedModelReleases.releases +
            Qwen3CuratedModelReleases.releases +
            Qwen35CuratedModelReleases.releases

    fun document(generatedAtEpochMs: Long, expiresAtEpochMs: Long): CatalogModelDocument = CatalogModelDocument(
        schemaVersion = 1,
        catalogId = catalogId,
        revision = REVISION,
        generatedAtEpochMs = generatedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        entries = releases,
    )
}
