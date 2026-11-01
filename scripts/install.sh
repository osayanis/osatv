#!/bin/sh
# Installe OsaTV sur une télé Android TV et en fait l'écran d'accueil.
# Usage : ./scripts/install.sh <ip-de-la-télé> [chemin/vers/OsaTV.apk]
set -e

IP="$1"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
if [ -z "$IP" ]; then
    echo "Usage : $0 <ip-de-la-télé> [OsaTV.apk]" >&2
    exit 1
fi
TV="$IP:5555"

adb connect "$TV"
echo "Si la télé affiche « Autoriser le débogage ? », coche « Toujours autoriser » puis OK."
adb -s "$TV" wait-for-device

adb -s "$TV" install -r "$APK"
adb -s "$TV" shell cmd package set-home-activity fr.osalabs.osatv/.MainActivity
# Le launcher Google reprend la main sur le bouton Home tant qu'il est actif
adb -s "$TV" shell pm disable-user --user 0 com.google.android.tvlauncher || true
adb -s "$TV" shell input keyevent HOME

echo "OsaTV est l'écran d'accueil. Pour revenir en arrière : ./scripts/restore.sh $IP"
