package com.simon.youtube.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Cœur logique du kiosque YouTube : 100 % pur.
 *
 * Aucun import Android, aucune entrée/sortie, aucune coroutine ici : tout est
 * calculable et testable sur la JVM nue. La date reste en java.text / java.util
 * (SimpleDateFormat) parce que java.time exigerait un desugaring sous minSdk 24.
 *
 * Différence assumée avec AppLimiter : une seule limite globale, plus de AppRule.
 */
object VideoLimitEngine {

    /** Limite minimale acceptée : 1 ouverture par jour. */
    const val MIN_LIMIT: Int = 1

    /** Limite maximale acceptée : 500 ouvertures par jour. */
    const val MAX_LIMIT: Int = 500

    /**
     * Jour courant au format AAAA-MM-JJ.
     *
     * Une instance de SimpleDateFormat est créée à chaque appel (cette classe n'est pas
     * sûre en concurrence), avec Locale.US pour rester sur un calendrier grégorien quel
     * que soit la locale de l'appareil.
     */
    fun todayStamp(
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = zone
        return format.format(Date(nowMillis))
    }

    /** Vrai si la limite tombe dans l'intervalle fermé MIN_LIMIT..MAX_LIMIT. */
    fun isValidLimit(limit: Int): Boolean = limit in MIN_LIMIT..MAX_LIMIT

    /** Ramène une limite quelconque dans MIN_LIMIT..MAX_LIMIT. */
    fun sanitizeLimit(limit: Int): Int = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)

    /**
     * Vrai si une nouvelle vidéo peut encore être ouverte aujourd'hui.
     *
     * Une limite invalide (0, négative, > MAX_LIMIT) bloque toujours : on échoue fermé,
     * une préférence abîmée ne doit pas ouvrir grand la porte. Un compteur absent ou
     * daté d'un autre jour ne consomme rien.
     */
    fun canOpen(limit: Int, usage: UsageEntry?, today: String): Boolean {
        if (!isValidLimit(limit)) return false
        if (usage == null || usage.date != today) return true
        return usage.count < limit
    }

    /**
     * Compteur après une nouvelle ouverture : le total repart de zéro si le compteur
     * connu date d'un autre jour (remise à zéro quotidienne).
     */
    fun recordOpen(usage: UsageEntry?, today: String): UsageEntry {
        val base = if (usage == null || usage.date != today) 0 else usage.count
        return UsageEntry(date = today, count = base + 1)
    }

    /**
     * Ouvertures restantes pour aujourd'hui.
     *
     * 0 si la limite est invalide, la limite pleine si aucun compteur ne concerne
     * aujourd'hui, sinon le reliquat (jamais négatif, même si le compteur a dépassé
     * la limite après une baisse de celle-ci).
     */
    fun remaining(limit: Int, usage: UsageEntry?, today: String): Int {
        if (!isValidLimit(limit)) return 0
        if (usage == null || usage.date != today) return limit
        return maxOf(0, limit - usage.count)
    }
}
