package com.simon.youtube.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * User-agent d'un Chrome mobile classique : YouTube sert alors sa version
 * mobile allégée au lieu d'une page « navigateur non pris en charge ».
 */
private const val USER_AGENT_MOBILE: String =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * Script qui masque les Shorts dans la page servie par YouTube.
 *
 * Injecté à chaque fin de chargement par `onPageFinished`. Deux familles de
 * balises sont couvertes : la version mobile (`ytm-…`, celle que rend YouTube
 * avec [USER_AGENT_MOBILE]) et la version bureau (`ytd-…`) au cas où le site
 * basculerait de lui-même sur l'autre rendu.
 *
 * Le marqueur `window.__masqueurShorts` évite d'installer deux observateurs dans
 * le même contexte JavaScript ; après un rechargement complet le contexte est
 * neuf, le marqueur disparaît et l'injection se réinstalle d'elle-même.
 *
 * YouTube est une application d'une seule page : les vignettes suivantes
 * arrivent par défilement infini, d'où le `MutationObserver` plutôt qu'une
 * simple passe au chargement.
 */
private const val JS_MASQUER_SHORTS: String = """
(function () {
  if (window.__masqueurShorts) { return; }
  window.__masqueurShorts = true;

  var CONTENEURS_VIGNETTE =
    'ytm-rich-item-renderer, ytm-video-with-context-renderer, ' +
    'ytm-grid-video-renderer, ytm-reel-item-renderer, ' +
    'ytm-shorts-lockup-view-model, ' +
    'ytd-rich-item-renderer, ytd-video-renderer, ' +
    'ytd-compact-video-renderer, ytd-grid-video-renderer, ' +
    'ytd-reel-item-renderer';

  var ETAGERES_SHORTS =
    'ytm-reel-shelf-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts]';

  var SECTIONS_CONDITIONNELLES =
    'ytm-rich-section-renderer, ytd-rich-section-renderer, ytd-rich-shelf-renderer';

  var MARQUEUR_SHORTS = '[href^="/shorts/"], ytm-shorts-lockup-view-model, ytm-reel-item-renderer, ytd-reel-item-renderer';

  function masquer() {
    var i, vignettes, sections, onglets;

    vignettes = document.querySelectorAll('[href^="/shorts/"]');
    for (i = 0; i < vignettes.length; i++) {
      var conteneur = vignettes[i].closest(CONTENEURS_VIGNETTE);
      if (conteneur) { conteneur.setAttribute('hidden', ''); }
    }

    var etageres = document.querySelectorAll(ETAGERES_SHORTS);
    for (i = 0; i < etageres.length; i++) { etageres[i].setAttribute('hidden', ''); }

    sections = document.querySelectorAll(SECTIONS_CONDITIONNELLES);
    for (i = 0; i < sections.length; i++) {
      if (sections[i].querySelector(MARQUEUR_SHORTS)) { sections[i].setAttribute('hidden', ''); }
    }

    onglets = document.querySelectorAll('ytm-pivot-bar-item-renderer, ytd-guide-entry-renderer, ytd-mini-guide-entry-renderer');
    for (i = 0; i < onglets.length; i++) {
      if (onglets[i].querySelector('a[href="/shorts/"], div.pivot-shorts')) { onglets[i].setAttribute('hidden', ''); }
    }
  }

  var planifie = false;
  function programme() {
    if (planifie) { return; }
    planifie = true;
    requestAnimationFrame(function () { planifie = false; masquer(); });
  }

  masquer();
  var observateur = new MutationObserver(programme);
  observateur.observe(document.documentElement, { childList: true, subtree: true });
})();
"""

/**
 * Motifs qui désignent une page de vidéo. Comparaison en minuscules, insensible
 * à la casse et au sous-domaine (www., m., music.).
 *
 * - "youtube.com/watch"  : vidéo classique (?v=XXXX)
 * - "m.youtube.com/watch": version mobile explicite du même chemin
 * - "youtu.be/"          : liens courts partagés
 * - "/shorts/"           : Shorts
 *
 * Volontairement en clair et sans expression régulière : la liste doit rester
 * relisible et ne jamais dépendre d'un moteur de regex.
 */
private val MOTIFS_VIDEO: List<String> = listOf(
    "youtube.com/watch",
    "m.youtube.com/watch",
    "youtu.be/",
    "/shorts/"
)

