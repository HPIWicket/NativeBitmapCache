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

	protected final Map<String, NativeBitmapCacheEntry> mCacheEntries;
	protected final int mMaxSize;
	protected int mSize;

	@SuppressWarnings("unchecked")
	static <T> T getService(Context context, String service) {
		return (T) context.getSystemService(service);
	}

	protected void loadNativeLibrary() {
		System.loadLibrary("nativebitmapcache-jni");
		Log.d(NativeBitmapCache.class.getSimpleName(), "Loaded nativebitmapcache-jni");
	}

	@TargetApi(HONEYCOMB)
	private static class ActivityManagerHoneycomb {
		static int getLargeMemoryClass(ActivityManager activityManager) {
			return activityManager.getLargeMemoryClass();
		}
	}

	static int calculateMemoryCacheSize(Context context) {
		// return Utils.calculateMemoryCacheSize(context);
		
		ActivityManager am = getService(context, ACTIVITY_SERVICE);

		MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
		am.getMemoryInfo(memoryInfo);
		long cacheSize = memoryInfo.availMem / 2;
		Log.d(NativeBitmapCache.class.getSimpleName(), "Calculated cache size: " + cacheSize);
		return (int) cacheSize;
		/*
		 * int memoryClass = am.getMemoryClass(); // Target ~15% of the available
		 * heap. return 1024 * 1024 * memoryClass / 7;
		 */
	}

	public NativeBitmapCache(Context context) {
		this(calculateMemoryCacheSize(context));
	}

	public NativeBitmapCache(int maxSize) {
		loadNativeLibrary();
		mCacheEntries = new LinkedHashMap<String, NativeBitmapCacheEntry>();
		mMaxSize = maxSize;
		mSize = 0;
	}

	@Override
	public void clear() {
		for (NativeBitmapCacheEntry entry : mCacheEntries.values()) {
			nativeClear(entry.handle);
		}
		mCacheEntries.clear();
	}

	@Override
	public Bitmap get(String key) {
		synchronized (this) {
			long start = System.currentTimeMillis();
			NativeBitmapCacheEntry entry = mCacheEntries.get(key);
			if (entry != null) {
				Bitmap nativeImageData = nativeGetImageData(entry.handle);
				if (nativeImageData == null) {
					mCacheEntries.remove(key);
					mSize -= entry.size;
					nativeClear(entry.handle);
					Log.d("Picasso", "Native image data is null! New currentSize: " + mSize);
				} else {
					Log.d("BitmapCacheTime", "Performed get in " + (System.currentTimeMillis() - start) + "ms");
				}
				return nativeImageData;
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
			if (newEntry == null)
				return;
			mSize += newEntry.size;
			newEntry.width = image.getWidth();
			newEntry.height = image.getHeight();
			NativeBitmapCacheEntry previousEntry = mCacheEntries.put(key, newEntry);
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

	protected void trimToSize(int maxSize) {
		Log.d("Picasso", "trimToSize maxSize: " + maxSize + " currentSize: " + mSize);
		while (true) {
			String key;
			NativeBitmapCacheEntry value;
			synchronized (this) {
				if (mSize < 0 || (mCacheEntries.isEmpty() && mSize != 0)) {
					throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
				}

				if (mSize <= maxSize || mCacheEntries.isEmpty()) {
					break;
				}

				Map.Entry<String, NativeBitmapCacheEntry> toEvict = mCacheEntries.entrySet().iterator().next();
				key = toEvict.getKey();
				value = toEvict.getValue();
				mCacheEntries.remove(key);
				mSize -= value.size;
				nativeClear(value.handle);
				Log.d("Picasso", "evict entry new currentSize: " + mSize);
			}
		}
	}

	protected native Bitmap nativeGetImageData(ByteBuffer handle);

	protected native NativeBitmapCacheEntry nativeStoreImageData(Bitmap bitmap);

	protected native void nativeClear(ByteBuffer handle);

}
