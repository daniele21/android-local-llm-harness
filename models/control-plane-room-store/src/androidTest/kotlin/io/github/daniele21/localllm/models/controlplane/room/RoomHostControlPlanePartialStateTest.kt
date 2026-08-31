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
class RoomHostControlPlanePartialStateTest {
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
    fun partialCurrentSchemaStateCanBeReopenedThenCompletedAtomicallyAndPersists() {
        val partial = partialState()
        withDatabase { database ->
            RoomHostControlPlaneStore(database).replace(partial)
        }

        withDatabase { database ->
            val store = RoomHostControlPlaneStore(database)
            assertEquals(partial.canonical(), store.snapshot())

            val completed = store.transact { current -> complete(current) }
            assertEquals(complete(partial).canonical(), completed)
        }

        withDatabase { database ->
            assertEquals(complete(partial).canonical(), RoomHostControlPlaneStore(database).snapshot())
        }
    }

    @Test
    fun failedRepairTransactionDoesNotExposePartialMutationAcrossReopen() {
        val partial = partialState()
        withDatabase { database ->
            val store = RoomHostControlPlaneStore(database)
            store.replace(partial)

            val failure = runCatching {
                store.transact {
                    throw IllegalStateException("synthetic reconciliation conflict")
                }
            }

            assertTrue(failure.exceptionOrNull() is IllegalStateException)
            assertEquals(partial.canonical(), store.snapshot())
        }

        withDatabase { database ->
            assertEquals(partial.canonical(), RoomHostControlPlaneStore(database).snapshot())
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

    private fun partialState(): HostControlPlaneState = HostControlPlaneState(
        useCases = listOf(useCase()),
        presets = listOf(preset()),
    )

    private fun complete(current: HostControlPlaneState): HostControlPlaneState {
        val application = RegisteredApplication(
            applicationId = APP_ID,
            packageName = "io.github.example.consumer",
            signerSha256 = "a".repeat(64),
            displayName = "Example consumer",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 10,
            lastSeenAtEpochMs = 10,
        )
        val binding = ApplicationUseCaseBinding(
            bindingId = BINDING_ID,
            applicationId = APP_ID,
            useCaseId = USE_CASE_ID,
            revision = 1,
            enabled = true,
            isDefault = true,
        )
        return current.copy(
            applications = current.applications + application,
            bindings = current.bindings + binding,
            exposures = current.exposures + StoredPresetExposure(
                bindingId = BINDING_ID,
                bindingRevision = 1,
                presetId = PRESET_ID,
                presetRevision = 1,
                isDefault = true,
            ),
        )
    }

    private fun useCase() = UseCaseDefinition(
        useCaseId = USE_CASE_ID,
        displayName = "Structured extraction",
        description = "Extract structured fields from local input",
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = false,
            minimumContextTokens = 2_048,
            maxInputCharacters = 8_000,
            maxJsonSchemaCharacters = 2_048,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = 1,
    )

    private fun preset() = UseCasePresetDefinition(
        useCaseId = USE_CASE_ID,
        metadata = PresetConsumerMetadata(
            presetId = PRESET_ID,
            revision = 1,
            displayName = "Structured default",
            description = "Default structured extraction preset",
        ),
        creationSource = PresetCreationSource.SUGGESTED,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = null,
            inferencePreset = InferencePresetRef(InferencePresetId("structured-json"), 1),
            contextTokens = 2_048,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 0,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
        ),
    )

    private companion object {
        const val DATABASE_NAME = "hcp-partial-state-test.db"
        val APP_ID = ApplicationId("example-consumer")
        val USE_CASE_ID = UseCaseId("structured-extraction")
        const val BINDING_ID = "example-consumer-structured-extraction"
        const val PRESET_ID = "structured-default"
    }
}