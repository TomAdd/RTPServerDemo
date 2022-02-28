package com.byd.rtpserverdemo.rtp;

import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RtpAvcStream extends RtpStream {
	private final static String TAG = "AvcRtpStream";
	private final static int MTU = 1500;

	public RtpAvcStream(RtpSocket socket,int frameRate) {
		super(96, 90000, socket, frameRate);
	}

	public void addNalu(ByteBuffer buf, int size, long timeUs) throws IOException{
		byte[] data = new byte[size];
		buf.get(data);

		addNalu(data, 0, size, timeUs);
	}

	public void addNalu(byte[] data, int offset, int size, long timeUs) throws IOException{
		if(size <= MTU){
			Log.i(TAG, "Single NALU send.");
			createSingleUnit(data, offset, size, timeUs);
		}else{
			Log.i(TAG, "Send in FuA");
			createFuA(data, offset, size, timeUs);
		}
	}

	private void createSingleUnit(byte[] data, int offset, int size, long timeUs) throws IOException {
		addPacket(data, offset, size, timeUs);
	}

	private void createFuA(byte[] data, int offset, int size, long timeUs) throws IOException {
		byte originHeader = data[offset++];
		size -= 1;
		Log.i(TAG, "FuA nalu  type:" +  (originHeader & 0x1f));
		int left = size;
		int read = 1400;
		for(;left > 0; left -= read, offset += read){
			byte indicator = (byte)( (originHeader & 0xe0) | 28);		
			byte naluHeader = (byte)(originHeader & 0x1f);
			if (left < read) {
				read = left;
			} else if (left == size) {
				naluHeader = (byte) (naluHeader | (1 << 7));
			} else if (left == read) {
				naluHeader = (byte) (naluHeader | (1 << 6));
			}
			addPacket(new byte[]{indicator, naluHeader}, data, offset, read, timeUs);
		}
	}
}
