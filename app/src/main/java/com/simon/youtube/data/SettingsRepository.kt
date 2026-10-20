package com.simon.youtube.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simon.youtube.core.UsageEntry
import com.simon.youtube.core.VideoLimitEngine
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Un seul DataStore pour toute l'application, adossé au fichier « youtube_prefs ». */
private val Context.dataStore by preferencesDataStore(name = "youtube_prefs")

/**
 * Persistance des réglages du kiosque et du compteur quotidien.
 *
 * Trois clés seulement :
 *  - « limit »       : entier, nombre d'ouvertures autorisées par jour ;
 *  - « daily_usage » : objet JSON [UsageEntry] du jour ;
 *  - « home_url »    : texte, adresse d'accueil du kiosque.
 *
 * Toute donnée illisible (entier hors bornes, JSON abîmé, fichier corrompu, erreur
 * d'entrée/sortie) est traitée comme absente : valeur par défaut, jamais de plantage.
 * L'application n'a qu'un seul usage, donc aucune map de compteurs par paquet.
 */
class SettingsRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Limite courante, réémise à chaque écriture.
     *
     * La valeur stockée est assainie à la lecture : une préférence écrite à la main
     * (0, négative, 100 000) ne peut pas désarmer la limite.
     */
    val limitFlow: Flow<Int> = appContext.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> VideoLimitEngine.sanitizeLimit(prefs[LIMIT_KEY] ?: DEFAULT_LIMIT) }

    /** Compteur du jour, ou null s'il n'y en a pas encore (ou s'il est illisible). */
    val usageFlow: Flow<UsageEntry?> = appContext.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> decodeUsage(prefs[USAGE_KEY]) }

    /** Adresse d'accueil du kiosque ; une valeur vide ou absente vaut l'adresse par défaut. */
    val homeUrlFlow: Flow<String> = appContext.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> normalizeHomeUrl(prefs[HOME_URL_KEY]) }

    /** Lecture ponctuelle de la limite. */
    suspend fun limit(): Int = limitFlow.first()

    /** Lecture ponctuelle du compteur du jour. */
    suspend fun usage(): UsageEntry? = usageFlow.first()

    /** Lecture ponctuelle de l'adresse d'accueil. */
    suspend fun homeUrl(): String = homeUrlFlow.first()

    /** Change la limite (assainie dans MIN_LIMIT..MAX_LIMIT). */
    suspend fun setLimit(value: Int) {
        val safeLimit = VideoLimitEngine.sanitizeLimit(value)
        appContext.dataStore.edit { prefs ->
            prefs[LIMIT_KEY] = safeLimit
        }
    }

    /** Change l'adresse d'accueil ; une saisie vide ou blanche rétablit la valeur par défaut. */
    suspend fun setHomeUrl(url: String) {
        val cleaned = url.trim()
        val target = if (cleaned.isEmpty()) DEFAULT_HOME_URL else cleaned
        appContext.dataStore.edit { prefs ->
            prefs[HOME_URL_KEY] = target
        }
    }

    /**
     * Enregistre une ouverture et renvoie le compteur à jour.
     *
     * Lecture et écriture se font dans le même bloc [edit] : DataStore applique la
     * transformation de façon atomique, donc deux ouvertures simultanées ne peuvent
     * pas lire le même compteur et n'en perdre qu'une. La remise à zéro quotidienne
     * est décidée par [VideoLimitEngine.recordOpen].
     */
    suspend fun recordOpen(today: String): UsageEntry {
        var entry = VideoLimitEngine.recordOpen(usage = null, today = today)
        appContext.dataStore.edit { prefs ->
            val current = decodeUsage(prefs[USAGE_KEY])
            entry = VideoLimitEngine.recordOpen(usage = current, today = today)
            prefs[USAGE_KEY] = encodeUsage(entry)
        }
        return entry
    }

    /** Efface le compteur du jour (les réglages sont conservés). */
    suspend fun resetUsage() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(USAGE_KEY)
        }
    }

    // --- Encodage / décodage tolérants -------------------------------------

    private fun decodeUsage(raw: String?): UsageEntry? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(UsageEntry.serializer(), raw)
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun encodeUsage(entry: UsageEntry): String =
        json.encodeToString(UsageEntry.serializer(), entry)

    /** Le texte stocké a déjà été nettoyé à l'écriture ; on ne rattrape que le vide. */
    private fun normalizeHomeUrl(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        return if (cleaned.isEmpty()) DEFAULT_HOME_URL else cleaned
    }

    companion object {

        private const val DEFAULT_LIMIT: Int = 10

        private const val DEFAULT_HOME_URL: String = "https://www.youtube.com"

        private val LIMIT_KEY: Preferences.Key<Int> = intPreferencesKey("limit")
        private val USAGE_KEY: Preferences.Key<String> = stringPreferencesKey("daily_usage")
        private val HOME_URL_KEY: Preferences.Key<String> = stringPreferencesKey("home_url")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Volatile
        private var instance: SettingsRepository? = null

        /** Instance unique : toujours construite sur le contexte applicatif. */
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
