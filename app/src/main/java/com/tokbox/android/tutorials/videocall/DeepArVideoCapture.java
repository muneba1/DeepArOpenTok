package com.tokbox.android.tutorials.videocall;

import android.content.Context;

import com.opentok.android.BaseVideoCapturer;

import org.otwebrtc.CapturerObserver;
import org.otwebrtc.SurfaceTextureHelper;
import org.otwebrtc.VideoCapturer;

public class DeepArVideoCapture extends BaseVideoCapturer {
    boolean captureStarted = false;

    @Override
    public void init() {

    }

    @Override
    public int startCapture() {
        captureStarted = true;
        return 0;
    }

    @Override
    public int stopCapture() {
        captureStarted = false;
        return 0;
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean isCaptureStarted() {
        return captureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        return null;
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }
}
