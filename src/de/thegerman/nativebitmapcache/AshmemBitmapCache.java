package de.thegerman.nativebitmapcache;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

public class AshmemBitmapCache extends NativeBitmapCache {

	@Override
	protected void loadNativeLibrary() {
		System.loadLibrary("ashmembitmapcache-jni");
		Log.d(NativeBitmapCache.class.getSimpleName(), "Loaded ashmembitmapcache-jni");
	}

	public AshmemBitmapCache(Context context) {
		super(context);
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

}
