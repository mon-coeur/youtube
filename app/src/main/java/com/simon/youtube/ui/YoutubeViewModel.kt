package com.simon.youtube.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simon.youtube.core.MultiplicationChallenge
import com.simon.youtube.core.VideoLimitEngine
import com.simon.youtube.data.SettingsRepository
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** URL chargée quand aucune adresse d'accueil n'a encore été choisie. */
const val URL_ACCUEIL_PAR_DEFAUT: String = "https://www.youtube.com"

/** Limite affichée avant la première lecture du DataStore (valeur par défaut du dépôt). */
private const val LIMITE_PAR_DEFAUT: Int = 10

/** Défi en cours : deux facteurs, l'URL visée et le nombre de mauvaises réponses. */
data class DefiEnCours(
    val a: Int,
    val b: Int,
    val url: String,
    val erreurs: Int = 0
)

/** Ouverture de NewPipe à effectuer par l'écran (le ViewModel n'a pas de Context). */
data class DemandeOuverture(
    val id: Long,
    val url: String
)

/** Message éphémère affiché en bas de l'écran, identifié pour ne pas se rejouer. */
data class Avis(
    val id: Long,
    val texte: String
)

/**
 * État complet de l'écran : ce que l'interface affiche, et rien d'autre.
 * Les champs dérivés du jour (compteur, restant) sont recalculés à chaque
 * émission pour qu'un changement de date ne laisse jamais un compteur périmé.
 */
data class YoutubeEtat(
    val limite: Int = LIMITE_PAR_DEFAUT,
    val compteDuJour: Int = 0,
    val restant: Int = LIMITE_PAR_DEFAUT,
    val urlAccueil: String = URL_ACCUEIL_PAR_DEFAUT,
    val defi: DefiEnCours? = null,
    val demandeOuverture: DemandeOuverture? = null,
    val avis: Avis? = null,
    val chargement: Boolean = true
)

/**
 * ViewModel unique du kiosque.
 *
 * Il ne détient aucun Context : la factory reçoit le contexte applicatif, en
 * extrait le [SettingsRepository] et ne conserve que lui. L'ouverture de
 * NewPipe est publiée sous forme de [DemandeOuverture] que l'écran exécute,
 * puis confirme ; le compteur n'est incrémenté qu'après confirmation.
 */
class YoutubeViewModel(private val depot: SettingsRepository) : ViewModel() {

    /** Partie volatile de l'état : défi, ouverture en attente, message. */
    private data class Ephemere(
        val defi: DefiEnCours? = null,
        val demande: DemandeOuverture? = null,
        val avis: Avis? = null
    )

    private val ephemere = MutableStateFlow(Ephemere())

    /** Générateur imprévisible : la suite des questions ne doit pas se deviner. */
    private val alea = SecureRandom()

    private val compteurIds = AtomicLong(0L)

    val etat: StateFlow<YoutubeEtat> = combine(
        depot.limitFlow,
        depot.usageFlow,
        depot.homeUrlFlow,
        ephemere
    ) { limite, usage, urlAccueil, instantane ->
        val aujourdHui = VideoLimitEngine.todayStamp()
        // Un compteur daté d'hier ne compte pas : l'affichage repart de zéro.
        val compte = if (usage != null && usage.date == aujourdHui) usage.count else 0
        YoutubeEtat(
            limite = limite,
            compteDuJour = compte,
            restant = VideoLimitEngine.remaining(limite, usage, aujourdHui),
            urlAccueil = urlAccueil.ifBlank { URL_ACCUEIL_PAR_DEFAUT },
            defi = instantane.defi,
            demandeOuverture = instantane.demande,
            avis = instantane.avis,
            chargement = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YoutubeEtat(chargement = true)
    )

    /**
     * Point d'entrée du flux : le WebView a repéré une URL de vidéo.
     *
     * Ordre impératif — la limite d'abord, le défi ensuite. Si la limite du jour
     * est atteinte, aucune question n'est posée et rien n'est compté.
     */
    fun demanderVideo(url: String) {
        viewModelScope.launch {
            val aujourdHui = VideoLimitEngine.todayStamp()
            val limite = depot.limit()
            val usage = depot.usage()
            if (!VideoLimitEngine.canOpen(limite, usage, aujourdHui)) {
                ephemere.update {
                    it.copy(
                        defi = null,
                        demande = null,
                        avis = Avis(compteurIds.incrementAndGet(), "Limite atteinte pour aujourd'hui")
                    )
                }
                return@launch
            }
            val facteurs = MultiplicationChallenge.generate(alea)
            ephemere.update {
                it.copy(
                    defi = DefiEnCours(a = facteurs.first, b = facteurs.second, url = url),
                    demande = null
                )
            }
        }
    }

    /** Valide la saisie ; une mauvaise réponse conserve la même question. */
    fun soumettreReponse(saisie: String) {
        val valeur = saisie.trim().toIntOrNull()
        ephemere.update { courant ->
            val defi = courant.defi ?: return@update courant
            if (valeur == null || !MultiplicationChallenge.isCorrect(defi.a, defi.b, valeur)) {
                courant.copy(
                    defi = defi.copy(erreurs = defi.erreurs + 1),
                    demande = null
                )
            } else {
                courant.copy(
                    defi = null,
                    demande = DemandeOuverture(compteurIds.incrementAndGet(), defi.url)
                )
            }
        }
    }

    /**
     * Retour de l'écran après la tentative d'ouverture de NewPipe.
     * Le compteur n'avance que si NewPipe a réellement été lancé.
     */
    fun terminerOuverture(id: Long, reussie: Boolean) {
        val demande = ephemere.value.demande
        if (demande == null || demande.id != id) return
        if (!reussie) {
            ephemere.update {
                it.copy(
                    demande = null,
                    avis = Avis(compteurIds.incrementAndGet(), "NewPipe n'est pas installé")
                )
            }
            return
        }
        ephemere.update { it.copy(demande = null) }
        viewModelScope.launch {
            depot.recordOpen(VideoLimitEngine.todayStamp())
        }
    }

    /** Abandon du défi (touche retour) : rien n'est ouvert ni compté. */
    fun annulerDefi() {
        ephemere.update { it.copy(defi = null) }
    }

    /** Ferme le message s'il est toujours celui qui s'affiche. */
    fun fermerAvis(id: Long) {
        ephemere.update { if (it.avis?.id == id) it.copy(avis = null) else it }
    }

    /** Change la limite quotidienne, toujours ramenée dans 1..500. */
    fun changerLimite(valeur: Int) {
        viewModelScope.launch {
            depot.setLimit(VideoLimitEngine.sanitizeLimit(valeur))
        }
    }

    /** Change l'URL d'accueil ; refuse tout ce qui n'est pas http(s). */
    fun changerUrlAccueil(url: String) {
        val propre = url.trim()
        if (!propre.startsWith("http://") && !propre.startsWith("https://")) return
        viewModelScope.launch {
            depot.setHomeUrl(propre)
        }
    }

    /** Remet le compteur du jour à zéro sans toucher à la limite. */
    fun reinitialiserCompteur() {
        viewModelScope.launch {
            depot.resetUsage()
        }
    }

    companion object {

        /**
         * Factory : le contexte applicatif sert uniquement à construire le
         * dépôt DataStore, il n'est jamais conservé dans le ViewModel.
         */
        fun fabrique(contexte: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val depot = SettingsRepository.get(contexte.applicationContext)
                    return YoutubeViewModel(depot) as T
                }
            }
    }
}
