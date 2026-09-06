# SmartAppUpdates

Android 单 APK 增量更新示例。[English](README.md)

## 当前测试输入

唯一的真实 APK 输入由 `Apks/fixtures.json` 定义：

| 角色 | 文件 | 包名 | 版本码 |
| --- | --- | --- | --- |
| 旧版 | `Apks/淘宝v10.65.10.apk` | `com.taobao.taobao` | 855 |
| 新版 | `Apks/淘宝v10.65.20.apk` | `com.taobao.taobao` | 856 |

两个原件保留在本地、由 Git 忽略，不需要 Git LFS。构建会先校验固定 SHA-256，
再生成差分包，用与 Android 相同的 bspatch 还原新版并逐字节比较。
Sample 的 assets 和 Java 校验常量自动生成到 `app/build/generated/fixtures/`，不用手动复制或修改三处摘要。

## 统一构建

从仓库根目录运行。环境：JDK 17/21、Python 3、C 编译器、SDK 36、
NDK 29.0.14206865、CMake 3.31.6。Gradle 8.11.1 / AGP 8.10.1 固定版本。
使用 `ANDROID_HOME` 或根目录忽略的 `local.properties` 设置 SDK。

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
./gradlew verifyFixtures :app:assembleDebug :app:lintDebug
./gradlew check build
```

首次生成大 APK 差分可能耗时数分钟、使用较多内存。缓存按输入和源码内容校验。
库生成 AAR，服务端生成包含本机 JNI 的 JAR/ZIP，Sample 直接依赖当前库源码。

```sh
./gradlew :server:run --args='diff ../Apks/淘宝v10.65.10.apk ../Apks/淘宝v10.65.20.apk /tmp/taobao.patch'
./gradlew :server:run --args='patch ../Apks/淘宝v10.65.10.apk /tmp/taobao-rebuilt.apk /tmp/taobao.patch'
```

CLI 工作目录为 `ApkPatchLibraryServer`；输出必须尚不存在。

## 演示流程

复制打包的旧版和 `update.patch` 到私有目录 → 校验输入 → 合成
`taobao-10.65.20.apk` → 校验目标 SHA-256、包名和版本 → 系统安装确认。
系统可能拒绝降级或签名不兼容的安装，安装结果与合成结果分别报告。

GitHub CI 验证公开源码的原生回归、CLI 和 AAR 构建；真实淘宝 APK 的 Sample 验证在本地运行。
单元测试的小文件和损坏补丁用于边界测试，不替换真实演示输入。

该示例不包含生产更新服务、元数据签名、回滚策略或 split APK 安装方案。
32 位 ABI 文件偏移限制为 2 GB 以下；旧 diff 仍在隔离的 JVM 子进程运行，共享 bspatch 返回错误码。

参阅 [AGENTS.md](AGENTS.md)、[CONTRIBUTING.md](CONTRIBUTING.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
