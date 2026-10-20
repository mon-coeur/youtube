package com.simon.youtube.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.youtube.core.VideoLimitEngine

/** Fond du panneau latéral. */
private val FondPanneau = Color(0xFF1C1C1E)

/** Contour des boutons et des champs du panneau. */
private val ContourPanneau = Color(0xFF3A3A3C)

/** Gris des libellés secondaires. */
private val GrisPanneau = Color(0xFF8E8E93)

/** Rouge des états d'alerte. */
private val RougePanneau = Color(0xFFFF453A)

/** Vert des états confortables. */
private val VertPanneau = Color(0xFF30D158)

/** Vrai si l'adresse peut servir d'URL d'accueil. */
private fun estUrlAccueilValide(url: String): Boolean {
    val propre = url.trim()
    return propre.startsWith("http://") || propre.startsWith("https://")
}

/**
 * Panneau de réglages ouvert par balayage depuis le bord gauche.
 *
 * Toutes les écritures sont déléguées à [YoutubeViewModel], donc au
 * SettingsRepository : ce composable ne conserve aucun état métier.
 *
 * @param etat limite, compteur du jour, restant et URL d'accueil courants
 * @param onLimiteChangee nouvelle limite demandée (déjà bornée par le ViewModel)
 * @param onReinitialiser remise à zéro du compteur du jour
 * @param onUrlAccueilValidee nouvelle URL d'accueil, déjà validée
 */
@Composable
fun SettingsDrawer(
    etat: YoutubeEtat,
    onLimiteChangee: (Int) -> Unit,
    onReinitialiser: () -> Unit,
    onUrlAccueilValidee: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = FondPanneau
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text(
                text = "Réglages",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Limite de vidéos par jour",
                fontSize = 14.sp,
                color = GrisPanneau
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onLimiteChangee(etat.limite - 1) },
                    enabled = etat.limite > VideoLimitEngine.MIN_LIMIT
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Diminuer la limite",
                        tint = Color.White
                    )
                }
                Text(
                    text = "${etat.limite}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(
                    onClick = { onLimiteChangee(etat.limite + 1) },
                    enabled = etat.limite < VideoLimitEngine.MAX_LIMIT
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Augmenter la limite",
                        tint = Color.White
                    )
                }
            }

            HorizontalDivider(color = ContourPanneau)
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Vidéos aujourd'hui : ${etat.compteDuJour} / ${etat.limite}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Restant : ${etat.restant}",
                fontSize = 16.sp,
                color = if (etat.restant > 0) VertPanneau else RougePanneau
            )

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onReinitialiser,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ContourPanneau),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Réinitialiser le compteur",
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "URL d'accueil",
                fontSize = 14.sp,
                color = GrisPanneau
            )
            Spacer(Modifier.height(8.dp))

            // Le champ repart de la valeur enregistrée dès qu'elle change.
            var texte by remember(etat.urlAccueil) { mutableStateOf(etat.urlAccueil) }
            val valide = estUrlAccueilValide(texte)

            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text(
                        text = "https://www.youtube.com",
                        color = GrisPanneau,
                        fontSize = 14.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E),
                    focusedBorderColor = ContourPanneau,
                    unfocusedBorderColor = ContourPanneau,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedPlaceholderColor = GrisPanneau,
                    unfocusedPlaceholderColor = GrisPanneau
                )
            )

            if (!valide) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "L'adresse doit commencer par http:// ou https://.",
                    fontSize = 13.sp,
                    color = RougePanneau
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onUrlAccueilValidee(texte.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = valide,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Enregistrer l'adresse",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
