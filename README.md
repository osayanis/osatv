# OsaTV

Un écran d'accueil pour Android TV, inspiré de l'Apple TV. Il remplace l'accueil Google sans root et sans toucher au firmware de la télé.

Il a été développé et testé sur une TCL S6500 (châssis RT41, Android TV 9, 720p, 1,4 Go de RAM).

![Accueil OsaTV](.github/assets/accueil.png)

| Toutes les apps | Appui long sur OK |
|---|---|
| ![Grille](.github/assets/grille.png) | ![Menu](.github/assets/menu.png) |

## Ce qu'il fait

- **Bandeau du haut** : l'élément sélectionné s'affiche en grand, avec son image floutée en fond plein écran.
- **Favoris** : une rangée de bannières 16:9. La carte sélectionnée grossit et s'éclaire, et son nom apparaît dessous.
- **Entrées et réglages** : les ports HDMI de la télé et un raccourci vers les réglages Android.
- **Toutes les apps** : une grille de toutes les apps installées. Une app sans bannière TV est affichée avec son icône sur un fond tiré de sa couleur.
- **Heure, date et météo** en haut à droite. La ville est trouvée d'après l'adresse IP, la météo vient d'Open-Meteo (gratuit, sans clé).
- **Appui long sur OK** : un menu pour ouvrir l'app, l'ajouter aux favoris ou l'en retirer, la déplacer, voir ses infos ou la désinstaller.
- **Bouton Home ou Retour** : retour en haut, sur le premier favori.

La liste se met à jour toute seule quand une app est installée ou supprimée.

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
- Le flou du fond n'utilise pas `Modifier.blur`, qui n'existe qu'à partir d'Android 12. L'image est réduite à 16 × 9 pixels puis agrandie avec lissage, ce qui ne coûte presque rien sur une puce d'entrée de gamme.
- Les entrées HDMI viennent de `TvInputManager`. Sur les TCL, il faut ouvrir l'URI passthrough avec le type `vnd.android.cursor.item/channel` : l'app TV de TCL ne répond qu'à ce type.

| Fichier | Rôle |
|---|---|
| `data/Apps.kt` | Liste des apps, bannières, couleurs, favoris |
| `data/Sources.kt` | Entrées HDMI |
| `data/Weather.kt` | Position par IP + météo Open-Meteo |
| `ui/HomeScreen.kt` | Écran d'accueil |
| `ui/Tiles.kt` | Cartes (apps, entrées, réglages) |
| `ui/Chrome.kt` | Fond, bandeau, heure et météo |
| `ui/AppMenu.kt` | Menu de l'appui long |

## Limites

- La télé n'est pas rootée : l'animation de démarrage TCL, les réglages et l'interface des apps (Netflix…) restent ceux d'origine.
- Une mise à jour système TCL peut réactiver l'accueil Google. Il suffit alors de relancer `scripts/install.sh`.
- Sur cette télé, le premier affichage après un redémarrage prend environ 3 secondes, le temps de charger les images des apps.

## Crédits

Police [Inter](https://github.com/rsms/inter), sous licence SIL Open Font License 1.1 (`licenses/Inter-OFL.txt`).
