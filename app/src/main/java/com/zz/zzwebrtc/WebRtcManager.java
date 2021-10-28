package com.zz.zzwebrtc;

import com.zz.zzwebrtc.peersconnect.PeersConnectManager;
import com.zz.zzwebrtc.socket.WebSocketManager;

import org.webrtc.EglBase;

public class WebRtcManager {
    public static WebRtcManager instance = new WebRtcManager();
    private MainActivity mMainActivity;
    private String mRoomId;
    private WebSocketManager mWebSocketManager;
    PeersConnectManager mPeersConnectManager;

    private WebRtcManager() {

    }

    public static WebRtcManager getInstance() {
        return instance;
    }

    public void connect(MainActivity mainActivity, String roomId) {
        mMainActivity = mainActivity;
        mRoomId = roomId;
        mPeersConnectManager = new PeersConnectManager();
        mWebSocketManager = new WebSocketManager(mainActivity, mPeersConnectManager);
        mWebSocketManager.connect("wss://116.62.66.154/wss");
//        mWebSocketManager.connect("ws://116.62.66.154:7001/p2p");
    }

    public void startVideo(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        mPeersConnectManager.initContext(chatRoomActivity, rootEglBase);
        mWebSocketManager.joinRoom(mRoomId);
    }

    public void stopVideo() {
        //通知服务端退房，服务端给所有房间内成员发_remove_peer通知退房
        mWebSocketManager.disConnect();
    }
}
