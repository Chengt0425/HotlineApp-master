package com.example.chengt.hotlineapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.webrtc.PeerConnectionFactory;

import java.io.File;

public class TextActivity extends AppCompatActivity implements View.OnClickListener, TextWebRTCClient.WebRTCListener {
    private static final int REQUEST_EXTERNAL_STORAGE = 200;

    private EditText editText;
    private TextWebRTCClient rtcClient;

    /* file */
    private MagicFileChooser chooser;
    private File file;

    /* message */
    private TextMessageAdapter messageAdapter;
    private ListView messagesView;

    private final static String TAG = "TextActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // 未取得權限，向使用者要求允許權限
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }

        int permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission2 != PackageManager.PERMISSION_GRANTED) {
            // 未取得權限，向使用者要求允許權限
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).setEnableVideoHwAcceleration(true).createInitializationOptions());
        PeerConnectionFactory factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        Intent intent = this.getIntent();
        String roomName = intent.getStringExtra("room");

        rtcClient = new TextWebRTCClient(factory, roomName);
        rtcClient.setWebRTCListener(this);

        /* file */
        chooser = new MagicFileChooser(this);

        /* message */
        editText = findViewById(R.id.input_text);
        messageAdapter = new TextMessageAdapter(this);
        messagesView = findViewById(R.id.messages_view);
        messagesView.setAdapter(messageAdapter);

        findViewById(R.id.btn_send_text).setOnClickListener(this);
        findViewById(R.id.btn_choose_file).setOnClickListener(this);
        findViewById(R.id.btn_send_file).setOnClickListener(this);

        findViewById(R.id.btn_send_file).setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!rtcClient.isRemotePeerLeft) {
            rtcClient.release();
        }
    }

    private void showSendMessage(String message) {
        if (message.length() > 0) {
            final TextMessage send_message = new TextMessage(message, true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageAdapter.add(send_message);
                    messagesView.setSelection(messagesView.getCount() - 1);
                }
            });
        }
    }

    private void showReceiveMessage(String message) {
        final TextMessage receive_message = new TextMessage(message, false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageAdapter.add(receive_message);
                messagesView.setSelection(messagesView.getCount() - 1);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_send_text:
                Log.d(TAG, "Message transfer");
                String message = editText.getText().toString();
                rtcClient.sendDataMessageToAllPeer(message);
                showSendMessage(message);
                editText.getText().clear();
                break;
            case R.id.btn_choose_file:
                Log.d(TAG,"choose file button pressed");
                boolean isThereChooser = this.chooser.showFileChooser("*/*", null, false, true);
                // "*/*"-所有file類型都可以選
                if (!isThereChooser) {
                    Log.d(TAG, "No chooser!");
                }
                break;
            case R.id.btn_send_file:
                Log.d(TAG,"File transfer");
                rtcClient.sendDataFileToAllPeer(this.file);
                showSendMessage("Send file: " + rtcClient.fileName);
                findViewById(R.id.btn_send_file).setEnabled(false);
                break;
            default:
                break;
        }
    }

    @Override
    public void onReceiveDataChannelMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showReceiveMessage(message);
            }
        });
    }

    @Override
    public void onRemotePeerLeave() {
        runOnUiThread(() ->
                Toast.makeText(TextActivity.this, "Remote Peer Leave", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void roomIsFull(String roomName) {
        runOnUiThread(() ->
                Toast.makeText(TextActivity.this, "Room " + roomName + " is full", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void initSuccess() {
        runOnUiThread(() ->
                Toast.makeText(TextActivity.this, "Init Success", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (this.chooser.onActivityResult(requestCode, resultCode, data))
        {
            File[] all_files = this.chooser.getChosenFiles();
            if (all_files.length > 0)
            {
                this.file = all_files[0];
                Log.d(TAG, this.file.getPath());
                runOnUiThread(() ->
                        Toast.makeText(TextActivity.this, "Success in choosing file", Toast.LENGTH_SHORT).show()
                );
                findViewById(R.id.btn_send_file).setEnabled(true);
            }
            else {
                Log.d(TAG,"Doesn't get a file");
            }
        }
    }

}
