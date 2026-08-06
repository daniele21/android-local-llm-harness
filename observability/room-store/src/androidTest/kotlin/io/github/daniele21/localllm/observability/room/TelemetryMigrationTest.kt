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

    private fun assertTrueNull(cursor: android.database.Cursor, column: Int) {
        check(cursor.isNull(column))
    }

    private companion object {
        const val DATABASE_NAME = "telemetry-migration"
        const val DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
