package com.byd.rtpserverdemo;

import java.io.IOException;
import java.nio.ByteBuffer;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

public class AvcEncoder {
	private static final String TAG = "AvcEncoder";
	private static final String MIME_TYPE = "video/avc";
	private MediaCodec mMediaCodec;
	private byte[] mInfo = null;

	@SuppressLint("NewApi")
	public AvcEncoder(int width, int height, int framerate, int bitrate) {
		try {
			mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
			MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
			mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
			mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
			mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
			mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mMediaCodec.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressLint("NewApi")
	public int offerEncoder(byte[] input, byte[] output) {
		Log.i(TAG, "offerEncoder input length = " + input.length + "; output length = " + output.length);
		int pos = 0;
	    try {
	        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
	        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
	        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
	        if (inputBufferIndex >= 0) {
	            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	            inputBuffer.clear();
	            inputBuffer.put(input);
	            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
	        }

	        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo,0);
	        while (outputBufferIndex >= 0) {
				Log.i(TAG, "outputBufferIndex >= 0");
	            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);

				/**if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // sps pps
					//Log.i(TAG, "sps = " + Arrays.toString(outData));
					System.arraycopy(outData, 0,  output, pos, outData.length);
					pos += outData.length;
				} else if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0){ // frame
					//Log.i(TAG, "frame = " + Arrays.toString(outData));
					System.arraycopy(outData, 0,  output, pos, outData.length);
					pos += outData.length;
				} else {
					//Log.i(TAG, "else = " + Arrays.toString(outData));
					System.arraycopy(outData, 0,  output, pos, outData.length);
					pos += outData.length;
				}*/

	            if(mInfo != null){
	            	System.arraycopy(outData, 0,  output, pos, outData.length);
	 	            pos += outData.length;
	            }else{		//Save pps sps only in the first frame, save it for later use
					ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
					if (spsPpsBuffer.getInt() == 0x00000001) {
						mInfo = new byte[outData.length];
						System.arraycopy(outData, 0, mInfo, 0, outData.length);
					}else {
						return -1;
					}
	            }
				Log.v(TAG, " 0: " + output[0] + " 1: " + output[1] + " 2: " + output[2] + " 3: " + output[3] + " 4: " + output[4]);	//数组元素0-3为间隔0001 元素4为nalu类型
	            if(output[4] == 0x65) {		//key frame When the encoder generates the key frame, there is only 00 00 00 01 65 without pps sps.
                    Log.v(TAG, "mInfo.length is " + mInfo.length);
					System.arraycopy(mInfo, 0,  output, 0, mInfo.length);	//每秒有一个关键帧，在关键帧前加上pps sps
	                System.arraycopy(outData, 0,  output, mInfo.length, outData.length);
		        }
	            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
	            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
	        }
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
		Log.i(TAG, "offerEncoder pos = " + pos);
	    return pos;
	}

	@SuppressLint("NewApi")
	public void close() {
		try {
			mMediaCodec.stop();
			mMediaCodec.release();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
}

