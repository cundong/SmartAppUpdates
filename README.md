# Android App 增量更新实例（Smart App Updates）

------

## 介绍

自从 Android 4.1 开始，Google引入了应用程序的增量更新。 

Link： [http://developer.android.com/about/versions/jelly-bean.html][1]
> Smart app updates is a new feature of Google Play that introduces a
> better way of delivering app updates to devices. When developers
> publish an update, Google Play now delivers only the bits that have
> changed to devices, rather than the entire APK. This makes the updates
> much lighter-weight in most cases, so they are faster to download,
> save the device’s battery, and conserve bandwidth usage on users’
> mobile data plan. On average, a smart app update is about 1/3 the
> sizeof a full APK update.

## 原理

增量更新的原理非常简单，就是将手机上已安装apk与服务器端最新apk进行二进制对比，并得到差分包，用户更新程序时，只需要下载差分包，并在本地使用差分包与已安装apk，合成新版apk。

例如，当前手机中已安装微博V1，大小为12.8MB，现在微博发布了最新版V2，大小为15.4MB，我们对两个版本的apk文件查分比对之后，发现差异只有3M，那么用户就只需要要下载一个3M的差分包，使用旧版apk与这个差分包，合成得到一个新版本apk，提醒用户安装即可，不需要整包下载15.4M的微博V2版apk。

apk文件的差分、合成，可以通过开源的二进制比较工具bsdiff来实现(Link：[http://www.daemonology.net/bsdiff/][2])

因为bsdiff依赖bzip2，所以我们还需要用到bzip2（Link：[http://www.bzip.org/downloads.html][3]）

bsdiff中，bsdiff.c用于生成查分包，bspatch.c用于合成文件。 

接下来，我们分开说，需要做3件事。

1.在服务器端，生成这两个版本微博的差分包； 

2.在手机客户端，使用已安装的旧版apk与这个差分包，合成为一个新版微博apk； 

3.校验新合成的微博客户端文件是否完成，签名时候和已安装客户端一致，如一致，提示用户安装；

## 过程分析

### 1 生成差分包

这一步需要在服务器端来实现，一般来说，每当apk有新版本需要提示用户升级，都需要运营人员在后台管理端上传新apk，上传时就应该由程序生成之前所有旧版本们与最新版的差分包。 

例如：
你的apk已经发布了3个版，V1.0、V2.0、V3.0，这时候你要在后台发布V4.0，那么，当你在服务器上传最新的V4.0包时，服务器端就应该立即生成以下差分包：

 1. V1.0 ——> V4.0的差分包；
 2. V2.0 ——> V4.0的差分包；
 3. V3.0 ——> V4.0的差分包；

ApkPatchLibraryServer工程即为Java语言实现的服务器端查分程序。

下面对ApkPatchLibraryServer做一些简单说明：

#### 1.1 C部分

ApkPatchLibraryServer/jni 中，除了以下4个：

>com_cundong_utils_DiffUtils.c
>com_cundong_utils_DiffUtils.h
>com_cundong_utils_PatchUtils.c
>com_cundong_utils_PatchUtils.h

全部来自bzip。

>com_cundong_utils_DiffUtils.c
>com_cundong_utils_DiffUtils.h

用于生成差分包。

>com_cundong_utils_PatchUtils.c
>com_cundong_utils_PatchUtils.h

用于合成新apk文件。

其中，com_cundong_utils_DiffUtils.c修改自 bsdiff/bsdiff.c，com_cundong_utils_PatchUtils.c修改自bsdiff/bspatch.c。

我们在需要将jni中的C文件，build输出为动态链接库，以供Java调用（Window环境下生成的文件名为libApkPatchLibraryServer.dll，Unix-like系统下为libApkPatchLibraryServer.so，OSX下为libApkPatchLibraryServer.dylib）。

Build成功后，将该动态链接库文件，加入环境变量，供Java语言调用。

#### 1.2 Java部分

com.cundong.utils包，为调用C语言的Java实现；
com.cundong.apkdiff包，为apk查分程序的Demo；
com.cundong.apkpatch包，为apk合并程序的Demo；

调用，com.cundong.utils.DiffUtils中genDiff()方法，可以通过传入的新旧apk路径，得到差分包。 

```java
/**
 * 类说明： 	apk diff 工具类
 * 
 * @author 	Cundong
 * @date 	2013-9-6
 * @version 1.0
 */
public class DiffUtils {

	/**
	 * 本地方法 比较路径为oldPath的apk与newPath的apk之间差异，并生成patch包，存储于patchPath
	 * 
	 * @param oldPath
	 * @param newPath
	 * @param patchPath
	 * @return
	 */
	public static native int genDiff(String oldApkPath, String newApkPath, String patchPath);
}
```

调用，com.cundong.utils.PatchUtils中patch()方法，可以通过旧apk与差分包，合成为新apk。

```java
/**
 * 类说明：   APK Patch工具类
 * 
 * @author  Cundong
 * @date    2013-9-6
 * @version 1.0
 */
public class PatchUtils {

	/**
	 * native方法
	 * 使用路径为oldApkPath的apk与路径为patchPath的补丁包，合成新的apk，并存储于newApkPath
	 * @param oldApkPath
	 * @param newApkPath
	 * @param patchPath
	 * @return
	 */
	public static native int patch(String oldApkPath, String newApkPath,
			String patchPath);
}
```

### 2.使用旧版apk与差分包，在客户端合成新apk

需要在手机客户端实现，ApkPatchLibrary工程封装了这个过程。

#### 2.1 C部分
ApkPatchLibrary/jni/bzip2目录中所有文件都来自bzip2项目

ApkPatchLibrary/jni/com_cundong_utils_PatchUtils.c、ApkPatchLibrary/jni/com_cundong_utils_PatchUtils.c实现文件的合并过程，其中com_cundong_utils_PatchUtils.c修改自bsdiff/bspatch.c。

我们需要用NDK编译出一个libApkPatchLibrary.so文件，生成的so文件位于libs/armeabi/ 下，其他 Android 工程便可以使用该libApkPatchLibrary.so文件来合成apk。

#### 2.2 Java部分

com.cundong.utils包，为调用C语言的Java实现；

调用，com.cundong.utils.PatchUtils中patch()方法，可以通过旧apk与差分包，合成为新apk。

```java
/**
 * 类说明：   APK Patch工具类
 * 
 * @author  Cundong
 * @date    2013-9-6
 * @version 1.0
 */
public class PatchUtils {

	/**
	 * native方法
	 * 使用路径为oldApkPath的apk与路径为patchPath的补丁包，合成新的apk，并存储于newApkPath
	 * @param oldApkPath
	 * @param newApkPath
	 * @param patchPath
	 * @return
	 */
	public static native int patch(String oldApkPath, String newApkPath,
			String patchPath);
}
```

### 3.校验新合成的apk文件

新包和成之后，还需要对客户端合成的apk包与最新版本apk包进行MD5或SHA1校验，如果校验码不一致，说明合成过程有问题，新合成的包将不能被安装。

## 注意事项

增量更新的前提条件，是在手机客户端能让我们读取到当前应用程序安装后的源apk，如果获取不到源apk，那么就无法进行增量更新了。

另外，如果你的应用程序不是很大，比如只有2、3M，那么完全没有必要使用增量更新，增量更新适用于apk包比较大的情况，比如游戏客户端。

## 一些说明

源码中，包含以下文件： 

> 
1.ApkPatchLibraryServer：Java语言实现的，服务器端生成差分包工程；
> 
2.ApkPatchLibrary：客户端使用的apk合成库；
> 
3.ApkPatchLibraryDemo：引用ApkPatchLibrary Library 的Demo，以新浪微博客户端的升级为例，假设手机上安装的是V4.5.0，最新版是V4.5.5，用户需要从V4.5.0升级到V4.5.5。 
> 
4.TestApk：用于测试的，旧版本的微博客户端，以及使用ApkPatchLibraryServer生成的新旧新浪微博差分包；

## Blog

[http://my.oschina.net/liucundong/blog][4]

## Update

1.目前的做法只是提供了一个例子，并没有做成开源库，打算这几天改进一下，做成一个开源库，push到GitHub上，开发ing..（2014年，8月31日）

2.已经大幅度重构原代码，并将原来的Demo程序提取成为开源库，欢迎所有人Watch、Star、Fork。（2014年，9月2日）

3.修改ReadMe.md，更加清晰的说明开源库的使用，同时进一步重构代码。（2014年，10月4日晚）


## License

    Copyright 2014 Cundong

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
