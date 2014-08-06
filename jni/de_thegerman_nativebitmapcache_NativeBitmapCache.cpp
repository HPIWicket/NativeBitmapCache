#include <jni.h>
#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <android/bitmap.h>
#include <cstring>
#include <unistd.h>

#define  LOG_TAG    "DEBUG"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

extern "C"
{
	JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeGetImageData(JNIEnv * env, jobject obj, jobject bitmap);
	JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeStoreImageData(JNIEnv * env, jobject obj, jobject handle);
	JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeClear(JNIEnv * env);
}

class JniBitmap
{
public:
	uint32_t* _storedBitmapPixels;
	AndroidBitmapInfo _bitmapInfo;
	JniBitmap()
	{
		_storedBitmapPixels = NULL;
	}
};

JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeClear(JNIEnv * env, jobject obj, jobject handle)
{
	JniBitmap* jniBitmap = (JniBitmap*) env->GetDirectBufferAddress(handle);
	if (jniBitmap->_storedBitmapPixels == NULL)	return;

	delete[] jniBitmap->_storedBitmapPixels;
	jniBitmap->_storedBitmapPixels = NULL;
	delete jniBitmap;
}

JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeGetImageData(
		JNIEnv * env, jobject obj, jstring key) {

	// get handle from key

	JniBitmap* jniBitmap = (JniBitmap*) env->GetDirectBufferAddress(handle);
	if (jniBitmap->_storedBitmapPixels == NULL) {
		LOGD("no bitmap data was stored. returning null...");
		return NULL;
	}
	//
	//creating a new bitmap to put the pixels into it - using Bitmap Bitmap.createBitmap (int width, int height, Bitmap.Config config) :
	//
	//LOGD("creating new bitmap...");
	jstring configName = env->NewStringUTF("ARGB_8888");
	jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
	jmethodID valueOfBitmapConfigFunction = env->GetStaticMethodID(
			bitmapConfigClass, "valueOf",
			"(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
	jobject bitmapConfig = env->CallStaticObjectMethod(bitmapConfigClass,
			valueOfBitmapConfigFunction, configName);

	jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
	jmethodID createBitmapFunction = env->GetStaticMethodID(bitmapCls,
			"createBitmap",
			"(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
	jobject newBitmap = env->CallStaticObjectMethod(bitmapCls,
			createBitmapFunction, jniBitmap->_bitmapInfo.height,
			jniBitmap->_bitmapInfo.width, bitmapConfig);
	//
	// putting the pixels into the new bitmap:
	//
	int ret;
	void* bitmapPixels;
	if ((ret = AndroidBitmap_lockPixels(env, newBitmap, &bitmapPixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return NULL;
	}
	uint32_t* newBitmapPixels = (uint32_t*) bitmapPixels;
	int pixelsCount = jniBitmap->_bitmapInfo.height
			* jniBitmap->_bitmapInfo.width;
	memcpy(newBitmapPixels, jniBitmap->_storedBitmapPixels,
			sizeof(uint32_t) * pixelsCount);
	AndroidBitmap_unlockPixels(env, newBitmap);
	//LOGD("returning the new bitmap");
	return newBitmap;
}

JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeStoreImageData (
		JNIEnv * env, jobject obj, jstring key, jobject bitmap) {
	AndroidBitmapInfo bitmapInfo;
	uint32_t* storedBitmapPixels = NULL;
	//LOGD("reading bitmap info...");
	int ret;
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return NULL;
	}
	LOGD(
			"width:%d height:%d stride:%d", bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
	if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888!");
	}
	//
	//read pixels of bitmap into native memory :
	//
	//LOGD("reading bitmap pixels...");
	void* bitmapPixels;
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return;
	}
	uint32_t* src = (uint32_t*) bitmapPixels;
	storedBitmapPixels = new uint32_t[bitmapInfo.height * bitmapInfo.width];
	int pixelsCount = bitmapInfo.height * bitmapInfo.width;
	memcpy(storedBitmapPixels, src, sizeof(uint32_t) * pixelsCount);
	AndroidBitmap_unlockPixels(env, bitmap);
	JniBitmap * jniBitmap = new	JniBitmap();
	jniBitmap->_bitmapInfo = bitmapInfo;
	jniBitmap->_storedBitmapPixels = storedBitmapPixels;
	return env->NewDirectByteBuffer(jniBitmap, 0);
}
