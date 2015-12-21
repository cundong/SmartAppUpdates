# Android应用增量更新 - Smart App Updates

------

## 介绍

你所看到的，是一个用于Android应用程序增量更新的库。

包括客户端、服务端两部分代码。

## 原理

自从 Android 4.1 开始， [Google Play 引入了应用程序的增量更新功能][1]，App使用该升级方式，可节省约2/3的流量。

> Smart app updates is a new feature of Google Play that introduces a
> better way of delivering app updates to devices. When developers
> publish an update, Google Play now delivers only the bits that have
> changed to devices, rather than the entire APK. This makes the updates
> much lighter-weight in most cases, so they are faster to download,
> save the device’s battery, and conserve bandwidth usage on users’
> mobile data plan. On average, a smart app update is about 1/3 the
> sizeof a full APK update.

现在国内主流的应用市场也都支持应用的增量更新了。

增量更新的原理非常简单，就是将手机上已安装apk与服务器端最新apk进行二进制对比，并得到差分包，用户更新程序时，只需要下载差分包，并在本地使用差分包与已安装apk，合成新版apk。

例如，当前手机中已安装微博V1，大小为12.8MB，现在微博发布了最新版V2，大小为15.4MB，我们对两个版本的apk文件差分比对之后，发现差异只有3M，那么用户就只需要要下载一个3M的差分包，使用旧版apk与这个差分包，合成得到一个新版本apk，提醒用户安装即可，不需要整包下载15.4M的微博V2版apk。

apk文件的差分、合成，可以通过 [开源的二进制比较工具 bsdiff][2] 来实现

因为bsdiff依赖bzip2，所以我们还需要用到 [bzip2][3]

bsdiff中，`bsdiff.c`用于生成差分包，`bspatch.c`用于合成文件。 

接下来，我们分开说，需要做3件事。

* 在服务器端，生成这两个版本微博的差分包； 

* 在手机客户端，使用已安装的旧版apk与这个差分包，合成为一个新版微博apk； 

* 校验新合成的微博客户端文件是否完整，签名时候和已安装客户端一致，如一致，提示用户安装；

## 过程分析

### 1 生成差分包

这一步需要在服务器端来实现，一般来说，每当apk有新版本需要提示用户升级，都需要运营人员在后台管理端上传新apk，上传时就应该由程序生成之前所有旧版本们与最新版的差分包。 

例如：
你的apk已经发布了3个版，V1.0、V2.0、V3.0，这时候你要在后台发布V4.0，那么，当你在服务器上传最新的V4.0包时，服务器端就应该立即生成以下差分包：

 1. V1.0 ——> V4.0的差分包；
 2. V2.0 ——> V4.0的差分包；
 3. V3.0 ——> V4.0的差分包；

ApkPatchLibraryServer工程即为Java语言实现的服务器端差分程序。

下面对ApkPatchLibraryServer做一些简单说明：

#### 1.1 C部分

ApkPatchLibraryServer/jni 中，除了以下4个：

>com_cundong_utils_DiffUtils.c

>com_cundong_utils_DiffUtils.h

>com_cundong_utils_PatchUtils.c

>com_cundong_utils_PatchUtils.h

jni/bzip2目录中的文件，全部来自bzip2项目。

>com_cundong_utils_DiffUtils.c

>com_cundong_utils_DiffUtils.h

用于生成差分包。

>com_cundong_utils_PatchUtils.c

>com_cundong_utils_PatchUtils.h

用于合成新apk文件。

`com_cundong_utils_DiffUtils.c` 修改自 `bsdiff/bsdiff.c`，`com_cundong_utils_PatchUtils.c`修改自`bsdiff/bspatch.c`。

我们在需要将jni中的C文件，build输出为动态链接库，以供Java调用（Window环境下生成的文件名为libApkPatchLibraryServer.dll，Unix-like系统下为libApkPatchLibraryServer.so，OSX下为libApkPatchLibraryServer.dylib）。

Build成功后，将该动态链接库文件，加入环境变量，供Java语言调用。

`com_cundong_utils_DiffUtils.c` 中 `Java_com_cundong_utils_DiffUtils_genDiff()` 方法，用于生成差分包的：

