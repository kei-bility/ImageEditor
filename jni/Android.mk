LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ImageEditor4
LOCAL_SRC_FILES := ImageEditor4.cpp

LOCAL_LDLIBS := -llog
LOCAL_LDLIBS += -ljnigraphics

include $(BUILD_SHARED_LIBRARY)
