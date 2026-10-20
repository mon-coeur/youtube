package com.simon.youtube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Thème unique du kiosque, volontairement sombre quel que soit le réglage du
 * système : l'application sert à regarder des vidéos, souvent le soir, et le
 * noir pur évite les bandes claires autour de l'image.
 *
 * Les couleurs dynamiques d'Android 12 ne sont pas reprises : la marque du
 * kiosque (rouge YouTube sur noir) doit rester identique d'un téléphone à l'autre.
 */
private val SchemaSombre = darkColorScheme(
    primary = RougeYoutube,
    onPrimary = BlancPur,
    primaryContainer = RougeYoutubeFonce,
    onPrimaryContainer = BlancPur,
    secondary = GrisTexte,
    onSecondary = NoirProfond,
    tertiary = VertDiscret,
    onTertiary = NoirProfond,
    background = NoirProfond,
    onBackground = BlancPur,
    surface = NoirProfond,
    onSurface = BlancPur,
    surfaceVariant = NoirSurface,
    onSurfaceVariant = GrisTexte,
    surfaceTint = RougeYoutube,
    inverseSurface = BlancPur,
    inverseOnSurface = NoirProfond,
    error = RougeErreur,
    onError = BlancPur,
    errorContainer = RougeYoutubeFonce,
    onErrorContainer = BlancPur,
    outline = GrisContour,
    outlineVariant = NoirEleve,
    scrim = NoirProfond
)

/** Enveloppe Material 3 de l'application. */
@Composable
fun YoutubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SchemaSombre,
        typography = TypographieYoutube,
        content = content
    )
}
