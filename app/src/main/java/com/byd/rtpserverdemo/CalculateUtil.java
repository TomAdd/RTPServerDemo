package com.byd.rtpserverdemo;

public class CalculateUtil {
    /**
     * Clear the value of buf
     * @param buf
     * @param value
     * @param size
     */
    public static void memset(byte[] buf, int value, int size) {
        for (int i = 0; i < size; i++) {
            buf[i] = (byte) value;
        }
    }
}
