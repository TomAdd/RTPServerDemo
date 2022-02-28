package com.byd.rtpserverdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Helper {
    private static final String TAG = "Camera2Helper";
    private Context mContext;
    private ImageReader mImageReader;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private ImageDataListener mImageDataListener;
    private String mCameraId = "0";
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private ArrayList<Range> mFpsRanges = new ArrayList<>();
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback(){
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "onOpened");
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "onDisconnected");
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError openDevice error:" + error);
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    public Camera2Helper(Context context) {
        this.mContext = context;
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startCamera(int width, int height) {
        Log.i(TAG, "start Camera.");
        startBackgroundThread();
        setUpCameraOutputs(width, height);
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startCamera error: " + e.getMessage());
        }
    }

    public void closeCamera() {
        Log.i(TAG, "closeCamera");
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            Surface imageSurface = mImageReader.getSurface();
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(imageSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(imageSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "onConfigured");
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCaptureSession = session;
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mFpsRanges.get(0));
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                            }
                        }, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "onConfigureFailed");
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Log.i(TAG, "setUpCameraOutputs start");
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(mCameraId);
            Range[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Log.i(TAG, "fpsRanges: " + Arrays.toString(fpsRanges));
            for (int i = 0; i < fpsRanges.length; i++) {
                int lower = (int) fpsRanges[i].getLower();
                int upper = (int) fpsRanges[i].getUpper();
                if (lower == 12 && upper == 15) {
                    mFpsRanges.add(fpsRanges[i]);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, /*maxImages*/2);
        mImageReader.setOnImageAvailableListener(new RTPOnImageAvailableListener(), mCameraHandler);
        return;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    private class RTPOnImageAvailableListener implements ImageReader.OnImageAvailableListener{

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.i(TAG, "onImageAvailable");
            Image readImage = reader.acquireNextImage();
            byte[] data = ImageUtil.getBytesFromImageAsType(readImage, 1);
            readImage.close();
            mImageDataListener.OnImageDataListener(data);
        }
    }

    public void setImageDataListener(ImageDataListener listener) {
        this.mImageDataListener = listener;
    }

    public interface ImageDataListener{
        void OnImageDataListener(byte[] reader);
    }
}
