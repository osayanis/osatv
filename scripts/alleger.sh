#!/bin/sh
# Désactive les apps système inutiles de la télé (télémétrie, boutiques, services Google abandonnés…)
# pour libérer de la mémoire et du processeur. Rien n'est supprimé : tout se réactive avec « annuler ».
# Usage : ./scripts/alleger.sh <ip-de-la-télé> [annuler]
set -e

IP="$1"
if [ -z "$IP" ]; then
    echo "Usage : $0 <ip-de-la-télé> [annuler]" >&2
    exit 1
fi
TV="$IP:5555"

PACKAGES="
com.tcl.bi
com.tcl.tvweishi
com.tcl.esticker
com.tcl.appmarket2
com.tcl.copydatatotv
com.google.android.videos
com.google.android.music
com.google.android.play.games
com.google.android.tvrecommendations
com.google.android.leanbacklauncher
com.google.android.leanbacklauncher.partnercustomizer
com.google.android.leanbacklauncher.partnerwidget
com.google.android.leanbacklauncher.recommendations
com.google.android.backdrop
com.google.android.feedback
com.google.android.tv.bugreportsender
com.android.gallery3d
com.android.printspooler
com.android.wallpaper.livepicker
com.google.android.apps.mediashell
"
# com.tcl.bi                      télémétrie TCL (envoie l'usage de la télé)
# com.tcl.tvweishi                « gardien » TCL (nettoyeur qui tourne en fond)
# com.tcl.esticker                autocollants publicitaires du mode magasin
# com.tcl.appmarket2              boutique d'apps TCL (le Play Store reste)
# com.tcl.copydatatotv            transfert depuis un téléphone
# videos, music, play.games       services Google fermés (Play Films, Play Musique, Play Jeux)
# tvrecommendations, leanback…    recommandations et ancien accueil Google, inutiles avec OsaTV
# backdrop                        économiseur Google (OsaTV a le sien)
# feedback, bugreportsender       rapports de bugs envoyés à Google
# gallery3d, printspooler, livepicker   galerie, impression, fonds animés : sans usage sur une télé
# apps.mediashell                 Chromecast intégré. Sur la TCL S6500, le système le réactive à chaque
#                                 redémarrage complet : relancer ce script après un redémarrage.

adb connect "$TV" >/dev/null
adb -s "$TV" wait-for-device

for package in $PACKAGES; do
    if [ "$2" = "annuler" ]; then
        adb -s "$TV" shell pm enable "$package" >/dev/null 2>&1 && echo "réactivé   $package" || echo "absent     $package"
    else
        # Une app désactivée peut rester en mémoire jusqu'au prochain redémarrage : on l'arrête aussi
        adb -s "$TV" shell pm disable-user --user 0 "$package" >/dev/null 2>&1 && echo "désactivé  $package" || echo "absent     $package"
        adb -s "$TV" shell am force-stop "$package" >/dev/null 2>&1 || true
    fi
done
