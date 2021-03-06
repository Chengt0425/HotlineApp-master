package com.example.chengt.Hotline;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class VideoActivity extends AppCompatActivity implements View.OnClickListener, VideoSignalingClient.VideoSignalingInterface {
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    AudioManager audioManager;

    int reset_audio_mode;
    boolean videoOn;

    List<SurfaceViewRenderer> viewslist = new ArrayList<>();
    FrameLayout viewframes;
    LinearLayout audioframes;
    EglBase rootEglBase = EglBase.create();
    int screenhight, screenwidth;

    Button hangup;
    List<PeerConnection> localPeers = new ArrayList<>();
    List<String> ID_list = new ArrayList<>();
    PeerConnection.RTCConfiguration rtcConfig;

    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    private static final String TAG = "VideoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        viewframes = findViewById(R.id.activity_main);

        //Enable changing the volume using the up/down keys during a conversation
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        //Change audio output to the speaker.
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean SpeakerEnable = getIntent().getBooleanExtra("speaker",false);
        if(SpeakerEnable) audioManager.setSpeakerphoneOn(true);
        else audioManager.setSpeakerphoneOn(false);

        reset_audio_mode = audioManager.getMode();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        //PeerConnection.IceServer = PeerConnection.IceServer.builder("your_turn_server_url_and_port").setUsername("user_name_you_set_on_server").setPassword("password_of_user_name").createIceServer()
        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("turn:140.113.167.181:3478").setUsername("turnserver").setPassword("turnserver").createIceServer();
        peerIceServers.add(peerIceServer);
        PeerConnection.IceServer peerIceServer_stun = PeerConnection.IceServer.builder("stun:140.113.167.181:3478").createIceServer();
        peerIceServers.add(peerIceServer_stun);
        PeerConnection.IceServer peerIceServer_stun2 = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
        peerIceServers.add(peerIceServer_stun2);

        Intent intent = this.getIntent();
        String signal_type = intent.getStringExtra("signaling");
        String data = intent.getStringExtra("data");
        String id = intent.getStringExtra("ip");
        VideoSignalingClient.getInstance().init(data, signal_type, id, this);

        //Init RTC configuration
        initConfiguration();

        // Setting screenwidth and screenhight
        GetScreenSize();

        // Get the previous selection of reference type and start setting
        videoOn = intent.getBooleanExtra("reference", true);
        if(videoOn) Video_Start();
        else {
            audioframes = findViewById(R.id.audio_frame);
            audioframes.setVisibility(View.VISIBLE);
            Audio_Start();
        }
    }

    private void Audio_Start() {
        //Create MediaConstraints - Will be useful for specifying audio constraints.
        audioConstraints = new MediaConstraints();

        //Create sdpConstraints.
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        //Create an AudioSource instance.
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        hangup = findViewById(R.id.end_call);
        hangup.setOnClickListener(this);
        addMyView();

        hangup.setEnabled(true);
        VideoSignalingClient.getInstance().Start();
    }

    private void Video_Start() {
        //Create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create sdpConstraints.
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        //Create a VideoSource instance.
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //Create an AudioSource instance.
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        //Start capturing the video from the camera.
        //width, height and fps
        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        hangup = findViewById(R.id.end_call);
        hangup.setOnClickListener(this);
        addMyView();

        hangup.setEnabled(true);
        VideoSignalingClient.getInstance().Start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.end_call: {
                Hangup(null);
                break;
            }
        }
    }

    @Override
    public void onRemoteHangUp(String id) {
        showToast("Remote peer hung up");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Hangup(id);
            }
        });
    }

    //Signaling Callback - called when remote peer sends offer.
    @Override
    public void onOfferReceived(final JSONObject data, String id) {
        if (ID_list.indexOf(id) == -1){
            //Not the room creator and have no peerconnection object
            createPeerConnection(id);
        }
        runOnUiThread(()->{
            try {
                int index = ID_list.indexOf(id);
                localPeers.get(index).setRemoteDescription(new VideoCustomSdpObserver("localSetRemoteDescription"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer(index);
                //updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    //Signaling Callback - called when remote peer sends answer to your offer.
    @Override
    public void onAnswerReceived(JSONObject data, String id) {
        Log.d("sdpflow", "Received answer from:"+id);
        try {
            int index = ID_list.indexOf(id);
            if(index == -1) showToast("Received illegal ID's answer:"+id);
            else localPeers.get(index).setRemoteDescription(new VideoCustomSdpObserver("localSetRemoteDescription"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            //updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //Remote Ice candidate received.
    @Override
    public void onIceCandidateReceived(JSONObject data, String id) {
        Log.d("sdpflow", "Receive IceCandidate");
        Log.d("sdpflow", data.toString());
        try {
            int index = ID_list.indexOf(id);
            if(index == -1) showToast("Received illegal ID's IceCandidate:"+id);
            else localPeers.get(index).addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data.
     */
    @Override
    public void onTryToStart(String id) {
        //If id is in ID_list, needn't start again
        if ((!videoOn || localVideoTrack != null) && VideoSignalingClient.getInstance().isChannelReady && ID_list.indexOf(id) == -1) {
            createPeerConnection(id);
            runOnUiThread(()->{
                doCall(id);
            });
        }
    }

    private void initConfiguration(){
        //Initialize PeerConnectionFactory Globals.
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).setEnableVideoHwAcceleration(true).createInitializationOptions());

        //Create a new PeerConnectionFactory instance.
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        // Set PC's ICE server
        // Log.d("iceserver", "ICE: " + peerIceServers.size());
        rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);

        //TCP candidates are only useful when connecting to a server that supports ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        //Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        //Enable DTLS-SRTP.
        rtcConfig.enableDtlsSrtp = true;
    }

    //Creating the local PeerConnection instance.
    private void createPeerConnection(String id) {
        ID_list.add(id);
        PeerConnection peer = peerConnectionFactory.createPeerConnection(rtcConfig, new VideoCustomPeerConnectionObserver("localPeerConnection"){
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                SendIceCandidate(iceCandidate, id);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received remote stream");
                super.onAddStream(mediaStream);
                GetRemoteStream(mediaStream);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
                if(iceConnectionState.toString().equalsIgnoreCase("failed")){
                    // When ICE connection is failed, it need to restart the connection
                    IceRestart(id);
                }
            }
        });

        //add local stream to localpeer
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        if(videoOn) stream.addTrack(localVideoTrack);
        peer.addStream(stream);
        localPeers.add(peer);
    }

    //close the peerconnection with id and remove it
    private void Hangup(String id) {
        try {
            //hangup by local
            if(id == null) {
                for(PeerConnection peers : localPeers) peers.close();
                localPeers.clear();
                VideoSignalingClient.getInstance().close();
                SettingReset();
                finish();
            } else{ //hangup by one of remote
                int bye_index = ID_list.indexOf(id);
                ID_list.remove(bye_index);
                CloseViews(bye_index+1);
                localPeers.get(bye_index).close();
                localPeers.remove(bye_index);

                //When no others in room, close the room
                if(localPeers.isEmpty()){
                    CloseViews(0);
                    VideoSignalingClient.getInstance().close();
                    SettingReset();
                    finish();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Received remote peer's media stream. We will get the first video track and render it.
    private void GetRemoteStream(MediaStream stream) {
        //We have remote video stream. Add to the render.
        if(stream.videoTracks.size() > 0) {
            final VideoTrack videoTrack = stream.videoTracks.get(0);
            runOnUiThread(() -> {
                try {
                    int peers = viewslist.size();
                    addOthersViews(peers);
                    adjustViewsLayout(peers);
                    videoTrack.addSink(viewslist.get(peers));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } else {
            // Audio-only mode
            runOnUiThread(() -> {
                try {
                    int peers = viewslist.size();
                    addOthersViews(peers);
                    adjustViewsLayout(peers);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }

    //Received local ice candidate. Send it to remote peer through signaling for negotiation.
    private void SendIceCandidate(IceCandidate iceCandidate, String id) {
        //We have received ice candidate We can set it to the other peer.
        VideoSignalingClient.getInstance().emitIceCandidate(iceCandidate, id);
    }

    private void addMyView() {
        SurfaceViewRenderer surfaceviewrenderer = new SurfaceViewRenderer(this);
        surfaceviewrenderer.init(rootEglBase.getEglBaseContext(), null);

        // Video conference mode
        if(videoOn) {
            surfaceviewrenderer.setVisibility(View.VISIBLE);

            FrameLayout.LayoutParams lp;
            lp = new FrameLayout.LayoutParams(dpToPx(120), dpToPx(150));
            lp.gravity = Gravity.BOTTOM|Gravity.END;
            surfaceviewrenderer.setLayoutParams(lp);
            surfaceviewrenderer.setZOrderMediaOverlay(true);
            surfaceviewrenderer.setMirror(true);

            localVideoTrack.addSink(surfaceviewrenderer);
        }

        viewslist.add(surfaceviewrenderer);
        viewframes.addView(surfaceviewrenderer);
    }

    private void addOthersViews(final int index) {
        SurfaceViewRenderer surfaceviewrenderer = new SurfaceViewRenderer(this);
        surfaceviewrenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceviewrenderer.setVisibility(View.VISIBLE);

        viewslist.add(surfaceviewrenderer);
        viewframes.addView(surfaceviewrenderer);
    }

    //Configure the position of screen
    private void adjustViewsLayout(int num) {
        switch (num) {
            case 1: {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(screenwidth,screenhight);
                viewslist.get(1).setLayoutParams(lp);
                break;
            }
            case 2: {
                int adjustHight = screenhight/2;
                for(int i=1; i<=num; i++) {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(screenwidth,adjustHight);
                    if(i==1) lp.gravity = Gravity.TOP;
                    else if(i==2) lp.gravity = Gravity.BOTTOM;
                    viewslist.get(i).setLayoutParams(lp);
                }
                break;
            }
            case 3: {
                int adjustHight = screenhight/2;
                int adjustWidth = screenwidth/2;
                for(int i=1; i<=num; i++) {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(adjustWidth,adjustHight);
                    if(i==1) lp.gravity = Gravity.TOP | Gravity.START;
                    else if(i==2) lp.gravity = Gravity.TOP | Gravity.END;
                    else {
                        lp.width = screenwidth;
                        lp.gravity = Gravity.BOTTOM;
                    }
                    viewslist.get(i).setLayoutParams(lp);
                }
                break;
            }
        }
    }

    private void CloseViews(final int index){
        viewframes.removeViewAt(index);
        viewslist.remove(index);
        if(viewslist.size() > 1) adjustViewsLayout(viewslist.size()-1);
    }

    /**
     * This method is called when the app is initiator.
     * We generate the offer and send it over through socket to remote peer.
     */
    private void doCall(String id) {
        int index = ID_list.indexOf(id);
        localPeers.get(index).createOffer(new VideoCustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeers.get(index).setLocalDescription(new VideoCustomSdpObserver("localSetLocalDescription"), sessionDescription);
                VideoSignalingClient.getInstance().emitMessage(sessionDescription, id, ID_list.size());
            }
        }, sdpConstraints);
    }

    private void doAnswer(int index) {
        localPeers.get(index).createAnswer(new VideoCustomSdpObserver("localCreateAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeers.get(index).setLocalDescription(new VideoCustomSdpObserver("localSetLocalDescription"), sessionDescription);
                VideoSignalingClient.getInstance().emitMessage(sessionDescription, ID_list.get(index), ID_list.size());
            }
        }, new MediaConstraints());
    }

    //SignalingCallback - called when the room is created.
    //i.e. you are the initiator.
    @Override
    public void onCreatedRoom() {
        showToast("You created the room");
    }

    //SignalingCallback - called when you join the room.
    //i.e. you are a participant.
    @Override
    public void onJoinedRoom() {
        showToast("You joined the room");
    }

    @Override
    public void onNewPeerJoined() {
        showToast("Remote peer joined");
    }

    @Override
    public void onReplaceID(String oid, String nid) {
        int index = ID_list.indexOf(oid);
        ID_list.set(index, nid);
    }

    @Override
    public void onGetIPList(String id) {
        VideoSignalingClient.getInstance().emitMessage(ID_list, id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VideoSignalingClient.getInstance().close();
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        //Try to find front facing camera.
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Create front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) return videoCapturer;
            }
        }

        //Front facing camera not found, try something else.
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) return videoCapturer;
            }
        }

        //Can't find any camera.
        return null;
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    @Override
    public void showToast(final String msg) {
        runOnUiThread(() ->
                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    private void GetScreenSize() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenwidth = size.x;
        screenhight = size.y;
    }

    // When IceConnection state change to "Failed", call this function to restart ICE
    public void IceRestart(final String id){
        final int index = ID_list.indexOf(id);

        MediaConstraints restartCon = sdpConstraints;
        restartCon.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));

        localPeers.get(index).createOffer(new VideoCustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeers.get(index).setLocalDescription(new VideoCustomSdpObserver("localSetLocalDescription"), sessionDescription);
                VideoSignalingClient.getInstance().emitMessage(sessionDescription, id, ID_list.size());
            }
        }, restartCon);
    }

    private void SettingReset(){
        audioManager.setSpeakerphoneOn(true);
        audioManager.setMode(reset_audio_mode);
    }
}
