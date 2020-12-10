package com.tokbox.android.tutorials.videocall.capturer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.media.Image;
import android.os.Handler;
import android.view.Display;
import android.view.WindowManager;

import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.BaseVideoCapturer.CaptureSwitch;
import com.opentok.android.Publisher;
import com.opentok.android.Publisher.CameraCaptureFrameRate;
import com.opentok.android.Publisher.CameraCaptureResolution;
import com.opentok.android.VideoUtils.Size;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import ai.deepar.ar.DeepAR;

import static android.content.Context.WINDOW_SERVICE;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;
import static android.hardware.Camera.Parameters.FLASH_MODE_TORCH;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_INFINITY;
import static android.os.Build.MODEL;
import static com.opentok.android.Publisher.CameraCaptureFrameRate.FPS_30;
import static com.opentok.android.Publisher.CameraCaptureResolution.HIGH;

@SuppressWarnings("deprecation")
public class CustomVideoCapturerV2 extends BaseVideoCapturer implements PreviewCallback, CaptureSwitch {
    private static final String BT_300 = "EMBT3C";
    private static final int LOW = 1;
    private static final int MEDIUM = 2;
    private static final String LOG_TAG = "custom-videocapturer-v2";

    public static final int HD = 3;

    private final Context context;

    private ReentrantLock mPreviewBufferLock = new ReentrantLock();
    private PixelFormat mPixelFormat = new PixelFormat();
    private int fps;
    private int width;
    private int height;
    private int mExpectedFrameSize = 0;
    private int mCaptureWidth = -1;
    private int mCaptureHeight = -1;
    private int mCameraIndex = 0;
    private int[] mCaptureFPSRange;
    private int[] frame;
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean blackFrames = false;
    private Handler mHandler;
    private Runnable newFrame;
    private Camera mCamera;
    private CameraInfo mCurrentDeviceInfo = null;
    private Display mCurrentDisplay;
    private SurfaceTexture mSurfaceTexture;
    private CameraCaptureResolution preferredResolution;
    private CameraCaptureFrameRate preferredFramerate;
    DeepAR deepAR;
    private Display currentDisplay;

    public CustomVideoCapturerV2(Context context, DeepAR deepAR, Publisher.CameraCaptureResolution resolution,
                                 Publisher.CameraCaptureFrameRate fps) {
        this.context = context;
        this.deepAR = deepAR;
        // Get current display to query UI orientation
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        currentDisplay = windowManager.getDefaultDisplay();
        this.preferredFramerate = fps;
        this.preferredResolution = resolution;
        if (windowManager != null) {
            this.mCurrentDisplay = windowManager.getDefaultDisplay();
        }
    }

    private static int roundRotation(int rotation) {
        return (int) (Math.round((double) rotation / 90.0D) * 90L) % 360;
    }

    private static int getCameraIndex(boolean front) {
        if (MODEL.equals(BT_300)) {
            //Special case to handle BT-300 bug. It has only one camera, but returns 2.
            return 0;
        }

        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (front && info.facing == CAMERA_FACING_FRONT) {
                return i;
            }

            if (!front && info.facing == CAMERA_FACING_BACK) {
                return i;
            }
        }

