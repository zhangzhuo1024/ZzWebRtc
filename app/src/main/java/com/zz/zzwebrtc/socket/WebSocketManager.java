package com.zz.zzwebrtc.socket;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.orhanobut.logger.Logger;
import com.zz.zzwebrtc.ChatRoomActivity;
import com.zz.zzwebrtc.MainActivity;
import com.zz.zzwebrtc.peersconnect.PeersConnectManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.webrtc.IceCandidate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebSocketManager {


    private MainActivity mActivity;
    private PeersConnectManager mPeersConnectManager;
    private WebSocketClient mWebSocketClient;

    public WebSocketManager() {

    }

    public WebSocketManager(MainActivity mainActivity, PeersConnectManager peersConnectManager) {
        mActivity = mainActivity;
        mPeersConnectManager = peersConnectManager;
    }

    public void connect(String wss) {

        URI uri = null;

        try {
            uri = new URI(wss);
        } catch (URISyntaxException e) {
            Logger.e(e.toString());
        }

        mWebSocketClient = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Logger.d("onOpen");
                ChatRoomActivity.openActivity(mActivity);
            }

            @Override
            public void onMessage(String message) {
                Logger.d("message = " + message);
                Map map = JSON.parseObject(message, Map.class);
                String eventName = (String) map.get("eventName");
                //进入房间map.put("eventName", "__join")，收到此消息
                if (eventName.equals("_peers")) {
                    handleMessage(map);
                }
                //给其他用户发sdp(map.put("eventName", "__offer")，他们回复sdp时收到此消息
                if (eventName.equals("_answer")) {
                    handleAnswer(map);
                }
                //给其他用户发送ice，他们回复或者其他用户给自己发送ice，map.put("eventName", "__ice_candidate"); 收到此消息
                if (eventName.equals("_ice_candidate")) {
                    handleRemoteCandidate(map);
                }
                //后面进来的用户主动给自己发送的sdp
                if (eventName.equals("_offer")) {
                    handleOffer(map);
                }

            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Logger.d("code = " + code + " reason = " + reason + " remote = " + remote);
            }

            @Override
            public void onError(Exception ex) {
                Logger.e("ex = " + ex);
            }
        };
        if (wss.startsWith("wss")) {
            SSLSocketFactory socketFactory = null;
            try {
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null, new TrustManager[]{new TrustManagerTest()}, new SecureRandom());
                socketFactory = tls.getSocketFactory();
                mWebSocketClient.setSocket(socketFactory.createSocket());
            } catch (Exception e) {
                Logger.e("e = " + e);
            }
        }
        mWebSocketClient.setReuseAddr(true);
        mWebSocketClient.connect();
    }

    private void handleOffer(Map map) {
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
            String sdp = (String) sdpDic.get("sdp");
            mPeersConnectManager.onReceiveOffer(socketId, sdp);
        }
    }

    private void handleRemoteCandidate(Map map) {
        Map data = (Map) map.get("data");
        String socketId;
        if (data != null) {
            socketId = (String) data.get("socketId");
            String sdpMid = (String) data.get("id");
            sdpMid = (null == sdpMid) ? "video" : sdpMid;
            int sdpMLineIndex = (int) Double.parseDouble(String.valueOf(data.get("label")));
            String candidate = (String) data.get("candidate");
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            mPeersConnectManager.onRemoteIceCandidate(socketId, iceCandidate);
        }
    }

    private void handleAnswer(Map map) {
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
            String sdp = (String) sdpDic.get("sdp");
            mPeersConnectManager.onReceiverAnswer(socketId, sdp);
        }
    }


    private void handleMessage(Map map) {
        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data != null) {
            arr = (JSONArray) data.get("connections");
            String js = JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            ArrayList<String> connections = (ArrayList<String>) JSONObject.parseArray(js, String.class);

            String myId = (String) data.get("you");
            mPeersConnectManager.joinRoom(this, connections, true, myId);
        }

    }

    public void disConnect() {
        mWebSocketClient.close();
    }

    public void joinRoom(String roomId) {
//        请求  http     socket 请求
        Map<String, Object> map = new HashMap<>();
        map.put("eventName", "__join");
        Map<String, String> childMap = new HashMap<>();
        childMap.put("room", roomId);
        map.put("data", childMap);
        JSONObject object = new JSONObject(map);
        final String jsonString = object.toString();
        Logger.json(jsonString);
        mWebSocketClient.send(jsonString.getBytes());
    }

    public void sendOffer(String userId, String description) {
        HashMap<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "offer");
        childMap1.put("sdp", description);

        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", userId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__offer");
        map.put("data", childMap2);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Logger.json(jsonString);
        mWebSocketClient.send(jsonString);
    }

    public void sendIceCandidate(String userId, IceCandidate iceCandidate) {
        HashMap<String, Object> childMap = new HashMap();
        childMap.put("id", iceCandidate.sdpMid);
        childMap.put("label", iceCandidate.sdpMLineIndex);
        childMap.put("candidate", iceCandidate.sdp);
        childMap.put("socketId", userId);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__ice_candidate");
        map.put("data", childMap);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Logger.json(jsonString);
        mWebSocketClient.send(jsonString);
    }

    public void sendAnswer(String userId, String description) {
        Map<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "answer");
        childMap1.put("sdp", description);
        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", userId);
        childMap2.put("sdp", childMap1);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__answer");
        map.put("data", childMap2);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Logger.json(jsonString);
        mWebSocketClient.send(jsonString);
    }

    private class TrustManagerTest implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
