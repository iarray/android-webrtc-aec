LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
CFLAGS := -Werror

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -landroid -ldl
LOCAL_CFLAGS += \
    -DWEBRTC_POSIX
LOCAL_CFLAGS += -fvisibility=hidden
LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES :=  \
	$(LOCAL_PATH)/ \
	$(LOCAL_PATH)/../external \
	$(LOCAL_PATH)/../external/webrtc \
	$(LOCAL_PATH)/../external/webrtc/base \
    $(LOCAL_PATH)/../external/webrtc/modules/include \
    $(LOCAL_PATH)/../external/webrtc/modules/audio_processing \
    $(LOCAL_PATH)/../external/webrtc/modules/audio_processing/include \

LOCAL_SHARED_LIBRARIES := webrtc_android

LOCAL_SRC_FILES := webrtc_aec.cpp \
	audio_aec.cpp \
	com_android_aec_util_WebRtcUtil.c

LOCAL_MODULE    := webrtc_jni

include $(BUILD_SHARED_LIBRARY)