```C

JNIEXPORT jint JNICALL Java_com_cundong_utils_DiffUtils_genDiff(JNIEnv *env,
		jclass cls, jstring old, jstring new, jstring patch) {
	int argc = 4;
	char * argv[argc];
	argv[0] = "bsdiff";
	argv[1] = (char*) ((*env)->GetStringUTFChars(env, old, 0));
	argv[2] = (char*) ((*env)->GetStringUTFChars(env, new, 0));
	argv[3] = (char*) ((*env)->GetStringUTFChars(env, patch, 0));

	printf("old apk = %s \n", argv[1]);
	printf("new apk = %s \n", argv[2]);
	printf("patch = %s \n", argv[3]);

	int ret = genpatch(argc, argv);

	printf("genDiff result = %d ", ret);

	(*env)->ReleaseStringUTFChars(env, old, argv[1]);
	(*env)->ReleaseStringUTFChars(env, new, argv[2]);
	(*env)->ReleaseStringUTFChars(env, patch, argv[3]);

	return ret;
}

```
`com_cundong_utils_PatchUtils.c` 中 `Java_com_cundong_utils_PatchUtils_patch()` 方法，用于合成新的APK；

```C
JNIEXPORT jint JNICALL Java_com_cundong_utils_PatchUtils_patch
  (JNIEnv *env, jclass cls,
			jstring old, jstring new, jstring patch){
	int argc = 4;
	char * argv[argc];
	argv[0] = "bspatch";
	argv[1] = (char*) ((*env)->GetStringUTFChars(env, old, 0));
	argv[2] = (char*) ((*env)->GetStringUTFChars(env, new, 0));
	argv[3] = (char*) ((*env)->GetStringUTFChars(env, patch, 0));

	printf("old apk = %s \n", argv[1]);
	printf("patch = %s \n", argv[3]);
	printf("new apk = %s \n", argv[2]);

	int ret = applypatch(argc, argv);

	printf("patch result = %d ", ret);

	(*env)->ReleaseStringUTFChars(env, old, argv[1]);
	(*env)->ReleaseStringUTFChars(env, new, argv[2]);
	(*env)->ReleaseStringUTFChars(env, patch, argv[3]);
	return ret;
}
```

#### 1.2 Java部分

com.cundong.utils包，为调用C语言的Java实现；
com.cundong.apkdiff包，为apk差分程序的Demo；
com.cundong.apkpatch包，为apk合并程序的Demo；

调用，`com.cundong.utils.DiffUtils`中`genDiff()`方法，可以通过传入的新旧apk路径，得到差分包。 

```java
/**
 * 类说明： 	APK Diff工具类
 * 
 * @author 	Cundong
 * @date 	2013-9-6
 * @version 1.0
 */
public class DiffUtils {

	/**
	 * native方法 比较路径为oldPath的apk与newPath的apk之间差异，并生成patch包，存储于patchPath
	 * 
	 * 返回：0，说明操作成功
	 *  
	 * @param oldApkPath 示例:/sdcard/old.apk
	 * @param newApkPath 示例:/sdcard/new.apk
	 * @param patchPath  示例:/sdcard/xx.patch
	 * @return
	 */
	public static native int genDiff(String oldApkPath, String newApkPath, String patchPath);
}
```

调用，`com.cundong.utils.PatchUtils`中`patch()`方法，可以通过旧apk与差分包，合成为新apk。

```java
/**
 * 类说明： 	APK Patch工具类
 * 
 * @author 	Cundong
 * @date 	2013-9-6
 * @version 1.0
 */
public class PatchUtils {

	/**
	 * native方法 使用路径为oldApkPath的apk与路径为patchPath的补丁包，合成新的apk，并存储于newApkPath
	 * 
	 * 返回：0，说明操作成功
	 * 
	 * @param oldApkPath 示例:/sdcard/old.apk
	 * @param newApkPath 示例:/sdcard/new.apk
	 * @param patchPath  示例:/sdcard/xx.patch
	 * @return
	 */
	public static native int patch(String oldApkPath, String newApkPath,
			String patchPath);
}
```

### 2.使用旧版apk与差分包，在客户端合成新apk

需要在手机客户端实现，ApkPatchLibrary工程封装了这个过程。

#### 2.1 C部分
同ApkPatchLibraryServer工程一样，ApkPatchLibrary/jni/bzip2 目录中所有文件都来自bzip2项目。

`ApkPatchLibrary/jni/com_cundong_utils_PatchUtils.c`、`ApkPatchLibrary/jni/com_cundong_utils_PatchUtils.c`实现文件的合并过程，其中`com_cundong_utils_PatchUtils.c`修改自`bsdiff/bspatch.c`。

我们需要用NDK编译出一个libApkPatchLibrary.so文件，生成的so文件位于libs/armeabi/ 下，其他 Android 工程便可以使用该libApkPatchLibrary.so文件来合成apk。

`com_cundong_utils_PatchUtils.Java_com_cundong_utils_PatchUtils_patch()`方法，即为生成差分包的代码：

