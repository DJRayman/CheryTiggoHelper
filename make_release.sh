#!/bin/bash
# Собирает файлы для релиза на GitHub в release/:
#   TiggoHelper-<версия>.apk, install.sh, install.bat и всё вместе в TiggoHelper-<версия>.zip
# Версия берётся из AndroidManifest.xml (versionName).
set -euo pipefail
cd "$(dirname "$0")"

./build.sh
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
rm -rf release && mkdir -p release
cp build/TiggoHelper.apk "release/TiggoHelper-$VERSION.apk"
cp install.sh install.bat release/
chmod +x release/install.sh
# Всё в одном архиве: удобно скачивать, и ZIP GitHub принимает в любом поле загрузки.
(cd release && python3 -I -c '
import sys, zipfile
name = sys.argv[1]
with zipfile.ZipFile(name + ".zip", "w", zipfile.ZIP_DEFLATED) as z:
    for f in sys.argv[2:]:
        z.write(f, name + "/" + f)
' "TiggoHelper-$VERSION" "TiggoHelper-$VERSION.apk" install.bat install.sh)
echo "Готово: $(pwd)/release"
ls -la release
