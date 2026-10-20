package com.simon.youtube.core

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

/**
 * Tests unitaires du moteur de limite quotidienne de YouTube Kiosk.
 *
 * JUnit 4 uniquement, JVM nue : aucun mock, aucune dépendance Android, aucune I/O.
 *
 * Règles de déterminisme appliquées partout :
 * - la date « aujourd'hui » est toujours passée explicitement aux fonctions ;
 * - todayStamp reçoit toujours un nowMillis figé et un TimeZone explicite,
 *   jamais l'horloge ni le fuseau de la machine de test ;
 * - aucun test ne dépend de l'ordre d'exécution des autres.
 *
 * Jour de référence de la suite : dimanche 18 octobre 2026 (CEST, UTC+02:00).
 */
class VideoLimitEngineTest {

    private companion object {
        /** Jour de référence de tous les tests. */
        const val AUJOURDHUI = "2026-10-18"

        /** Veille du jour de référence. */
        const val HIER = "2026-10-17"

        /** Lendemain du jour de référence. */
        const val DEMAIN = "2026-10-19"

        /** Format attendu d'un horodatage de jour : AAAA-MM-JJ. */
        val FORMAT_JOUR = Regex("""\d{4}-\d{2}-\d{2}""")

        /**
         * 18/10/2026 à 22:30:00 heure de Paris (UTC+02:00), soit 20:30:00 UTC.
         * Epoch millis figé, vérifié hors du test : aucune conversion de date n'est
         * refaite ici, donc rien ne peut « s'accorder » avec l'implémentation par erreur.
         */
        const val LE_18_A_22H30_PARIS = 1_792_355_400_000L

        /** 18/10/2026 à 23:59:00 heure de Paris, juste avant minuit. */
        const val LE_18_A_23H59_PARIS = 1_792_360_740_000L

        /** 19/10/2026 à 00:01:00 heure de Paris, deux minutes plus tard. */
        const val LE_19_A_00H01_PARIS = 1_792_360_860_000L

        /** 18/10/2026 à 00:30:00 heure de Paris, soit 17/10/2026 à 22:30 UTC. */
        const val LE_18_A_00H30_PARIS = 1_792_276_200_000L
    }

    // ------------------------------------------------------------------
    // 1. Ouverture autorisée puis comptée
    // ------------------------------------------------------------------

    @Test
    fun ouvertureAutoriseePuisComptee() {
        // Arrange : limite de 5, aucun compteur pour aujourd'hui.
        val limite = 5

        // Act + Assert : sans compteur, l'ouverture est autorisée.
        assertTrue(
            "Un usage absent doit autoriser l'ouverture",
            VideoLimitEngine.canOpen(limite, null, AUJOURDHUI)
        )
        assertEquals(
            "La première ouverture du jour doit créer un usage daté d'aujourd'hui à 1",
            UsageEntry(AUJOURDHUI, 1),
            VideoLimitEngine.recordOpen(null, AUJOURDHUI)
        )

        // Sous la limite : 2 ouvertures consommées sur 5.
        val deuxOuvertures = UsageEntry(AUJOURDHUI, 2)
        assertTrue(
            "2 ouvertures sur 5 : l'ouverture doit rester autorisée",
            VideoLimitEngine.canOpen(limite, deuxOuvertures, AUJOURDHUI)
        )
        assertEquals(
            "L'ouverture suivante doit porter le compteur à 3",
            UsageEntry(AUJOURDHUI, 3),
            VideoLimitEngine.recordOpen(deuxOuvertures, AUJOURDHUI)
        )
    }

    // ------------------------------------------------------------------
    // 2. Limite atteinte puis refus
    // ------------------------------------------------------------------

    @Test
    fun limiteAtteintePuisRefus() {
        val limite = 5

        // Act + Assert : à la limite exactement, plus aucune ouverture.
        assertFalse(
            "5 ouvertures pour une limite de 5 : l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(limite, UsageEntry(AUJOURDHUI, 5), AUJOURDHUI)
        )
        // Au-delà de la limite : refus également.
        assertFalse(
            "6 ouvertures pour une limite de 5 : l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(limite, UsageEntry(AUJOURDHUI, 6), AUJOURDHUI)
        )
    }

    // ------------------------------------------------------------------
    // 3. Remise à zéro quotidienne
    // ------------------------------------------------------------------

