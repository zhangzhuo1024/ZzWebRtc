# WebRtc

//首先通过Websocket，调用进入房间，发送"__join"
map.put("eventName", "__join");
//房间返回消息"_peers",内容为自己id和房间内其他用户id
eventName.equals("_peers")
//给房间内每个用户id创建一个对应的Peer对象
//在Peer类中创建peerConnection, 并让Peer实现IceObserver
PeerConnection peerConnection = factory.createPeerConnection(rtcConfiguration,  IceObserver);
//通过peerConnection创建offer
peer.peerConnection.createOffer(peer, offerAndAnswerConstraint());
//创建offer成功之后会回调onCreateSuccess，并获取到sdp
onCreateSuccess(SessionDescription sessionDescription)
//将此sdp，设置到peerConnection
peerConnection.setLocalDescription(Peer.this, sessionDescription);
//设置完成后，sdp的状态会在onSetSuccess中回调，此时状态为HAVE_LOCAL_OFFER，再将此sdp通过webSocket经服务器传到房间里面对应的人，"__offer"
webSocketManager.sendOffer(userId, peerConnection.getLocalDescription().description);
//==========远端用户逻辑开始
//远端接收到"_offer"的人（经过服务器处理，将__offer变为_offer，说明经过了处理），将offer中的sdp设置为自己的setRemoteDescription，设置完成后会回调onSetSuccess，此时状态为HAVE_REMOTE_OFFER，在此状态下创建answer
pc.createAnswer(Peer.this, offerOrAnswerConstraint());
//answer创建完成后会回调onSetSuccess，此时的状态为HAVE_LOCAL_OFFER;此时要通过websocket将自己的sdp发出去，注意：HAVE_LOCAL_OFFER这种状态会在自己createOffer和createAnswer两种情况下回调
map.put("eventName", "__answer");
//==========远端用户逻辑结束
//接收到远端用户回复的eventName.equals("_answer")，将远端用户的sdp设置到setRemoteDescription。 注意：实测时，answer和ice一起通过websocket答复过来了？！！
peer.peerConnection.setRemoteDescription(peer, sessionDescription);
//此时，peerConnection本地和远端的sdp都设置了，在设置了远端sdp之后，会回调IceOberver的方法onIceCandidate，注意此时不用像sdp设置本地，直接将ice发到远端
webSocketManager.sendIceCandidate(userId, iceCandidate);
//通过webSocket将Ice经服务器传到房间里面对应人，实际测试，ice传出之后，addStream中就可以添加view 并且两端对话和显示都正常了
map.put("eventName", "__ice_candidate");
//远端接收到Ice之后，将远端自己的ice回复过来

//收到远端ice之后，添加到peerConnection
peer.pc.addIceCandidate(iceCandidate);
//添加之后ice就交换完了，会回调onAddStream，在onAddStream中通过MediaStream添加view就可以显示了





