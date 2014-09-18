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

## 实现原理

增量更新的原理非常简单，就是将本地apk与服务器端最新版本比对，并得到差异包，用户更新App时只需要下载差异包。

例如，当前安装新浪微博V1，12.8MB，新浪微博最新版V2，15.4MB，经过对两个版本比较之后，发现差异只有3M，这时候用户更新的时候只需要下载一个3M的差异包便可，不需要整包下载15.4M的新版微博客户端。下载差异包后，在手机端使用旧版apk+差异包，合成得到微博最新版V2，提醒用户安装即可。 

## 实现

以新浪微博客户端的升级为例，假设手机上安装的是V4.5.0，现在最新版是V4.5.5，用户需要从V4.5.0升级到V4.5.5。 

弄清楚原理之后，我们就需要解决两个问题： 
1.如何比对两个版本的apk生成差异包； 
2.如何使用旧的apk+差异包，生成一个新apk； 

### 1）生成差异包
这一步需要在服务器端来实现，一般来说，apk有新版本之后，需要往后台管理端上传新apk，上传时就应该生成每一个旧版与最新版本的差异包。 

假设，你的apk已经发布了3个版，1.0，2.0，3.0，这时候你要在后台发布4.0，在你上传时，就应该生成：

 1. 1.0——>4.0的差异包；
 2. 2.0——>4.0的差异包；
 3. 3.0——>4.0的差异包；

选择使用这个开源二进制比较工具来实现： 
[http://www.daemonology.net/bsdiff/][2]<br/>下载后得到bsdiff-4.3.tar.gz。 

其中bsdiff.c是二进制文件比对的代码；bspatch.c是二进制文件合成的代码； 
我们将使用这个bsdiff来生成两个apk的patch包，并且使用bspatch.c来合成旧apk与patch包； 

使用bsdiff、bspatch时，还需用到bzip2： [http://www.bzip.org/downloads.html][3]<br/>下载后得到：bzip2-1.0.6.tar.gz。 

我们需要用到bzip2-1.0.6.tar.gz中以下13个文件（这里面可能有的是不需要的，我都拷贝过来了）： 

> blocksort.c  
> bzip2.c  
> bzip2recover.c  
> bzlib_private.h  
> bzlib.c 
> bzlib.h  
> compress.c  
> crctable.c  
> decompress.c  
> dlltest.c  
> huffman.c 
> randtable.c  
> spewG.c

将这13个文件拷贝至jni目录下，接下来，我们就调用bsdiff生成差异包，并且调用bspatch合成新包。 

我是在Mac下做的，使用java开发，通过jni调用C程序（bsdiff、bzip2）。 

调用，com.cundong.utils.DiffUtils.java中genDiff()方法，可以通过传入的新（newApkPath）旧（oldApkPath）apk，得到差异包（patchPath）。 

> public static native int genDiff(String oldApkPath, String
> newApkPath,String patchPath);

调用，com.cundong.utils.PatchUtils.java中patch()方法，可以通过旧apk（oldApkPath）与差异包（patchPath），得到新apk（newApkPath）。

> public static native int patch(String oldApkPath, String
> newApkPath,String patchPath);

### 2）使用旧apk+差异包，在客户端合成新apk

差异包已经在服务器端生成，我们只需要在客户端提示用户有更新，然后让用户来下载差异包，下载成功之后， 
使用本地apk与差异包，生成新版apk。 

这一步需要在Android应用中开发。 

1.首先NDK编译出一个*.so，APKPatch工程负责生成 *.so 文件，生成的so文件位于libs/armeabi/ 下，其他 Android 工程便可以使用该 *.so 文件来合成apk。 

2.调用该 *.so 文件。 
任意一个Android工程，使用该so文件，拷贝至libs\armeabi中，便可以调用patch()方法，来和成旧apk+差异包。 
附件中，test工程，就是一个调用该so文件的例子。 

## 注意事项

新包和成之后，还需要对合成升级版本的apk包及最新版本apk包进行MD5或SHA1校验，如果校验码不一致，说明合成过程有问题，新合成的包将不能被安装。

增量升级成功的前提是，用户手机端必须有能够让你拷贝出来且与你服务器用于差分的版本一致的apk，这样就存在，例如，系统内置的apk无法获取到，无法进行增量升级；对于某些与你差分版本一致，但是内容有过修改的(比如破解版apk)，这样也是无法进行增量升级的，为了防止合成补丁错误，最好在补丁合成前对旧版本的apk进行校验，保证基础包的一致性。 

## 代码说明

源码中，包含以下文件： 

> 1.服务器端生成差异包的工程：ApkPatchLibraryServer；<br/>
> 2.客户端使用的开源apk合并库：ApkPatchLibrary；<br/>
> 3.引用ApkPatchLibrary，实现增量更新的ApkPatchLibraryDemo；<br/>
> 4.旧版本的新浪微博Android客户端，以及服务端生成的新旧新浪微博差分包；<br/>

## Update

1.目前的做法只是提供了一个例子，并没有做成开源库，打算这几天改进一下，做成一个开源库，push到GitHub上，开发中。（2014年8月31日）

2.已经大幅度重构原先代码，并将原来的Demo程序提取成为开源库，欢迎所有人Watch、Star、Fork。（2014年9月2日）

  [1]: http://developer.android.com/about/versions/jelly-bean.html
  [2]: http://www.daemonology.net/bsdiff/
  [3]: http://www.bzip.org/downloads.html
