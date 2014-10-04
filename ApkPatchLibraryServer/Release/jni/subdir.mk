################################################################################
# Automatically-generated file. Do not edit!
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
C_SRCS += \
../jni/blocksort.c \
../jni/bzip2.c \
../jni/bzip2recover.c \
../jni/bzlib.c \
../jni/com_cundong_utils_DiffUtils.c \
../jni/com_cundong_utils_PatchUtils.c \
../jni/compress.c \
../jni/crctable.c \
../jni/decompress.c \
../jni/dlltest.c \
../jni/huffman.c \
../jni/mk251.c \
../jni/randtable.c \
../jni/spewG.c \
../jni/unzcrash.c 

OBJS += \
./jni/blocksort.o \
./jni/bzip2.o \
./jni/bzip2recover.o \
./jni/bzlib.o \
./jni/com_cundong_utils_DiffUtils.o \
./jni/com_cundong_utils_PatchUtils.o \
./jni/compress.o \
./jni/crctable.o \
./jni/decompress.o \
./jni/dlltest.o \
./jni/huffman.o \
./jni/mk251.o \
./jni/randtable.o \
./jni/spewG.o \
./jni/unzcrash.o 

C_DEPS += \
./jni/blocksort.d \
./jni/bzip2.d \
./jni/bzip2recover.d \
./jni/bzlib.d \
./jni/com_cundong_utils_DiffUtils.d \
./jni/com_cundong_utils_PatchUtils.d \
./jni/compress.d \
./jni/crctable.d \
./jni/decompress.d \
./jni/dlltest.d \
./jni/huffman.d \
./jni/mk251.d \
./jni/randtable.d \
./jni/spewG.d \
./jni/unzcrash.d 


# Each subdirectory must supply rules for building sources it contributes
jni/%.o: ../jni/%.c
	@echo 'Building file: $<'
	@echo 'Invoking: GCC C Compiler'
	gcc -O3 -Wall -c -fmessage-length=0 -MMD -MP -MF"$(@:%.o=%.d)" -MT"$(@:%.o=%.d)" -o "$@" "$<"
	@echo 'Finished building: $<'
	@echo ' '


