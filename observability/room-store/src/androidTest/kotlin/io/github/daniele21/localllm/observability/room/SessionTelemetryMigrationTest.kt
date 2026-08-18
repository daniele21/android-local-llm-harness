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
class SessionTelemetryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TelemetryDatabase::class.java,
    )

    @Test
    fun migrationFrom8To9PreservesRunsAndAddsSessionIdentityTables() {
        helper.createDatabase(DATABASE_NAME, 8).use { database ->
            database.execSQL(
                "INSERT INTO generation_runs (" +
                    "request_id, application_id, use_case_id, model_digest, started_at_epoch_ms, status, model_load_kind" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("request-v8", "app", "use-case", DIGEST, 100L, "COMPLETED", "COLD"),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            9,
            true,
            RoomTelemetryRepository.MIGRATION_8_9,
        ).use { database ->
            database.query(
                "SELECT request_id, session_id, use_case_revision, binding_revision " +
                    "FROM generation_runs WHERE request_id = ?",
                arrayOf("request-v8"),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("request-v8", cursor.getString(0))
                assertNull(cursor.getString(1))
                check(cursor.isNull(2))
                check(cursor.isNull(3))
            }

            database.query("SELECT COUNT(*) FROM inference_sessions").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "telemetry-session-migration"
        const val DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