/** Vrai si l'adresse désigne une page de vidéo YouTube. */
private fun estUrlVideo(url: String): Boolean {
    val minuscule = url.lowercase(Locale.ROOT)
    return MOTIFS_VIDEO.any { motif -> minuscule.contains(motif) }
}

/**
 * Mémoire de navigation partagée entre les rappels du WebView.
 * Elle n'est pas un état Compose : la modifier ne doit pas recomposer l'écran.
 */
private class EtatNavigation {
    /** URL d'accueil courante, tenue à jour par l'écran. */
    var urlAccueil: String = URL_ACCUEIL_PAR_DEFAUT

    /** Dernière URL de vidéo interceptée, pour ne pas traiter deux fois la même. */
    var derniereVideo: String? = null

    /** Horodatage monotone de la dernière interception. */
    var derniereVideoMillis: Long = 0L

    /** Dernière URL d'accueil effectivement chargée dans le WebView. */
    var urlAppliquee: String? = null
}

/**
 * Vrai si l'URL doit être traitée maintenant.
 *
 * YouTube réécrit son historique plusieurs fois pour une seule vidéo : sans ce
 * filtre, un simple appui ouvrirait deux défis. La fenêtre de 1,5 s couvre la
 * rafale de rappels sans gêner une réouverture volontaire plus tard.
 */
private fun estNouvelleVideo(navigation: EtatNavigation, url: String): Boolean {
    val maintenant = SystemClock.elapsedRealtime()
    val dejaVue = url == navigation.derniereVideo &&
        maintenant - navigation.derniereVideoMillis < 1_500L
    if (dejaVue) return false
    navigation.derniereVideo = url
    navigation.derniereVideoMillis = maintenant
    return true
}

/**
 * Traite une navigation du WebView.
 *
 * @return true si l'URL a été prise en charge (page vidéo bloquée), false pour
 *         laisser la navigation se faire normalement (accueil, recherche, chaîne,
 *         connexion…).
 */
private fun traiterNavigation(
    url: String,
    navigation: EtatNavigation,
    viewModel: YoutubeViewModel,
    vue: WebView?,
    ramenerAccueil: Boolean
): Boolean {
    if (!estUrlVideo(url)) return false
    val premiere = estNouvelleVideo(navigation, url)
    if (premiere) viewModel.demanderVideo(url)
    // YouTube est une application d'une seule page : en revenant sur l'URL
    // d'accueil, on quitte la vidéo que le site avait déjà commencé à charger.
    if (ramenerAccueil) vue?.loadUrl(navigation.urlAccueil)
    return true
}

