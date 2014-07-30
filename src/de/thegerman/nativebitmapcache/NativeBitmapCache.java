package de.thegerman.nativebitmapcache;

import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.squareup.picasso.Cache;

public class NativeBitmapCache implements Cache {

    @Override
    public void clear() {
        nativeClear();
    }

    @Override
    public Bitmap get(String key) {
        NativeBitmapCacheEntry entry = nativeGetImageData(key);
        if (entry == null) return null;
        Bitmap newImage = BitmapFactory.decodeByteArray(entry.imageData, 0, entry.imageData.length);
        return newImage;
    }

    @Override
    public int maxSize() {
        return 0;
    }

    @Override
    public void set(String key, Bitmap image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        final int imageBytes = imageWidth * imageHeight * 4;
        ByteBuffer imageBuffer = ByteBuffer.allocateDirect(imageBytes);
        image.copyPixelsToBuffer(imageBuffer);
        nativeStoreImageData(key, imageBuffer, imageWidth, imageHeight);
    }

    @Override
    public int size() {
        return 0;
    }

    private native NativeBitmapCacheEntry nativeGetImageData(String key);

    private native void nativeStoreImageData(String key, ByteBuffer imageBuffer, int imageWidth, int imageHeight);

    private native void nativeClear();

}
