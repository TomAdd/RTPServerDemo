package com.byd.rtpserverdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.byd.rtpserverdemo.rtp.RtpSenderWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String IP = "192.168.43.30";
    private static final int PORT = 5004;
    private static final int TIME = 1000;

    private int mWidth = 1920;
    private int mHeight = 1080;
    private int mFramerate = 15;
    private int mBitrate = 1900000;
    private int mStartState = 0;
    private int mStopState = 1;
    private int mFrames = 0;
    private int mCameraState = mStopState;
    private byte[] mH264Data = new byte[mWidth * mHeight * 3];

    private RtpSenderWrapper mRtpSenderWrapper;
    private Camera2Helper mCamera2Helper;
    private AvcEncoder mAvcEncoder;
    private ServerSocket mServerSocket;
    private HandlerThread mProcessingRequestThread;
    private Handler mProcessingRequestHandler;
    private Handler mUIHandler = new Handler() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    mUIHandler.postDelayed(mRunnable, TIME);
                    mCamera2Helper.startCamera(mWidth,mHeight);
                    mCameraState = mStartState;
                    break;
                case 1:
                    mUIHandler.removeCallbacks(mRunnable);
                    mCamera2Helper.closeCamera();
                    mCameraState = mStopState;
                    break;
                default:
                    break;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");
        mCamera2Helper = new Camera2Helper(this);
        mAvcEncoder = new AvcEncoder(mWidth, mHeight, mFramerate, mBitrate);
        mRtpSenderWrapper = new RtpSenderWrapper(IP, PORT, false, mFramerate);
        mCamera2Helper.setImageDataListener(new Camera2Helper.ImageDataListener() {
            @Override
            public void OnImageDataListener(byte[] data) {
                Log.i(TAG, "OnImageDataListener");
                mFrames++;
                int ret = mAvcEncoder.offerEncoder(data, mH264Data);
                if (ret > 0) {
                    mRtpSenderWrapper.sendAvcPacket(mH264Data, 0, ret, 0);
                }
            }
        });
        try {
            mServerSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        initHandlerThraed();
    }

    private void initHandlerThraed() {
        mProcessingRequestThread = new HandlerThread("ProcessingRequestThread");
        mProcessingRequestThread.start();
        Looper loop = mProcessingRequestThread.getLooper();
        mProcessingRequestHandler = new Handler(loop){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                while (true) {
                    try {
                        Socket socket = mServerSocket.accept();
                        InputStream inputStream = socket.getInputStream();
                        byte[] bytes = new byte[1024];
                        int len = inputStream.read(bytes);
                        String data = new String(bytes, 0, len);
                        Log.i(TAG, "data = " + data);
                        if (data.equals("OpenCamera1")) {
                            Message message = new Message();
                            message.what = 0;
                            message.obj = "OpenCamera1";
                            mUIHandler.sendMessage(message);
                        } else if (data.equals("CloseCamera1")) {
                            Message message = new Message();
                            message.what = 1;
                            message.obj = "CloseCamera1";
                            mUIHandler.sendMessage(message);
                        }
                        OutputStream outputStream = socket.getOutputStream();
                        outputStream.write("MessageAccepted".getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        mProcessingRequestHandler.sendEmptyMessage(0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        if (mCameraState != mStopState) {
            mCamera2Helper.closeCamera();
            mCameraState = mStopState;
        }
        mUIHandler.removeCallbacks(mRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if (mAvcEncoder != null) {
            mAvcEncoder.close();
            mAvcEncoder = null;
        }

        if (mRtpSenderWrapper != null) {
            mRtpSenderWrapper.close();
            mRtpSenderWrapper = null;
        }

        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mProcessingRequestThread != null) {
            mProcessingRequestThread.quit();
        }
    }

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mUIHandler.postDelayed(this, TIME);
                Log.i(TAG,"Frame rate is " + mFrames);
                mFrames = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}
