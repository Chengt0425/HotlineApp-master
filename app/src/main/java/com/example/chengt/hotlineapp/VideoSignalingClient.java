package com.example.chengt.hotlineapp;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;

class VideoSignalingClient {
    private static VideoSignalingClient instance;
    private String signaling = null;
    private String identification = null;
    boolean isChannelReady = false; // Does room have two or more peers?
    //boolean isInitiator = false; // Have room been created?
    //boolean isStarted = false; // Have peerconnection been created?
    boolean isInRoom = false; // Is in the any room?
    private VideoSignalingInterface callback;

    //For server sognaling
    private Socket socket;
    private String roomName = null;


    //For direct signaling
    private ServerSocket myServerSocket;
    private boolean isListening = false;
    private Map<String, PrintStream> streamMap = new HashMap<>();
    private List<java.net.Socket> socketList = new ArrayList<>();
    private String remoteIP = null;

    private final String TAG = "VideoSignalingClient";

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

    static VideoSignalingClient getInstance() {
        if (instance == null) {
            instance = new VideoSignalingClient();
        }
        return instance;
    }

    void init(String data, String signal, String id, VideoSignalingInterface videoSignalingInterface){
        signaling = signal;
        this.callback = videoSignalingInterface;

        if(signaling.equals("direct")) {
            remoteIP = data;
            identification = id;
        }
        else {
            roomName = data;
        }
    }

    void Start() {
        if(signaling.equals("direct")) {
            Signal_by_Direct();
        }
        else {
            Signal_by_Server();
        }
    }

    private void Signal_by_Direct() {
        ServerThread serverThread = new ServerThread();
        ClientThread clientThread;
        serverThread.start();
        if(!remoteIP.isEmpty()) {
            clientThread = new ClientThread();
            clientThread.start();
        }
    }

