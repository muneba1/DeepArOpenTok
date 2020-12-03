package com.tokbox.android.tutorials.videocall.utils;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

/**
 * Created by zeerak on 12/3/2020 bt
 */
public class Helper {
    public static Bitmap getBitmapFromImageReader(Image image) {
        if (image == null)
            return null;
        Bitmap bitmap;

        //get image buffer
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();

        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        // create bitmap
        bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        return bitmap;
    }

    public static ByteBuffer deepCopy(ByteBuffer source) {

        int sourceP = source.position();
        int sourceL = source.limit();

        ByteBuffer target = ByteBuffer.allocateDirect(source.remaining());

        target.put(source);
        target.flip();

        source.position(sourceP);
        source.limit(sourceL);
        return target;
    }
}
