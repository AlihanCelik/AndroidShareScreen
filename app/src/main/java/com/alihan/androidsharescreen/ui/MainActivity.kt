package com.alihan.androidsharescreen.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.alihan.androidsharescreen.R
import com.alihan.androidsharescreen.databinding.ActivityMainBinding
import com.alihan.androidsharescreen.repository.MainRepository
import com.alihan.androidsharescreen.service.WebrtcService
import com.alihan.androidsharescreen.service.WebrtcServiceRepository
import org.webrtc.MediaStream
import javax.inject.Inject

class MainActivity : AppCompatActivity(), MainRepository.Listener {
    lateinit var binding: ActivityMainBinding
    private var username:String?=null

    @Inject lateinit var WebrtcServiceRepository: WebrtcServiceRepository
    private val capturePermissionRequestCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        binding=ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        init()

    }

    private fun init(){
        username = intent.getStringExtra("username")
        if (username.isNullOrEmpty()){
            finish()
        }
        WebrtcService.surfaceView = binding.surfaceView
        WebrtcService.listener = this
        WebrtcServiceRepository.startIntent(username!!)
        binding.requestBtn.setOnClickListener {
            startScreenCapture()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != capturePermissionRequestCode) return
        WebrtcService.screenPermissionIntent = data
        WebrtcServiceRepository.requestConnection(
            binding.username.text.toString()
        )
    }

    private fun startScreenCapture(){
        val mediaProjectionManager = application.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), capturePermissionRequestCode
        )
    }

    override fun onConnectionRequestReceived(target: String) {
        runOnUiThread{
            binding.apply {
                requestTitle.text = "$target is requesting for connection"
                requestly.isVisible = true
                requestaccept.setOnClickListener {
                    WebrtcServiceRepository.acceptCAll(target)
                    requestly.isVisible = false
                }
                reguestdecline.setOnClickListener {
                    requestly.isVisible = false
                }
            }
        }
    }

    override fun onConnectionConnected() {
        runOnUiThread {
            binding.apply {
                requestly.isVisible = false
                disconnect.isVisible = true
                disconnect.setOnClickListener {
                    WebrtcServiceRepository.endCallIntent()
                    restartUi()
                }
            }
        }
    }

    override fun onCallEndReceived() {
        runOnUiThread {
            restartUi()
        }
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        runOnUiThread {
            binding.surfaceView.isVisible = true
            stream.videoTracks[0].addSink(binding.surfaceView)
        }
    }

    private fun restartUi(){
        binding.apply {
            disconnect.isVisible=false
            disconnect.isVisible = true
            requestly.isVisible = false
            surfaceView.isVisible = false
        }
    }
}