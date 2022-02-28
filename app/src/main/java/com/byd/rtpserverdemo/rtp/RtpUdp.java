package com.byd.rtpserverdemo.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RtpUdp implements RtpSocket {
	private DatagramSocket mSocket;
	private InetAddress mInetAddress;
	private int mPort;

	public RtpUdp(String ip, int port, boolean broadcast){
		try {
			mInetAddress = InetAddress.getByName(ip);
			mPort = port;
			mSocket = new DatagramSocket();
			mSocket.setBroadcast(broadcast);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void sendPacket(final byte[] data,final int offset, final int size) {
		try{
			DatagramPacket p;
			p = new DatagramPacket(data, 0, size, mInetAddress, mPort);
			mSocket.send(p);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close(){
		mSocket.close();
	}
}
