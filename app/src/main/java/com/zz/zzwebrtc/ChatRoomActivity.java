package com.zz.zzwebrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.zz.zzwebrtc.utils.PermissionUtil;
import com.zz.zzwebrtc.utils.Utils;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoomActivity extends Activity {
    private FrameLayout wrVideoLayout;
    private WebRtcManager mWebRtcManager;
    private EglBase rootEglBase;
    private Map<String, SurfaceViewRenderer> videoViews = new HashMap<>();
    private List<String> persons = new ArrayList<>();

    public static void openActivity(MainActivity mActivity) {
        Intent intent = new Intent(mActivity, ChatRoomActivity.class);
        mActivity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);
//        聊天  界面    房间发小   再给房间服务器发送器    我要进入哪个房间
        initView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebRtcManager.stopVideo();
    }

    private void initView() {
        wrVideoLayout = findViewById(R.id.wr_video_view);
        wrVideoLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams
                .MATCH_PARENT, ViewGroup.LayoutParams
                .MATCH_PARENT));
        mWebRtcManager = WebRtcManager.getInstance();
        rootEglBase = EglBase.create();

        if (!PermissionUtil.isNeedRequestPermission(this)) {
            mWebRtcManager.startVideo(this, rootEglBase);
        }

    }

    public void onSetLocalStream(MediaStream mediaStream, String userId) {
        if (mediaStream.videoTracks.size() > 0) {
            VideoTrack videoTrack = mediaStream.videoTracks.get(0);

        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(mediaStream, userId);
            }
        });

    }

    private void addView(MediaStream mediaStream, String userId) {
        SurfaceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);
        surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        surfaceViewRenderer.setMirror(true);

        if (mediaStream.videoTracks.size() > 0) {
            mediaStream.videoTracks.get(0).addSink(surfaceViewRenderer);
        }
        videoViews.put(userId, surfaceViewRenderer);
        persons.add(userId);
        wrVideoLayout.addView(surfaceViewRenderer);
        int size = videoViews.size();
        for (int i = 0; i < size; i++) {
//            surfaceViewRenderer  setLayoutParams
            String peerId = persons.get(i);
            SurfaceViewRenderer renderer1 = videoViews.get(peerId);

            if (renderer1 != null) {
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.height = Utils.getWidth(this, size);
                layoutParams.width = Utils.getWidth(this, size);
                layoutParams.leftMargin = Utils.getX(this, size, i);
                layoutParams.topMargin = Utils.getY(this, size, i);
                renderer1.setLayoutParams(layoutParams);
            }
        }
    }

    public void onAddRemoteStream(MediaStream mediaStream, String userId) {
        runOnUiThread(() -> {
            addRemoteView(userId, mediaStream);
        });
    }

    private void addRemoteView(String userId, MediaStream mediaStream) {
//        不用SurfaceView  采用webrtc给我们提供的SurfaceViewRenderer
        SurfaceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);
        //        初始化SurfaceView
        surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        surfaceViewRenderer.setMirror(true);
//        关联
        if (mediaStream.videoTracks.size() > 0) {
            mediaStream.videoTracks.get(0).addSink(surfaceViewRenderer);
        }
        videoViews.put(userId, surfaceViewRenderer);
        persons.add(userId);
        wrVideoLayout.addView(surfaceViewRenderer);
        int size = videoViews.size();
        for (int i = 0; i < size; i++) {
//            surfaceViewRenderer  setLayoutParams
            String peerId = persons.get(i);
            SurfaceViewRenderer renderer1 = videoViews.get(peerId);

            if (renderer1 != null) {
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.height = Utils.getWidth(this, size);
                layoutParams.width = Utils.getWidth(this, size);
                layoutParams.leftMargin = Utils.getX(this, size, i);
                layoutParams.topMargin = Utils.getY(this, size, i);
                renderer1.setLayoutParams(layoutParams);
            }
        }
    }
}
