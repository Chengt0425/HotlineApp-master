package com.example.chengt.hotlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    Button call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        EditText editText_room = findViewById(R.id.room);

        Intent intent = this.getIntent();
        String roomName = intent.getStringExtra("room");
        editText_room.setText(roomName);

        call = findViewById(R.id.call);

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
                    startActivity(intent);
                }
                else {
                    intent.setClass(CallActivity.this, TextActivity.class);
                    intent.putExtra("room", string_room);
                    startActivity(intent);
                }
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
}
