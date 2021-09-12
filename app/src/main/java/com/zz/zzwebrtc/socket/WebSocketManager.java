package com.zz.zzwebrtc.socket;

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
                if (eventName.equals("_peers")){
                    handleMessage(map);
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

    private void handleMessage(Map map) {
        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data != null) {
            arr = (JSONArray) data.get("connections");
            String js = JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            ArrayList<String> connections = (ArrayList<String>) JSONObject.parseArray(js, String.class);

            String myId = (String) data.get("you");
            mPeersConnectManager.joinRoom(this,connections, true, myId);
        }

    }

    public void disConnect() {
        mWebSocketClient.close();
    }

    public void startVideo(String roomId) {
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
