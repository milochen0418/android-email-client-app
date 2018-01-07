
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
    src/com/android/exchange/IEmailService.aidl \
    src/com/android/exchange/IEmailServiceCallback.aidl


LOCAL_PACKAGE_NAME := Email

include $(BUILD_PACKAGE)

# additionally, build unit tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))
