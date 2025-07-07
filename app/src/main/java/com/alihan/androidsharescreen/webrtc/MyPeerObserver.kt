package com.alihan.androidsharescreen.webrtc

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.VideoTrack

open class MyPeerObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}

    override fun onIceConnectionReceivingChange(receiving: Boolean) {}

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

    override fun onIceCandidate(candidate: IceCandidate?) {}

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

    override fun onAddStream(stream: MediaStream?) {}

    override fun onRemoveStream(stream: MediaStream?) {}

    override fun onDataChannel(dataChannel: DataChannel?) {}

    override fun onRenegotiationNeeded() {}

    // Unified Plan’da remote track'ler bu metotta gelir
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        receiver?.track()?.let { track ->
            if (track is VideoTrack) {
                // İstersen burada remote video track için işlemler yap
                // Örneğin, UI'ya bildir, stream’i sakla vs.
                // mediaStreams?.firstOrNull() üzerinden MediaStream erişilebilir
            }
        }
    }
}