/**
 * Écran unique du kiosque : le WebView plein écran, la poignée de réglages,
 * l'écran de défi et les messages.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val contexte = LocalContext.current
    val viewModel: YoutubeViewModel = viewModel(factory = YoutubeViewModel.fabrique(contexte))
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    val etatDrawer = rememberDrawerState(initialValue = DrawerValue.Closed)
    val navigation = remember { EtatNavigation() }

    // Lu par les rappels du WebView pour ramener la page sur l'accueil.
    SideEffect {
        navigation.urlAccueil = etat.urlAccueil.ifBlank { URL_ACCUEIL_PAR_DEFAUT }
    }

    val webView = remember {
        WebView(contexte).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            // Rendu à la largeur réelle de l'écran : sans ces deux réglages, le
            // WebView compose la page à la largeur d'un bureau (~980 px) puis la
            // découpe, ce qui produit des bandes grises et du texte tronqué
            // (la page de connexion ou le bandeau d'historique devenait illisible).
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = USER_AGENT_MOBILE
            // Aucune JavascriptInterface : le contenu distant ne doit jamais
            // pouvoir appeler du code natif.

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {

                /**
                 * Point d'injection du masqueur de Shorts : la page est chargée,
                 * on glisse le script à sens unique. Pas de JavascriptInterface,
                 * le contenu distant n'a aucun retour vers le code natif.
                 *
                 * Rappelé à chaque fin de chargement ; le garde-fou interne au
                 * script (`window.__masqueurShorts`) empêche la double injection
                 * dans un même contexte, et le script se réinstalle de lui-même
                 * après un rechargement complet.
                 */
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(JS_MASQUER_SHORTS, null)
                }

                /** Liens cliqués : on bloque la page vidéo avant son chargement. */
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return traiterNavigation(
                        url = url,
                        navigation = navigation,
                        viewModel = viewModel,
                        vue = view,
                        ramenerAccueil = false
                    )
                }

                /** Variante obsolète, conservée pour les WebView qui l'appellent encore. */
                @Deprecated("Surcharge obsolète d'Android")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val adresse = url ?: return false
                    return traiterNavigation(
                        url = adresse,
                        navigation = navigation,
                        viewModel = viewModel,
                        vue = view,
                        ramenerAccueil = false
                    )
                }

                /**
                 * YouTube est une application d'une seule page : beaucoup de
                 * navigations internes ne passent pas par shouldOverrideUrlLoading.
                 * On surveille donc aussi l'historique : si l'adresse courante
                 * devient une vidéo, on la mémorise, on revient à l'accueil et on
                 * déclenche le défi.
                 */
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    if (url == null || isReload) return
                    traiterNavigation(
                        url = url,
                        navigation = navigation,
                        viewModel = viewModel,
                        vue = view,
                        ramenerAccueil = true
                    )
                }
            }
        }
    }

    // Chargement (et rechargement si l'URL d'accueil change) hors de la composition.
    LaunchedEffect(webView, etat.urlAccueil) {
        val cible = etat.urlAccueil.ifBlank { URL_ACCUEIL_PAR_DEFAUT }
        if (navigation.urlAppliquee != cible) {
            navigation.urlAppliquee = cible
            webView.loadUrl(cible)
        }
    }

    // Libération du WebView quand l'écran quitte la composition.
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // Ouverture de NewPipe : l'écran exécute l'intention, le ViewModel compte
    // seulement si l'ouverture a réussi.
    LaunchedEffect(etat.demandeOuverture?.id) {
        val demande = etat.demandeOuverture ?: return@LaunchedEffect
        val intention = Intent(Intent.ACTION_VIEW, Uri.parse(demande.url)).apply {
            setPackage("org.schabi.newpipe")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ouverte = try {
            contexte.startActivity(intention)
            true
        } catch (erreur: ActivityNotFoundException) {
            false
        }
        viewModel.terminerOuverture(demande.id, ouverte)
    }

    ModalNavigationDrawer(
        drawerState = etatDrawer,
        // Fermé au doigt sauf par la poignée : le site garde tous ses gestes.
        gesturesEnabled = false,
        drawerContent = {
            SettingsDrawer(
                etat = etat,
                onLimiteChangee = { valeur -> viewModel.changerLimite(valeur) },
                onReinitialiser = { viewModel.reinitialiserCompteur() },
                onUrlAccueilValidee = { url ->
                    viewModel.changerUrlAccueil(url)
                    portee.launch { etatDrawer.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )

            PoigneeReglages(
                modifier = Modifier.align(Alignment.TopStart),
                onOuvrir = { portee.launch { etatDrawer.open() } }
            )

            BandeauAvis(
                avis = etat.avis,
                onFermer = { id -> viewModel.fermerAvis(id) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Défi modal : par-dessus tout, y compris le panneau s'il était ouvert.
    etat.defi?.let { defi ->
        EcranDefi(
            defi = defi,
            onValider = { saisie -> viewModel.soumettreReponse(saisie) },
            onFermer = { viewModel.annulerDefi() }
        )
    }
}

/**
 * Poignée invisible de 40 dp dans le coin supérieur gauche : un glissement vers
 * la droite ouvre les réglages. Elle ne capte que son carré, le reste de l'écran
 * continue de faire défiler le site.
 */
@Composable
private fun PoigneeReglages(
    modifier: Modifier = Modifier,
    onOuvrir: () -> Unit
) {
    val densite = LocalDensity.current
    val seuil = with(densite) { 36.dp.toPx() }

    Box(
        modifier = modifier
            .size(40.dp)
            .pointerInput(Unit) {
                var cumul = 0f
                detectHorizontalDragGestures(
                    onDragStart = { cumul = 0f },
                    onDragEnd = { cumul = 0f },
                    onDragCancel = { cumul = 0f },
                    onHorizontalDrag = { changement, glissement ->
                        changement.consume()
                        cumul += glissement
                        if (cumul >= seuil) {
                            cumul = 0f
                            onOuvrir()
                        }
                    }
                )
            }
    )
}

/** Petit bandeau sombre en bas de l'écran, effacé après quelques secondes. */
@Composable
private fun BandeauAvis(
    avis: Avis?,
    onFermer: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (avis == null) return

    LaunchedEffect(avis.id) {
        delay(3_500L)
        onFermer(avis.id)
    }

    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = avis.texte,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
