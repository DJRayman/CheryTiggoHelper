@echo off
chcp 65001 >nul
rem Установка TiggoHelper на магнитолу и первичная настройка (Windows).
rem   install.bat [путь\к\TiggoHelper.apk]
rem Без аргумента берёт TiggoHelper*.apk из папки скрипта.
rem adb: переменная ADB, затем adb.exe рядом со скриптом, затем adb из PATH.
setlocal

set "PKG=ru.tiggo.cluster"
set "SVC=ru.tiggo.cluster/ru.tiggo.cluster.BootAccessibilityService"

if not defined ADB (
    if exist "%~dp0adb.exe" (set "ADB=%~dp0adb.exe") else (set "ADB=adb")
)

set "APK=%~1"
if not defined APK (
    for %%F in ("%~dp0TiggoHelper*.apk") do set "APK=%%~fF"
)
if not defined APK (
    echo Не найден TiggoHelper*.apk. Положите APK рядом со скриптом или укажите путь: install.bat TiggoHelper.apk
    exit /b 1
)
if not exist "%APK%" (
    echo Нет файла %APK%
    exit /b 1
)

echo Устанавливаю %APK%
"%ADB%" install -r "%APK%" || goto :error
rem Полноэкранный режим пишет системную настройку policy_control.
"%ADB%" shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS || goto :error
rem Вкладка «Процессы» берёт список фоновых приложений из статистики использования.
"%ADB%" shell appops set %PKG% GET_USAGE_STATS allow || goto :error
rem Без исключения из энергосбережения сервис могут выгрузить.
"%ADB%" shell dumpsys deviceidle whitelist +%PKG%
rem Автозапуск через accessibility-сервис: дописываем к уже включённым, не затирая их.
rem Склеиваем список shell'ом самой магнитолы. Внутри нет кавычек и «||»: cmd переключает
rem режим кавычек на каждой «"», и оператор оказался бы снаружи. В значениях нет пробелов.
rem В .bat двойной процент превращается в одинарный: магнитола получает ${C%:} —
rem срезать «:» в конце списка.
"%ADB%" shell "C=$(settings get secure enabled_accessibility_services); C=${C%%:}; case :$C: in *:%SVC%:*) ;; ::|:null:) settings put secure enabled_accessibility_services %SVC% ;; *) settings put secure enabled_accessibility_services $C:%SVC% ;; esac; settings put secure accessibility_enabled 1" || goto :error
rem Первый запуск выводит приложение из stopped state, иначе автозапуск не сработает.
"%ADB%" shell am start -n %PKG%/.MainActivity || goto :error
echo Установлено.
pause
exit /b 0

:error
echo Ошибка. Проверьте, что магнитола подключена: "%ADB%" devices
pause
exit /b 1
