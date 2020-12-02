package com.tokbox.android.tutorials.videocall;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.Publisher;
import com.opentok.android.VideoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import ai.deepar.ar.CameraResolutionPreset;
import ai.deepar.ar.DeepAR;

import static android.content.Context.WINDOW_SERVICE;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;


/**
 * Created by luka on 19/04/17.
 * This is an example implementation of how the camera frames are fed to the DeepAR SDK. Feel free
 * to use it as is, or modify for your own needs.
 */


public class CameraGrabber extends BaseVideoCapturer implements Camera.PreviewCallback, CameraGrabberListener {
    private static final String TAG = CameraGrabber.class.getSimpleName();
    boolean isCaptureStarted = false;
    private static final int NUMBER_OF_BUFFERS = 2;

    private static int currentCameraDevice = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private int width = 640;
    private int height = 480;
    private CameraResolutionPreset resolutionPreset = CameraResolutionPreset.P640x480;
    private int screenOrientation = 0;
    private Display mCurrentDisplay;
    Context context;

    DeepAR deepAR;

    public CameraGrabber(Context context, DeepAR deepAR) {
        this.context = context;
        this.deepAR = deepAR;
    }

    @Override
    public void init() {
        WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            this.mCurrentDisplay = windowManager.getDefaultDisplay();
        }
        if (mThread == null) {
            mThread = new CameraHandlerThread(this, width, height, screenOrientation);
        }

