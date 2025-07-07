package com.alihan.androidsharescreen.repository

import android.content.Intent
import android.util.Log
import com.alihan.androidsharescreen.socket.SocketClient
import com.alihan.androidsharescreen.utils.DataModel
import com.alihan.androidsharescreen.utils.DataModelType
import com.alihan.androidsharescreen.webrtc.MyPeerObserver
import com.alihan.androidsharescreen.webrtc.WebrtcClient
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

// Repository sınıfı: WebRTC ve WebSocket yönetimini bir araya getirir
class MainRepository @Inject constructor(
    private val socketClient: SocketClient,
    private val webrtcClient: WebrtcClient,
    private val gson: Gson
) : SocketClient.Listener, WebrtcClient.Listener { // İki ayrı client'tan gelen verileri dinler

    private lateinit var username: String               // Bu cihazın kullanıcı adı
    private lateinit var target: String                 // Bağlantı kurulan hedef kullanıcının adı
    private lateinit var surfaceView: SurfaceViewRenderer // Ekran görüntüsünün gösterileceği UI bileşeni
    var listener: Listener? = null                      // UI’a bilgi göndermek için kullanılan interface

    // Repository'yi başlatır: WebSocket ve WebRTC yapılandırmasını yapar
    fun init(username: String, surfaceView: SurfaceViewRenderer) {
        this.username = username
        this.surfaceView = surfaceView
        initSocket()
        initWebrtcClient()
    }

    // WebSocket bağlantısını başlatır
    private fun initSocket() {
        socketClient.listener = this
        socketClient.init(username)
    }

    // WebRTC'de ekran paylaşımı için izin bilgisi verilir
    fun setPermissionIntentToWebrtcClient(intent: Intent){
        webrtcClient.setPermissionIntent(intent)
    }

    // Karşı tarafa ekran paylaşımı başlatılacağını bildirir
    fun sendScreenShareConnection(target: String){
        socketClient.sendMessageToSocket(
            DataModel(
                type = DataModelType.StartStreaming,
                username = username,
                target = target,
                null
            )
        )
    }

    // Yerel cihazda ekran paylaşımı başlatılır
    fun startScreenCapturing(surfaceView: SurfaceViewRenderer){
        webrtcClient.startScreenCapturing(surfaceView)
    }

    // Karşı tarafa SDP Offer gönderilerek çağrı başlatılır
    fun startCall(target: String){
        webrtcClient.call(target)
    }

    // Karşı tarafa çağrının bittiğini bildirir
    fun sendCallEndedToOtherPeer(){
        socketClient.sendMessageToSocket(
            DataModel(
                type = DataModelType.EndCall,
                username = username,
                target = target,
                null
            )
        )
    }

    // Bağlantı ve kaynaklar sıfırlanarak yeniden başlatılır
    fun restartRepository(){
        webrtcClient.restart()
    }

    // Kaynaklar ve bağlantılar temizlenir (uygulama kapanışı gibi)
    fun onDestroy(){
        socketClient.onDestroy()
        webrtcClient.closeConnection()
    }

    // WebRTC yapılandırması ve olay dinleyicileri burada yapılır
    private fun initWebrtcClient() {
        webrtcClient.listener = this
        webrtcClient.initializeWebrtcClient(username, surfaceView,
            object : MyPeerObserver() {
                // ICE Candidate üretildiğinde karşı tarafa gönder
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    p0?.let { webrtcClient.sendIceCandidate(it, target) }
                }

                // Bağlantı başarılı olduğunda UI’a bildir
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    Log.d("TAG", "onConnectionChange: $newState")
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED){
                        listener?.onConnectionConnected()
                    }
                }

                // Karşı taraf ekranını paylaştığında gelen stream UI’a gönderilir
                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.d("TAG", "onAddStream: $p0")
                    p0?.let { listener?.onRemoteStreamAdded(it) }
                }
            })
    }

    // WebSocket üzerinden gelen signaling mesajlarını işler
    override fun onNewMessageReceived(model: DataModel) {
        when (model.type) {
            DataModelType.StartStreaming -> {
                this.target = model.username
                // UI'a bağlantı isteği geldiğini bildir
                listener?.onConnectionRequestReceived(model.username)
            }

            DataModelType.EndCall -> {
                // UI’a çağrının sonlandığını bildir
                listener?.onCallEndReceived()
            }

            DataModelType.Offer -> {
                // Karşı taraf Offer gönderdi, cevap verilecek
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        model.data.toString()
                    )
                )
                this.target = model.username
                webrtcClient.answer(target)
            }

            DataModelType.Answer -> {
                // Karşı tarafın SDP cevabı alındı
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.ANSWER, model.data.toString())
                )
            }

            DataModelType.IceCandidates -> {
                // ICE Candidate mesajı alındı, parse edip bağlantıya ekle
                val candidate = try {
                    gson.fromJson(model.data.toString(), IceCandidate::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                candidate?.let {
                    webrtcClient.addIceCandidate(it)
                }
            }

            else -> Unit // Diğer türler için bir işlem yapılmaz
        }
    }

    // WebRTC tarafından üretilen signaling verisini WebSocket üzerinden gönder
    override fun onTransferEventToSocket(data: DataModel) {
        socketClient.sendMessageToSocket(data)
    }

    // UI ile haberleşmek için kullanılan interface
    interface Listener {
        fun onConnectionRequestReceived(target: String)       // Bir bağlantı isteği geldi
        fun onConnectionConnected()                           // WebRTC bağlantısı başarılı
        fun onCallEndReceived()                               // Karşı taraf çağrıyı sonlandırdı
        fun onRemoteStreamAdded(stream: MediaStream)          // Karşı tarafın ekran görüntüsü alındı
    }
}
