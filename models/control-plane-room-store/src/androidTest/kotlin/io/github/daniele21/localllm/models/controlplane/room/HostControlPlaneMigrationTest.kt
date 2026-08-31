package io.github.daniele21.localllm.models.controlplane.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostControlPlaneMigrationTest {
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
    fun migrationFromOneToTwoPreservesBindingsAndDefaultsExistingRowsToNonDefault() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(versionOneCallback())
                .build(),
        )

        helper.use {
            val database = it.writableDatabase
            HostControlPlaneDatabase.MIGRATION_1_2.migrate(database)

            database.query(
                "SELECT binding_id, revision, enabled, is_default FROM hcp_binding_revisions",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("binding-redactguard-pii", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
        }
    }

    @Test
    fun migrationFromTwoToThreePreservesPresetAndDefaultsGenerationOverridesToInherited() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(versionTwoCallback())
                .build(),
        )

        helper.use {
            val database = it.writableDatabase
            HostControlPlaneDatabase.MIGRATION_2_3.migrate(database)

            database.query(
                """
                SELECT preset_id, display_name,
                    generation_max_output_tokens,
                    generation_temperature,
                    generation_top_p,
                    generation_top_k,
                    generation_min_p,
                    generation_presence_penalty,
                    generation_repeat_penalty,
                    generation_repeat_last_n,
                    generation_thinking_mode,
                    generation_seed_mode,
                    generation_fixed_seed
                FROM hcp_preset_revisions
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("balanced", cursor.getString(0))
                assertEquals("Balanced", cursor.getString(1))
                for (columnIndex in 2..12) {
                    assertTrue("Generation override column $columnIndex must inherit by default", cursor.isNull(columnIndex))
                }
            }
        }
    }

    private fun versionOneCallback() = object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE hcp_binding_revisions (
                    binding_id TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    application_id TEXT,
                    use_case_id TEXT,
                    enabled INTEGER NOT NULL,
                    PRIMARY KEY(binding_id, revision)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO hcp_binding_revisions (
                    binding_id, revision, application_id, use_case_id, enabled
                ) VALUES ('binding-redactguard-pii', 7, 'redactguard', 'document-pii-detection', 1)
                """.trimIndent(),
            )
        }

        override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun versionTwoCallback() = object : SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE hcp_preset_revisions (
                    use_case_id TEXT NOT NULL,
                    preset_id TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    display_name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    creation_source TEXT NOT NULL,
                    state TEXT NOT NULL,
                    model_profile_id TEXT,
                    inference_preset_id TEXT NOT NULL,
                    inference_preset_version INTEGER NOT NULL,
                    context_tokens INTEGER,
                    retain_model_warm_ms INTEGER NOT NULL,
                    reuse_stateless_context INTEGER NOT NULL,
                    enable_prefix_snapshot INTEGER NOT NULL,
                    enable_deterministic_result_cache INTEGER NOT NULL,
                    PRIMARY KEY(use_case_id, preset_id, revision)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO hcp_preset_revisions (
                    use_case_id, preset_id, revision, display_name, description, creation_source, state,
                    model_profile_id, inference_preset_id, inference_preset_version, context_tokens,
                    retain_model_warm_ms, reuse_stateless_context, enable_prefix_snapshot,
                    enable_deterministic_result_cache
                ) VALUES (
                    'document-pii-detection', 'balanced', 1, 'Balanced', 'Balanced preset', 'SUGGESTED',
                    'PUBLISHED', NULL, 'qwen35-json', 1, 4096, 60000, 0, 0, 0
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "hcp-control-plane-migration-test.db"
    }
}