    @Test
    fun resetQuotidienRemetLeCompteurAZero() {
        // Arrange : compteur plein mais daté de la veille.
        val limite = 5
        val compteurDHier = UsageEntry(HIER, 5)

        // Act + Assert : un usage d'un autre jour ne bloque rien et repart de 1.
        assertTrue(
            "Un usage d'hier, même plein, ne doit pas bloquer aujourd'hui",
            VideoLimitEngine.canOpen(limite, compteurDHier, AUJOURDHUI)
        )
        assertEquals(
            "Une ouverture le lendemain doit repartir de 1, pas de 6",
            UsageEntry(AUJOURDHUI, 1),
            VideoLimitEngine.recordOpen(compteurDHier, AUJOURDHUI)
        )
    }

    @Test
    fun compteurDuFuturTraiteCommeUnAutreJour() {
        // Arrange : compteur daté de demain (horloge reculée ou données incohérentes).
        val limite = 2
        val compteurDeDemain = UsageEntry(DEMAIN, 2)

        // Act + Assert : seule l'égalité de date compte, pas l'ordre chronologique.
        assertTrue(
            "Un usage daté d'un autre jour que celui demandé ne doit pas bloquer",
            VideoLimitEngine.canOpen(limite, compteurDeDemain, AUJOURDHUI)
        )
        assertEquals(
            "Un usage daté d'un autre jour doit être remplacé, pas incrémenté",
            UsageEntry(AUJOURDHUI, 1),
            VideoLimitEngine.recordOpen(compteurDeDemain, AUJOURDHUI)
        )
    }

    // ------------------------------------------------------------------
    // 4. Limites invalides : refus et normalisation
    // ------------------------------------------------------------------

    @Test
    fun limitesInvalidesRefuseesEtRameneesDansLIntervalle() {
        // 0 et -3 : sous le minimum.
        for (limiteInvalide in intArrayOf(0, -3)) {
            assertFalse(
                "La limite $limiteInvalide doit être jugée invalide",
                VideoLimitEngine.isValidLimit(limiteInvalide)
            )
            assertFalse(
                "Limite $limiteInvalide : l'ouverture doit être refusée même sans usage",
                VideoLimitEngine.canOpen(limiteInvalide, null, AUJOURDHUI)
            )
            assertFalse(
                "Limite $limiteInvalide : l'ouverture doit être refusée même à 0 ouverture",
                VideoLimitEngine.canOpen(limiteInvalide, UsageEntry(AUJOURDHUI, 0), AUJOURDHUI)
            )
            assertEquals(
                "Limite $limiteInvalide : aucune ouverture ne doit être annoncée",
                0,
                VideoLimitEngine.remaining(limiteInvalide, null, AUJOURDHUI)
            )
            assertEquals(
                "sanitizeLimit($limiteInvalide) doit ramener la limite au minimum",
                1,
                VideoLimitEngine.sanitizeLimit(limiteInvalide)
            )
        }

        // 600 : au-delà du plafond.
        val horsPlafond = 600
        assertFalse(
            "600 dépasse MAX_LIMIT : la limite doit être invalide",
            VideoLimitEngine.isValidLimit(horsPlafond)
        )
        assertFalse(
            "Limite 600 invalide : l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(horsPlafond, null, AUJOURDHUI)
        )
        assertEquals(
            "Limite 600 invalide : aucune ouverture ne doit être annoncée",
            0,
            VideoLimitEngine.remaining(horsPlafond, null, AUJOURDHUI)
        )
        assertEquals(
            "sanitizeLimit(600) doit ramener la limite au maximum",
            500,
            VideoLimitEngine.sanitizeLimit(horsPlafond)
        )
    }

    @Test
    fun bornesDuContratSontValidesEtInchangees() {
        // Arrange + Act : constantes exposées par l'objet.
        val min = VideoLimitEngine.MIN_LIMIT
        val max = VideoLimitEngine.MAX_LIMIT

        // Assert : bornes du contrat, valides et non modifiées par sanitizeLimit.
        assertEquals("MIN_LIMIT doit valoir 1", 1, min)
        assertEquals("MAX_LIMIT doit valoir 500", 500, max)
        assertTrue(
            "1 est la limite minimale valide",
            VideoLimitEngine.isValidLimit(min)
        )
        assertTrue(
            "500 est la limite maximale valide",
            VideoLimitEngine.isValidLimit(max)
        )
        assertEquals(
            "Une limite déjà valide ne doit pas être modifiée (borne basse)",
            min,
            VideoLimitEngine.sanitizeLimit(min)
        )
        assertEquals(
            "Une limite déjà valide ne doit pas être modifiée (borne haute)",
            max,
            VideoLimitEngine.sanitizeLimit(max)
        )
        assertTrue(
            "Limite 1 sans usage : la première ouverture est autorisée",
            VideoLimitEngine.canOpen(min, null, AUJOURDHUI)
        )
        assertEquals(
            "Limite 500 sans usage : les 500 ouvertures sont annoncées",
            500,
            VideoLimitEngine.remaining(max, null, AUJOURDHUI)
        )
    }

