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

    private companion object {
        const val DATABASE_NAME = "hcp-control-plane-migration-test.db"
    }
}
