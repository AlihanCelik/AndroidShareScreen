package com.alihan.androidsharescreen.service

import android.content.Context
import android.content.Intent
import android.os.Build
import javax.inject.Inject

class WebrtcServiceRepository @Inject constructor(
    private val context: Context
) {

    // Servisi başlatır ve kullanıcı adını iletir
    fun startIntent(username: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java) // Servis için intent oluşturulur
            startIntent.action = "StartIntent"                          // Eylem: servisi başlat
            startIntent.putExtra("username", username)                 // Kullanıcı adı eklendi

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start() // Arka planda çalıştır
    }

    // Ekran paylaşımı bağlantısı isteği gönderir (karşı tarafa bağlantı kurulacak)
    fun requestConnection(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "RequestConnectionIntent"     // Eylem: ekran paylaşımı başlat
            startIntent.putExtra("target", target)             // Hedef kullanıcı bilgisi

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    // Gelen çağrıyı kabul etmek için servis üzerinden işlem yapılır
    fun acceptCAll(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "AcceptCallIntent"            // Eylem: çağrıyı kabul et
            startIntent.putExtra("target", target)             // Karşı taraf bilgisi

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    // Çağrıyı sonlandırmak için servis yönlendirilir
    fun endCallIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "EndCallIntent"               // Eylem: çağrıyı sonlandır

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    // Servisi tamamen durdurmak için kullanılır
    fun stopIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "StopIntent"                  // Eylem: servisi durdur

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }
}
