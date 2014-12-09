package de.thegerman.nativebitmapcache;

import android.content.Context;
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

}
