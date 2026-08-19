package io.github.daniele21.localllm.observability.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TelemetryDatabase::class.java,
    )

    @Test
    fun migrationFrom4To5PreservesHistoricalRunsAndAddsNullableGenerationMetadata() {
        helper.createDatabase(DATABASE_NAME, 4).use { database ->
            database.execSQL(
                "INSERT INTO generation_runs (" +
                    "request_id, application_id, use_case_id, model_digest, started_at_epoch_ms, status, model_load_kind" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("request-old", "app", "use-case", DIGEST, 100L, "COMPLETED", "COLD"),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            RoomTelemetryRepository.MIGRATION_4_5,
        ).use { database ->
            database.query(
                "SELECT request_id, preset_id, effective_seed, context_size, stop_reason " +
                    "FROM generation_runs WHERE request_id = ?",
                arrayOf("request-old"),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("request-old", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertTrueNull(cursor, 2)
                assertTrueNull(cursor, 3)
                assertNull(cursor.getString(4))
            }
        }
    }

    @Test
    fun migrationFrom5To6PreservesHistoricalRunsAndAddsNullableRepetitionSettings() {
        helper.createDatabase(DATABASE_NAME, 5).use { database ->
            database.execSQL(
                "INSERT INTO generation_runs (" +
                    "request_id, application_id, use_case_id, model_digest, started_at_epoch_ms, status, model_load_kind" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("request-v5", "app", "use-case", DIGEST, 200L, "COMPLETED", "WARM"),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            RoomTelemetryRepository.MIGRATION_5_6,
        ).use { database ->
            database.query(
                "SELECT request_id, repeat_penalty, repeat_last_n FROM generation_runs WHERE request_id = ?",
                arrayOf("request-v5"),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("request-v5", cursor.getString(0))
                assertTrueNull(cursor, 1)
                assertTrueNull(cursor, 2)
            }
        }
    }

    @Test
    fun migrationFrom8To9PreservesHistoricalRunsAndAddsNullableBackendExecutionEvidence() {
        helper.createDatabase(DATABASE_NAME, 8).use { database ->
            database.execSQL(
                "INSERT INTO generation_runs (" +
                    "request_id, application_id, use_case_id, model_digest, started_at_epoch_ms, status, model_load_kind" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("request-v8", "app", "use-case", DIGEST, 300L, "COMPLETED", "WARM"),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            9,
            true,
            RoomTelemetryRepository.MIGRATION_8_9,
        ).use { database ->
            database.query(
                "SELECT request_id, backend_id, backend_revision, backend_execution_fingerprint, effective_placement " +
                    "FROM generation_runs WHERE request_id = ?",
                arrayOf("request-v8"),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("request-v8", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
            }
        }
    }

    private fun assertTrueNull(cursor: android.database.Cursor, column: Int) {
        check(cursor.isNull(column))
    }

    private companion object {
        const val DATABASE_NAME = "telemetry-migration"
        const val DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