        return 0;
    }

    public void init() {
        try {
            this.mCamera = Camera.open(this.mCameraIndex);

            this.mCurrentDeviceInfo = new CameraInfo();
            Camera.getCameraInfo(this.mCameraIndex, this.mCurrentDeviceInfo);
        } catch (Exception e) {
            throw e;
        }
    }

    public int startCapture() {
        try {
            if (this.isCaptureStarted) {
                return -1;
            } else {
                if (this.mCamera != null) {
                    Size resolution = this.getPreferredResolution();
                    this.configureCaptureSize(resolution.width, resolution.height);
                    Parameters parameters = this.mCamera.getParameters();
                    parameters.setPreviewSize(this.mCaptureWidth, this.mCaptureHeight);
                    final int PIXEL_FORMAT = 17;
                    parameters.setPreviewFormat(PIXEL_FORMAT);
                    parameters.setPreviewFpsRange(this.mCaptureFPSRange[0], this.mCaptureFPSRange[1]);
                    parameters.setAutoExposureLock(false);

                    if (mCamera.getParameters().isVideoStabilizationSupported()) {
                        parameters.setVideoStabilization(true);
                    }


                    if (!mCamera.getParameters().getSupportedFocusModes().isEmpty() && mCamera.getParameters().getSupportedFocusModes().contains(FOCUS_MODE_INFINITY)) {
                        parameters.setFocusMode(FOCUS_MODE_INFINITY);

                    }

                    try {
                        this.mCamera.setParameters(parameters);
                    } catch (Exception var7) {
                        return -1;
                    }

                    PixelFormat.getPixelFormatInfo(PIXEL_FORMAT, this.mPixelFormat);
                    int bufSize = this.mCaptureWidth * this.mCaptureHeight * this.mPixelFormat.bitsPerPixel / 8;
                    Object buffer = null;

                    for (int e = 0; e < 3; ++e) {
                        byte[] var8 = new byte[bufSize];
                        this.mCamera.addCallbackBuffer(var8);
                    }

                    try {
                        this.mSurfaceTexture = new SurfaceTexture(42);
                        this.mCamera.setPreviewTexture(this.mSurfaceTexture);
                    } catch (Exception var6) {
                        return -1;
                    }

                    this.mCamera.setPreviewCallbackWithBuffer(this);
                    this.mCamera.startPreview();
                    this.mPreviewBufferLock.lock();
                    this.mExpectedFrameSize = bufSize;
                    this.mPreviewBufferLock.unlock();
                } else {
                    this.blackFrames = true;
                    this.mHandler.postDelayed(this.newFrame, (long) (1000 / this.fps));
                }

                this.isCaptureRunning = true;
                this.isCaptureStarted = true;
                return 0;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public int stopCapture() {
        if (this.mCamera != null) {
            this.mPreviewBufferLock.lock();

            try {
                if (this.isCaptureRunning) {
                    this.isCaptureRunning = false;
                    this.mCamera.stopPreview();
                    this.mCamera.setPreviewCallbackWithBuffer(null);
                    this.mCamera.release();
                }
            } catch (Exception var2) {
                return -1;
            }

            this.mPreviewBufferLock.unlock();
        }

        this.isCaptureStarted = false;
        if (this.blackFrames) {
            this.mHandler.removeCallbacks(this.newFrame);
        }

        return 0;
    }

    public void destroy() {
    }

    public boolean isCaptureStarted() {
        return this.isCaptureStarted;
    }

    public CaptureSettings getCaptureSettings() {
        CaptureSettings settings = new CaptureSettings();
        try {
            new Size();
            Size resolution = this.getPreferredResolution();
            int framerate = this.getPreferredFrameRate();
            if (this.mCamera != null) {
                settings = new CaptureSettings();
                this.configureCaptureSize(resolution.width, resolution.height);
                settings.fps = framerate;
                settings.width = this.mCaptureWidth;
                settings.height = this.mCaptureHeight;
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

    public void onPause() {
    }

    public void onResume() {
    }

    private int getNaturalCameraOrientation() {
        return this.mCurrentDeviceInfo != null ? this.mCurrentDeviceInfo.orientation : 0;
    }

    public boolean isFrontCamera() {
        return this.mCurrentDeviceInfo != null ? this.mCurrentDeviceInfo.facing == 1 : false;
    }

    public int getCameraIndex() {
        return this.mCameraIndex;
    }

    public void cycleCamera() {
        try {
            this.swapCamera((this.getCameraIndex() + 1) % Camera.getNumberOfCameras());
        } catch (Exception e) {
            throw e;
        }
    }

    public void swapCamera(int index) {
        try {
            boolean wasStarted = this.isCaptureStarted;
            if (this.mCamera != null) {
                this.stopCapture();
                this.mCamera.release();
                this.mCamera = null;
            }

            this.mCameraIndex = index;
            if (wasStarted) {
                this.forceCameraRelease(index);
                this.mCamera = Camera.open(index);
                this.mCurrentDeviceInfo = new CameraInfo();
                Camera.getCameraInfo(index, this.mCurrentDeviceInfo);
                this.startCapture();
            }
        } catch (Exception e) {
            throw e;
        }

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

        int cameraOrientation = this.getNaturalCameraOrientation();
        int cameraRotation = roundRotation(currentDeviceOrientation);
        boolean usingFrontCamera = this.isFrontCamera();
        int totalCameraRotation1;
        if (usingFrontCamera) {
            int inverseCameraRotation = (360 - cameraRotation) % 360;
            totalCameraRotation1 = (inverseCameraRotation + cameraOrientation) % 360;
        } else {
            totalCameraRotation1 = (cameraRotation + cameraOrientation) % 360;
        }

        return totalCameraRotation1;
    }

    public void onPreviewFrame(byte[] data, Camera camera) {
        this.mPreviewBufferLock.lock();
        if (this.isCaptureRunning && data.length == this.mExpectedFrameSize) {
            int currentRotation = this.compensateCameraRotation(this.mCurrentDisplay.getRotation());
            this.provideByteArrayFrame(data, 1, this.mCaptureWidth, this.mCaptureHeight, currentRotation, this.isFrontCamera());
            camera.addCallbackBuffer(data);
        }

        this.mPreviewBufferLock.unlock();
    }

    public int getMaxExposureValue() {
        if (mCamera != null) {
            return this.mCamera.getParameters().getMaxExposureCompensation();
        } else {
            return 0;
        }

    }

    public int getMinExposureValue() {
        if (mCamera != null) {
            return this.mCamera.getParameters().getMinExposureCompensation();
        } else {
            return 0;
        }

    }

    private boolean forceCameraRelease(int cameraIndex) {
        Camera camera = null;

        boolean var4;
        try {
            camera = Camera.open(cameraIndex);
            return false;
        } catch (Exception var8) {
            var4 = true;
        } finally {
            if (camera != null) {
                camera.release();
            }

        }

        return var4;
    }

    private Size getPreferredResolution() {
        Size resolution = new Size();
        resolution.width = 640;
        resolution.height = 480;
        return resolution;
    }

    private int getPreferredFrameRate() {
        return this.fps;
    }

    private void configureCaptureSize(int preferredWidth, int preferredHeight) {
        List sizes = null;
        int preferredFramerate = this.getPreferredFrameRate();

        try {
            Parameters maxw = this.mCamera.getParameters();
            sizes = maxw.getSupportedPreviewSizes();
            this.mCaptureFPSRange = this.findClosestEnclosingFpsRange(preferredFramerate * 1000, maxw.getSupportedPreviewFpsRange());
        } catch (Exception var12) {
        }

        int var13 = 0;
        int maxh = 0;

        for (int s = 0; s < sizes.size(); ++s) {
            android.hardware.Camera.Size minw = (android.hardware.Camera.Size) sizes.get(s);
            if (minw.width >= var13 && minw.height >= maxh && minw.width <= preferredWidth && minw.height <= preferredHeight) {
                var13 = minw.width;
                maxh = minw.height;
            }
        }

        if (var13 == 0 || maxh == 0) {
            android.hardware.Camera.Size var14 = (android.hardware.Camera.Size) sizes.get(0);
            int var15 = var14.width;
            int minh = var14.height;

            for (int i = 1; i < sizes.size(); ++i) {
                var14 = (android.hardware.Camera.Size) sizes.get(i);
                if (var14.width <= var15 && var14.height <= minh) {
                    var15 = var14.width;
                    minh = var14.height;
                }
            }

            var13 = var15;
            maxh = minh;
        }

        this.mCaptureWidth = var13;
        this.mCaptureHeight = maxh;
    }

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

    public void newFrameProcessed(final Image image) {
        if (image != null) {
            provideBufferFrame( , ABGR, image.getWidth(),
                    image.getHeight(), 0, false);
        }
    }
}
