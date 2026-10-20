package com.simon.youtube.core

import kotlinx.serialization.Serializable

/**
 * Réglages globaux du kiosque YouTube.
 *
 * Une seule limite pour toute l'application : le kiosque n'a qu'un usage, donc une
 * seule règle et un seul compteur, là où AppLimiter devait gérer une règle par paquet.
 *
 * @property limit nombre maximal d'ouvertures autorisées par jour
 */
@Serializable
data class SettingsState(
    val limit: Int
)

/**
 * Compteur d'ouvertures pour une journée donnée.
 *
 * @property date jour au format AAAA-MM-JJ, dans le fuseau local de l'appareil
 * @property count nombre d'ouvertures déjà consommées ce jour-là
 */
@Serializable
data class UsageEntry(
    val date: String,
    val count: Int
)
