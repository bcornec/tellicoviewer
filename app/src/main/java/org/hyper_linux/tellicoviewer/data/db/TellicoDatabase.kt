package org.hyper_linux.tellicoviewer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database class.
 *
 * @Database declares the tables (entities) and schema version.
 * When migrating (adding a column, new table), increment
 * the version and provide a Migration object — Room applies the SQL delta.
 *
 * Analogy: like a singleton wrapping an sqlite3 connection.
 * Room manages the connection pool, transactions and thread-safety.
 *
 * The database is instantiated ONCE via Hilt (see DatabaseModule).
 * Elle vit pendant toute la durée de vie de l'application.
 */
@Database(
    entities = [
        CollectionEntity::class,
        FieldEntity::class,
        EntryEntity::class,
        EntryFtsEntity::class,
        ImageEntity::class
    ],
    version = 2,
    exportSchema = true   // exports JSON schema to version migrations
)
@TypeConverters(Converters::class)
abstract class TellicoDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao
    abstract fun fieldDao(): FieldDao
    abstract fun entryDao(): EntryDao
    abstract fun imageDao(): ImageDao

    companion object {
        const val DATABASE_NAME = "tellico_viewer.db"

        /**
         * Migration v1→v2: adds imageBasePath column to the collections table.
         * ALTER TABLE is the SQLite way to add a column without recreating the table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE collections ADD COLUMN imageBasePath TEXT"
                )
            }
        }
    }
}
