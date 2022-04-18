# WebRtc

加入房间流程
//首先通过Websocket，调用进入房间，发送"__join"
map.put("eventName", "__join");
send-->{"eventName":"__join","data":{"room":"666554"}}

//服务端房间返回消息"_peers",内容为自己id和房间内其他用户id
eventName.equals("_peers")
onMessage: {"eventName":"_peers","data":{"connections":["ba56b138-71ac-409d-ae3a-53269301dc06"],"you":"5d56e008-35ec-44cd-92a0-b1cceb5b3e61"}}

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
send-->{"eventName":"__offer","data":{"socketId":"ba56b138-71ac-409d-ae3a-53269301dc06","sdp":{"type":"offer","sdp":"v=0\r\no=- 1677228693363977208 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE audio video\r\na=msid-semantic: WMS ARDAMS\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 102 0 8 106 105 13 110 112 113 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:q1Jj\r\na=ice-pwd:NPtpb6pVAKXV24jLlmx0aYgU\r\na=ice-options:trickle renomination\r\na=fingerprint:sha-256 78:65:75:BF:FA:96:84:0B:25:9E:F3:E5:53:DD:4F:F4:B5:B0:BA:25:A0:05:CA:C6:06:71:3E:8A:C0:14:59:69\r\na=setup:actpass\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\na=recvonly\r\na=rtcp-mux\r\na=rtpmap:111 opus/48000/2\r\na=rtcp-fb:111 transport-cc\r\na=fmtp:111 minptime=10;useinbandfec=1\r\na=rtpmap:103 ISAC/16000\r\na=rtpmap:104 ISAC/32000\r\na=rtpmap:9 G722/8000\r\na=rtpmap:102 ILBC/8000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:106 CN/32000\r\na=rtpmap:105 CN/16000\r\na=rtpmap:13 CN/8000\r\na=rtpmap:110 telephone-event/48000\r\na=rtpmap:112 telephone-event/32000\r\na=rtpmap:113 telephone-event/16000\r\na=rtpmap:126 telephone-event/8000\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 127\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:q1Jj\r\na=ice-pwd:NPtpb6pVAKXV24jLlmx0aYgU\r\na=ice-options:trickle renomination\r\na=fingerprint:sha-256 78:65:75:BF:FA:96:84:0B:25:9E:F3:E5:53:DD:4F:F4:B5:B0:BA:25:A0:05:CA:C6:06:71:3E:8A:C0:14:59:69\r\na=setup:actpass\r\na=mid:video\r\na=extmap:14 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=extmap:13 urn:3gpp:video-orientation\r\na=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\na=extmap:5 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\na=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/video-content-type\r\na=extmap:7 http://www.webrtc.org/experiments/rtp-hdrext/video-timing\r\na=extmap:8 http://www.webrtc.org/experiments/rtp-hdrext/color-space\r\na=sendrecv\r\na=rtcp-mux\r\na=rtcp-rsize\r\na=rtpmap:96 VP8/90000\r\na=rtcp-fb:96 goog-remb\r\na=rtcp-fb:96 transport-cc\r\na=rtcp-fb:96 ccm fir\r\na=rtcp-fb:96 nack\r\na=rtcp-fb:96 nack pli\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=96\r\na=rtpmap:98 VP9/90000\r\na=rtcp-fb:98 goog-remb\r\na=rtcp-fb:98 transport-cc\r\na=rtcp-fb:98 ccm fir\r\na=rtcp-fb:98 nack\r\na=rtcp-fb:98 nack pli\r\na=rtpmap:99 rtx/90000\r\na=fmtp:99 apt=98\r\na=rtpmap:100 red/90000\r\na=rtpmap:101 rtx/90000\r\na=fmtp:101 apt=100\r\na=rtpmap:127 ulpfec/90000\r\na=ssrc-group:FID 1082247582 1152255301\r\na=ssrc:1082247582 cname:t4FVb7WvVyBeMYD6\r\na=ssrc:1082247582 msid:ARDAMS ARDAMSv0\r\na=ssrc:1082247582 mslabel:ARDAMS\r\na=ssrc:1082247582 label:ARDAMSv0\r\na=ssrc:1152255301 cname:t4FVb7WvVyBeMYD6\r\na=ssrc:1152255301 msid:ARDAMS ARDAMSv0\r\na=ssrc:1152255301 mslabel:ARDAMS\r\na=ssrc:1152255301 label:ARDAMSv0\r\n"}}}

//==========远端用户逻辑开始
//远端接收到"_offer"的人（经过服务器处理，将__offer变为_offer，说明经过了处理），将offer中的sdp设置为自己的setRemoteDescription，设置完成后会回调onSetSuccess，此时状态为HAVE_REMOTE_OFFER，在此状态下创建answer
pc.createAnswer(Peer.this, offerOrAnswerConstraint());

//answer创建后会回调onCreateSuccess，在回调中设置setLocalDescription，设置完成后，此时RemoteDescription和LocalDescription都被设置了，回调onSetSuccess，此时的状态为STABLE;此时要通过websocket将自己的sdp发出去，注意：onCreateSuccess这种状态会在自己createOffer和createAnswer两种情况下回调，onSetSuccess是否也在这两种情况回调？
map.put("eventName", "__answer");
//==========远端用户逻辑结束

//接收到远端用户回复的eventName.equals("_answer")，将远端用户的sdp设置到setRemoteDescription。 注意：实测时，answer和ice一起通过websocket答复过来了？！！
peer.peerConnection.setRemoteDescription(peer, sessionDescription);
//此时，peerConnection本地和远端的sdp都设置了，注意这时连接通道已经打通了！！！会回调下onSetSuccess的STABLE告诉你连接完成，同时，要进行ICE的协商，会回调IceOberver的方法onIceCandidate，注意此时不用像sdp设置本地，直接将ice发到远端
webSocketManager.sendIceCandidate(userId, iceCandidate);
//通过webSocket将Ice经服务器传到房间里面对应人，实际测试，ice传出之后，addStream中就可以添加view 并且两端对话和显示都正常了
map.put("eventName", "__ice_candidate");
//远端接收到Ice之后，将远端自己的ice回复过来

//收到远端ice之后，添加到peerConnection
peer.pc.addIceCandidate(iceCandidate);
//实际上，ice的回调会回调13次，这些全部是ice协商信息，全部添加之后ice就交换完了，会回调onAddStream，在onAddStream中通过MediaStream添加view就可以显示了

本地预览逻辑：
mediaStream = factory.createLocalMediaStream("ARDAMS");
videoSource = factory.createVideoSource(videoCapturer.isScreencast());
VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
mediaStream.addTrack(videoTrack);

aceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);
mediaStream.videoTracks.get(0).addSink(surfaceViewRenderer);
wrVideoLayout.addView(surfaceViewRenderer);