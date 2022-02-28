package com.byd.rtpserverdemo.rtp;

import java.io.IOException;

public interface RtpSocket {
	public void sendPacket(byte[] data, int offset, int size) throws IOException;
	public void close();
}
