package io.github.daniele21.localllm.models.controlplane.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHostControlPlaneStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun persistedControlPlaneSurvivesDatabaseRestart() {
        val expected = state()
        withDatabase { first ->
            RoomHostControlPlaneStore(first).replace(expected)
        }

        withDatabase { second ->
            val actual = RoomHostControlPlaneStore(second).snapshot()
            assertEquals(expected.canonical(), actual)
            assertTrue(actual.bindings.single().isDefault)
        }
    }

    @Test
    fun invalidTransactionRollsBackWithoutDestroyingPreviousConfiguration() {
        val expected = state()
        withDatabase { database ->
            val store = RoomHostControlPlaneStore(database)
            store.replace(expected)

            val result = runCatching {
                store.transact {
                    HostControlPlaneState(
                        applications = emptyList(),
                        useCases = it.useCases,
                        presets = it.presets,
                        bindings = it.bindings,
                        exposures = it.exposures,
                    )
                }
            }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(expected.canonical(), store.snapshot())
        }
    }

    private inline fun <T> withDatabase(block: (HostControlPlaneDatabase) -> T): T {
        val database = openDatabase()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun openDatabase(): HostControlPlaneDatabase = Room.databaseBuilder(
        context,
        HostControlPlaneDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(HostControlPlaneDatabase.MIGRATION_1_2).allowMainThreadQueries().build()

    private fun state(): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(
            RegisteredApplication(
                applicationId = APP_ID,
                packageName = "io.github.redactguard",
                signerSha256 = "b".repeat(64),
                displayName = "RedactGuard",
                state = ApplicationRegistrationState.AUTHORIZED,
                firstSeenAtEpochMs = 10,
                lastSeenAtEpochMs = 20,
            ),
        ),
        useCases = listOf(
            UseCaseDefinition(
                useCaseId = USE_CASE_ID,
                displayName = "Document PII detection",
                description = "Detect configured PII",
                requirements = UseCaseRequirements(
                    outputMode = OutputMode.JSON_SCHEMA,
                    sessionKind = SessionKind.STATELESS,
                    reasoningSupported = false,
                    minimumContextTokens = 4_096,
                ),
                state = UseCaseDefinitionState.ACTIVE,
                revision = 2,
            ),
        ),
        presets = listOf(
            UseCasePresetDefinition(
                useCaseId = USE_CASE_ID,
                metadata = PresetConsumerMetadata("balanced", 3, "Balanced", "Balanced local PII analysis"),
                creationSource = PresetCreationSource.CUSTOM,
                state = PresetLifecycleState.PUBLISHED,
                execution = PresetExecutionPolicy(
                    modelProfileId = "qwen35-2b-q4",
                    inferencePreset = InferencePresetRef(InferencePresetId("pii-json"), 5),
                    contextTokens = 8_192,
                    cachePolicy = UseCaseCachePolicy(
                        retainModelWarmMs = 120_000,
                        reuseStatelessContext = false,
                        enablePrefixSnapshot = false,
                        enableDeterministicResultCache = false,
                    ),
                ),
            ),
        ),
        bindings = listOf(
            ApplicationUseCaseBinding(
                bindingId = BINDING_ID,
                applicationId = APP_ID,
                useCaseId = USE_CASE_ID,
                revision = 7,
                isDefault = true,
            ),
        ),
        exposures = listOf(
            StoredPresetExposure(
                bindingId = BINDING_ID,
                bindingRevision = 7,
                presetId = "balanced",
                presetRevision = 3,
                isDefault = true,
            ),
        ),
    )

    private companion object {
        const val DATABASE_NAME = "hcp-control-plane-test.db"
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        const val BINDING_ID = "binding-redactguard-pii"
    }
}
