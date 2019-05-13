package com.example.chengt.hotlineapp;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class TextWebRTCClient {
    private final static String TAG = "TextWebRTCClient";
    private final static String mSocketAddress = "http://192.168.1.100:3000/";

    private String roomName;

    private static final String downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
    private static final int CHUNK_SIZE = 64000;

    private String incomingFileName = "";
    private int incomingFileSize;
    private int currentIndexPointer;
    private byte[] imageFileBytes;
    private boolean receivingFile = false;

    private PeerConnectionFactory factory;
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private Socket client;
    private String mClientId;
    private Map<String, Peer> peers = new HashMap<>();
    private MediaConstraints constraints = new MediaConstraints();
    private WebRTCListener webRTCListener;

    public String fileName;

    public boolean isRemotePeerLeft = false;
    public boolean isInit = false;
    public boolean isInRoom = false;

    private Emitter.Listener messageListener = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            Log.d(TAG, "messageListener call data: " + data);
            try {
                String from = data.getString("from");
                String type = data.getString("type");
                JSONObject payload = null;
                if (!type.equals("init")) {
                    payload = data.getJSONObject("payload");
                }
                switch (type) {
                    case "init":
                        onReceiveInit(from);
                        break;
                    case "offer":
                        onReceiveOffer(from, payload);
                        break;
                    case "answer":
                        onReceiveAnswer(from, payload);
                        break;
                    case "candidate":
                        onReceiveCandidate(from, payload);
                        break;
                    default:
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private Emitter.Listener clientIdListener = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            mClientId = (String) args[0];
            Log.d(TAG, "clientIdListener call data: " + mClientId);
        }
    };

    TextWebRTCClient(PeerConnectionFactory factory, String roomName) {
        this.factory = factory;
        this.roomName = roomName;
        try {
            client = IO.socket(mSocketAddress);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        client.on("message", messageListener);
        client.on("id", clientIdListener);
        client.connect();

        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("turn:140.113.167.189:3478").setUsername("turnserver").setPassword("turnserver").createIceServer();
        iceServers.add(peerIceServer);
        PeerConnection.IceServer peerIceServer_stun = PeerConnection.IceServer.builder("stun:140.113.167.189:3478").createIceServer();
        iceServers.add(peerIceServer_stun);

        emitInitStatement(roomName);

        // room created event
        client.on("created", args -> {
            Log.d("SignalingClient", "created call() called with: args =[" + Arrays.toString(args) + "]");
            isInRoom = true;
        });

        // room is full event
        client.on("full", args -> {
            Log.d("SignalingClient", "full call() called with: args = [" + Arrays.toString(args) + "]");
            webRTCListener.roomIsFull(roomName);
        });

        // peer joined event
        client.on("join",  args -> {
            Log.d("SignalingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");
        });

        // when you joined a chat room successfully
        client.on("joined", args -> {
            Log.d("SignalingClient", "joined call() called with: args = [" + Arrays.toString(args) + "]");
            isInRoom = true;
        });

        client.on("init", args -> {
            Log.d("SignalingClient", "init call() called with: args = [" + Arrays.toString(args) + "]");
            client.emit("init", roomName);
            if (!isInit) {
                webRTCListener.initSuccess();
                isInit = true;
            }
        });

        client.on("remotepeer leave", args -> {
            Log.d("SignalingClient", "remotepeer leave call() called with: args = [" + Arrays.toString(args) + "]");
            Log.d(TAG, "call leave by myself");
            isRemotePeerLeft = true;
            factory.dispose();
            client.disconnect();
            client.close();
            webRTCListener.onRemotePeerLeave();
        });
    }

    private void emitInitStatement(String message) {
        Log.d("SignalingClient", "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        client.emit("create or join", message);
    }

    public void setWebRTCListener(WebRTCListener webRTCListener) {
        this.webRTCListener = webRTCListener;
    }

    /**
     * 向Signaling Server發訊息
     *
     * @param to id of recipient
     * @param type type of message
     * @param payload payload of message
     * @throws JSONException
     */
    private void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        message.put("from", mClientId);
        client.emit("message", roomName, message);
    }

    /**
     * 向所有連接的peer端發送訊息
     *
     * @param file
     */
    public void sendDataFileToAllPeer(File file) {
        for (Peer peer : peers.values()) {
            peer.sendDataChannelFile(file);
        }
        Log.d(TAG,"in sendDataFileToAllPeer");
    }

    public void sendDataMessageToAllPeer(String message) {
        for (Peer peer : peers.values()) {
            peer.sendDataChannelMessage(message);
        }
        Log.d(TAG,"in sendDataMessageToAllPeer");
    }

    private Peer getPeer(String from) {
        Peer peer;
        if (!peers.containsKey(from)) {
            peer = addPeer(from);
        } else {
            peer = peers.get(from);
        }
        return peer;
    }

    private Peer addPeer(String id) {
        Peer peer = new Peer(id);
        peers.put(id, peer);
        return peer;
    }

    private void removePeer(String id) {
        Peer peer = peers.get(id);
        peer.release();
        peers.remove(peer.id);
    }

    private void onReceiveInit(String fromUid) {
        Log.d(TAG, "onReceiveInit fromUid:" + fromUid);
        Peer peer = getPeer(fromUid);
        peer.pc.createOffer(peer, constraints);
        if (!isInit) {
            webRTCListener.initSuccess();
            isInit = true;
        }
    }

    private void onReceiveOffer(String fromUid, JSONObject payload) {
        Log.d(TAG, "onReceiveOffer uid:" + fromUid + " data:" + payload);
        try {
            Peer peer = getPeer(fromUid);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, constraints);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void onReceiveAnswer(String fromUid, JSONObject payload) {
        Log.d(TAG, "onReceiveAnswer uid:" + fromUid + " data:" + payload);
        try {
            Peer peer = getPeer(fromUid);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void onReceiveCandidate(String fromUid, JSONObject payload) {
        Log.d(TAG, "onReceiveCandidate uid:" + fromUid + " data:" + payload);
        try {
            Peer peer = getPeer(fromUid);
            if (peer.pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("id"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                peer.pc.addIceCandidate(candidate);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        for (Peer peer : peers.values()) {
            peer.release();
        }
        factory.dispose();
        if (isInRoom) {
            client.emit("bye", roomName);
        }
        client.disconnect();
        client.close();
    }

    public class Peer implements SdpObserver, PeerConnection.Observer, DataChannel.Observer {
        PeerConnection pc;
        String id;
        DataChannel dc;

        Peer(String id) {
            Log.d(TAG, "new Peer: " + id);
            this.pc = factory.createPeerConnection(iceServers, this);
            this.id = id;

            /*
            DataChannel.Init-可配參數說明
            ordered: 是否保證順序傳輸
            maxRetransmitTimeMs: 重傳允許的最長時間
            maxRetransmits: 重傳允許的最大次數
             */
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            dc = pc.createDataChannel("dataChannel", init);
        }

        void sendDataChannelFile(File file) {
            Log.d(TAG,"file size: " + file.length());
            File xfile = new File(file.getPath());
            byte[] bytes = convertFileToByteArray(xfile);
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

            int size = (int)xfile.length();
            int numberOfChunks = size / CHUNK_SIZE;

            ByteBuffer name = stringToByteBuffer("-n" + xfile.getName(), Charset.defaultCharset());
            dc.send(new DataChannel.Buffer(name, false));
            ByteBuffer meta = stringToByteBuffer("-i" + size, Charset.defaultCharset());
            dc.send(new DataChannel.Buffer(meta, false));

            for (int i = 0; i < numberOfChunks; i++) {
                ByteBuffer wrap = ByteBuffer.wrap(bytes, i * CHUNK_SIZE, CHUNK_SIZE);
                dc.send(new DataChannel.Buffer(wrap, false));
            }
            int remainder = size % CHUNK_SIZE;
            if (remainder > 0) {
                ByteBuffer wrap = ByteBuffer.wrap(bytes, numberOfChunks * CHUNK_SIZE, remainder);
                dc.send(new DataChannel.Buffer(wrap, false));
            }

            // Set file name to show
            fileName = xfile.getName();

            DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, false);
            Log.d(TAG,"Before data channel send, buffer remaining: " + buffer.data.remaining());
            dc.send(buffer);
            Log.d(TAG,"After data channel send, buffer remaining: " + buffer.data.remaining());

            // Sending file over
            dc.close();
            dc.dispose();
            dc = null;
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            dc = pc.createDataChannel("dataChannel", init);
        }

        void sendDataChannelMessage(String message) {
            byte[] msg = message.getBytes();
            DataChannel.Buffer buffer = new DataChannel.Buffer(
                    ByteBuffer.wrap(msg),
                    false);
            dc.send(buffer);
        }

        private byte[] convertFileToByteArray(File file) {
            byte[] bArray = new byte[(int) file.length()];
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                fileInputStream.read(bArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bArray;
        }

        private void writeByteArrayToFile(String fileName, byte[] data){
            File file = new File(downloadDir + fileName);
            try{
                file.createNewFile();
            } catch(IOException ie) {
                ie.printStackTrace();
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(downloadDir + fileName);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                fos.write(data);
                Log.d(TAG,"file size: " + data.length + ", file path: " + file.getPath());
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void readIncomingMessage(ByteBuffer buffer) {
            byte[] bytes;
            if (buffer.hasArray()) {
                bytes = buffer.array();
            }
            else {
                bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
            }

            if (!receivingFile) {
                String firstMessage = new String(bytes, Charset.defaultCharset());
                Log.d(TAG, firstMessage);
                String type = firstMessage.substring(0, 2);

                if (type.equals("-n")) {
                    incomingFileName = firstMessage.substring(2, firstMessage.length());
                }
                else if (type.equals("-i")) {
                    incomingFileSize = Integer.parseInt(firstMessage.substring(2, firstMessage.length()));
                    imageFileBytes = new byte[incomingFileSize];
                    Log.d(TAG, "readIncomingMessage: incoming file size " + incomingFileSize);
                    receivingFile = true;

                    // Print received file message
                    if (webRTCListener != null) {
                        webRTCListener.onReceiveDataChannelMessage("Receive file: " + incomingFileName);
                    }
                }
                else {
                    // Receive message
                    String msg = new String(bytes);
                    if (webRTCListener != null) {
                        webRTCListener.onReceiveDataChannelMessage(msg);
                    }
                }
            }
            else {
                for (byte b : bytes) {
                    imageFileBytes[currentIndexPointer++] = b;
                }
                if (currentIndexPointer == incomingFileSize) {
                    Log.d(TAG, "readIncomingMessage: received all bytes");
                    receivingFile = false;
                    currentIndexPointer = 0;
                    writeByteArrayToFile(incomingFileName,imageFileBytes);

                    // Receiving file over
                    dc.close();
                    dc.dispose();
                    dc = null;
                    DataChannel.Init init = new DataChannel.Init();
                    init.ordered = true;
                    dc = pc.createDataChannel("dataChannel", init);
                    client.emit("init", roomName);
                }
            }
        }

        private ByteBuffer stringToByteBuffer(String msg, Charset charset) {
            return ByteBuffer.wrap(msg.getBytes(charset));
        }

        void release() {
            pc.dispose();
            dc.close();
            dc.dispose();
        }

        /* SdpObserver */
        @Override
        public void onCreateSuccess(SessionDescription sdp) {
            Log.d(TAG, "onCreateSuccess: " + sdp.description);
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", sdp.description);
                sendMessage(id, sdp.type.canonicalForm(), payload);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }

        /* DataChannel.Observer */
        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            Log.d(TAG, "onDataChannel onStateChange: " + dc.state());
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.d(TAG, "Incoming file on DataChannel");
            readIncomingMessage(buffer.data);
        }

        /* PeerConnection.Observer */
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "onIceConnectionChange: " + iceConnectionState.name());
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("label", candidate.sdpMLineIndex);
                payload.put("id", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);
                sendMessage(id, "candidate", payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel label: " + dataChannel.label());
            Log.d(TAG, "onDataChannel state: " + dataChannel.state());
            dataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {

                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "onStateChange() called, remote data channel state: " + dataChannel.state().toString());
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    Log.d(TAG, "Incoming file or msg on DataChannel");
                    Log.d(TAG, "onDataChannel state: " + dataChannel.state());
                    readIncomingMessage(buffer.data);
                }
            });
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    public interface WebRTCListener {
        void onReceiveDataChannelMessage(String message);
        void onRemotePeerLeave();
        void roomIsFull(String roomName);
        void initSuccess();
    }

}
