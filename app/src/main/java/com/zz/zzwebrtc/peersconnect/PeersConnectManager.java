package com.zz.zzwebrtc.peersconnect;

import android.util.Log;

import com.orhanobut.logger.Logger;
import com.zz.zzwebrtc.ChatRoomActivity;
import com.zz.zzwebrtc.socket.WebSocketManager;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeersConnectManager {
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";    //    googEchoCancellation   回音消除
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";    //    googNoiseSuppression   噪声抑制
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";    //    googAutoGainControl    自动增益控制
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";    //    googHighpassFilter     高通滤波器
    private ArrayList<PeerConnection.IceServer> ICEServers;
    private ArrayList<PeerConnection.IceServer> ICEServerList;
    private ArrayList<String> mConnectionIdList;
    private HashMap<String, Peer> mConnectionIdPeerMap;
    private WebSocketManager mWebSocketManager;
    private ExecutorService mExecutorService;
    private PeerConnectionFactory mFactory;
    private ChatRoomActivity mChatRoomActivity;
    private MediaStream mMediaStream;
    private EglBase mEglBase;
    private String myId;
    private boolean mIsVideoEnable;
    private Role mRole;
    private AudioSource mAudioSource;
    private VideoSource mVideoSource;
    private VideoCapturer mVideoCapture;

    enum Role {Caller, Receiver}

    public PeersConnectManager() {
        mExecutorService = Executors.newSingleThreadExecutor();
        mConnectionIdList = new ArrayList<>();
        mConnectionIdPeerMap = new HashMap<String, Peer>();
        ICEServers = new ArrayList<>();
//        PeerConnection.IceServer iceServer1 = PeerConnection.IceServer
//                .builder("turn:116.62.66.154:3478?transport=udp")
//                .setUsername("zz")
//                .setPassword("123456")
//                .createIceServer();
//        ICEServers.add(iceServer1);

        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("stun:stun.voipbuster.com:3478").createIceServer();
        ICEServers.add(iceServer);
    }

    public void initContext(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        mChatRoomActivity = chatRoomActivity;
        mEglBase = rootEglBase;
    }

    public void joinRoom(WebSocketManager webSocketManager, ArrayList<String> connections, boolean isVideoEnable, String myId) {
        this.mWebSocketManager = webSocketManager;
        this.mIsVideoEnable = isVideoEnable;
        this.myId = myId;
        mConnectionIdList.addAll(connections);

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                if (mFactory == null) {
                    mFactory = createPeerConnectionFactory();
                }

                //本地预览
                if (mMediaStream == null) {
                    addLocalStreamPreview(isVideoEnable, myId);
                }

                //构建每个用户的peerconnect
                createPeerConnections();

                //添加每个用户到视图上
                addRemoteStream();

                //给房间服务器里面的每个人发送offer
                sendOffers();
            }
        });
    }

    public void closePeerConnection() {
        mExecutorService.execute(() -> {
            if (null != mConnectionIdPeerMap) {
                for (String s : mConnectionIdPeerMap.keySet()) {
                    Peer peer = mConnectionIdPeerMap.get(s);
                    if (peer != null) {
                        PeerConnection connection = peer.peerConnection;
                        if (connection != null) {
                            connection.close();
                            connection.dispose();            //关闭peerconnecttion连接
                        }
                    } else {
                        Logger.e("peers===null");
                    }
                }
                mConnectionIdPeerMap.clear();
                if (null != mAudioSource) {//释放音频资源
                    mAudioSource.dispose();
                    mAudioSource = null;
                }
                if (null != mVideoSource) {//释放视频资源
                    mVideoSource.dispose();
                    mVideoSource = null;
                }
                if (mVideoCapture != null) {//释放画面
                    try {
                        mVideoCapture.stopCapture();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mVideoCapture.dispose();
                    mVideoCapture = null;
                }

                if (mMediaStream != null) {
                    mMediaStream.dispose();
                    mMediaStream = null;
                }
                if (mEglBase != null) {
                    mEglBase.release();
                    mEglBase = null;
                }
                if (null != mFactory) {//释放掉PeerConnecttionFactroy
                    mFactory.stopAecDump();
                    mFactory.dispose();
                    mFactory = null;
                }
            }
        });
    }

    private void sendOffers() {
        for (Map.Entry<String, Peer> stringPeerEntry : mConnectionIdPeerMap.entrySet()) {
            mRole = Role.Caller;
            Peer peer = stringPeerEntry.getValue();
            //给每个房间的人发送offer，结果在第一个参数peer中回调
            peer.peerConnection.createOffer(peer, offerAndAnswerConstraint());
        }
    }

    private MediaConstraints offerAndAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(mIsVideoEnable)));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    private void addRemoteStream() {
        for (Map.Entry<String, Peer> stringPeerEntry : mConnectionIdPeerMap.entrySet()) {
            stringPeerEntry.getValue().peerConnection.addStream(mMediaStream);
        }
    }

    private void createPeerConnections() {
        for (String remoteUserId : mConnectionIdList) {
            Peer peer = new Peer(remoteUserId);
            mConnectionIdPeerMap.put(remoteUserId, peer);
        }
    }

    private void addLocalStreamPreview(boolean isVideoEnable, String myId) {
        mMediaStream = mFactory.createLocalMediaStream("ARDAMS");
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        mAudioSource = mFactory.createAudioSource(audioConstraints);
        AudioTrack audioTrack = mFactory.createAudioTrack("ARDAMSa0", mAudioSource);
//        mediaStream.addTrack(audioTrack);

        if (isVideoEnable) {
            if (Camera2Enumerator.isSupported(mChatRoomActivity)) {
                Camera2Enumerator camera2Enumerator = new Camera2Enumerator(mChatRoomActivity);
                mVideoCapture = createCameraCapture(camera2Enumerator);
            } else {
                Camera1Enumerator enumerator = new Camera1Enumerator(true);
                mVideoCapture = createCameraCapture(enumerator);
            }
            mVideoSource = mFactory.createVideoSource(mVideoCapture.isScreencast());
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mEglBase.getEglBaseContext());
            mVideoCapture.initialize(surfaceTextureHelper, mChatRoomActivity, mVideoSource.getCapturerObserver());
            mVideoCapture.startCapture(320, 240, 10);
            VideoTrack videoTrack = mFactory.createVideoTrack("ARDAMSv0", mVideoSource);
            mMediaStream.addTrack(videoTrack);
            if (mChatRoomActivity != null) {
                mChatRoomActivity.onSetLocalStream(mMediaStream, myId);
            }
        }
    }

    //    意思  获取前置前置摄像头   后置摄像头
    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {

        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }

        }
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(mChatRoomActivity).createInitializationOptions());
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(mEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext());
        JavaAudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(mChatRoomActivity).createAudioDeviceModule();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
        return peerConnectionFactory;
    }

    public void onReceiverAnswer(String socketId, String sdp) {
        mExecutorService.execute(() -> {
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                peer.peerConnection.setRemoteDescription(peer, sessionDescription);
            }
        });
    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        mExecutorService.execute(() -> {
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                peer.peerConnection.addIceCandidate(iceCandidate);
            }
        });
    }

    public void onReceiveOffer(String socketId, String sdp) {
        mExecutorService.execute(() -> {
            mRole = Role.Receiver;
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                peer.peerConnection.setRemoteDescription(peer, sessionDescription);
            }
        });
    }

    public void onRemoteJoinToRoom(String remoteUserId) {
        mExecutorService.execute(() -> {
            if (mMediaStream == null) {
                //本地预览
                addLocalStreamPreview(mIsVideoEnable, myId);
            }
            Peer peer = new Peer(remoteUserId);
            peer.peerConnection.addStream(mMediaStream);
            mConnectionIdPeerMap.put(remoteUserId, peer);
            mConnectionIdList.add(remoteUserId);
        });
    }


    public void onRemoteLeaveRoom(String remoteUserId) {
        if (mConnectionIdList.contains(remoteUserId)) {
            mConnectionIdList.remove(remoteUserId);
        }
        if (mConnectionIdPeerMap.containsKey(remoteUserId)) {
            Peer peer = mConnectionIdPeerMap.get(remoteUserId);
            peer.close();
        }

    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private String remoteUserId;
        private PeerConnection peerConnection;

        public Peer(String remoteUserId) {
            this.remoteUserId = remoteUserId;
            peerConnection = createPeerConnection();

            peerConnection.getStats(new RTCStatsCollectorCallback() {
                @Override
                public void onStatsDelivered(RTCStatsReport rtcStatsReport) {
                    Log.e("eeeeee", rtcStatsReport.toString());
                }
            });
        }

        private PeerConnection createPeerConnection() {
            if (mFactory == null) {
                mFactory = createPeerConnectionFactory();
            }
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(ICEServers);
            return mFactory.createPeerConnection(rtcConfiguration, this);
        }

        // SDP 回调  start
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Logger.e("onCreateSuccess = " + sessionDescription.description);
            peerConnection.setLocalDescription(Peer.this, sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            Logger.e("onSetSuccess = " + peerConnection.signalingState() + "  role = " + mRole);
            if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                if (mRole == Role.Caller) {
                    mWebSocketManager.sendOffer(remoteUserId, peerConnection.getLocalDescription().description);
                }
                if (mRole == Role.Receiver) {
                    mWebSocketManager.sendAnswer(remoteUserId, peerConnection.getLocalDescription().description);
                }
            } else if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                peerConnection.createAnswer(Peer.this, offerAndAnswerConstraint());
            } else if (peerConnection.signalingState() == PeerConnection.SignalingState.STABLE) {
                if (mRole == Role.Receiver) {
                    mWebSocketManager.sendAnswer(remoteUserId, peerConnection.getLocalDescription().description);
                }
            }
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
        // SDP 回调  end


        // PeerConnection.Observer start
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        //
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Logger.e("onIceCandidate = ");
            mWebSocketManager.sendIceCandidate(remoteUserId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mChatRoomActivity.onAddRemoteStream(mediaStream, remoteUserId);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        public void close() {
            mChatRoomActivity.removeRemoteStream(remoteUserId);
            if (peerConnection != null) {
                try {
                    peerConnection.close();
                } catch (Exception e) {
                    Logger.e("Exception = " + e);
                }
            }
        }
        //PeerConnection.Observer end
    }
}
