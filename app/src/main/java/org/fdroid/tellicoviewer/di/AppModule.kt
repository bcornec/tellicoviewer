package org.fdroid.tellicoviewer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.fdroid.tellicoviewer.data.db.*
import org.fdroid.tellicoviewer.data.parser.TellicoParser
import javax.inject.Singleton

/**
 * Module Hilt d'injection de dépendances.
 *
 * CONCEPT DE L'INJECTION DE DÉPENDANCES (DI) :
 * Plutôt que d'écrire :
 *   val db = Room.databaseBuilder(...).build()
 *   val dao = db.entryDao()
 *   val repo = TellicoRepository(context, db, TellicoParser(), SearchEngine())
 *
 * ...dans chaque ViewModel, on déclare UNE FOIS comment construire chaque objet.
 * Hilt injecte ensuite automatiquement les dépendances là où elles sont annotées @Inject.
 *
 * Avantages :
 * - Un seul singleton pour la BDD (pas de double ouverture SQLite)
 * - Remplacement facile par des mocks pour les tests
 * - Moins de boilerplate dans les ViewModels
 *
 * Analogie : c'est comme LD_PRELOAD pour les bibliothèques partagées —
 * on peut substituer des implémentations sans toucher au code appelant.
 *
 * @Module   : déclare que cette classe fournit des dépendances
 * @InstallIn(SingletonComponent) : les dépendances vivent pendant toute l'app
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Fournit la base de données Room (singleton).
     * Une seule instance existe pendant toute la durée de vie de l'application.
     *
     * fallbackToDestructiveMigration() : en développement, si le schéma change
     * sans migration, on recrée la BDD. À retirer en production !
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
        .fallbackToDestructiveMigration()  // filet de sécurité si migration inconnue
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

    /** Parseur Tellico : sans état, peut être un singleton */
    @Provides @Singleton
    fun provideTellicoParser(): TellicoParser = TellicoParser()
}
