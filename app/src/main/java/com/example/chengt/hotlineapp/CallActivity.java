package com.example.chengt.hotlineapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    Button call;
    Switch speaker;
    boolean SpeakerEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        EditText editText_room = findViewById(R.id.room);

        /*
        Intent intent = this.getIntent();
        String roomName = intent.getStringExtra("room");
        editText_room.setText(R.string.room);
        */

        getLocalIpAddress();

        call = findViewById(R.id.call);
        speaker = findViewById(R.id.switch1);

        //Ask for permissions.
        int cmrpermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int rcdpermission = ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO);
        if (cmrpermission != PackageManager.PERMISSION_GRANTED && rcdpermission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(CallActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1);
        }

        call.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string_room = editText_room.getText().toString();

                // Check whether the room name is empty
                if (string_room.isEmpty()) {
                    showToast("The room name can't be empty.");
                    return;
                }

                RadioGroup radioGroup = findViewById(R.id.callType);
                Intent intent = new Intent();
                if (radioGroup.getCheckedRadioButtonId() == R.id.radio_video) {
                    intent.setClass(CallActivity.this, VideoActivity.class);
                    intent.putExtra("room", string_room);
                    intent.putExtra("speaker",SpeakerEnable);
                    startActivity(intent);
                }
                else {
                    intent.setClass(CallActivity.this, TextActivity.class);
                    intent.putExtra("room", string_room);
                    intent.putExtra("speaker",SpeakerEnable);
                    startActivity(intent);
                }
            }
        });

        speaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    SpeakerEnable = true;
                }
                else SpeakerEnable = false;
            }
        });
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(CallActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        Log.d("addr",inetAddress.getHostAddress());
                        //return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
