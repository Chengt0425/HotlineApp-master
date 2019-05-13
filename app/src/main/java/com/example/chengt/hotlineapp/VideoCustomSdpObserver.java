package com.example.chengt.hotlineapp;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class VideoCustomSdpObserver implements SdpObserver {
    private String logTag;

    VideoCustomSdpObserver(String logTag) {
        this.logTag = this.getClass().getCanonicalName() + " " + logTag;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(logTag, "onCreateSuccess() called with: sessionDescription = [" + sessionDescription + "]");
    }

    @Override
    public void onSetSuccess() {
        Log.d(logTag, "onSetSuccess() called");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.d(logTag, "onCreateFailure() called with: s = [" + s + "]");
    }

    @Override
    public void onSetFailure(String s) {
        Log.d(logTag, "onSetFailure() called with: s = [" + s + "]");
    }
}
