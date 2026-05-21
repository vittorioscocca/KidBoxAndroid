#!/usr/bin/env bash
# Installa la build DEBUG su un telefono Android collegato (USB o wireless adb).
# Uso: ./scripts/install-debug-device.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
  :
else
  echo "Errore: serve JDK 17+ (Android Studio JBR o JAVA_HOME)." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Errore: adb non trovato. Installa Android platform-tools (Android Studio SDK)." >&2
  exit 1
fi

echo "==> Dispositivi collegati:"
adb devices -l
count="$(adb devices | awk 'NR>1 && $2=="device" { c++ } END { print c+0 }')"
if [[ "$count" -eq 0 ]]; then
  echo ""
  echo "Nessun telefono in stato 'device'."
  echo "Sul Samsung: Opzioni sviluppatore → Debug USB attivo, cavo USB, accetta 'Consenti debug USB'."
  echo "In alternativa: Debug wireless (Android 11+) → adb pair / adb connect."
  exit 1
fi

echo ""
echo "==> Compilo e installo debug (versionCode dal build.gradle)..."
./gradlew :app:installDebug

echo ""
echo "==> Fatto. Apri KidBox sul telefono."
echo "Prima installazione debug: in logcat cerca il token App Check e registralo in Firebase Console"
echo "(App Check → Android → Token di debug). Vedi AppCheckInstaller.kt."
