package com.tokbox.android.tutorials.videocall.camera;

public interface CameraGrabberListener {
    void onCameraInitialized();
    void onCameraError(String errorMsg);
}
