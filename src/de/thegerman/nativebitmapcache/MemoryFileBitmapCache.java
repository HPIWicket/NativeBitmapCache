package de.thegerman.nativebitmapcache;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Build.VERSION_CODES.HONEYCOMB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.os.MemoryFile;
import android.util.Log;

import com.squareup.picasso.Cache;

public class MemoryFileBitmapCache implements Cache {

	private final Map<String, MemoryFileCacheEntry> mCacheEntries;
	private final int mMaxSize;
	private int mSize;

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
		return NativeBitmapCache.calculateMemoryCacheSize(context);
	}

	public MemoryFileBitmapCache(Context context) {
		this(calculateMemoryCacheSize(context));
	}

	public MemoryFileBitmapCache(int maxSize) {
		mCacheEntries = new LinkedHashMap<String, MemoryFileCacheEntry>();
		mMaxSize = maxSize;
		mSize = 0;
	}

	@Override
	public void clear() {
		for (MemoryFileCacheEntry entry : mCacheEntries.values()) {
			entry.memoryFile.close();
		}
		mCacheEntries.clear();
	}

	@Override
	public Bitmap get(String key) {
		synchronized (this) {
    	long start = System.currentTimeMillis();
			MemoryFileCacheEntry entry = mCacheEntries.get(key);
			if (entry != null) {
				InputStream inputStream = entry.memoryFile.getInputStream();
				try {
					byte[] buffer = new byte[entry.size];
					inputStream.read(buffer);
					ByteBuffer b = ByteBuffer.allocate(entry.size);
					b.put(buffer, 0, buffer.length);
					b.rewind();
					Bitmap image = Bitmap.createBitmap(entry.width, entry.height, entry.config);
					image.copyPixelsFromBuffer(b);
        	Log.d("BitmapCacheTime", "Performed get in " + (System.currentTimeMillis() - start) + "ms");
					return image;
				} catch (Exception e) {
					e.printStackTrace();
					mCacheEntries.remove(key);
					mSize -= entry.size;
					entry.memoryFile.close();
					Log.d(getClass().getSimpleName(), "File was purged");
				} catch (OutOfMemoryError e) {
					e.printStackTrace();
				}
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
			try {
				int size = image.getRowBytes() * image.getHeight(); // image.getAllocationByteCount();
				MemoryFile memoryFile = new MemoryFile(null, size);

				ByteBuffer b = ByteBuffer.allocate(size);
				image.copyPixelsToBuffer(b);
				b.rewind();
				byte[] bytes = new byte[size];
				b.get(bytes, 0, bytes.length);

				OutputStream outputStream = memoryFile.getOutputStream();
				outputStream.write(bytes);
				memoryFile.allowPurging(true);
				MemoryFileCacheEntry newEntry = new MemoryFileCacheEntry(size,
						image.getWidth(), image.getHeight(), memoryFile,
						image.getConfig());
				mSize += size;
				MemoryFileCacheEntry previousEntry = mCacheEntries.put(key,
						newEntry);
				if (previousEntry != null) {
					mSize -= previousEntry.size;
					previousEntry.memoryFile.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
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
			MemoryFileCacheEntry value;
			synchronized (this) {
				if (mSize < 0 || (mCacheEntries.isEmpty() && mSize != 0)) {
					throw new IllegalStateException(getClass().getName()
							+ ".sizeOf() is reporting inconsistent results!");
				}

				if (mSize <= maxSize || mCacheEntries.isEmpty()) {
					break;
				}

				Map.Entry<String, MemoryFileCacheEntry> toEvict = mCacheEntries
						.entrySet().iterator().next();
				key = toEvict.getKey();
				value = toEvict.getValue();
				mCacheEntries.remove(key);
				mSize -= value.size;
				value.memoryFile.close();
			}
		}
	}

	private static class MemoryFileCacheEntry {
		public final MemoryFile memoryFile;
		public final Config config;
		public final int height;
		public final int width;
		public final int size;

		public MemoryFileCacheEntry(int size, int width, int height,
				MemoryFile memoryFile, Config config) {
			super();
			this.width = width;
			this.height = height;
			this.size = size;
			this.memoryFile = memoryFile;
			this.config = config;
		}
	}
}
