package com.tokbox.android.tutorials.videocall.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.view.View;

import com.opentok.android.BaseVideoRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import ai.deepar.ar.DeepAR;

public class DeepArOpenTokRenderer extends BaseVideoRenderer {

    private final DeepAR deepAR;
    GLSurfaceView mRenderView;
    DeepARRenderer mRenderer;
    Context context;

    public DeepArOpenTokRenderer(Context context, DeepAR deepAR) {
        this.context = context;
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
//        Bitmap bmp = loadBitmapFromAssets(context, "asd.jpg");
//        ByteArrayOutputStream stream = new ByteArrayOutputStream();
//        bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
//
//        int bytes = bmp.getByteCount();
////or we can calculate bytes this way. Use a different value than 4 if you don't use 32bit images.
////int bytes = b.getWidth()*b.getHeight()*4;
//
//        ByteBuffer buffer = ByteBuffer.allocate(bytes); //Create a new buffer
//        bmp.copyPixelsToBuffer(buffer); //Move the byte data to the buffer



        deepAR.receiveFrame(imageBuffer, frame.getWidth(), frame.getHeight(), 0, true);
        mRenderView.requestRender();
    }

    public Bitmap loadBitmapFromAssets(Context context, String path) {
        InputStream stream = null;
        try {
            stream = context.getAssets().open(path);
            return BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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