    // ------------------------------------------------------------------
    // 5. Ouvertures restantes
    // ------------------------------------------------------------------

    @Test
    fun restantSelonLeCompteurEtLaDate() {
        val limite = 3

        // Act + Assert : valeur attendue sous la limite.
        assertEquals(
            "1 ouverture consommée sur 3 : il doit en rester 2",
            2,
            VideoLimitEngine.remaining(limite, UsageEntry(AUJOURDHUI, 1), AUJOURDHUI)
        )
        assertEquals(
            "3 ouvertures consommées sur 3 : il ne reste rien",
            0,
            VideoLimitEngine.remaining(limite, UsageEntry(AUJOURDHUI, 3), AUJOURDHUI)
        )
    }

    @Test
    fun restantJamaisNegatifQuandLeCompteurDepasseLaLimite() {
        val limite = 3

        // Act + Assert : compteur au-delà de la limite, y compris très au-delà.
        assertEquals(
            "4 ouvertures pour une limite de 3 : le restant ne doit pas être négatif",
            0,
            VideoLimitEngine.remaining(limite, UsageEntry(AUJOURDHUI, 4), AUJOURDHUI)
        )
        assertEquals(
            "99 ouvertures pour une limite de 3 : le restant ne doit pas être négatif",
            0,
            VideoLimitEngine.remaining(limite, UsageEntry(AUJOURDHUI, 99), AUJOURDHUI)
        )
    }

    @Test
    fun restantPleinQuandLUsageEstAbsentOuDUnAutreJour() {
        val limite = 3

        // Act + Assert : aucune consommation aujourd'hui, la limite est entière.
        assertEquals(
            "Usage absent : la limite entière est disponible",
            3,
            VideoLimitEngine.remaining(limite, null, AUJOURDHUI)
        )
        assertEquals(
            "Usage d'hier : la limite entière est disponible aujourd'hui",
            3,
            VideoLimitEngine.remaining(limite, UsageEntry(HIER, 2), AUJOURDHUI)
        )
        assertTrue(
            "Un usage d'un autre jour, même très élevé, n'interdit pas l'ouverture",
            VideoLimitEngine.canOpen(limite, UsageEntry(HIER, 99), AUJOURDHUI)
        )
    }

    @Test
    fun compteurAZeroNaUnEffetSurAucuneRegle() {
        // Arrange : usage du jour créé mais aucune ouverture consommée.
        val limite = 1
        val aucunUsage = UsageEntry(AUJOURDHUI, 0)

        // Act + Assert : 0 consommé se comporte comme « rien de consommé ».
        assertTrue(
            "0 ouverture consommée sur 1 : l'ouverture doit être autorisée",
            VideoLimitEngine.canOpen(limite, aucunUsage, AUJOURDHUI)
        )
        assertEquals(
            "0 ouverture consommée sur 1 : il reste une ouverture",
            1,
            VideoLimitEngine.remaining(limite, aucunUsage, AUJOURDHUI)
        )
        assertEquals(
            "La dernière ouverture autorisée doit porter le compteur à 1",
            UsageEntry(AUJOURDHUI, 1),
            VideoLimitEngine.recordOpen(aucunUsage, AUJOURDHUI)
        )
        assertFalse(
            "Limite 1 avec 1 ouverture : l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(limite, UsageEntry(AUJOURDHUI, 1), AUJOURDHUI)
        )
    }

    // ------------------------------------------------------------------
    // 6. Horodatage du jour (fuseau fixe, aucune horloge réelle)
    // ------------------------------------------------------------------

    @Test
    fun horodatageAuFormatAttenduEtValeurExacte() {
        // Arrange : fuseau explicitement passé, instant figé.
        val paris = TimeZone.getTimeZone("Europe/Paris")

        // Act : horodatage du jour pour cet instant.
        val horodatage = VideoLimitEngine.todayStamp(LE_18_A_22H30_PARIS, paris)

        // Assert : format puis valeur exacte.
        assertTrue(
            "L'horodatage doit respecter le format AAAA-MM-JJ, obtenu : « $horodatage »",
            FORMAT_JOUR.matches(horodatage)
        )
        assertEquals(
            "Le 18/10/2026 à 22 h 30 à Paris doit produire la date du 18/10/2026",
            "2026-10-18",
            horodatage
        )
    }

