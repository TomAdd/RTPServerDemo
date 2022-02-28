package com.byd.rtpserverdemo.rtp;

public class RtpSenderWrapper {
    private RtpUdp mRtpUdp;
    //private RtpAacStream mRtpAacStream;
    private RtpAvcStream mRtpAvcStream;

    public RtpSenderWrapper(String ip,int port,boolean broadcast, int frameRate) {
        mRtpUdp = new RtpUdp(ip, port, broadcast);
        if (mRtpUdp != null){
            mRtpAvcStream = new RtpAvcStream(mRtpUdp, frameRate);
            //mRtpAacStream = new RtpAacStream(44100,mRtpUdp);
        }
    }

    public void sendAvcPacket(final byte[] data,final int offset, final int size,long timeUs){
        if (mRtpAvcStream != null){
            try {
                mRtpAvcStream.addPacket(data, offset, size, timeUs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**public void sendAacPacket(final byte[] data,final int offset, final int size,long timeUs){
        if (mRtpAacStream != null){
            try {
                mRtpAacStream.addAU(data,offset,size,timeUs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }*/

    //Audio and video are turned off at the same time
    public void close(){
        if (mRtpUdp != null){
            mRtpUdp.close();
        }
    }
}
