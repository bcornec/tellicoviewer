package org.hyper_linux.tellicoviewer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.hyper_linux.tellicoviewer.data.db.*
import org.hyper_linux.tellicoviewer.data.parser.TellicoParser
import javax.inject.Singleton

/**
 * Module Hilt d'injection de dépendances.
 *
 * CONCEPT OF DEPENDENCIES INJECTION (DI) :
 * Instead of writing:
 *   val db = Room.databaseBuilder(...).build()
 *   val dao = db.entryDao()
 *   val repo = TellicoRepository(context, db, TellicoParser(), SearchEngine())
 *
 * ...in every ViewModel, declare ONCE how to build each object.
 * Hilt then automatically injects dependencies wherever @Inject is present.
 *
 * Avantages :
 * - Single DB singleton (no double SQLite open)
 * - Easy mock substitution for tests
 * - Less boilerplate in ViewModels
 *
 * Analogy: like LD_PRELOAD for shared libraries —
 * implementations can be swapped without touching the calling code.
 *
 * @Module: declares that this class provides dependencies
 * @InstallIn(SingletonComponent): dependencies live for the entire app lifetime
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the Room database (singleton).
     * A single instance exists for the entire application lifetime.
     *
     * fallbackToDestructiveMigration() : en développement, si le schéma change
     * without migration, recreate the DB. Remove in production!
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TellicoDatabase =
        Room.databaseBuilder(
            context,
            TellicoDatabase::class.java,
            TellicoDatabase.DATABASE_NAME
        )
        .addMigrations(TellicoDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()  // Safety net for unknown migrations.
        .build()

    @Provides @Singleton
    fun provideCollectionDao(db: TellicoDatabase): CollectionDao = db.collectionDao()

    @Provides @Singleton
    fun provideFieldDao(db: TellicoDatabase): FieldDao = db.fieldDao()

    @Provides @Singleton
    fun provideEntryDao(db: TellicoDatabase): EntryDao = db.entryDao()

    @Provides @Singleton
    fun provideImageDao(db: TellicoDatabase): ImageDao = db.imageDao()
}

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    /** Tellico parser: stateless, safe as a singleton. */
    @Provides @Singleton
    fun provideTellicoParser(): TellicoParser = TellicoParser()
}
