package org.fdroid.tellicoviewer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Classe principale de la base de données Room.
 *
 * @Database déclare les tables (entities) et la version du schéma.
 * En cas de migration (ajout de colonne, nouvelle table), on incrémente
 * la version et on fournit un objet Migration — Room applique le delta SQL.
 *
 * Analogie : c'est comme un singleton qui wrape une connexion sqlite3.
 * Room gère le pool de connexions, les transactions et la thread-safety.
 *
 * La base est instanciée UNE SEULE FOIS via Hilt (voir DatabaseModule).
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
    version = 1,
    exportSchema = true   // exporte le schéma JSON pour versionner les migrations
)
@TypeConverters(Converters::class)
abstract class TellicoDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao
    abstract fun fieldDao(): FieldDao
    abstract fun entryDao(): EntryDao
    abstract fun imageDao(): ImageDao

    companion object {
        const val DATABASE_NAME = "tellico_viewer.db"
    }
}
