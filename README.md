# OsaTV

Un écran d'accueil pour Android TV, inspiré de l'Apple TV. Il remplace l'accueil Google sans root et sans toucher au firmware de la télé.

Il a été développé et testé sur une TCL S6500 (châssis RT41, Android TV 9, 720p, 1,4 Go de RAM).

![Accueil OsaTV](.github/assets/accueil.png)

| Dans la grille | Appui long sur OK |
|---|---|
| ![Grille](.github/assets/grille.png) | ![Menu](.github/assets/menu.png) |
| **Mode déplacement** | **Économiseur** |
| ![Déplacement](.github/assets/deplacer.png) | ![Économiseur](.github/assets/economiseur.png) |

## Ce qu'il fait

- **Une grande image en haut** : un paysage en plein écran, qui change toutes les 20 secondes avec un fondu.
- **Une seule grille d'icônes** au format 5:3, comme sur tvOS. La première rangée sert de dock et se pose sur l'image. Les entrées HDMI et les réglages sont des icônes comme les autres.
- **Au focus**, l'icône grossit avec un ressort, se soulève avec une ombre portée et un reflet. Son nom n'apparaît que sous elle.
- **En descendant dans la grille**, l'image disparaît vers le haut et le fond devient flou et sombre.
- **Appui long sur OK** : un menu pour déplacer l'icône, voir les infos de l'app ou la supprimer.
- **Mode déplacement** : l'icône tremble et se déplace avec les flèches. OK ou Retour pour la poser. L'ordre est mémorisé.
- **Économiseur** : après 3 minutes sans toucher la télécommande, la grille s'efface. Il reste le paysage avec un zoom très lent, l'heure, la date et la météo. La première touche réveille l'écran sans rien ouvrir.
- **Bouton Home ou Retour** : retour en haut, sur la première icône.

Au premier lancement, les apps de streaming connues (Netflix, YouTube, Prime Video…) sont placées dans le dock. Une app installée plus tard s'ajoute à la fin de la grille.

## Installer sur une télé

Sur la télé :
1. Ouvre **Paramètres → Préférences de l'appareil → À propos**, puis appuie 7 fois sur **Build**.
2. Dans **Options pour les développeurs**, active le **débogage USB**.
3. Note l'adresse IP de la télé (Paramètres → Réseau).

Sur le Mac, avec `adb` installé (`brew install android-platform-tools`) :

```sh
./scripts/install.sh 192.168.1.XX OsaTV-0.1.0.apk
```

Le script installe l'APK, fait d'OsaTV l'accueil par défaut et désactive l'accueil Google. Sans ça, celui-ci reprend la main sur le bouton Home.

Pour revenir à l'accueil d'origine :

```sh
./scripts/restore.sh 192.168.1.XX
```

Une réinitialisation d'usine de la télé remet aussi tout comme avant.

## Compiler

Il faut un JDK 17 et le SDK Android (plateforme 35) :

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
```

L'APK sort dans `app/build/outputs/apk/release/`. La version release est signée avec la clé décrite dans `keystore.properties`, qui n'est pas dans le dépôt. Sans ce fichier, elle est signée avec la clé de debug.

⚠️ Une mise à jour ne s'installe par-dessus l'ancienne version que si elle est signée avec la même clé.

## Technique

- Kotlin + Jetpack Compose, avec [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv) (`androidx.tv:tv-material`).
- minSdk 28 (Android TV 9).
- L'activité déclare la catégorie `HOME` pour pouvoir servir d'écran d'accueil.
- Le flou du fond n'utilise pas `Modifier.blur`, qui n'existe qu'à partir d'Android 12. L'image est réduite à 32 × 18 pixels puis agrandie avec lissage, ce qui ne coûte presque rien sur une puce d'entrée de gamme.
- Le défilement est piloté à la main (un `translationY` animé, sans conteneur défilant), pour placer chaque rangée au même endroit comme sur tvOS.
- La puce graphique de la télé est modeste. Le zoom lent ne tourne donc que dans l'économiseur, et les couches invisibles (alpha 0) ne sont pas dessinées.
- Les entrées HDMI viennent de `TvInputManager`. Sur les TCL, il faut ouvrir l'URI passthrough avec le type `vnd.android.cursor.item/channel` : l'app TV de TCL ne répond qu'à ce type.

| Fichier | Rôle |
|---|---|
| `data/Apps.kt` | Liste des apps, bannières, couleurs, favoris |
| `data/Sources.kt` | Entrées HDMI |
| `data/Weather.kt` | Position par IP + météo Open-Meteo |
| `HomeState.kt` | Icônes de l'accueil, ordre, déplacement |
| `ui/HomeScreen.kt` | Grille, défilement, économiseur |
| `ui/Tiles.kt` | Icônes (focus, reflet, tremblement) |
| `ui/Shelf.kt` | Image du haut, fond flou, heure |
| `ui/AppMenu.kt` | Menu de l'appui long |

## Limites

- La télé n'est pas rootée : l'animation de démarrage TCL, les réglages et l'interface des apps (Netflix…) restent ceux d'origine.
- Une mise à jour système TCL peut réactiver l'accueil Google. Il suffit alors de relancer `scripts/install.sh`.
- Sur cette télé, le premier affichage après un redémarrage prend environ 3 secondes, le temps de charger les images des apps.

## Crédits

- Police [Inter](https://github.com/rsms/inter), sous licence SIL Open Font License 1.1 (`licenses/Inter-OFL.txt`).
- Paysages : photos [Unsplash](https://unsplash.com/license) récupérées via [picsum.photos](https://picsum.photos) (ids 29, 184, 218, 235, 249, 296, 323, 327, 353, 362).

OsaTV n'est ni affilié à Apple, ni approuvé par Apple. « Apple TV » n'est cité ici que comme source d'inspiration.
