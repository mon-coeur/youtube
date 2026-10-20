package com.simon.youtube.core

import java.util.Random

/**
 * Petite multiplication mentale demandée avant chaque ouverture.
 *
 * Objet pur : le générateur est fourni par l'appelant, ce qui rend les tests
 * reproductibles (graine fixe) et permet d'utiliser SecureRandom côté application.
 */
object MultiplicationChallenge {

    /** Plus petit facteur proposé : 10, pour rester faisable de tête. */
    const val MIN_FACTOR: Int = 10

    /** Plus grand facteur proposé : 99, borne incluse. */
    const val MAX_FACTOR: Int = 99

    /**
     * Tire deux facteurs indépendamment et uniformément dans MIN_FACTOR..MAX_FACTOR,
     * bornes incluses. Les deux tirages sont séparés : un carré parfait doit rester
     * aussi probable que n'importe quel autre couple.
     */
    fun generate(random: Random): Pair<Int, Int> {
        val span = MAX_FACTOR - MIN_FACTOR + 1
        val first = MIN_FACTOR + random.nextInt(span)
        val second = MIN_FACTOR + random.nextInt(span)
        return first to second
    }

    /** Réponse attendue. */
    fun answer(a: Int, b: Int): Int = a * b

    /** Vrai si la saisie correspond au produit des deux facteurs. */
    fun isCorrect(a: Int, b: Int, userAnswer: Int): Boolean = userAnswer == a * b
}
