package com.example.chengt.hotlineapp;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;

public class VideoSignalingClient {
    private static VideoSignalingClient instance;
    private String roomName = null;
    private Socket socket;
    boolean isChannelReady = false; // Does room have two or more peers?
    boolean isInitiator = false; // Have room been created?
    boolean isStarted = false; // Have peerconnection been created?
    boolean isInRoom = false; // Is in the any room?
    private VideoSignalingInterface callback;

    //This piece of code should not go into production!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings.
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }};

    public static VideoSignalingClient getInstance() {
        if (instance == null) {
            instance = new VideoSignalingClient();
        }
        return instance;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public void init(VideoSignalingInterface videoSignalingInterface) {
        this.callback = videoSignalingInterface;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, null);
            OkHttpClient okHttpClient = new OkHttpClient.Builder().hostnameVerifier((hostname, session) -> true).sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]).build();
            // default settings for all sockets
            IO.setDefaultOkHttpCallFactory(okHttpClient);
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient);
            //Set the socket.io url here.
            /*socket = IO.socket("your_socket_io_instance_url_with_port");*/
            socket = IO.socket("https://192.168.1.103:1794");
            socket.connect();
            Log.d("VideoSignalingClient", "init() called");

            if (!roomName.isEmpty()) {
                emitInitStatement(roomName);
            }

            // room created event
            socket.on("created", args -> {
                Log.d("VideoSignalingClient", "created call() called with: args =[" + Arrays.toString(args) + "]");
                isInitiator = true;
                isInRoom = true;
                callback.onCreatedRoom();
            });

            // room is full event
            socket.on("full", args -> {
                Log.d("VideoSignalingClient", "full call() called with: args = [" + Arrays.toString(args) + "]");
                callback.roomIsFull(roomName);
            });

            // peer joined event
            socket.on("join",  args -> {
                Log.d("VideoSignalingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                callback.onNewPeerJoined();
            });

            // when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.d("VideoSignalingClient", "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                isInRoom = true;
                callback.onJoinedRoom();
            });

            // log event
            socket.on("log", args ->
                    Log.d("VideoSignalingClient", "log call() called with: args = [" + Arrays.toString(args) + "]")
            );

            // bye event
            socket.on("bye", args -> {
                Log.d("VideoSignalingClient", "bye call() called with: args = [" + Arrays.toString(args) + "]");
                callback.onRemoteHangUp((String) args[0], (String) args[1]);
            });

            // messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.d("VideoSignalingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d("VideoSignalingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    String id = (String) args[1];
                    if (data.equalsIgnoreCase("got user media")) {
                        callback.onTryToStart(id);
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data, id);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {
                        JSONObject data = (JSONObject) args[0];
                        String id = (String) args[1];
                        Log.d("VideoSignalingClient", "JSONObject received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data, id);
                        } else if (type.equalsIgnoreCase("answer")) {
                            callback.onAnswerReceived(data, id);
                        } else if (type.equalsIgnoreCase("candidate")) {
                            callback.onIceCandidateReceived(data, id);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.d("VideoSignalingClient", "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        socket.emit("create or join", message);
    }

    public void emitMessage(String message) {
        Log.d("VideoSignalingClient", "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", roomName, message);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d("VideoSignalingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject object = new JSONObject();
            object.put("type", message.type.canonicalForm());
            object.put("sdp", message.description);
            Log.d("emitMessage", object.toString());
            socket.emit("message", roomName, object);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", roomName, object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (socket != null) {
            if (isInRoom) {
                socket.emit("bye", roomName);
            }
            socket.disconnect();
            socket.close();
        }
        instance = null;
    }

    interface VideoSignalingInterface {
        void onRemoteHangUp(String msg, String id);
        void onOfferReceived(JSONObject data, String id);
        void onAnswerReceived(JSONObject data, String id);
        void onIceCandidateReceived(JSONObject data, String id);
        void onTryToStart(String id);
        void onCreatedRoom();
        void onJoinedRoom();
        void onNewPeerJoined();
        void roomIsFull(String roomName);
    }
}
