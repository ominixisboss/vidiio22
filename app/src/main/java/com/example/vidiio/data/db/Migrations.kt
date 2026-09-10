package com.example.vidiio.data.db

import androidx.room.migration.Migration

/*
 * Schema migrations for [VidiioDatabase].
 *
 * Versions 1-5 predate schema export, so no migration can be reconstructed for them;
 * those upgrades stay destructive (see [LEGACY_DESTRUCTIVE_VERSIONS]). Version 5 is the
 * first schema committed under `app/schemas/`, so every bump from 5 onward must add a
 * [Migration] here — Room will throw at open time rather than silently wipe favorites,
 * watch progress and download records.
 *
 * To add one:
 *   1. Change the entity, bump `version` in [VidiioDatabase], build once so KSP writes
 *      `app/schemas/...VidiioDatabase/<new>.json`, and commit that file.
 *   2. Add a `Migration(old, new)` below with the matching SQL.
 *   3. Add a case to `VidiioDatabaseMigrationTest`.
 *
 * Example:
 * ```
 * private val MIGRATION_5_6 = Migration(5, 6) { db ->
 *     db.execSQL("ALTER TABLE watch_progress ADD COLUMN lastDeviceId TEXT")
 * }
 * ```
 */

/**
 * Versions whose upgrade path is allowed to drop and recreate the database.
 *
 * Only the pre-schema-export versions belong here. Never extend this list to dodge
 * writing a migration — that is exactly the data loss this file exists to stop.
 */
internal val LEGACY_DESTRUCTIVE_VERSIONS = intArrayOf(1, 2, 3, 4)

/** All migrations, in order. Empty until the first bump past version 5. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf()