```C
/*
 * Class:     com_cundong_utils_PatchUtils
 * Method:    patch
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_cundong_utils_PatchUtils_patch(JNIEnv *env,
		jobject obj, jstring old, jstring new, jstring patch) {

	char * ch[4];
	ch[0] = "bspatch";
	ch[1] = (char*) ((*env)->GetStringUTFChars(env, old, 0));
	ch[2] = (char*) ((*env)->GetStringUTFChars(env, new, 0));
	ch[3] = (char*) ((*env)->GetStringUTFChars(env, patch, 0));

	__android_log_print(ANDROID_LOG_INFO, "ApkPatchLibrary", "old = %s ", ch[1]);
	__android_log_print(ANDROID_LOG_INFO, "ApkPatchLibrary", "new = %s ", ch[2]);
	__android_log_print(ANDROID_LOG_INFO, "ApkPatchLibrary", "patch = %s ", ch[3]);

	int ret = applypatch(4, ch);

	__android_log_print(ANDROID_LOG_INFO, "ApkPatchLibrary", "applypatch result = %d ", ret);

	(*env)->ReleaseStringUTFChars(env, old, ch[1]);
	(*env)->ReleaseStringUTFChars(env, new, ch[2]);
	(*env)->ReleaseStringUTFChars(env, patch, ch[3]);

	return ret;
}
```

#### 2.2 Java部分

com.cundong.utils包，为调用C语言的Java实现；

调用，`com.cundong.utils.PatchUtils中patch()`方法，可以通过旧apk与差分包，合成为新apk。

```java
/**
 * 类说明： 	APK Patch工具类
 * 
 * @author 	Cundong
 * @date 	2013-9-6
 * @version 1.0
 */
public class PatchUtils {

	/**
	 * native方法 使用路径为oldApkPath的apk与路径为patchPath的补丁包，合成新的apk，并存储于     newApkPath
	 * 
	 * 返回：0，说明操作成功
	 * 
	 * @param oldApkPath 示例:/sdcard/old.apk
	 * @param newApkPath 示例:/sdcard/new.apk
	 * @param patchPath  示例:/sdcard/xx.patch
	 * @return
	 */
	public static native int patch(String oldApkPath, String newApkPath,
			String patchPath);
}
```

### 3.校验新合成的apk文件

在执行patch之前，需要先读取本地安装旧版本APK的MD5或SHA1，判断当前安装的文件是否为合法版本，同样，patch得到新包之后，也需要对它进行MD5或SHA1校验，校验失败，说明合成过程有问题。

## 注意事项

增量更新的前提条件，是在手机客户端能让我们读取到当前应用程序安装后的源apk，如果获取不到源apk，那么就无法进行增量更新了。

另外，如果你的应用程序不是很大，比如只有2、3M，那么完全没有必要使用增量更新，增量更新只适用于apk包比较大的情况，比如手机游戏客户端。

## 一些说明

各目录说明如下： 

* ApkPatchLibraryServer：服务器端生成差分包工程，使用Java实现；

* ApkPatchLibrary：客户端使用的apk合成库，用于生成libApkPatchLibrary.so，使用Eclipse开发；

* ApkPatchLibrarySample：一个Sample，手机上安装 Weibo5.5.apk，通过与SD卡上预先存放的weibo.patch文件进行合并，实现升级过程，使用AndroidStudio开发。 

另外，我把 ApkPatchLibraryServer、ApkPatchLibrarySample 中用到的Weibo5.5.apk，Weibo5.6.apk，以及使用ApkPatchLibraryServer生成的差分包(Weibo5.5.apk->Weibo5.6.apk)， [都通过云盘共享了][5]。

## 关于我

* Blog: [http://my.oschina.net/liucundong/blog][4]
* Mail: cundong.liu#gmail.com

## Update

1.目前的做法只是提供了一个例子，并没有做成开源库，打算这几天改进一下，做成一个开源库，push到GitHub上，开发ing..（2014年，8月31日）

2.已经大幅度重构原代码，并将原来的Demo程序提取成为开源库，欢迎所有人Watch、Star、Fork。（2014年，9月2日）

3.修改ReadMe.md，更加清晰的说明开源库的使用，同时进一步重构代码。（2014年，10月4日晚）

4.调整ApkPatchLibraryServer工程目录。（2015年，4月24日)

5.上传[一个演示demo ApkPatchLibrarySample.apk][6]。（2015-4-26）

6.ApkPatchLibrarySample重新使用AndroidStudio开发，修改文件MD5的对比逻辑。

## License

    Copyright 2015 Cundong

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  [1]: http://developer.android.com/about/versions/jelly-bean.html
  [2]: http://www.daemonology.net/bsdiff/
  [3]: http://www.bzip.org/downloads.html
  [4]: http://my.oschina.net/liucundong/blog
  [5]: http://pan.baidu.com/s/1T5Szc#path=%252FSmartAppUpdates
  [6]: https://github.com/cundong/SmartAppUpdates/blob/master/ApkPatchLibrarySample.apk