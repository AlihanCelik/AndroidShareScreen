package com.alihan.androidsharescreen.socket

import com.alihan.androidsharescreen.utils.DataModel
import com.alihan.androidsharescreen.utils.DataModelType
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.inject.Inject
import kotlin.Exception

class SocketClient @Inject constructor(
    private val gson: Gson // JSON dönüşümleri için Gson bağımlılığı
) {
    private var username: String? = null // Bu istemcinin kullanıcı adı
    var listener: Listener? = null       // WebSocket’ten gelen mesajları dinleyecek dış sınıf

    companion object {
        // WebSocketClient örneği static olarak tutulur (tüm SocketClient için tek bağlantı)
        private var websocket: WebSocketClient? = null
    }

    /**
     * WebSocket bağlantısını başlatır.
     * Sunucuya bağlanır ve `SignIn` mesajı gönderir.
     */
    fun init(username: String) {
        this.username = username

        // WebSocketClient instance'ı oluşturuluyor
        websocket = object : WebSocketClient(URI("ws://10.0.2.2:3000")) {
            // Bağlantı açıldığında çalışır
            override fun onOpen(handshakedata: ServerHandshake?) {
                // Giriş mesajı gönderilir
                sendMessageToSocket(
                    DataModel(
                        type = DataModelType.SignIn, // "SignIn" tipi
                        username = username,         // Kullanıcı adı
                        target = null,
                        data = null
                    )
                )
            }

            // Sunucudan mesaj alındığında çalışır
            override fun onMessage(message: String?) {
                // JSON mesajı DataModel'a parse et
                val model = try {
                    gson.fromJson(message.toString(), DataModel::class.java)
                } catch (e: Exception) {
                    null // Parse başarısızsa null dön
                }

                // Eğer mesaj parse edildiyse listener’a bildir
                model?.let {
                    listener?.onNewMessageReceived(it)
                }
            }

            // Bağlantı kapandığında çalışır
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                // Bağlantı 5 saniye sonra yeniden başlatılır
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000)
                    init(username) // Yeniden bağlan
                }
            }

            // Hata olduğunda çalışır
            override fun onError(ex: Exception?) {
                // Hatalar sessiz geçiliyor ama istenirse loglanabilir
            }
        }

        // WebSocket bağlantısını başlat
        websocket?.connect()
    }

    /**
     * WebSocket'e mesaj gönderir
     */
    fun sendMessageToSocket(message: Any?) {
        try {
            // JSON'a çevirip gönder
            websocket?.send(gson.toJson(message))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Uygulama kapanırken WebSocket bağlantısını kapat
     */
    fun onDestroy() {
        websocket?.close()
    }

    /**
     * DI dış dünya ile haberleşmek için kullanılan arayüz
     * Bu interface üzerinden ViewModel veya başka bir sınıf,
     * gelen mesajlara tepki verebilir
     */
    interface Listener {
        fun onNewMessageReceived(model: DataModel)
    }
}
