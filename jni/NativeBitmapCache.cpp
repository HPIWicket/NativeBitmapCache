#include "de_thegerman_nativebitmapcache_NativeBitmapCache.h"
#include "ashmem.h"

#include <stdio.h>
#include <sstream>
#include <android/bitmap.h>
#include <cstring>
#include <unistd.h>
#include <sys/mman.h>

int savedPages = 0;

class JavaClasses {
	jclass nativeBitmapCacheEntryClass;
	jmethodID nativeBitmapCacheEntryConstructor;
	jfieldID nativeBitmapCacheEntrySizeField;
	jfieldID nativeBitmapCacheEntryHandleField;
	JNIEnv * jniEnv;

public:
	JavaClasses(JNIEnv *env) {
		jniEnv = env;
		nativeBitmapCacheEntryClass = jniEnv->FindClass("de/thegerman/nativebitmapcache/NativeBitmapCacheEntry");
		nativeBitmapCacheEntryConstructor = jniEnv->GetMethodID(nativeBitmapCacheEntryClass, "<init>", "()V");
		nativeBitmapCacheEntryHandleField = jniEnv->GetFieldID(nativeBitmapCacheEntryClass, "handle", "Ljava/nio/ByteBuffer;");
		nativeBitmapCacheEntrySizeField = jniEnv->GetFieldID(nativeBitmapCacheEntryClass, "size", "I");
	}

	jobject createNativeBitmapCacheEntry() {
		return jniEnv->NewObject(nativeBitmapCacheEntryClass, nativeBitmapCacheEntryConstructor, "");
	}

	void setNativeBitmapCacheEntrySize(jobject nativeBitmapCacheEntryObject, int size) {
		jniEnv->SetIntField(nativeBitmapCacheEntryObject, nativeBitmapCacheEntrySizeField, size);
	}

	void setNativeBitmapCacheEntryHandle(jobject nativeBitmapCacheEntryObject, jobject handleObject) {
		jniEnv->SetObjectField(nativeBitmapCacheEntryObject, nativeBitmapCacheEntryHandleField, handleObject);
	}
};

class JniBitmap {
public:
	int _storedBitmapPixelsSize;
	int _bitmapPixelsFd;
	uint32_t* _storedBitmapPixels;
	AndroidBitmapInfo _bitmapInfo;
	JniBitmap() {
		_storedBitmapPixels = NULL;
	}
};

JNIEXPORT void JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeClear(JNIEnv * env, jobject obj, jobject handle) {
	JniBitmap* jniBitmap = (JniBitmap*) env->GetDirectBufferAddress(handle);
	int fd = jniBitmap->_bitmapPixelsFd;

	if (jniBitmap->_storedBitmapPixels != NULL) {
		LOGD("delete stored pixel");
		munmap(jniBitmap->_storedBitmapPixels,jniBitmap->_storedBitmapPixelsSize);
		//delete[] jniBitmap->_storedBitmapPixels;
		jniBitmap->_storedBitmapPixels = NULL;
	}

	LOGD("close file descriptor");
    close(fd);

	LOGD("delete jniBitmap");
	delete jniBitmap;

    ashmem_unpin_region(fd, 0, 0);
}

JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeGetImageData(
		JNIEnv * env, jobject obj, jobject handle) {

	JniBitmap* jniBitmap = (JniBitmap*) env->GetDirectBufferAddress(handle);

	if (jniBitmap->_storedBitmapPixels == NULL) {
		LOGD("no bitmap data was stored. returning null...");
		return NULL;
	}

	if (ashmem_pin_region(jniBitmap->_bitmapPixelsFd, 0, 0) == ASHMEM_WAS_PURGED) {
		return NULL;
	}

	//
	//creating a new bitmap to put the pixels into it - using Bitmap Bitmap.createBitmap (int width, int height, Bitmap.Config config) :
	//, fd
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
			createBitmapFunction, jniBitmap->_bitmapInfo.width,
			jniBitmap->_bitmapInfo.height, bitmapConfig);
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

    ashmem_unpin_region(jniBitmap->_bitmapPixelsFd, 0, 0);
	//LOGD("returning the new bitmap");
	return newBitmap;
}

JNIEXPORT jobject JNICALL Java_de_thegerman_nativebitmapcache_NativeBitmapCache_nativeStoreImageData(
		JNIEnv * env, jobject obj, jobject bitmap) {
	JavaClasses* javaClasses = new JavaClasses(env);
	AndroidBitmapInfo bitmapInfo;
	uint32_t* storedBitmapPixels = NULL;
	//LOGD("reading bitmap info...");
	int ret;
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return NULL;
	}
	LOGD("width:%d height:%d stride:%d", bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
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
		return NULL;
	}

	//storedBitmapPixels = new uint32_t[bitmapInfo.height * bitmapInfo.width];
	int pixelsDataSize = bitmapInfo.height * bitmapInfo.width * sizeof(uint32_t);
	std::stringstream ss;
	ss << "NBCE" << savedPages;
	const char* areaName = ss.str().c_str();
	int fd = ashmem_create_region(areaName, pixelsDataSize);
	if (fd < 0) {
		LOGE("ashmem_create_region %s failed ! error=%d", areaName, fd);
		return NULL;
	}
	LOGE("ashmem_create_region %s successful !", areaName);
	storedBitmapPixels = (uint32_t*) mmap(NULL, pixelsDataSize, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
	if (!storedBitmapPixels) {
		LOGE("mmap failed !");
		return NULL;
	}

	memcpy(storedBitmapPixels, bitmapPixels, pixelsDataSize);
	AndroidBitmap_unlockPixels(env, bitmap);
	JniBitmap * jniBitmap = new JniBitmap();
	jniBitmap->_storedBitmapPixelsSize = pixelsDataSize;
	LOGD("_storedBitmapPixelsSize %d", pixelsDataSize);
	jniBitmap->_bitmapInfo = bitmapInfo;
	jniBitmap->_bitmapPixelsFd = fd;
	jniBitmap->_storedBitmapPixels = storedBitmapPixels;
	jobject nativeBitmapCacheEntryObject = javaClasses->createNativeBitmapCacheEntry();
	javaClasses->setNativeBitmapCacheEntrySize(nativeBitmapCacheEntryObject, pixelsDataSize);
	javaClasses->setNativeBitmapCacheEntryHandle(nativeBitmapCacheEntryObject, env->NewDirectByteBuffer(jniBitmap, sizeof(JniBitmap)));

    ashmem_unpin_region(fd, 0, 0);
	savedPages++;
	return nativeBitmapCacheEntryObject;
}
