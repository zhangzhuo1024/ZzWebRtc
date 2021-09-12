package com.zz.zzwebrtc.peersconnect;

import android.content.Context;

import com.zz.zzwebrtc.ChatRoomActivity;
import com.zz.zzwebrtc.socket.WebSocketManager;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeersConnectManager {
    private EglBase mEglBase;
    ExecutorService executorService;
    PeerConnectionFactory factory;
    private ChatRoomActivity mContext;
    private MediaStream mediaStream;

    //    googEchoCancellation   回音消除
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    //
    //    googNoiseSuppression   噪声抑制
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    //    googAutoGainControl    自动增益控制
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    //    googHighpassFilter     高通滤波器
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private boolean isVideoEnable;
    private String myId;


    public PeersConnectManager() {
        executorService = Executors.newSingleThreadExecutor();
    }


    public void initContext(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        mContext = chatRoomActivity;
        mEglBase = rootEglBase;
    }

    public void joinRoom(WebSocketManager webSocketManager, ArrayList<String> connections, boolean isVideoEnable, String myId) {
        this.isVideoEnable = isVideoEnable;
        this.myId = myId;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (factory == null) {
                    factory = createPeerConnectionFactory();
                }

                mediaStream = factory.createLocalMediaStream("ARDAMS");

                MediaConstraints audioConstraints = new MediaConstraints();
                audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
                audioConstraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
                audioConstraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
                audioConstraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
                AudioSource audioSource = factory.createAudioSource(audioConstraints);
                AudioTrack audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
                mediaStream.addTrack(audioTrack);


                if (isVideoEnable) {

                    VideoCapturer videoCapturer;
                    if (Camera2Enumerator.isSupported(mContext)) {
                        Camera2Enumerator camera2Enumerator = new Camera2Enumerator(mContext);
                        videoCapturer = createCameraCapture(camera2Enumerator);
                    } else {
                        Camera1Enumerator enumerator = new Camera1Enumerator(true);
                        videoCapturer = createCameraCapture(enumerator);
                    }
                    VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
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
        });
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

}