    private void Signal_by_Server() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, null);
            OkHttpClient okHttpClient = new OkHttpClient.Builder().hostnameVerifier((hostname, session) -> true).sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]).build();

            // default settings for all sockets
            IO.setDefaultOkHttpCallFactory(okHttpClient);
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient);

            //Set the socket.io url here.
            /*socket = IO.socket("your_socket_io_instance_url_with_port");*/
            socket = IO.socket("https://140.113.167.179:1794");
            socket.connect();
            Log.d(TAG, "init() called");

            if (!roomName.isEmpty()) {
                emitInitStatement(roomName);
            }

            // room created event
            socket.on("created", args -> {
                Log.d(TAG, "created call() called with: args =[" + Arrays.toString(args) + "]");
                //isInitiator = true;
                isInRoom = true;
                identification = (String)args[0];
                Log.d(TAG, "identification:"+identification);
                callback.onCreatedRoom();
            });

            // peer joined event
            socket.on("join",  args -> {
                Log.d(TAG, "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                callback.onNewPeerJoined();
            });

            // when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.d(TAG, "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                isInRoom = true;
                identification = (String)args[0];
                Log.d(TAG, "socketid:"+identification);
                callback.onJoinedRoom();

                //Inform everyone that I'm in
                emitMessage("handshake request");
            });

            // log event
            socket.on("log", args ->
                    Log.d(TAG, "log call() called with: args = [" + Arrays.toString(args) + "]")
            );

            // bye event
            socket.on("bye", args -> {
                Log.d(TAG, "bye call() called with: args = [" + Arrays.toString(args) + "]");
                String sid = (String) args[0];
                callback.onRemoteHangUp(sid);
            });

            // messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.d(TAG, "message call() called with: args = [" + Arrays.toString(args) + "]");
                try {
                    JSONObject data = (JSONObject) args[0];
                    Log.d(TAG, "JSONObject received :: " + data.toString());
                    String type = data.getString("type");
                    String sid = data.getString("sender");
                    if(type.equalsIgnoreCase("message")) {
                        callback.onTryToStart(sid);
                    }
                    else {
                        String rid = data.getString("receiver");
                        if (type.equalsIgnoreCase("offer") && rid.equals(identification)) {
                            callback.onOfferReceived(data, sid);
                        } else if (type.equalsIgnoreCase("answer") && rid.equals(identification)) {
                            callback.onAnswerReceived(data, sid);
                        } else if (type.equalsIgnoreCase("candidate") && rid.equals(identification)) {
                            callback.onIceCandidateReceived(data, sid);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.d(TAG, "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        socket.emit("create or join", message);
    }

    void emitMessage(String message) {
        Log.d(TAG, "emitMessage() called with: message = [" + message + "]");

        JSONObject object = new JSONObject();
        try {
            object.put("type", "message");
            object.put("sender", identification);
            object.put("msg", message);
        } catch (JSONException e) {
            e.toString();
        }
        socket.emit("message", roomName, object);
    }

    void emitMessage(SessionDescription message, String recvid, int num) {
        JSONObject object = new JSONObject();
        try {
            Log.d(TAG, "emitMessage() called with: message = [" + message + "]");
            object.put("type", message.type.canonicalForm());
            object.put("sender", identification);
            object.put("receiver", recvid);
            object.put("sdp", message.description);
            if(num == 1) {
                object.put("newer", "y");
            }
            else {
                object.put("newer", "n");
            }
            Log.d("emitMessage", object.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(signaling.equals("direct")) {
            streamMap.get(recvid).println(object.toString());
            streamMap.get(recvid).flush();
        }
        else {
            socket.emit("message", roomName, object);
        }
    }

    void emitMessage(List<String> list, String recvid) {
        JSONObject object = new JSONObject();
        try {
            object.put("type", "participants");
            object.put("receiver", recvid);
            object.put("participants", new JSONArray(list));
        }
        catch (JSONException e) {
            Log.d(TAG, e.toString());
        }
        streamMap.get(recvid).println(object.toString());
        streamMap.get(recvid).flush();
    }

    void emitIceCandidate(IceCandidate iceCandidate, String recvid) {
        JSONObject object = new JSONObject();
        try {
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("sender", identification);
            object.put("receiver", recvid);
            object.put("candidate", iceCandidate.sdp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(signaling.equals("direct")) {
            streamMap.get(recvid).println(object.toString());
            streamMap.get(recvid).flush();
        }
        else {
            socket.emit("message", roomName, object);
        }
    }

    void close() {
        //Client hasn't init
        //or hangout had been pushed and onDestroy call close() again
        if(signaling == null) return;

        if(signaling.equals("direct")) {
            try {
                isListening = false;
                myServerSocket.close();
                if(isChannelReady) {
                    for(java.net.Socket s : socketList) {
                        s.close();
                    }
                }
            }
            catch (IOException e) {
                Log.d(TAG, e.toString());
            }
        }
        else {
            if (socket != null) {
                if (isInRoom) {
                    socket.emit("bye", roomName);
                }
                socket.disconnect();
                socket.close();
            }
        }
        instance = null;
    }

    private class ServerThread extends Thread {
        final String ServerTAG = "VideoSignalingClient_S";

        private ExecutorService myExecutor = null;
        java.net.Socket dsocket;

        ServerThread() {
            isListening = true;
        }

        @Override
        public void run() {
            try {
                myServerSocket = new ServerSocket(7777);
            }
            catch(IOException e) {
                Log.d(ServerTAG,e.toString());
            }

            myExecutor = Executors.newCachedThreadPool();

            while(isListening) {
                try {
                    dsocket = myServerSocket.accept();
                    if(!isListening) break;
                    Log.d(ServerTAG, "Accept connection");

                    socketList.add(dsocket);
                    myExecutor.execute(new ProcessMessage(dsocket));
                }
                catch(IOException e) {
                    Log.d(ServerTAG,e.toString());
                }
            }
        }
    }

    private class ClientThread extends Thread {
        final String ClientTAG = "VideoSignalingClient_C";

        private String IP;
        java.net.Socket dsocket;

        ClientThread() {
            IP = remoteIP;
        }

        ClientThread(String ip) {
            IP = ip;
        }

        @Override
        public void run() {
            try {
                dsocket = new java.net.Socket(IP, 7777);
            }
            catch(IOException e) {
                Log.d(ClientTAG, e.toString());
            }
            if(dsocket!=null) {
                Log.d(ClientTAG, "Connect " + IP + " success");

                socketList.add(dsocket);
                new ProcessMessage(dsocket, IP).run();
            }
            else {
                callback.showToast("Fail to connect " + IP);
            }
        }
    }

    private class ProcessMessage implements Runnable {
        private java.net.Socket socket;
        private PrintStream sender;
        private BufferedReader receiver;
        private String IP = null;

        ProcessMessage(java.net.Socket dsocket) {
            socket = dsocket;
            isChannelReady = true;
        }

        ProcessMessage(java.net.Socket dsocket, String ip) {
            socket = dsocket;
            isChannelReady = true;
            IP = ip;
        }

        public void run() {
            try {
                Log.d(TAG, "Start processing messages");
                sender = new PrintStream(socket.getOutputStream());
                receiver = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                //Active side send offer to passive side
                if(IP != null){
                    streamMap.put(IP, sender);
                    callback.onTryToStart(IP);
                }

                while(true) {
                    String s = receiver.readLine();

                    //remote is closed the socket connect to here
                    if(s == null) {
                        callback.onRemoteHangUp(IP);
                        break;
                    }
                    else {
                        JSONObject object = new JSONObject(s);
                        Log.d(TAG, "JSONObject received :: " + object.toString());

                        String type = object.getString("type");
                        if(type.equals("participants")) {
                            JSONArray Jarray = object.getJSONArray("participants");
                            Log.d(TAG, "JSONArray recieved :: " + Jarray.toString());
                            for(int i=0; i<Jarray.length(); i++) {
                                String ip = Jarray.getString(i);
                                if(!ip.equals(identification)) {
                                    Log.d("Jarray", "IP : " + ip);
                                    ClientThread clientThread = new ClientThread(ip);
                                    clientThread.start();
                                }
                            }
                        }
                        else if(type.equals("bye")) {
                            callback.onRemoteHangUp(IP);
                            break;
                        }
                        else {
                            String rid = object.getString("receiver");
                            if(!rid.equals(identification)) {
                                continue;
                            }
                            if(type.equals("candidate")) {
                                callback.onIceCandidateReceived(object, IP);
                            }
                            else if(type.equals("offer")) {
                                IP = object.getString("sender");
                                streamMap.put(IP, sender);
                                callback.onOfferReceived(object, IP);
                                if(object.getString("newer").equals("y")) {
                                    callback.onGetIPList(IP);
                                }
                            }
                            else if(type.equals("answer")) {
                                callback.onAnswerReceived(object, IP);
                            }
                        }
                    }
                }
            }
            catch(IOException|JSONException e) {
                Log.d(TAG, e.toString());
            }
        }
    }

    interface VideoSignalingInterface {
        void onRemoteHangUp(String id);
        void onOfferReceived(JSONObject data, String id);
        void onAnswerReceived(JSONObject data, String id);
        void onIceCandidateReceived(JSONObject data, String id);
        void onTryToStart(String id);
        void onCreatedRoom();
        void onJoinedRoom();
        void onNewPeerJoined();
        void onGetIPList(String id);
        void roomIsFull(String roomName);
        void showToast(String msg);
    }
}
