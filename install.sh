#!/bin/bash
# Установка TiggoHelper на магнитолу и первичная настройка.
#   ./install.sh [путь/к/TiggoHelper.apk]
# Без аргумента ищет TiggoHelper*.apk рядом со скриптом (так лежат файлы релиза), затем build/.
#   ADB      — команда adb (по умолчанию adb)
#   WIN_COPY — для Windows adb.exe из WSL: куда скопировать APK на диск Windows (путь Linux)
#   ADB_APK  — путь к APK так, как его понимает adb (для adb.exe — путь Windows к WIN_COPY)
# Свои значения удобно держать в local.env рядом со скриптом (в git не попадает).
set -e
APK=${1:-}
[ -n "$APK" ] && APK=$(realpath "$APK")
cd "$(dirname "$0")"
[ -f local.env ] && . ./local.env
A=${ADB:-adb}
PKG=ru.tiggo.cluster

if [ -z "$APK" ]; then
    APK=$(ls -t TiggoHelper*.apk 2>/dev/null | head -1)
    [ -n "$APK" ] || APK=build/TiggoHelper.apk
fi
[ -f "$APK" ] || { echo "Нет $APK — укажите путь к APK или соберите его: ./build.sh" >&2; exit 1; }
echo "Устанавливаю $APK"
# Windows adb.exe пути Linux не видит: кладём APK на диск Windows.
[ -n "${WIN_COPY:-}" ] && cp "$APK" "$WIN_COPY"
$A install -r "${ADB_APK:-$APK}"
# Полноэкранный режим пишет системную настройку policy_control.
$A shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
# Вкладка «Процессы» берёт список фоновых приложений из статистики использования.
$A shell appops set $PKG GET_USAGE_STATS allow
# Без исключения из энергосбережения сервис могут выгрузить.
$A shell dumpsys deviceidle whitelist +$PKG
# Автозапуск через accessibility-сервис: BOOT_COMPLETED прошивка не доставляет приложениям
# вне своего белого списка. Дописываем к уже включённым сервисам, не затирая их.
# Сервис может быть записан коротко (pkg/.Cls) или полностью (pkg/pkg.Cls) — оставляем одну запись.
SVC=$PKG/$PKG.BootAccessibilityService
CUR=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
[ "$CUR" = "null" ] && CUR=""
NEW=$( (echo "$CUR" | tr ':' '\n' | grep -vxF -e "$SVC" -e "$PKG/.BootAccessibilityService"; echo "$SVC") \
    | grep -v '^$' | paste -sd ':')
if [ "$NEW" != "$CUR" ]; then
    $A shell settings put secure enabled_accessibility_services "$NEW"
fi
$A shell settings put secure accessibility_enabled 1

# Первый запуск выводит приложение из stopped state, иначе BOOT_COMPLETED не придёт.
$A shell am start -n $PKG/.MainActivity
echo "Установлено. Журнал: $A logcat -s ClusterLauncher"
