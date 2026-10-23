package ru.tiggo.cluster;

import android.app.admin.DeviceAdminReceiver;

/**
 * Приёмник администратора: нужен, чтобы TiggoHelper можно было назначить владельцем
 * устройства (dpm set-device-owner) — тогда приложение само скрывает и возвращает штатные приложения.
 */
public class AdminReceiver extends DeviceAdminReceiver {}
