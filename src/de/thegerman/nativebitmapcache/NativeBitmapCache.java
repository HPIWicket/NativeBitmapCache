package de.thegerman.nativebitmapcache;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Build.VERSION_CODES.HONEYCOMB;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.squareup.picasso.Cache;
public class NativeBitmapCache implements Cache {

    private final Map<String, NativeBitmapCacheEntry> mChacheEntries;
    private final int mMaxSize;
    private int mSize;
    
    static {
    System.loadLibrary("nativebitmapcache-jni");
    }
    
    @SuppressWarnings("unchecked")
    static <T> T getService(Context context, String service) {
      return (T) context.getSystemService(service);
    }
    
    @TargetApi(HONEYCOMB)
    private static class ActivityManagerHoneycomb {
      static int getLargeMemoryClass(ActivityManager activityManager) {
        return activityManager.getLargeMemoryClass();
      }
    }
    
    static int calculateMemoryCacheSize(Context context) {
        ActivityManager am = getService(context, ACTIVITY_SERVICE);
        MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memoryInfo);
        long cacheSize = memoryInfo.availMem / 2;
        Log.d(NativeBitmapCache.class.getSimpleName(), "Calculated cache size: " + cacheSize);
		return (int) cacheSize;
//        boolean largeHeap = (context.getApplicationInfo().flags & FLAG_LARGE_HEAP) != 0;
//        int memoryClass = am.getMemoryClass();
//        if (largeHeap && SDK_INT >= HONEYCOMB) {
//          memoryClass = ActivityManagerHoneycomb.getLargeMemoryClass(am);
//        }
//        // Target ~15% of the available heap.
//        return 1024 * 1024 * memoryClass / 7;
      }

    public NativeBitmapCache(Context context) {
        this(calculateMemoryCacheSize(context));
    }
    
    public NativeBitmapCache(int maxSize) {
        mChacheEntries = new LinkedHashMap<String, NativeBitmapCacheEntry>();
        mMaxSize = maxSize;
        mSize = 0;
    }

    @Override
    public void clear() {
        for (NativeBitmapCacheEntry entry : mChacheEntries.values()) {
            nativeClear(entry.handle);
        }
        mChacheEntries.clear();
    }

    @Override
    public Bitmap get(String key) {
        synchronized (this) {
        	NativeBitmapCacheEntry entry = mChacheEntries.get(key);
            if (entry != null) {
                return nativeGetImageData(entry.handle);
            }
        }
        return null;
    }

    @Override
    public int maxSize() {
        return mMaxSize;
    }

    @Override
    public void set(String key, Bitmap image) {
        synchronized (this) {
        	NativeBitmapCacheEntry newEntry = nativeStoreImageData(image);
        	if (newEntry == null) return;
            mSize += newEntry.size;
            NativeBitmapCacheEntry previousEntry = mChacheEntries.put(key, newEntry);
            if (previousEntry != null) {
                mSize -= previousEntry.size;
                nativeClear(previousEntry.handle);
            }
        }

        trimToSize(mMaxSize);
    }

    @Override
    public int size() {
        return mSize;
    }

    private void trimToSize(int maxSize) {
        while (true) {
            String key;
            NativeBitmapCacheEntry value;
            synchronized (this) {
                if (mSize < 0 || (mChacheEntries.isEmpty() && mSize != 0)) {
                    throw new IllegalStateException(
                            getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (mSize <= maxSize || mChacheEntries.isEmpty()) {
                    break;
                }

                Map.Entry<String, NativeBitmapCacheEntry> toEvict = mChacheEntries.entrySet().iterator().next();
                key = toEvict.getKey();
                value = toEvict.getValue();
                mChacheEntries.remove(key);
                mSize -= value.size;
                nativeClear(value.handle);
            }
        }
    }

    private native Bitmap nativeGetImageData(ByteBuffer handle);

    private native NativeBitmapCacheEntry nativeStoreImageData(Bitmap bitmap);

    private native void nativeClear(ByteBuffer handle);

}
