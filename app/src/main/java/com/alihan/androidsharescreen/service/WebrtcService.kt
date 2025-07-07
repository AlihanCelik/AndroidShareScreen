package com.alihan.androidsharescreen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alihan.androidsharescreen.R
import com.alihan.androidsharescreen.repository.MainRepository
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@AndroidEntryPoint
class WebrtcService @Inject constructor() : Service(), MainRepository.Listener {

    companion object {
        // Activity'den bu servis sınıfına gönderilen global referanslar
        var screenPermissionIntent: Intent? = null               // Ekran paylaşımı için alınan izin
        var surfaceView: SurfaceViewRenderer? = null             // Yerel SurfaceView (görüntü göstermek için)
        var listener: MainRepository.Listener? = null            // UI ile haberleşmek için dış listener
    }

    @Inject lateinit var mainRepository: MainRepository         // Repository (WebRTC + Socket yönetimi)

    private lateinit var notificationManager: NotificationManager // Foreground servis bildirimi için
    private lateinit var username: String                         // Bu cihazdaki kullanıcının adı

    // Servis başlatıldığında çalışır
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        mainRepository.listener = this                           // Repository olaylarını dinlemeye başla
    }

    // Servis başlatıldığında veya bir `Intent` gönderildiğinde çalışır
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {

                // Bağlantıyı başlat
                "StartIntent" -> {
                    this.username = intent.getStringExtra("username").toString()
                    mainRepository.init(username, surfaceView!!)     // WebRTC + Socket bağlantılarını başlat
                    startServiceWithNotification()                   // Foreground notification başlat
                }

                // Servisi tamamen durdur
                "StopIntent" -> {
                    stopMyService()
                }

                // Çağrı sonlandırıldığında yapılacak işlemler
                "EndCallIntent" -> {
                    mainRepository.sendCallEndedToOtherPeer()        // Karşı tarafa çağrıyı bitir mesajı gönder
                    mainRepository.onDestroy()                       // Bağlantıları temizle
                    stopMyService()
                }

                // Gelen çağrı kabul edildiğinde çalışır
                "AcceptCallIntent" -> {
                    val target = intent.getStringExtra("target")
                    target?.let {
                        mainRepository.startCall(it)                // WebRTC üzerinden çağrıyı başlat
                    }
                }

                // Ekran paylaşımı başlatma işlemi (kullanıcı başlatır)
                "RequestConnectionIntent" -> {
                    val target = intent.getStringExtra("target")
                    target?.let {
                        // Önce ekran paylaşımı izni aktarılır
                        mainRepository.setPermissionIntentToWebrtcClient(screenPermissionIntent!!)
                        // Ekran paylaşımı başlatılır
                        mainRepository.startScreenCapturing(surfaceView!!)
                        // Karşı tarafa bağlantı başlatma isteği gönderilir
                        mainRepository.sendScreenShareConnection(it)
                    }
                }
            }
        }
        return START_STICKY // Sistem gerekirse servisi yeniden başlatır
    }

    // Servisi tamamen durdurur ve bağlantıları kapatır
    private fun stopMyService() {
        mainRepository.onDestroy()
        stopSelf()                    // Servisi sonlandır
        notificationManager.cancelAll()
    }

    // Foreground servis bildirimi (Android 8+ için zorunlu)
    private fun startServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "channel1", "foreground", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(notificationChannel)

            // Bildirim hazırlanır
            val notification = NotificationCompat.Builder(this, "channel1")
                .setSmallIcon(R.mipmap.ic_launcher) // Uygulama ikonu

            startForeground(1, notification.build()) // Foreground servis başlatılır
        }
    }

    // Aşağıdaki 4 metod, Repository’den gelen olayları UI’a aktarmak için servis üzerinden yönlendirilir

    // Karşı taraf ekran paylaşımı başlatmak istiyor
    override fun onConnectionRequestReceived(target: String) {
        listener?.onConnectionRequestReceived(target)
    }

    // Bağlantı kurulduğunda UI’a bilgi verilir
    override fun onConnectionConnected() {
        listener?.onConnectionConnected()
    }

    // Karşı taraf çağrıyı sonlandırdığında
    override fun onCallEndReceived() {
        listener?.onCallEndReceived()
        stopMyService()
    }

    // Karşı tarafın ekran yayını alındığında
    override fun onRemoteStreamAdded(stream: MediaStream) {
        listener?.onRemoteStreamAdded(stream)
    }

    // Bind edilmediği için null döner
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
