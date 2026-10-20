package com.simon.youtube.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Tests unitaires du défi de multiplication du cœur de YouTube Kiosk.
 *
 * JUnit 4 uniquement, JVM nue : aucun mock, aucune dépendance Android, aucune I/O.
 *
 * Règles de déterminisme appliquées partout :
 * - chaque test tire son propre [Random] avec une graine figée : le flux de nombres
 *   est reproductible et ne dépend ni de l'ordre d'exécution des tests, ni de
 *   l'horloge, ni de la machine ;
 * - aucun test ne dépend de l'ordre d'exécution des autres ;
 * - les tirages sont faits une seule fois par test, sur un échantillon fixé.
 *
 * Contrat testé : generate tire deux facteurs indépendants dans [MIN_FACTOR, MAX_FACTOR],
 * bornes incluses ; answer renvoie le produit exact ; isCorrect n'accepte que ce produit.
 */
class MultiplicationChallengeTest {

    private companion object {
        /** Graine fixe de l'échantillon principal : même suite à chaque exécution. */
        const val GRAINE = 20_261_018L

        /** Taille de l'échantillon : assez grand pour couvrir les 90 valeurs. */
        const val NOMBRE_DE_TIRAGES = 10_000

        /** Nombre de valeurs possibles pour un facteur, bornes incluses : 99 - 10 + 1. */
        const val VALEURS_POSSIBLES = 90
    }

    // ------------------------------------------------------------------
    // 1. Bornes du contrat
    // ------------------------------------------------------------------

    @Test
    fun bornesDuContratInchangees() {
        // Arrange + Act : constantes exposées par l'objet.

        // Assert : les bornes annoncées dans le contrat ne bougent pas en silence.
        assertEquals(
            "MIN_FACTOR doit rester la borne basse du contrat",
            10,
            MultiplicationChallenge.MIN_FACTOR
        )
        assertEquals(
            "MAX_FACTOR doit rester la borne haute du contrat",
            99,
            MultiplicationChallenge.MAX_FACTOR
        )
    }

    // ------------------------------------------------------------------
    // 2. Les deux facteurs restent dans [10, 99]
    // ------------------------------------------------------------------

    @Test
    fun lesDeuxFacteursRestentDansLesBornesSurDixMilleTirages() {
        // Arrange : générateur à graine figée, bornes lues depuis le contrat.
        val generateur = Random(GRAINE)
        val min = MultiplicationChallenge.MIN_FACTOR
        val max = MultiplicationChallenge.MAX_FACTOR
        var plusPetitVu = Int.MAX_VALUE
        var plusGrandVu = Int.MIN_VALUE
        var dixiemeAtteint = false
        var quatreVingtDixNeuviemeAtteint = false

        repeat(NOMBRE_DE_TIRAGES) {
            // Act : un tirage.
            val (a, b) = MultiplicationChallenge.generate(generateur)

            // Assert : chaque facteur doit tenir dans l'intervalle, bornes incluses.
            assertTrue(
                "Tirage hors bornes : premier facteur $a, attendu dans [$min, $max]",
                a in min..max
            )
            assertTrue(
                "Tirage hors bornes : second facteur $b, attendu dans [$min, $max]",
                b in min..max
            )

            for (facteur in intArrayOf(a, b)) {
                if (facteur < plusPetitVu) plusPetitVu = facteur
                if (facteur > plusGrandVu) plusGrandVu = facteur
                if (facteur == min) dixiemeAtteint = true
                if (facteur == max) quatreVingtDixNeuviemeAtteint = true
            }
        }

        // Les bornes doivent être réellement atteintes : un générateur qui resterait
        // dans [11, 98] passerait le test de bornes sans jamais produire 10 ni 99.
        assertTrue(
            "Sur $NOMBRE_DE_TIRAGES tirages, la borne basse $min n'est jamais apparue " +
                "(plus petit facteur observé : $plusPetitVu)",
            dixiemeAtteint
        )
        assertTrue(
            "Sur $NOMBRE_DE_TIRAGES tirages, la borne haute $max n'est jamais apparue " +
                "(plus grand facteur observé : $plusGrandVu)",
            quatreVingtDixNeuviemeAtteint
        )
    }

    // ------------------------------------------------------------------
    // 3. Couverture grossière des 90 valeurs possibles
    // ------------------------------------------------------------------

    @Test
    fun lesQuatreVingtDixValeursApparaissentToutesSurDixMilleTirages() {
        // Arrange : le tableau compte chaque valeur rencontrée (une ligne par valeur).
        val generateur = Random(GRAINE)
        val rencontrees = BooleanArray(100)
        var valeursDistinctes = 0

        repeat(NOMBRE_DE_TIRAGES) {
            // Act : un tirage, deux facteurs.
            val (a, b) = MultiplicationChallenge.generate(generateur)

            // Assert (accumulé) : on note les valeurs vues.
            for (facteur in intArrayOf(a, b)) {
                if (!rencontrees[facteur]) {
                    rencontrees[facteur] = true
                    valeursDistinctes++
                }
            }
        }

        for (valeur in MultiplicationChallenge.MIN_FACTOR..MultiplicationChallenge.MAX_FACTOR) {
            assertTrue(
                "La valeur $valeur n'est jamais sortie en $NOMBRE_DE_TIRAGES tirages : " +
                    "le tirage n'est pas uniforme sur [$VALEURS_POSSIBLES valeurs]",
                rencontrees[valeur]
            )
        }
        assertEquals(
            "Les $VALEURS_POSSIBLES valeurs de l'intervalle doivent toutes être atteintes",
            VALEURS_POSSIBLES,
            valeursDistinctes
        )
    }

