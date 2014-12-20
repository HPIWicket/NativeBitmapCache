package de.thegerman.nativebitmapcache;

import android.content.Context;
import android.graphics.Bitmap;

import com.squareup.picasso.Cache;
public class ProxyBitmapCache implements Cache {
	final private Cache mRealCache;
	
	public ProxyBitmapCache(Context context) {
		mRealCache = new PicassoLruCache(context); 
	}

	@Override
	public void clear() {
		mRealCache.clear();
	}

	@Override
	public Bitmap get(String key) {
		return mRealCache.get(key);
	}

	@Override
	public int maxSize() {
		return mRealCache.maxSize();
	}

	@Override
	public void set(String key, Bitmap value) {
		mRealCache.set(key, value);
	}

	@Override
	public int size() {
		return mRealCache.size();
	}

}
