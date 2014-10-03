LOCAL_PATH := $(call my-dir)

#include $(CLEAR_VARS)
#
#LOCAL_MODULE    := test
##LOCAL_CPP_EXTENSION := .cpp
#LOCAL_CXXFLAGS :=
##LOCAL_CFLAGS	:= -D__cplusplus -g
#LOCAL_C_INCLUDES := $(LOCAL_PATH)
##LOCAL_EXPORT_C_INCLUDES := ./../bzip/include
#LOCAL_SRC_FILES := bzip2/bzlib.c\bzip2/blocksort.c\bzip2/compress.c\bzip2/crctable.c\bzip2/decompress.c\bzip2/huffman.c\bzip2/randtable.c\bzip2/bzip2.c\ 
#include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
#LOCAL_STATIC_LIBRARIES := test
LOCAL_MODULE    := ApkPatchLibrary
##LOCAL_CPP_EXTENSION := .cpp
LOCAL_CXXFLAGS :=
#LOCAL_CFLAGS	:= -D__cplusplus -g
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_SRC_FILES := com_cundong_utils_PatchUtils.c
LOCAL_LDLIBS := -lz -llog

include $(BUILD_SHARED_LIBRARY)


#LOCAL_PATH := $(call my-dir)
#
#include $(CLEAR_VARS)
#
#LOCAL_MODULE    := diffjni2
#LOCAL_SRC_FILES := diffjni2.c
#
#include $(BUILD_SHARED_LIBRARY)