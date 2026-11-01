#!/bin/sh
# Remet l'accueil d'origine de la télé (OsaTV reste installé, désinstallable depuis les réglages).
# Usage : ./scripts/restore.sh <ip-de-la-télé>
set -e

IP="$1"
if [ -z "$IP" ]; then
    echo "Usage : $0 <ip-de-la-télé>" >&2
    exit 1
fi
TV="$IP:5555"

adb connect "$TV"
adb -s "$TV" wait-for-device
adb -s "$TV" shell pm enable com.google.android.tvlauncher
adb -s "$TV" shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb -s "$TV" shell input keyevent HOME

echo "Accueil d'origine rétabli."