    // ------------------------------------------------------------------
    // 4. Independence grossière des deux facteurs
    // ------------------------------------------------------------------

    @Test
    fun lesDeuxFacteursNeSontPasToujoursEgaux() {
        // Arrange : même graine que les autres tests, mais observation différente.
        val generateur = Random(GRAINE)
        var couplesSemblables = 0
        var couplesDifferents = 0

        repeat(NOMBRE_DE_TIRAGES) {
            // Act : un tirage.
            val (a, b) = MultiplicationChallenge.generate(generateur)

            // Assert (accumulé) : les deux facteurs ne sont pas un seul et même tirage.
            if (a == b) couplesSemblables++ else couplesDifferents++
        }

        assertTrue(
            "Aucun couple de facteurs différents en $NOMBRE_DE_TIRAGES tirages : " +
                "les deux facteurs ne sont pas tirés indépendamment",
            couplesDifferents > 0
        )
        // Un seul tirage recopié donnerait 100 % de couples égaux ; deux tirages
        // indépendants en donnent environ 1 sur 90.
        assertTrue(
            "Trop de couples égaux ($couplesSemblables sur $NOMBRE_DE_TIRAGES) : " +
                "les deux facteurs semblent dépendants",
            couplesSemblables < NOMBRE_DE_TIRAGES / 4
        )
    }

    // ------------------------------------------------------------------
    // 5. Produit exact
    // ------------------------------------------------------------------

    @Test
    fun answerProduitExactementLesDeuxFacteurs() {
        // Arrange : couples choisis à la main, dont les deux bornes et un couple à
        // retenue, avec le produit attendu calculé hors du code testé.
        val cas = listOf(
            intArrayOf(10, 10, 100),   // borne basse : 10 × 10
            intArrayOf(99, 99, 9801),  // borne haute : 99 × 99
            intArrayOf(47, 63, 2961),  // couple quelconque à retenue
            intArrayOf(10, 99, 990),   // bornes croisées
            intArrayOf(12, 12, 144)
        )

        for (casTeste in cas) {
            val a = casTeste[0]
            val b = casTeste[1]
            val attendu = casTeste[2]

            // Act : produit des deux facteurs.
            val obtenu = MultiplicationChallenge.answer(a, b)

            // Assert : produit exact.
            assertEquals(
                "answer($a, $b) doit valoir $attendu",
                attendu,
                obtenu
            )
        }
    }

    // ------------------------------------------------------------------
    // 6. Validation de la réponse du joueur
    // ------------------------------------------------------------------

    @Test
    fun isCorrectAccepteLeProduitEtRefuseLesVoisins() {
        // Arrange : un couple à retenue, produit connu.
        val a = 47
        val b = 63
        val produit = 2961

        // Act + Assert : seule la bonne réponse passe.
        assertTrue(
            "La bonne réponse $produit doit être acceptée pour $a × $b",
            MultiplicationChallenge.isCorrect(a, b, produit)
        )
        assertFalse(
            "La réponse ${produit - 1} ne doit pas être acceptée pour $a × $b",
            MultiplicationChallenge.isCorrect(a, b, produit - 1)
        )
        assertFalse(
            "La réponse ${produit + 1} ne doit pas être acceptée pour $a × $b",
            MultiplicationChallenge.isCorrect(a, b, produit + 1)
        )
        assertFalse(
            "La réponse 0 ne doit jamais être acceptée",
            MultiplicationChallenge.isCorrect(a, b, 0)
        )
    }

    @Test
    fun isCorrectAUneErreurPresSurLesBornes() {
        // Arrange : les deux cas extrêmes du produit.
        val produitBas = 10 * 10   // 100
        val produitHaut = 99 * 99  // 9801

        // Act + Assert : le voisin immédiat reste faux, y compris sur les bornes.
        assertTrue(
            "10 × 10 = 100 doit être accepté",
            MultiplicationChallenge.isCorrect(10, 10, produitBas)
        )
        assertFalse(
            "99 n'est pas la réponse à 10 × 10",
            MultiplicationChallenge.isCorrect(10, 10, produitBas - 1)
        )
        assertTrue(
            "99 × 99 = 9801 doit être accepté",
            MultiplicationChallenge.isCorrect(99, 99, produitHaut)
        )
        assertFalse(
            "9800 n'est pas la réponse à 99 × 99",
            MultiplicationChallenge.isCorrect(99, 99, produitHaut - 1)
        )
        assertFalse(
            "9802 n'est pas la réponse à 99 × 99",
            MultiplicationChallenge.isCorrect(99, 99, produitHaut + 1)
        )
    }
}
