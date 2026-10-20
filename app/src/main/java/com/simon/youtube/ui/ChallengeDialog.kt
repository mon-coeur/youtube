package com.simon.youtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Fond noir pur de l'écran de défi. */
private val FondDefi = Color(0xFF000000)

/** Gris des libellés secondaires. */
private val GrisDefi = Color(0xFF8E8E93)

/** Fond du champ de saisie. */
private val FondChamp = Color(0xFF1C1C1E)

/** Contour discret du champ. */
private val ContourChamp = Color(0xFF3A3A3C)

/** Rouge du message d'erreur. */
private val RougeDefi = Color(0xFFFF453A)

/**
 * Écran modal du défi de multiplication.
 *
 * Un seul bouton « Valider », aucun moyen de changer de question : une mauvaise
 * réponse vide le champ et conserve la même opération, pour que le calcul soit
 * réellement fait avant d'ouvrir la vidéo.
 *
 * @param defi l'opération en cours, avec son compteur de mauvaises réponses
 * @param onValider appelée avec le texte saisi brut
 * @param onFermer appelée sur la touche retour (abandon sans ouverture ni comptage)
 */
@Composable
fun EcranDefi(
    defi: DefiEnCours,
    onValider: (String) -> Unit,
    onFermer: () -> Unit
) {
    Dialog(
        onDismissRequest = onFermer,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // La saisie est locale à la question : elle survit aux mauvaises réponses
        // puisqu'elle est mémorisée sur le couple de facteurs, pas sur l'objet entier.
        var reponse by remember(defi.a, defi.b) { mutableStateOf("") }
        val requeteFocus = remember { FocusRequester() }

        // Champ vidé à chaque mauvaise réponse (le compteur d'erreurs change à chaque fois).
        LaunchedEffect(defi.erreurs) {
            if (defi.erreurs > 0) reponse = ""
        }

        // Autofocus demandé à l'ouverture : le clavier numérique apparaît directement.
        LaunchedEffect(Unit) {
            requeteFocus.requestFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FondDefi)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bloc supérieur : le libellé et l'opération occupent le haut de l'écran.
            Spacer(Modifier.height(88.dp))
            Text(
                text = "Résous",
                color = GrisDefi,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${defi.a} × ${defi.b}",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(52.dp))

            OutlinedTextField(
                value = reponse,
                onValueChange = { saisie -> reponse = saisie.filter { it.isDigit() }.take(5) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .focusRequester(requeteFocus),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                placeholder = {
                    Text(
                        text = "Réponse...",
                        color = GrisDefi,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FondChamp,
                    unfocusedContainerColor = FondChamp,
                    focusedBorderColor = ContourChamp,
                    unfocusedBorderColor = ContourChamp,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedPlaceholderColor = GrisDefi,
                    unfocusedPlaceholderColor = GrisDefi
                )
            )

            if (defi.erreurs > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ce n'est pas la bonne réponse.",
                    color = RougeDefi,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onValider(reponse) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Valider",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Le reste de l'écran reste vide : rien d'autre à toucher.
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
