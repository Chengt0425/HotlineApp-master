package com.example.chengt.hotlineapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    Button call, start_record, stop_record;
    Switch speaker;
    EditText input_text;
    TextView input_type;
    TextView ip_text;
    boolean SpeakerEnable = true;

    boolean isRecording = false;
    private MediaRecorder myrecorder;
    private Handler myhandler = new Handler();
    public static final int INTERVAL = 40;
    List<Integer> amplitudes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        input_text = findViewById(R.id.input_text);
        input_type = findViewById(R.id.input_type);
        ip_text = findViewById(R.id.ip_addr);
        call = findViewById(R.id.call);
        /*
        start_record = findViewById(R.id.start_record);
        stop_record = findViewById(R.id.stop_record);
        */
        speaker = findViewById(R.id.switch1);



        String local_ip = getLocalIpAddress();

        //Ask for permissions.
        int cmrpermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int rcdpermission = ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO);
        //int writepermission = ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        /*
        if (cmrpermission != PackageManager.PERMISSION_GRANTED &&
                rcdpermission != PackageManager.PERMISSION_GRANTED &&
                writepermission != PackageManager.PERMISSION_GRANTED
            ){
            ActivityCompat.requestPermissions(CallActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }
        */
        if (cmrpermission != PackageManager.PERMISSION_GRANTED &&
                rcdpermission != PackageManager.PERMISSION_GRANTED
        ){
            ActivityCompat.requestPermissions(CallActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    1);
        }

        call.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string_content = input_text.getText().toString();

                RadioGroup callType = findViewById(R.id.callType);
                RadioGroup signalType = findViewById(R.id.signal_type);
                Intent intent = new Intent();

                //Get call type from radio group
                if (callType.getCheckedRadioButtonId() == R.id.radio_video) {
                    intent.setClass(CallActivity.this, VideoActivity.class);
                }
                else {
                    intent.setClass(CallActivity.this, TextActivity.class);
                }

                //Get signaling type from radio group
                if (signalType.getCheckedRadioButtonId() == R.id.by_direct) {
                    intent.putExtra("signaling", "direct");
                    intent.putExtra("ip", local_ip);

                    // If IP is empty, it's waiting for peer connecting me
                    if(string_content.isEmpty()) {
                        showToast("Waiting for someone's connection");
                    }
                }
                else {
                    intent.putExtra("signaling", "server");

                    // Check whether the room name is empty
                    if(string_content.isEmpty()) {
                        showToast("The room name can't be empty.");
                        return;
                    }
                }

                intent.putExtra("speaker", SpeakerEnable);
                intent.putExtra("data",string_content);

                startActivity(intent);
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

        /*
        start_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                createFolder();

                myrecorder = new MediaRecorder();
                myrecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                myrecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                myrecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
                myrecorder.setOutputFile(Environment.getExternalStorageDirectory()+"/Recordings/test.3gp");
                try{
                    myrecorder.prepare();
                    myrecorder.start();

                    findViewById(R.id.stop_record).setEnabled(true);
                    findViewById(R.id.start_record).setEnabled(false);

                    isRecording = true;
                    myhandler.post(addAmplitude);
                }
                catch (IllegalStateException e){
                    e.printStackTrace();
                    findViewById(R.id.stop_record).setEnabled(false);
                    findViewById(R.id.start_record).setEnabled(true);
                }
                catch (IOException e){
                    e.printStackTrace();
                    findViewById(R.id.stop_record).setEnabled(false);
                    findViewById(R.id.start_record).setEnabled(true);
                }
            }
        });

        stop_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (null != myrecorder) {
                        myrecorder.stop();
                        myrecorder.release();
                        myrecorder = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                findViewById(R.id.start_record).setEnabled(true);
                findViewById(R.id.stop_record).setEnabled(false);

                float sum = 0;
                for (int i : amplitudes) {
                    sum += i;
                }
                Log.d("amplitude", "average:" + sum/amplitudes.size());
                amplitudes.clear();

                isRecording = false;
                myhandler.removeCallbacks(addAmplitude);
            }
        });
        */
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(CallActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        Log.d("addr",inetAddress.getHostAddress());
                        ip_text.setText("IP: "+inetAddress.getHostAddress());
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /*
    Runnable addAmplitude = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                int x = myrecorder.getMaxAmplitude();
                Log.d("amplitude", "amplitude:"+x);
                amplitudes.add(x);

                myhandler.postDelayed(this, INTERVAL);
            }
        }
    };

    void createFolder() {
        if (!new File(Environment.getExternalStorageDirectory()+"/Recordings").exists()) {
            new File(Environment.getExternalStorageDirectory()+"/Recordings").mkdir();
        }
    }
    */

    public void onSelect(View view) {
        switch(view.getId()) {
            case R.id.by_server: {
                input_type.setText("Room Name :");
                input_text.setText("");
                break;
            }
            case R.id.by_direct: {
                input_type.setText("Remote IP :");
                input_text.setText("");
                break;
            }
        }
    }
}
