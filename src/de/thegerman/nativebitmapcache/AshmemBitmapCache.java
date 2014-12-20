package de.thegerman.nativebitmapcache;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

public class AshmemBitmapCache extends NativeBitmapCache {

	private BitmapPool mBitmapPool;

	@Override
	protected void loadNativeLibrary() {
		System.loadLibrary("ashmembitmapcache-jni");
		Log.d(NativeBitmapCache.class.getSimpleName(), "Loaded ashmembitmapcache-jni");
	}

	public AshmemBitmapCache(Context context, BitmapPool pool) {
		super(context);
		mBitmapPool = pool;
	}

	@Override
	public Bitmap get(String key) {
		synchronized (this) {
			long start = System.currentTimeMillis();
			NativeBitmapCacheEntry entry = mCacheEntries.get(key);
			if (entry != null) {
				Bitmap cachedBitmap = mBitmapPool.getSuitableBitmap(entry.width, entry.height);
				if (cachedBitmap != null) {
					Log.d("BitmapPool", "Found suitable Bitmap with width " + entry.width + " and height " + entry.height + ". Pool size: " + mBitmapPool.getSize());
				}
				Bitmap nativeImageData = (cachedBitmap == null) ? nativeGetImageData(entry.handle) : nativeGetImageDataInBitmap(entry.handle, cachedBitmap);
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
	protected void trimToSize(int maxSize) {
		final int cacheCount = mCacheEntries.size();
		if (cacheCount > 2) {
			synchronized (this) {
				int checkCount = 0;
				Set<Object> toEvictSet = new HashSet<Object>();
				for (Object key : mCacheEntries.keySet()) {
					NativeBitmapCacheEntry entry = mCacheEntries.get(key);
					checkCount ++;
					if (entry != null) {
						Bitmap nativeImageData = nativeGetImageData(entry.handle);
						if (nativeImageData == null) {
							toEvictSet.add(key);
						}
					}
					if (checkCount > 2 && toEvictSet.isEmpty()) {
						break;
					}
				}
				
				if (!toEvictSet.isEmpty()) {
					for (Object key : toEvictSet) {
						NativeBitmapCacheEntry remove = mCacheEntries.remove(key);
						mSize -= remove.size;
						nativeClear(remove.handle);
					}
					Log.d(NativeBitmapCache.class.getSimpleName(), "Cache might have been purged! Old count: " + cacheCount + "; new count: " + mCacheEntries.size() + "; cache size: " + mSize);
				}
			}
		}
		super.trimToSize(maxSize);
	}
	
	protected native Bitmap nativeGetImageDataInBitmap(ByteBuffer handle, Bitmap bitmap);

}
