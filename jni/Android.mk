LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE :=  libnativebitmapcache-jni
LOCAL_SRC_FILES := NativeBitmapCache.cpp

LOCAL_LDLIBS += -llog
LOCAL_LDFLAGS += -ljnigraphics

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE :=  libashmembitmapcache-jni
LOCAL_SRC_FILES := ashmem.c AshmemBitmapCache.cpp

LOCAL_LDLIBS += -llog
LOCAL_LDFLAGS += -ljnigraphics

include $(BUILD_SHARED_LIBRARY)