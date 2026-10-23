#!/bin/bash
# Собирает файлы для релиза на GitHub в release/:
#   TiggoHelper-<версия>.apk, install.sh, install.bat
# Версия берётся из AndroidManifest.xml (versionName).
set -euo pipefail
cd "$(dirname "$0")"

./build.sh
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
rm -rf release && mkdir -p release
cp build/TiggoHelper.apk "release/TiggoHelper-$VERSION.apk"
cp install.sh install.bat release/
chmod +x release/install.sh
echo "Готово: $(pwd)/release"
ls -la release
