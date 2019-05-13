package com.example.chengt.hotlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    ArrayList<String> membersAccount;
    ArrayList<String> membersPassword;
    Button login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        membersAccount = new ArrayList<>();
        membersPassword = new ArrayList<>();

        // Connect to database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference databaseReference = database.getReference("accounts");

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                membersAccount.clear();
                membersPassword.clear();
                for (DataSnapshot ds : dataSnapshot.getChildren()) {
                    Log.w(TAG, "account:"+ds.child("account").getValue().toString());
                    Log.w(TAG, "account:"+ds.child("password").getValue().toString());
                    membersAccount.add(ds.child("account").getValue().toString());
                    membersPassword.add(ds.child("password").getValue().toString());
                }
                Log.w(TAG, "Total account:"+membersAccount.size());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        login = findViewById(R.id.login);

        login.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText_Account = findViewById(R.id.account);
                EditText editText_Password = findViewById(R.id.password);
                String string_Account = editText_Account.getText().toString();
                String string_Password = editText_Password.getText().toString();
                String string_Password_Hashed = "";

                // Check whether the account or the password is empty
                if (string_Account.isEmpty() || string_Password.isEmpty()) {
                    showToast("The account or the password can't be empty.");
                    return;
                }

                // Check whether the account exists
                int index = membersAccount.indexOf(string_Account);
                if (index == -1) {
                    showToast("The account doesn't exists.");
                    return;
                }

                // Hash the password by SHA-512
                try {
                    MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                    byte[] digest = messageDigest.digest(string_Password.getBytes());
                    StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 0; i < digest.length; i++) {
                        stringBuilder.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
                    }
                      string_Password_Hashed = stringBuilder.toString();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

                // Check whether the password of the account is correct
                if (string_Password_Hashed.equals(membersPassword.get(index))) {
                    showToast("Login Success!");
                    Intent intent = new Intent();
                    intent.setClass(MainActivity.this, CallActivity.class);
                    startActivity(intent);
                }
                else {
                    showToast("The password is incorrect.");
                }
            }
        });
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
