package com.byd.rtpserverdemo;

import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.nio.ByteBuffer;

public class ImageUtil {
    public static final int YUV420P = 0;
    public static final int YUV420SP = 1;
    public static final int NV21 = 2;
    private static final String TAG = "ImageUtil";

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static byte[] getBytesFromImageAsType(Image image, int type) {
        try {
            //Get the source data, if it is YUV format data planes.length = 3
            final Image.Plane[] planes = image.getPlanes();

            //Data effective width, in general, image width <= rowStride, which is also the reason for byte []. Length <= capacity
            // So we only take the width part
            int width = image.getWidth();
            int height = image.getHeight();
            Log.i(TAG, "image width = " + image.getWidth() + "; image height = " + image.getHeight());

            //This is used to fill the final YUV data, which requires 1.5 times the picture size, because the YUV ratio is 4: 1: 1
            byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            //The position to which the target array is filled
            int dstIndex = 0;

            //Temporary storage of uv data
            byte uBytes[] = new byte[width * height / 4];
            byte vBytes[] = new byte[width * height / 4];
            int uIndex = 0;
            int vIndex = 0;

            int pixelsStride, rowStride;
            for (int i = 0; i < planes.length; i++) {
                pixelsStride = planes[i].getPixelStride();
                rowStride = planes[i].getRowStride();

                ByteBuffer buffer = planes[i].getBuffer();

                //The index of the source data. The data of y is continuous in byte. The data of u is shifted to the left. It is assumed that both are even-numbered bits.
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                int srcIndex = 0;
                if (i == 0) {
                    //Take out all the valid areas of Y directly, or store them as a temporary byte, and then copy it to the next step.
                    for (int j = 0; j < height; j++) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                        srcIndex += rowStride;
                        dstIndex += width;
                    }
                } else if (i == 1) {
                    //Take corresponding data according to pixelsStride
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            uBytes[uIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                } else if (i == 2) {
                    //Take corresponding data according to pixelsStride
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            vBytes[vIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                }
            }
            //Fill based on required result type
            switch (type) {
                case YUV420P:
                    System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                    System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                    break;
                case YUV420SP:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = uBytes[i];
                        yuvBytes[dstIndex++] = vBytes[i];
                    }
                    break;
                case NV21:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = vBytes[i];
                        yuvBytes[dstIndex++] = uBytes[i];
                    }
                    break;
            }
            return yuvBytes;
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, e.toString());
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static byte[] getBytesFromImageAsType(Image image) {
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            int i420Size = w * h * 3 / 2;

            Image.Plane[] planes = image.getPlanes();
            int remaining0 = planes[0].getBuffer().remaining();
            int remaining1 = planes[1].getBuffer().remaining();
            int remaining2 = planes[2].getBuffer().remaining();

            //Get pixelStride, may be equal to width, may not be equal
            int pixelStride = planes[2].getPixelStride();
            int rowOffest = planes[2].getRowStride();
            byte[] nv21 = new byte[i420Size];

            //Prepare three arrays to receive YUV components.
            byte[] yRawSrcBytes = new byte[remaining0];
            byte[] uRawSrcBytes = new byte[remaining1];
            byte[] vRawSrcBytes = new byte[remaining2];
            planes[0].getBuffer().get(yRawSrcBytes);
            planes[1].getBuffer().get(uRawSrcBytes);
            planes[2].getBuffer().get(vRawSrcBytes);
            if (pixelStride == 1) {
                //Both are equal, indicating that each YUV block is closely connected and can be copied directly
                System.arraycopy(yRawSrcBytes, 0, nv21, 0, rowOffest * h);
                System.arraycopy(vRawSrcBytes, 0, nv21, rowOffest * h, rowOffest * h / 2 - 1);
            } else {
                //Generate byte array according to the size of each component
                byte[] ySrcBytes = new byte[w * h];
                byte[] uSrcBytes = new byte[w * h / 2 - 1];
                byte[] vSrcBytes = new byte[w * h / 2 - 1];
                for (int row = 0; row < h; row++) {
                    //The source array copies w bytes every rowOffest bytes to the destination array
                    System.arraycopy(yRawSrcBytes, rowOffest * row, ySrcBytes, w * row, w);
                    //y executes twice, uv executes once
                    if (row % 2 == 0) {
                        //The last line needs to be reduced by one
                        if (row == h - 2) {
                            System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w - 1);
                        } else {
                            System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w);
                        }
                    }
                }
                //yuv into an array
                System.arraycopy(ySrcBytes, 0, nv21, 0, w * h);
                System.arraycopy(vSrcBytes, 0, nv21, w * h, w * h / 2 - 1);
            }
            return nv21;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        image.close();
        return null;
    }

    public static byte[] rotateYUVDegree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }
}
