package com.tokbox.android.tutorials.videocall.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.view.View;

import com.opentok.android.BaseVideoRenderer;

import java.nio.ByteBuffer;

import ai.deepar.ar.DeepAR;

public class DeepArOpenTokRenderer extends BaseVideoRenderer {

    private final DeepAR deepAR;
    GLSurfaceView mRenderView;
    DeepARRenderer mRenderer;


    public DeepArOpenTokRenderer(Context context, DeepAR deepAR) {
        mRenderView = new GLSurfaceView(context);
        mRenderView.setEGLContextClientVersion(2);

        mRenderer = new DeepARRenderer(deepAR);
        mRenderView.setRenderer(mRenderer);
        this.deepAR = deepAR;
        mRenderView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onFrame(Frame frame) {
        ByteBuffer imageBuffer = frame.getBuffer();
        deepAR.receiveFrame(imageBuffer, frame.getWidth(), frame.getHeight(), 0, false);
        mRenderView.requestRender();
    }

    @Override
    public void setStyle(String key, String value) {
    }

    @Override
    public void onVideoPropertiesChanged(boolean videoEnabled) {
    }

    @Override
    public View getView() {
        return mRenderView;
    }

    @Override
    public void onPause() {
        mRenderView.onPause();
    }

    @Override
    public void onResume() {
        mRenderView.onResume();
    }
}