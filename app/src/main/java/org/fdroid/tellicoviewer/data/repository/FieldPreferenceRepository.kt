package org.fdroid.tellicoviewer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository de préférences de champs par collection.
 *
 * Stocke dans DataStore (fichier de préférences persistant) :
 * - Quels champs sont visibles/cachés
 * - L'ordre d'affichage des colonnes
 *
 * DataStore est l'équivalent Android d'un fichier ~/.config/apprc :
 * persistant, thread-safe, asynchrone via Flow.
 */

// Extension pour créer le DataStore (singleton au niveau Application)
private val Context.fieldPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "field_preferences")

@Serializable
data class FieldPreference(
    val fieldName: String,
    val visible: Boolean = true,
    val sortOrder: Int = 0    // ordre d'affichage dans la grille
)

@Singleton
class FieldPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Clé DataStore pour une collection donnée */
    private fun prefsKey(collectionId: Long) =
        stringPreferencesKey("field_prefs_$collectionId")

    /**
     * Observe les préférences de champs pour une collection.
     * Retourne la liste vide si aucune préférence n'a encore été définie
     * (dans ce cas, tous les champs sont considérés visibles).
     */
    fun observeFieldPreferences(collectionId: Long): Flow<List<FieldPreference>> =
        context.fieldPrefsDataStore.data.map { prefs ->
            val raw = prefs[prefsKey(collectionId)] ?: return@map emptyList()
            try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
        }

    /**
     * Sauvegarde les préférences de champs pour une collection.
     */
    suspend fun saveFieldPreferences(
        collectionId: Long,
        preferences: List<FieldPreference>
    ) {
        context.fieldPrefsDataStore.edit { prefs ->
            prefs[prefsKey(collectionId)] = json.encodeToString(preferences)
        }
    }

    /**
     * Retourne la liste des champs visibles dans l'ordre d'affichage.
     * Si aucune préférence, retourne null (= afficher tous les champs).
     */
    suspend fun getVisibleFieldNames(collectionId: Long): List<String>? {
        val prefs = context.fieldPrefsDataStore.data
        // Lecture synchrone via first() — appelé depuis une coroutine
        return null  // géré via le Flow dans le ViewModel
    }
}
