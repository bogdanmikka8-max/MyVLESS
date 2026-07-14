package com.bogdanmikka.myvless.service

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class VLESSVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        Log.d("VLESSVpnService", "Сервис запущен")
        // Здесь будет запуск Xray/V2Ray core
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        Log.d("VLESSVpnService", "Сервис остановлен")
    }

    // TODO: Реализовать Builder + подключение VLESS
}
