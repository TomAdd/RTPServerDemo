package com.byd.rtpserverdemo.rtp;

import android.nfc.Tag;
import android.util.Log;
import com.byd.rtpserverdemo.CalculateUtil;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RtpStream {
	private static final String TAG = "RtpStream";
	private static final int BUFFER_SIZE = 1500;
	private static final int MTU = 1400;
	private static final int SSRC = 1;
	private byte[] mNal = new byte[2];
	private int mTimeStamp = 0;
	private short mSequenceNumber;
	private RtpSocket mSocket;
	private int mPayloadType;
	private int mSampleRate;
	private int mFrameRate;

	public RtpStream(int pt, int sampleRate, RtpSocket socket, int frameRate) {
		this.mPayloadType = pt;
		this.mSampleRate = sampleRate;
		this.mSocket = socket;
		this.mFrameRate = frameRate;
	}

	public void addPacket(byte[] data, int offset, int size, long timeUs) throws IOException{
		addPacket(null, data, offset, size, timeUs);
	}

    /**
        RTP packet header
        Bit offset[b]	0-1	2	3	4-7	8	9-15	16-31
        0			Version	P	X	CC	M	PT	Sequence Number  31
        32			Timestamp									 63
        64			SSRC identifier								 95
     */
	public void addPacket(byte[] prefixData, byte[] data, int offset, int size, long timeUs) throws IOException{
		Log.i(TAG, "size: " + size);
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		mTimeStamp += mSampleRate / mFrameRate;
		if (size < MTU) {
			buffer.put((byte) ((2 << 6) + (2 << 3)));
			buffer.put((byte) ((1 << 7) + (mPayloadType)));
			buffer.putShort(mSequenceNumber++);
			buffer.putInt(mTimeStamp);
			buffer.putInt(SSRC);
			buffer.putInt(size);

			buffer.put(data, offset, size);
			sendPacket(buffer, buffer.position());
		} else {
			int packages = size / MTU;
			int remainingSize = size % MTU;
			if (remainingSize == 0) {
				packages = packages - 1;
			}

			for (int i = 0; i <= packages; i++) {
				CalculateUtil.memset(mNal, 0, 2);
				mNal[0] = (byte) (mNal[0] | ((byte) (data[4] & 0x80)) << 7);				//禁止位，为0
				mNal[0] = (byte) (mNal[0] | ((byte) ((data[4] & 0x60) >> 5)) << 5);		//NRI，表示包的重要性
				mNal[0] = (byte) (mNal[0] | (byte) (28));									//TYPE，表示此FU-A包为什么类型，一般此处为28

				if (i == 0) {
					//FU header，一个字节，S,E，R，TYPE
					mNal[1] = (byte) (mNal[1] & 0xBF);										//E=0，表示是否为最后一个包，是则为1
					mNal[1] = (byte) (mNal[1] & 0xDF);										//R=0，保留位，必须设置为0
					mNal[1] = (byte) (mNal[1] | 0x80);										//S=1，表示是否为第一个包，是则为1

					//判断是否为关键帧
                    if (data[4] == 97) {
                        mNal[1] = (byte) (mNal[1] | ((byte) (1 & 0x1f)));					//TYPE，即NALU头对应的TYPE
                    } else {
                        mNal[1] = (byte) (mNal[1] | ((byte) (5 & 0x1f)));
                    }
                    buffer.put((byte) ((2 << 6) + (2 << 3)));
					buffer.put((byte) (mPayloadType));
					buffer.putShort(mSequenceNumber++);
					buffer.putInt(mTimeStamp);
					buffer.putInt(SSRC);
                    buffer.putInt(MTU);
					buffer.put(mNal);

					buffer.put(data, 0, MTU);
					sendPacket(buffer, buffer.position());
				} else if (i == packages) {
					mNal[1] = (byte) (mNal[1] & 0xDF); //R=0，保留位必须设为0
					mNal[1] = (byte) (mNal[1] & 0x7F); //S=0，不是第一个包
					mNal[1] = (byte) (mNal[1] | 0x40); //E=1，是最后一个包

                    //判断是否为关键帧
                    if (data[4] == 97) {
                        mNal[1] = (byte) (mNal[1] | ((byte) (1 & 0x1f)));					//TYPE，即NALU头对应的TYPE
                    } else {
                        mNal[1] = (byte) (mNal[1] | ((byte) (5 & 0x1f)));
                    }

                    buffer.put((byte) ((2 << 6) + (2 << 3)));
                    buffer.put((byte) ((1 << 7) + (mPayloadType)));
                    buffer.putShort(mSequenceNumber++);
                    buffer.putInt(mTimeStamp);
                    buffer.putInt(SSRC);

					if (remainingSize == 0) {
						buffer.putInt(MTU);
						buffer.put(mNal);
						buffer.put(data, i * MTU, MTU);
					} else {
						buffer.putInt(remainingSize);
						buffer.put(mNal);
						buffer.put(data, i * MTU, remainingSize);
					}

					sendPacket(buffer, buffer.position());
				} else {
					mNal[1] = (byte) (mNal[1] & 0xDF); //R=0，保留位必须设为0
					mNal[1] = (byte) (mNal[1] & 0x7F); //S=0，不是第一个包
					mNal[1] = (byte) (mNal[1] & 0xBF); //E=0，不是最后一个包

                    //判断是否为关键帧
                    if (data[4] == 97) {
                        mNal[1] = (byte) (mNal[1] | ((byte) 1));					//TYPE，即NALU头对应的TYPE
                    } else if (data[26] == 101) {   //由于视频数据前有22byte的pps和sps以及4byte分隔符，因此data[26]才是判断帧类型的元素
                        Log.v(TAG, "key frame");
                        mNal[1] = (byte) (mNal[1] | ((byte) 5));
                    }

                    buffer.put((byte) ((2 << 6) + (2 << 3)));
                    buffer.put((byte) (mPayloadType));
                    buffer.putShort(mSequenceNumber++);
                    buffer.putInt(mTimeStamp);
                    buffer.putInt(SSRC);
                    buffer.putInt(MTU);
					buffer.put(mNal);

					buffer.put(data, i * MTU, MTU);
					sendPacket(buffer, buffer.position());
				}
			}
		}
	}

	protected void sendPacket(ByteBuffer buffer, int size) throws IOException{
		mSocket.sendPacket(buffer.array(), 0, size);
		buffer.clear();
	}

	protected void sendPacket(byte[] buffer, int size) throws IOException {
		mSocket.sendPacket(buffer, 0, size);
	}
}