    @Test
    fun horodatageBasculeAMinuit() {
        // Arrange : deux instants à deux minutes d'intervalle, à cheval sur minuit.
        val paris = TimeZone.getTimeZone("Europe/Paris")

        // Act
        val avantMinuit = VideoLimitEngine.todayStamp(LE_18_A_23H59_PARIS, paris)
        val apresMinuit = VideoLimitEngine.todayStamp(LE_19_A_00H01_PARIS, paris)

        // Assert
        assertEquals(
            "À 23 h 59, on est encore le 18/10/2026",
            "2026-10-18",
            avantMinuit
        )
        assertEquals(
            "À 00 h 01, on est déjà le 19/10/2026",
            "2026-10-19",
            apresMinuit
        )
        assertNotEquals(
            "Deux minutes à cheval sur minuit doivent changer de jour",
            avantMinuit,
            apresMinuit
        )
    }

    @Test
    fun horodatageRespecteLeFuseauDemande() {
        // Arrange : même instant, deux fuseaux.
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val utc = TimeZone.getTimeZone("UTC")

        // Act
        val vuDeParis = VideoLimitEngine.todayStamp(LE_18_A_00H30_PARIS, paris)
        val vuDUTC = VideoLimitEngine.todayStamp(LE_18_A_00H30_PARIS, utc)

        // Assert : 00 h 30 à Paris, c'est encore la veille en UTC (17/10 22 h 30).
        assertEquals(
            "Le fuseau passé doit être utilisé : 00 h 30 à Paris, c'est le 18/10",
            "2026-10-18",
            vuDeParis
        )
        assertEquals(
            "Le même instant lu en UTC tombe la veille : 17/10",
            "2026-10-17",
            vuDUTC
        )
        assertNotEquals(
            "Deux fuseaux différents sur un même instant ne doivent pas donner le même jour",
            vuDeParis,
            vuDUTC
        )
    }

    // ------------------------------------------------------------------
    // 7. Cohérence entre les fonctions (chaîne ouverture → comptage → refus)
    // ------------------------------------------------------------------

    @Test
    fun chaineOuvertureComptageJusquAuRefus() {
        // Arrange : limite de 2, aucun usage, tout en dates explicites.
        val limite = 2
        var usage: UsageEntry? = null
        val joursOuverts = mutableListOf<Int>()

        // Act : on tente 10 ouvertures de suite, en n'enregistrant que celles autorisées.
        // La boucle est bornée : une implémentation fautive doit faire échouer le test,
        // pas le faire tourner indéfiniment.
        repeat(10) {
            if (VideoLimitEngine.canOpen(limite, usage, AUJOURDHUI)) {
                usage = VideoLimitEngine.recordOpen(usage, AUJOURDHUI)
                joursOuverts.add(usage!!.count)
            }
        }

        // Assert : exactement 2 ouvertures, compteurs 1 puis 2, puis blocage.
        assertEquals(
            "La limite de 2 doit autoriser exactement 2 ouvertures, obtenu : $joursOuverts",
            listOf(1, 2),
            joursOuverts
        )
        assertFalse(
            "Après 2 ouvertures sur 2, l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(limite, usage, AUJOURDHUI)
        )
        assertEquals(
            "Après 2 ouvertures sur 2, il ne doit plus rien rester",
            0,
            VideoLimitEngine.remaining(limite, usage, AUJOURDHUI)
        )
        assertEquals(
            "L'usage enregistré doit être daté d'aujourd'hui",
            AUJOURDHUI,
            usage?.date
        )
    }

    @Test
    fun leLendemainLaLimiteEstDeNouveauPleine() {
        // Arrange : limite de 2 pleinement consommée aujourd'hui.
        val limite = 2
        val pleinCeJour = UsageEntry(AUJOURDHUI, 2)

        // Act + Assert : au changement de date tout est rouvert, sans toucher aux données.
        assertFalse(
            "Limite pleine aujourd'hui : l'ouverture doit être refusée",
            VideoLimitEngine.canOpen(limite, pleinCeJour, AUJOURDHUI)
        )
        assertTrue(
            "Le lendemain, la limite doit être de nouveau disponible",
            VideoLimitEngine.canOpen(limite, pleinCeJour, DEMAIN)
        )
        assertEquals(
            "Le lendemain, la limite entière doit être annoncée",
            2,
            VideoLimitEngine.remaining(limite, pleinCeJour, DEMAIN)
        )
        assertEquals(
            "Le lendemain, le compteur doit repartir de 1 et porter la nouvelle date",
            UsageEntry(DEMAIN, 1),
            VideoLimitEngine.recordOpen(pleinCeJour, DEMAIN)
        )
    }
}