        synchronized (mThread) {
            mThread.openCamera();
        }

    }

    private PixelFormat mPixelFormat = new PixelFormat();

    @Override
    public int startCapture() {
        try {
            if (this.isCaptureStarted) {
                return -1;
            } else {
                if (mThread.camera != null) {
                    VideoUtils.Size resolution = this.getPreferredResolution();
                    this.configureCaptureSize(resolution.width, resolution.height);
                    Camera.Parameters parameters = mThread.camera.getParameters();
                    parameters.setPreviewSize(this.width, this.height);
                    final int PIXEL_FORMAT = 17;
                    parameters.setPreviewFormat(PIXEL_FORMAT);
                    parameters.setPreviewFpsRange(this.mCaptureFPSRange[0], this.mCaptureFPSRange[1]);
                    parameters.setAutoExposureLock(false);

                    if (mThread.camera.getParameters().isVideoStabilizationSupported()) {
                        parameters.setVideoStabilization(true);
                    }

                    if (parameters.getSupportedFocusModes().contains(FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        parameters.setFocusMode(FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    try {
                        mThread.camera.setParameters(parameters);
                    } catch (Exception var7) {
                        return -1;
                    }



                    try {
                        mThread.surface = new SurfaceTexture(42);
                        mThread.camera.setPreviewTexture(mThread.surface);
                        Surface surface = new Surface(mThread.surface);

//                        deepAR.setRenderSurface(surface, width, height);

                    } catch (Exception var6) {
                        return -1;
                    }

                    mThread.camera.setPreviewCallbackWithBuffer(this);
                    mThread.camera.startPreview();
                    this.mPreviewBufferLock.lock();
                    this.mExpectedFrameSize = mThread.buffers.length;
                    this.mPreviewBufferLock.unlock();
                } else {
                    this.blackFrames = true;
//                    this.mHandler.postDelayed(this.newFrame, (long) (1000 / this.fps));
                }

                this.isCaptureRunning = true;
                this.isCaptureStarted = true;
                return 0;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private ReentrantLock mPreviewBufferLock = new ReentrantLock();

    private boolean isCaptureRunning = false;

    private int mExpectedFrameSize = 0;

    public void onPreviewFrame(byte[] data, Camera camera) {
        this.mPreviewBufferLock.lock();
        if (this.isCaptureRunning && data != null && data.length == this.mExpectedFrameSize) {
            int currentRotation = this.compensateCameraRotation(this.mCurrentDisplay.getRotation());
            this.provideByteArrayFrame(data, 1, width, height, currentRotation, true);
        }
        this.mPreviewBufferLock.unlock();


    }

    private int compensateCameraRotation(int uiRotation) {
        short currentDeviceOrientation = 0;
        switch (uiRotation) {
            case 0:
                currentDeviceOrientation = 0;
                break;
            case 1:
                currentDeviceOrientation = 270;
                break;
            case 2:
                currentDeviceOrientation = 180;
                break;
            case 3:
                currentDeviceOrientation = 90;
        }

        int cameraRotation = roundRotation(currentDeviceOrientation);
        boolean usingFrontCamera = true;
        int totalCameraRotation1;
        if (usingFrontCamera) {
            int inverseCameraRotation = (360 - cameraRotation) % 360;
            totalCameraRotation1 = (inverseCameraRotation + 1) % 360;
        } else {
            totalCameraRotation1 = (cameraRotation + 1) % 360;
        }

        return totalCameraRotation1;
    }


    private static int roundRotation(int rotation) {
        return (int) (Math.round((double) rotation / 90.0D) * 90L) % 360;
    }

    @Override
    public int stopCapture() {
        if (mThread.camera != null) {
            this.mPreviewBufferLock.lock();

            try {
                if (this.isCaptureRunning) {
                    this.isCaptureRunning = false;
                    mThread.camera.stopPreview();
                    mThread.camera.setPreviewCallbackWithBuffer(null);
                    mThread.camera.release();
                }
            } catch (Exception var2) {
                return -1;
            }

            this.mPreviewBufferLock.unlock();
        }

        this.isCaptureStarted = false;
        if (this.blackFrames) {
//            this.mHandler.removeCallbacks(this.newFrame);
        }

        return 0;
    }

    private boolean blackFrames = false;

    @Override
    public void destroy() {

    }

    @Override
    public boolean isCaptureStarted() {
        return isCaptureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        CaptureSettings settings = new CaptureSettings();
        try {
            new VideoUtils.Size();
            VideoUtils.Size resolution = this.getPreferredResolution();
            int framerate = 10;
            if (mThread != null && mThread.camera != null) {
                settings = new CaptureSettings();
                this.configureCaptureSize(resolution.width, resolution.height);
                settings.fps = framerate;
                settings.width = this.width;
                settings.height = this.height;
                settings.format = 1;
                settings.expectedDelay = 0;
            } else {
                settings.fps = framerate;
                settings.width = resolution.width;
                settings.height = resolution.height;
                settings.format = 2;
            }

        } catch (Exception e) {
            throw e;
        }

        return settings;
    }

    private int[] mCaptureFPSRange;

    private int[] findClosestEnclosingFpsRange(int preferredFps, List<int[]> supportedFpsRanges) {
        if (supportedFpsRanges != null && supportedFpsRanges.size() != 0) {
            int[] closestRange = (int[]) ((int[]) supportedFpsRanges.get(0));
            int measure = Math.abs(closestRange[0] - preferredFps) + Math.abs(closestRange[1] - preferredFps);
            Iterator i$ = supportedFpsRanges.iterator();

            while (i$.hasNext()) {
                int[] fpsRange = (int[]) i$.next();
                if (fpsRange[0] <= preferredFps && fpsRange[1] >= preferredFps) {
                    int currentMeasure = Math.abs(fpsRange[0] - preferredFps) + Math.abs(fpsRange[1] - preferredFps);
                    if (measure > currentMeasure) {
                        measure = currentMeasure;
                        closestRange = fpsRange;
                    }
                }
            }

            return closestRange;
        } else {
            return new int[]{0, 0};
        }
    }

    private void configureCaptureSize(int preferredWidth, int preferredHeight) {
        List sizes = null;
        int preferredFramerate = 10;

        try {
            Camera.Parameters maxw = mThread.camera.getParameters();
            sizes = maxw.getSupportedPreviewSizes();
            this.mCaptureFPSRange = this.findClosestEnclosingFpsRange(preferredFramerate * 1000, maxw.getSupportedPreviewFpsRange());
        } catch (Exception var12) {
        }

        int var13 = 0;
        int maxh = 0;

        for (int s = 0; s < sizes.size(); ++s) {
            Camera.Size minw = (Camera.Size) sizes.get(s);
            if (minw.width >= var13 && minw.height >= maxh && minw.width <= preferredWidth && minw.height <= preferredHeight) {
                var13 = minw.width;
                maxh = minw.height;
            }
        }

        if (var13 == 0 || maxh == 0) {
            Camera.Size var14 = (Camera.Size) sizes.get(0);
            int var15 = var14.width;
            int minh = var14.height;

            for (int i = 1; i < sizes.size(); ++i) {
                var14 = (Camera.Size) sizes.get(i);
                if (var14.width <= var15 && var14.height <= minh) {
                    var15 = var14.width;
                    minh = var14.height;
                }
            }

            var13 = var15;
            maxh = minh;
        }

        this.width = var13;
        this.height = maxh;
    }


    private VideoUtils.Size getPreferredResolution() {
        VideoUtils.Size resolution = new VideoUtils.Size();

        resolution.width = 1280;
        resolution.height = 720;


        return resolution;
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }


    public void setFrameReceiver(DeepAR receiver) {
        if (mThread != null) {
            mThread.setFrameReceiver(receiver, currentCameraDevice);
        }
    }

    public void initCamera(CameraGrabberListener listener) {
        if (mThread == null) {
            mThread = new CameraHandlerThread(listener, width, height, screenOrientation);
        }

        synchronized (mThread) {
            mThread.openCamera();
        }
    }


    public void startPreview() {
        if (mThread != null && mThread.camera != null) {
            mThread.camera.startPreview();
        }
    }

    public void stopPreview() {
        if (mThread != null && mThread.camera != null) {
            mThread.camera.stopPreview();
        }
    }

    public void changeCameraDevice(int cameraDevice) {
        currentCameraDevice = cameraDevice;
        initCamera(new CameraGrabberListener() {
            @Override
            public void onCameraInitialized() {
                startPreview();
            }

            @Override
            public void onCameraError(String errorMsg) {
                Log.e(TAG, errorMsg);
            }
        });

    }

    public int getCurrCameraDevice() {
        return currentCameraDevice;
    }

    public void releaseCamera() {
        if (mThread != null) {
            mThread.releaseCamera();
            mThread = null;
        }
    }

    private CameraHandlerThread mThread = null;

    public CameraResolutionPreset getResolutionPreset() {
        return resolutionPreset;
    }

    public void setResolutionPreset(CameraResolutionPreset resolutionPreset) {

        this.resolutionPreset = resolutionPreset;

        if (this.resolutionPreset == CameraResolutionPreset.P640x480) {
            width = 640;
            height = 480;
        } else if (this.resolutionPreset == CameraResolutionPreset.P1280x720) {
            width = 1280;
            height = 720;
        } else if (this.resolutionPreset == CameraResolutionPreset.P640x360) {
            width = 640;
            height = 360;
        }

        if (mThread != null) {
            mThread.reinitCamera(width, height);
        }

    }

    public int getScreenOrientation() {
        return screenOrientation;
    }

    public void setScreenOrientation(int screenOrientation) {
        this.screenOrientation = screenOrientation;
    }

    public Camera getCamera() {
        if (mThread == null) {
            return null;
        }
        return mThread.camera;
    }

    @Override
    public void onCameraInitialized() {

    }

    @Override
    public void onCameraError(String errorMsg) {

    }

    private static class CameraHandlerThread extends HandlerThread {
        Handler mHandler = null;
        public Camera camera;
        public SurfaceTexture surface;
        private DeepAR frameReceiver;
        private ByteBuffer[] buffers;
        private int currentBuffer = 0;
        private CameraGrabberListener listener;
        private int cameraOrientation;
        private int width;
        private int height;
        private int screenOrientation;

        CameraHandlerThread(CameraGrabberListener listener, int width, int height, int screenOrientation) {
            super("CameraHandlerThread");

            this.listener = listener;
            this.width = width;
            this.height = height;
            this.screenOrientation = screenOrientation;
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        synchronized void releaseCamera() {
            if (camera == null) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    camera.stopPreview();
                    camera.setPreviewCallbackWithBuffer(null);
                    camera.release();
                    camera = null;
                    mHandler = null;
                    listener = null;
                    frameReceiver = null;
                    surface = null;
                    buffers = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        quitSafely();
                    }
                }
            });
        }

        synchronized void setFrameReceiver(DeepAR receiver, final int cameraDevice) {
            frameReceiver = receiver;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (camera == null) {
                        return;
                    }
                    camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera arg1) {
                            if (frameReceiver != null) {
                                buffers[currentBuffer].put(data);
                                buffers[currentBuffer].position(0);
                                if (frameReceiver != null) {
                                    frameReceiver.receiveFrame(buffers[currentBuffer], width, height, cameraOrientation, cameraDevice == Camera.CameraInfo.CAMERA_FACING_FRONT);
                                }
                                currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS;
                            }
                            if (camera != null) {
                                try {
                                    camera.addCallbackBuffer(data);
                                } catch (Exception e) {
                                    Log.e(TAG, "onPreviewFrame: " + e);
                                }
                            }
                        }
                    });
                }
            });
        }

        private void init() {

            if (camera != null) {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                camera.release();
                camera = null;
            }

            if (surface == null) {
                surface = new SurfaceTexture(0);
            }

            Camera.CameraInfo info = new Camera.CameraInfo();
            int cameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == currentCameraDevice) {
                    cameraOrientation = info.orientation;

                    if (currentCameraDevice == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cameraOrientation = (info.orientation + screenOrientation) % 360;
                        //cameraOrientation = (360 - cameraOrientation) % 360;
                    } else {
                        cameraOrientation = (info.orientation - screenOrientation + 360) % 360;
                    }
                    cameraId = i;
                    break;
                }
            }

            if (cameraId == -1) {
                if (listener != null) {
                    listener.onCameraError("Camera not found error.");
                }
                return;
            }

            try {
                camera = Camera.open(cameraId);
            } catch (Exception e) {
                // event error
                if (listener != null) {
                    listener.onCameraError("Could not open camera device. Could be used by another process.");
                }
                return;
            }

            Camera.Parameters params = camera.getParameters();


            boolean presetExists = false;
            List<Camera.Size> availableSizes = camera.getParameters().getSupportedPictureSizes();
            for (Camera.Size size : availableSizes) {
                if (size.width == width && size.height == height) {
                    presetExists = true;
                    break;
                }
            }

            if (!presetExists) {
                Log.e(TAG, "Selected resolution preset is not available on this device");
                listener.onCameraError("Selected preset resolution of " + width + "x" + height + " is not supported for this device.");
                return;
            }

            params.setPreviewSize(width, height);
            params.setPictureSize(width, height);
            params.setPictureFormat(PixelFormat.JPEG);
            params.setJpegQuality(90);
            params.setPreviewFormat(ImageFormat.NV21);

            /*
            List<int[]> ranges = params.getSupportedPreviewFpsRange();
            int[] bestRange = {0,0};

            for (int[] range : ranges) {
                if (range[0] > bestRange[0]) {
                    bestRange[0] = range[0];
                    bestRange[1] = range[1];
                }
            }
            params.setPreviewFpsRange(bestRange[0], bestRange[1]);
            */

            camera.setParameters(params);


            buffers = new ByteBuffer[NUMBER_OF_BUFFERS];
            for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
                buffers[i] = ByteBuffer.allocateDirect(width * height * 3 / 2);
                buffers[i].order(ByteOrder.nativeOrder());
                buffers[i].position(0);
                byte[] buffer = new byte[width * height * 3 / 2];
                try {
                    camera.addCallbackBuffer(buffer);
                } catch (Exception e) {
                    Log.e(TAG, "init: " + e);
                }
            }


            try {
                camera.setPreviewTexture(surface);
            } catch (IOException ioe) {
                if (listener != null) {
                    listener.onCameraError("Error setting preview texture.");
                }
                return;
            }

            if (frameReceiver != null) {
                setFrameReceiver(frameReceiver, currentCameraDevice);
            }
            if (listener != null) {
                listener.onCameraInitialized();
            }
        }

        void reinitCamera(final int newWidth, final int newHeight) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    camera.stopPreview();
                    camera.setPreviewCallbackWithBuffer(null);
                    camera.release();
                    camera = null;
                    width = newWidth;
                    height = newHeight;
                    init();
                }
            });
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    init();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            } catch (InterruptedException e) {
                Log.w(TAG, "wait was interrupted");
            }
        }
    }

};