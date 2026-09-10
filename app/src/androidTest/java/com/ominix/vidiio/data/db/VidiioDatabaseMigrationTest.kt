package com.ominix.vidiio.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Replays every committed schema forward through [ALL_MIGRATIONS].
 *
 * When you bump the database version, add a `migrate(old, new)` case here. A missing
 * or wrong migration fails this test instead of wiping a user's library in the field.
 */
@RunWith(AndroidJUnit4::class)
class VidiioDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VidiioDatabase::class.java
    )

    /**
     * Opens the current schema and runs every declared migration end to end.
     *
     * With no migrations declared yet this asserts the weaker but still useful property
     * that version 5 opens cleanly from its committed schema — which is what proves the
     * schema JSON is actually exported and committed.
     */
    @Test
    @Throws(IOException::class)
    fun migratesFromLatestCommittedSchema() {
        helper.createDatabase(TEST_DB, CURRENT_VERSION).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            CURRENT_VERSION,
            /* validateDroppedTables = */ true,
            *ALL_MIGRATIONS
        ).close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val CURRENT_VERSION = 5
    }
}
