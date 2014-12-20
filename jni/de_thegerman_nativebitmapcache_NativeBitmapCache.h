#include <jni.h>
#include <android/log.h>

#define  LOG_TAG    "DEBUG"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#ifndef _Included_de_thegerman_nativebitmapcache_NativeBitmapCache
	#define _Included_de_thegerman_nativebitmapcache_NativeBitmapCache

	#ifdef __cplusplus
	extern "C" {
	#endif
		JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeStoreImageData(JNIEnv * env, jobject obj, jobject bitmap);
		JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeGetImageData(JNIEnv * env, jobject obj, jobject handle);
		JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_AshmemBitmapCache_nativeGetImageDataInBitmap(JNIEnv * env, jobject obj, jobject handle, jobject bitmap);
		JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeClear(JNIEnv * env, jobject obj, jobject handle);
	#ifdef __cplusplus
	}
	#endif
#endif
