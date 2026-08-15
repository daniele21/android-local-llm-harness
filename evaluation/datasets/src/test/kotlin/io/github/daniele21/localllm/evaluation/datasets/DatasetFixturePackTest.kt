package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DatasetFixturePackTest {
    @Test
    fun `clean installs retain deterministic standard sample identity`() = withRoots { firstRoot, secondRoot ->
        val fixture = EvaluationDatasetFixtures.pack()
        val firstInstaller = installer(firstRoot)
        val secondInstaller = installer(secondRoot)

        assertTrue(firstInstaller.install(fixture.manifest, fixture.input()) is DatasetInstallResult.Installed)
        assertTrue(secondInstaller.install(fixture.manifest, fixture.input()) is DatasetInstallResult.Installed)

        val firstCases = installedCases(firstInstaller, fixture)
        val secondCases = installedCases(secondInstaller, fixture)
        val sampler = EvaluationStratifiedSampler()
        val firstRanking = sampler.rank(fixture.manifest, firstCases, seed = 42)
        val secondRanking = sampler.rank(fixture.manifest, secondCases.reversed(), seed = 42)
        val firstStandard = EvaluationSamplePresetResolver.resolve(
            firstRanking,
            EvaluationSampleRequest.Preset(EvaluationSamplePreset.STANDARD),
        ) as DatasetSampleResolution.Resolved
        val secondStandard = EvaluationSamplePresetResolver.resolve(
            secondRanking,
            EvaluationSampleRequest.Preset(EvaluationSamplePreset.STANDARD),
        ) as DatasetSampleResolution.Resolved

        assertEquals(firstRanking.orderedCaseIds, secondRanking.orderedCaseIds)
        assertEquals(firstStandard.selection.orderedCaseIds, secondStandard.selection.orderedCaseIds)
        assertEquals(firstStandard.selection.digest, secondStandard.selection.digest)
    }

    @Test
    fun `duplicate case fixture is rejected without publication`() = withRoot { root ->
        val valid = EvaluationDatasetFixtures.pack(caseCount = 20)
        val duplicateCases = valid.cases.dropLast(1) + valid.cases.first()
        val fixture = EvaluationDatasetFixtures.pack(duplicateCases)
        val installer = installer(root)

        val result = installer.install(fixture.manifest, fixture.input()) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.VALIDATION_FAILURE, result.code)
        assertTrue(result.validationIssues.any { it.code == DatasetValidationIssueCode.DUPLICATE_CASE_ID })
        assertNotPublished(installer, fixture)
    }

    @Test
    fun `malformed record fixture is rejected without publication`() = withRoot { root ->
        val fixture = EvaluationDatasetFixtures.pack(caseCount = 20)
        val malformed = ByteArrayInputStream("{\"schemaVersion\":1}\n".toByteArray(StandardCharsets.UTF_8))
        val installer = installer(root)

        val result = installer.install(fixture.manifest, malformed) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.PARSE_FAILURE, result.code)
        assertEquals(1, result.parseLineNumber)
        assertNotPublished(installer, fixture)
    }

    @Test
    fun `digest mismatch fixture is rejected without publication`() = withRoot { root ->
        val fixture = EvaluationDatasetFixtures.pack(caseCount = 20)
        val mismatched = fixture.copy(manifest = fixture.manifest.copy(contentDigest = EvaluationDatasetDigest("f".repeat(64))))
        val installer = installer(root)

        val result = installer.install(mismatched.manifest, mismatched.input()) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.DIGEST_MISMATCH, result.code)
        assertNotPublished(installer, mismatched)
    }

    @Test
    fun `publication failure removes staged fixture bytes`() = withRoot { root ->
        val fixture = EvaluationDatasetFixtures.pack(caseCount = 20)
        val installer = installer(root)
        val blockedDatasetDirectory = installer.installedDirectory(fixture.manifest).parentFile
        blockedDatasetDirectory.writeText("not-a-directory")

        val result = installer.install(fixture.manifest, fixture.input()) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.IO_FAILURE, result.code)
        assertFalse(installer.installedDirectory(fixture.manifest).exists())
        assertTrue(!installer.stagingRoot().exists() || installer.stagingRoot().listFiles().orEmpty().isEmpty())
    }

    private fun installedCases(
        installer: EvaluationDatasetInstaller,
        fixture: EvaluationDatasetFixtures.DatasetFixturePack,
    ) = FileInputStream(installer.installedDirectory(fixture.manifest).resolve("cases.jsonl")).use(parser::parse)

    private fun installer(root: File) = EvaluationDatasetInstaller(
        rootDirectory = root,
        parser = parser,
        validator = EvaluationDatasetPackValidator(EvaluatorRegistry(listOf(ExactMatchEvaluator.REGISTRATION))),
    )

    private fun assertNotPublished(
        installer: EvaluationDatasetInstaller,
        fixture: EvaluationDatasetFixtures.DatasetFixturePack,
    ) {
        assertFalse(installer.installedDirectory(fixture.manifest).exists())
        assertTrue(!installer.stagingRoot().exists() || installer.stagingRoot().listFiles().orEmpty().isEmpty())
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("evaluation-d09-fixture").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withRoots(block: (File, File) -> Unit) = withRoot { first ->
        withRoot { second -> block(first, second) }
    }

    private val parser = EvaluationDatasetJsonlParser()
}
