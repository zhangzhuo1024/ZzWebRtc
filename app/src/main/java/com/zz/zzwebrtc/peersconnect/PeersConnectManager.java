package com.zz.zzwebrtc.peersconnect;

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
    private WebSocketManager webSocketManager;
    private ExecutorService executorService;
    private PeerConnectionFactory factory;
    private ChatRoomActivity mContext;
    private MediaStream mediaStream;
    private EglBase mEglBase;
    private String myId;
    private boolean isVideoEnable;
    private Role role;
    private AudioSource audioSource;
    private VideoSource videoSource;
    private VideoCapturer videoCapturer;

    enum Role {Caller, Receiver}

    public PeersConnectManager() {
        executorService = Executors.newSingleThreadExecutor();
        mConnectionIdList = new ArrayList<>();
        mConnectionIdPeerMap = new HashMap<String, Peer>();
        ICEServers = new ArrayList<>();
        PeerConnection.IceServer iceServer1 = PeerConnection.IceServer
                .builder("turn:116.62.66.154:3478?transport=udp")
                .setUsername("zz")
                .setPassword("123456")
                .createIceServer();
        ICEServers.add(iceServer1);
    }

    public void initContext(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        mContext = chatRoomActivity;
        mEglBase = rootEglBase;
    }

    public void joinRoom(WebSocketManager webSocketManager, ArrayList<String> connections, boolean isVideoEnable, String myId) {
        this.webSocketManager = webSocketManager;
        this.isVideoEnable = isVideoEnable;
        this.myId = myId;
        mConnectionIdList.addAll(connections);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (factory == null) {
                    factory = createPeerConnectionFactory();
                }

                //本地预览
                if (mediaStream == null) {
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
        executorService.execute(() -> {
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
                if (null != audioSource) {//释放音频资源
                    audioSource.dispose();
                    audioSource = null;
                }
                if (null != videoSource) {//释放视频资源
                    videoSource.dispose();
                    videoSource = null;
                }
                if (videoCapturer != null) {//释放画面
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    videoCapturer.dispose();
                    videoCapturer = null;
                }

                if (mediaStream != null) {
                    mediaStream.dispose();
                    mediaStream = null;
                }
                if (mEglBase != null) {
                    mEglBase.release();
                    mEglBase = null;
                }
                if (null != factory) {//释放掉PeerConnecttionFactroy
                    factory.stopAecDump();
                    factory.dispose();
                    factory = null;
                }
            }
        });
    }

    private void sendOffers() {
        for (Map.Entry<String, Peer> stringPeerEntry : mConnectionIdPeerMap.entrySet()) {
            role = Role.Caller;
            Peer peer = stringPeerEntry.getValue();
            //给每个房间的人发送offer，结果在第一个参数peer中回调
            peer.peerConnection.createOffer(peer, offerAndAnswerConstraint());
        }
    }

    private MediaConstraints offerAndAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoEnable)));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    private void addRemoteStream() {

        for (Map.Entry<String, Peer> stringPeerEntry : mConnectionIdPeerMap.entrySet()) {
            stringPeerEntry.getValue().peerConnection.addStream(mediaStream);
        }
    }

    private void createPeerConnections() {
        for (String userId : mConnectionIdList) {
            Peer peer = new Peer(userId);
            mConnectionIdPeerMap.put(userId, peer);
        }
    }

    private void addLocalStreamPreview(boolean isVideoEnable, String myId) {
        mediaStream = factory.createLocalMediaStream("ARDAMS");
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioSource = factory.createAudioSource(audioConstraints);
        AudioTrack audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        mediaStream.addTrack(audioTrack);


        if (isVideoEnable) {
            if (Camera2Enumerator.isSupported(mContext)) {
                Camera2Enumerator camera2Enumerator = new Camera2Enumerator(mContext);
                videoCapturer = createCameraCapture(camera2Enumerator);
            } else {
                Camera1Enumerator enumerator = new Camera1Enumerator(true);
                videoCapturer = createCameraCapture(enumerator);
            }
            videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mEglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper, mContext, videoSource.getCapturerObserver());
            videoCapturer.startCapture(320, 240, 10);
            VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
            mediaStream.addTrack(videoTrack);

            if (mContext != null) {
                mContext.onSetLocalStream(mediaStream, myId);
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

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(mContext).createInitializationOptions());
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(mEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext());
        JavaAudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();
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
        executorService.execute(() -> {
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                peer.peerConnection.setRemoteDescription(peer, sessionDescription);
            }
        });
    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        executorService.execute(() -> {
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                peer.peerConnection.addIceCandidate(iceCandidate);
            }
        });
    }

    public void onReceiveOffer(String socketId, String sdp) {
        executorService.execute(() -> {
            role = Role.Receiver;
            Peer peer = mConnectionIdPeerMap.get(socketId);
            if (peer != null) {
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                peer.peerConnection.setRemoteDescription(peer, sessionDescription);
            }
        });
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private String userId;
        private PeerConnection peerConnection;

        public Peer(String userId) {
            this.userId = userId;
            peerConnection = createPeerConnection();
        }

        private PeerConnection createPeerConnection() {
            if (factory == null) {
                factory = createPeerConnectionFactory();
            }
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(ICEServers);
            return factory.createPeerConnection(rtcConfiguration, this);
        }

        // SDP 回调  start
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Logger.e("onCreateSuccess = " + sessionDescription.description);
            peerConnection.setLocalDescription(Peer.this, sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                Logger.e("HAVE_LOCAL_OFFER = ");
                if (role == Role.Caller) {
                    webSocketManager.sendOffer(userId, peerConnection.getLocalDescription().description);
                }
                if (role == Role.Receiver) {
                    webSocketManager.sendAnswer(userId, peerConnection.getLocalDescription().description);
                }
            } else if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                Logger.e("HAVE_REMOTE_OFFER = ");
                peerConnection.createAnswer(Peer.this, offerAndAnswerConstraint());
            } else if (peerConnection.signalingState() == PeerConnection.SignalingState.STABLE) {
                Logger.e("STABLE = ");
                if (role == Role.Receiver) {
                    webSocketManager.sendAnswer(userId, peerConnection.getLocalDescription().description);
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
            webSocketManager.sendIceCandidate(userId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mContext.onAddRemoteStream(mediaStream, userId);
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

        //PeerConnection.Observer end
    }

}
