#!/bin/bash
# Сборка APK без Gradle: aapt2 → javac → d8 → zipalign → apksigner.
# Результат: build/TiggoHelper.apk
#
# Нужны JDK 17+ и Android SDK с build-tools 34.0.0, platforms;android-28 и cmdline-tools
# (из них берётся d8: d8 из build-tools 34 падает на анонимных классах).
#   ANDROID_SDK  — путь к SDK            (по умолчанию ~/android/tools/sdk)
#   JAVA_HOME    — путь к JDK            (по умолчанию ~/android/tools/jdk-21*)
#   KEYSTORE     — ключ подписи          (по умолчанию ./debug.keystore, создаётся сам)
# Обновление поверх установленного APK возможно только с тем же ключом.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_SDK=${ANDROID_SDK:-$HOME/android/tools/sdk}
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(ls -d "$HOME"/android/tools/jdk-21*[0-9] 2>/dev/null | head -1)
fi
export JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH
BT=$ANDROID_SDK/build-tools/34.0.0
D8=$ANDROID_SDK/cmdline-tools/latest/bin/d8
ANDROID_JAR=$ANDROID_SDK/platforms/android-28/android.jar
KEYSTORE=${KEYSTORE:-$(pwd)/debug.keystore}
OUT=build
APK=$OUT/TiggoHelper.apk

for f in "$JAVA_HOME/bin/javac" "$BT/aapt2" "$D8" "$ANDROID_JAR"; do
    [ -e "$f" ] || { echo "Не найдено: $f (проверьте ANDROID_SDK и JAVA_HOME)" >&2; exit 1; }
done

rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes

"$BT/aapt2" compile --dir res -o $OUT/res/
"$BT/aapt2" link -o $OUT/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --java $OUT/gen $OUT/res/*.flat

"$JAVA_HOME/bin/javac" -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
    -classpath "$BT/core-lambda-stubs.jar" -g -Xlint:-options \
    -d $OUT/classes $(find src $OUT/gen -name '*.java')

"$JAVA_HOME/bin/jar" cf $OUT/classes.jar -C $OUT/classes .
"$D8" --min-api 26 --lib "$ANDROID_JAR" --output $OUT $OUT/classes.jar

# zip в системе может не быть — дописываем classes.dex в APK средствами Python.
cp $OUT/base.apk $OUT/unaligned.apk
python3 -I -c 'import sys,zipfile; zipfile.ZipFile(sys.argv[1],"a",zipfile.ZIP_DEFLATED).write(sys.argv[2],"classes.dex")' \
    $OUT/unaligned.apk $OUT/classes.dex
"$BT/zipalign" -f 4 $OUT/unaligned.apk $OUT/aligned.apk

if [ ! -f "$KEYSTORE" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TiggoHelper Debug" >/dev/null
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --out $APK $OUT/aligned.apk
echo "Готово: $(pwd)/$APK"
