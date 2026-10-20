# YouTube

Mini-navigateur kiosque pour Android : une WebView qui ouvre YouTube, un défi de
multiplication avant chaque vidéo, et un plafond de vidéos par jour.
Kotlin + Jetpack Compose (Material 3), paquet `com.simon.youtube`.

## Ce que fait l'application

L'application affiche la page d'accueil de YouTube dans une WebView plein écran, avec les
cookies et le DOM storage activés et un user-agent de Chrome mobile classique. Le feed
« Accueil » est celui d'un compte connecté : sans connexion, YouTube ne propose que des
vidéos suggérées.

Toute la navigation reste normale (recherche, chaînes, page de connexion). Seul le clic sur
une vidéo est intercepté, c'est-à-dire les adresses de la forme :

- `youtube.com/watch?v=…`
- `m.youtube.com/watch?…`
- `youtu.be/…`
- `…/shorts/…`

À ce moment-là, la vidéo ne se lit pas dans le navigateur. L'application affiche un défi de
multiplication ; une bonne réponse ouvre la vidéo dans NewPipe et incrémente le compteur du
jour, une mauvaise réponse repose la même question.

La limite est un **nombre de vidéos par jour**, pas une durée : de 1 à 500, réglée à 10 par
défaut, remise à zéro automatiquement à minuit. Une fois la limite atteinte, l'application
affiche « Limite atteinte pour aujourd'hui » et n'ouvre plus rien.

## Prérequis

- Un téléphone sous Android 7.0 ou plus récent (minSdk 24) ; l'application cible l'API 34.
- Un compte GitHub (gratuit) pour lancer la compilation.
- **NewPipe** installé, puisque c'est lui qui lit les vidéos :
  <https://github.com/TeamNewPipe/NewPipe/releases>
- Un compte Google déjà connecté à YouTube, pour retrouver le feed « Accueil ».

## Récupérer l'APK depuis GitHub Actions

L'APK n'est pas distribué : il se compile dans le cloud, sans Android Studio ni SDK local.

1. Forker le dépôt (ou pousser une copie sur votre compte GitHub).
2. Ouvrir l'onglet **Actions** du dépôt.
3. Choisir le workflow **Build APK** dans la colonne de gauche.
4. Cliquer sur **Run workflow**, sélectionner la branche `main`, valider. Un simple push sur
   `main` déclenche aussi la compilation.
5. Attendre la fin du job, puis ouvrir le run terminé et descendre jusqu'à la section
   **Artifacts**.
6. Télécharger l'artefact **youtube-apk** : c'est une archive ZIP conservée 30 jours.
7. Décompresser l'archive : elle contient `app-release.apk`.

## Installer l'APK

1. Copier le fichier `app-release.apk` sur le téléphone.
2. L'ouvrir depuis le gestionnaire de fichiers.
3. Android demande d'autoriser les installations depuis cette source :
   **Paramètres → Applications → Accès spécial → Installer des applications inconnues**, puis
   autoriser l'application utilisée pour ouvrir le fichier (Fichiers, Chrome…).
4. Revenir à l'APK, toucher **Installer**, puis **Ouvrir**.
   Play Protect peut afficher un avertissement : **Installer quand même** (l'APK est signé
   avec la clé de debug, voir « Limites connues »).

## Se connecter à YouTube

Le feed « Accueil » n'existe que pour un compte connecté : sans connexion, la page d'accueil
se réduit à des vidéos suggérées et l'interception reste possible, mais l'expérience est
dégradée.

**Limitation à connaître.** Google refuse souvent la connexion depuis un navigateur intégré
et affiche un message du type « Ce navigateur ou cette application ne sont peut-être pas
sécurisés ». C'est une restriction côté Google, pas un défaut de l'application : la WebView
n'est pas reconnue comme un navigateur de confiance.

Contournements possibles, sans garantie :

- se connecter une première fois à YouTube depuis Chrome sur le même téléphone, puis
  rouvrir l'application ;
- réessayer la connexion, parfois plusieurs fois ;
- utiliser la version web de YouTube en étant déjà connecté dans le navigateur.

Si Google bloque malgré tout, l'application reste utilisable sans compte : YouTube propose
alors des vidéos suggérées, et le défi comme la limite quotidienne continuent de fonctionner.

## Le défi de multiplication

Avant chaque vidéo, l'application affiche deux nombres tirés entre 10 et 99 et demande leur
produit, avec un seul bouton **Valider**. Par exemple, pour `37 × 84`, la réponse attendue
est `3108`.

- Bonne réponse : la vidéo s'ouvre dans NewPipe et le compteur du jour augmente de 1.
- Mauvaise réponse : la même question est reposée ; il n'y a ni nouvelle paire de nombres ni
  pénalité, seulement une nouvelle tentative.

## Les réglages (balayage depuis le haut-gauche)

Le panneau n'a pas de bouton visible : glisser depuis le coin **supérieur gauche** vers la
droite, sur une poignée invisible d'environ 40 dp, ouvre le tiroir de réglages. Il contient :

- la limite du jour, réglable avec **−** et **+**, de 1 à 500 ;
- « Vidéos aujourd'hui : X / limite » ;
- « Restant : Y » ;
- le bouton **Réinitialiser le compteur** ;
- le champ **URL d'accueil**, modifiable, qui définit la page chargée au démarrage.

## Limites connues

- **Signature debug** : l'APK est signé avec la clé de debug. Cela suffit pour un usage
  personnel, mais l'application n'est pas publiable sur le Play Store, et une installation
  qui entre en conflit de signature doit être désinstallée puis réinstallée.
- **Dépendance au site YouTube** : l'interception repose sur les adresses et le DOM actuels
  du site. Une refonte de YouTube peut la casser jusqu'à une mise à jour de l'application.
- **Aucune donnée ne quitte l'appareil**, hormis le trafic vers YouTube : pas de serveur, pas
  de compte applicatif, pas de télémétrie. Les réglages et le compteur restent sur le
  téléphone.
- **NewPipe est obligatoire** : s'il n'est pas installé, l'application affiche « NewPipe n'est
  pas installé » et ne décompte aucune vidéo.

## Compiler soi-même

Il faut le JDK 17 et un Android SDK (API 34), avec la variable `ANDROID_HOME` ou un fichier
`local.properties` pointant vers le SDK.

Le dépôt ne contient pas le binaire `gradle-wrapper.jar` : le workflow le génère avant de
compiler. En local, générer le wrapper une première fois, puis lancer les tests et la
compilation :

```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

L'APK est produit dans `app/build/outputs/apk/release/app-release.apk`.
