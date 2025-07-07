package com.alihan.androidsharescreen.webrtc

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.alihan.androidsharescreen.utils.DataModel
import com.alihan.androidsharescreen.utils.DataModelType
import com.google.gson.Gson
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import javax.inject.Inject

class WebrtcClient @Inject constructor(
    private val context: Context,       // Android context
    private val gson: Gson,             // ICE candidate'ları JSON'a çevirmek için
) {

    // Kullanıcı adı, PeerConnection Observer ve görüntüleme için kullanılacak WebRTC SurfaceView
    private lateinit var username: String
    private lateinit var observer: PeerConnection.Observer
    private lateinit var localSurfaceView: SurfaceViewRenderer

    var listener: Listener? = null      // ICE, SDP gibi verileri socket üzerinden göndermek için dışa açık arayüz
    private var permissionIntent: Intent?=null // Ekran görüntüsünü yakalamak için MediaProjection izni

    private var peerConnection: PeerConnection? = null

    // WebRTC için EGL (OpenGL bağlamı), video encoder/decoder'da kullanılır
    private val eglBaseContext = EglBase.create().eglBaseContext

    // PeerConnectionFactory'yi sadece gerektiğinde başlat
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }

    // SDP teklif/yanıtı oluştururken kullanılacak kısıtlamalar
    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    // TURN sunucusu (STUN yerine bağlantı kurmakta zorlanıldığında kullanılır)
    private val iceServer = listOf(
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"
        )
    )

    // Video ekran yakalama bileşenleri
    private var screenCapturer : VideoCapturer?=null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }

    private val localTrackId = "local_track"
    private val localStreamId = "local_stream"

    private var localVideoTrack: VideoTrack?=null
    private var localStream: MediaStream?=null

    init {
        // PeerConnectionFactory genel WebRTC yapılandırmasını başlat
        initPeerConnectionFactory(context)
    }

    /**
     * WebRTC bağlantısını başlatır
     * - Kullanıcı adı atanır
     * - SurfaceView başlatılır
     * - PeerConnection oluşturulur
     */
    fun initializeWebrtcClient(
        username: String, view: SurfaceViewRenderer, observer: PeerConnection.Observer
    ) {
        this.username = username
        this.observer = observer
        peerConnection = createPeerConnection(observer)
        initSurfaceView(view)
    }

    // MediaProjection izni Activity'den alınır ve burada set edilir
    fun setPermissionIntent(intent: Intent) {
        this.permissionIntent = intent
    }

    // SurfaceViewRenderer’ı yapılandır: Mirror yok, donanımsal ölçekleme açık, EGL context atanır
    private fun initSurfaceView(view: SurfaceViewRenderer){
        this.localSurfaceView = view
        view.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }

    /**
     * Ekranı paylaşmak için:
     * - Ekran çözünürlüğünü al
     * - SurfaceTextureHelper oluştur
     * - ScreenCapturer başlatılır
     * - VideoTrack ve MediaStream oluşturulur
     */
    fun startScreenCapturing(view: SurfaceViewRenderer){
        val displayMetrics = DisplayMetrics()
        val windowsManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowsManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidthPixels = displayMetrics.widthPixels
        val screenHeightPixels = displayMetrics.heightPixels

        // SurfaceTextureHelper: OpenGL üzerinde capture işlemi için kullanılır
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, eglBaseContext
        )

        screenCapturer = createScreenCapturer()
        screenCapturer!!.initialize(surfaceTextureHelper, context, localVideoSource.capturerObserver)
        screenCapturer!!.startCapture(screenWidthPixels, screenHeightPixels, 15)

        // VideoTrack ve MediaStream oluşturulup PeerConnection'a eklenir
        localVideoTrack = peerConnectionFactory.createVideoTrack(localTrackId + "_video", localVideoSource)
        localVideoTrack?.addSink(view) // Görüntü bu view’a gönderilir
        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        localStream?.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)
    }

    // Android ekranını yakalamak için WebRTC’in ScreenCapturerAndroid sınıfı kullanılır
    private fun createScreenCapturer(): VideoCapturer {
        return ScreenCapturerAndroid(permissionIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d("TAG", "onStop: stopped screen casting permission")
            }
        })
    }

    // PeerConnectionFactory global olarak WebRTC sistemini başlatır
    private fun initPeerConnectionFactory(application: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/") // H264 video desteği
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    // WebRTC PeerConnectionFactory oluşturur (video encode/decode fabrikaları ile)
    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    // ICE sunucusu ve Observer ile PeerConnection oluştur
    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    /**
     * Teklif (offer) başlatılır:
     * - SDP offer oluşturulur
     * - LocalDescription olarak set edilir
     * - Socket ile karşı tarafa offer gönderilir
     */
    fun call(target: String) {
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Offer,
                                username = username,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    /**
     * Gelen teklif üzerine cevap (answer) oluşturulur ve gönderilir
     */
    fun answer(target: String) {
        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Answer,
                                username = username,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    // Gelen SDP remote olarak set edilir
    fun onRemoteSessionReceived(sessionDescription: SessionDescription){
        peerConnection?.setRemoteDescription(MySdpObserver(), sessionDescription)
    }

    // ICE candidate peerConnection'a eklenir
    fun addIceCandidate(iceCandidate: IceCandidate){
        peerConnection?.addIceCandidate(iceCandidate)
    }

    // ICE candidate hem eklenir hem socket ile karşıya gönderilir
    fun sendIceCandidate(candidate: IceCandidate, target: String){
        addIceCandidate(candidate)
        listener?.onTransferEventToSocket(
            DataModel(
                type = DataModelType.IceCandidates,
                username = username,
                target = target,
                data = gson.toJson(candidate)
            )
        )
    }

    // Tüm bağlantı ve kaynaklar kapatılır
    fun closeConnection(){
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            localStream?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Restart: bağlantı kapatılır ve her şey baştan başlatılır
    fun restart(){
        closeConnection()
        localSurfaceView.let {
            it.clearImage()
            it.release()
            initializeWebrtcClient(username, it, observer)
        }
    }

    // Arayüz: WebRTC verilerini socket sistemine dışarı aktarmak için kullanılır
    interface Listener {
        fun onTransferEventToSocket(data: DataModel)
    }

}
