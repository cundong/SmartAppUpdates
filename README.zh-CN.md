# SmartAppUpdates

**面向 Android 的 APK 差分生成与合成工具。**

[English](README.md) | 简体中文

SmartAppUpdates 根据新旧两个 APK 生成 BSDIFF40 格式的差分包，再通过原始旧版 APK 和差分包合成新版 APK。项目包含 Android 合成库、主机端命令行工具，以及覆盖本地合成、完整性校验和系统安装流程的离线示例。

差分包大小取决于两个 APK 的实际差异，不保证一定小于完整新版 APK。

## 项目组成

| 模块 | 用途 | 文档 |
| --- | --- | --- |
| `ApkPatchLibrary` | 提供 Java/JNI 合成接口的 Android AAR | [合成库](ApkPatchLibrary/README.md) |
| `ApkPatchLibraryServer` | 在 macOS 和 Linux 上生成、应用差分包的 Java/JNI CLI | [命令行工具](ApkPatchLibraryServer/README.md) |
| `ApkPatchLibrarySample` | 使用本地 APK 样本演示完整流程的 Android 应用 | [示例应用](ApkPatchLibrarySample/README.md) |

CLI 与 Android 库共用补丁解析器和 bzip2 源码。解析器检查补丁结构与数据边界，并通过错误码报告原生层失败。CLI 在独立 JVM 子进程中执行原生操作，提供超时控制和临时输出清理。

## 构建环境

请在 **macOS 或 Linux** 上，从仓库根目录执行命令。目前不支持原生 Windows 构建。

| 依赖 | 版本或要求 |
| --- | --- |
| JDK | 17 或 21 |
| Gradle | 8.11.1，由仓库内 Wrapper 提供 |
| Android Gradle Plugin | 8.10.1 |
| Android SDK | Platform 36 |
| Android NDK | 29.0.14206865 |
| CMake | 3.31.6 |
| 主机工具 | Python 3、C 编译器 |

通过 `ANDROID_HOME` 或本地 `local.properties` 中的 `sdk.dir` 配置 Android SDK。`local.properties` 已由 Git 忽略。首次构建需要能够访问依赖仓库。

Android 库支持 API 21 及以上，构建 `armeabi-v7a`、`arm64-v8a` 和 `x86_64` 原生库，并启用 16 KB 页面对齐。示例应用的 target SDK 为 36。

## 快速开始

### 构建合成库与 CLI

以下命令**不需要**示例 APK：

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
```

构建产物：

- Android AAR：`ApkPatchLibrary/build/outputs/aar/`
- 可执行 CLI JAR：`ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar`
- CLI 分发包：`ApkPatchLibraryServer/build/distributions/`

CLI 产物包含与构建主机操作系统、JVM 架构对应的原生库。不同目标平台需要分别构建。

### 生成与应用差分包

建议使用绝对路径。输出文件的父目录必须已存在，输出文件本身必须尚不存在。

```sh
java -jar ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar \
  diff /path/to/old.apk /path/to/new.apk /path/to/update.patch

java -jar ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar \
  patch /path/to/old.apk /path/to/rebuilt.apk /path/to/update.patch
```

参数顺序分别为 `diff <old> <new> <patch>` 和 `patch <old> <new> <patch>`。CLI 可以处理符合要求的其他输入文件，不绑定示例 APK。`ApkPatchLibraryServer` 是本地命令行工具，不提供 HTTP 更新服务。

原生操作默认超时为 900 秒。在 `diff` 或 `patch` 前添加 `--timeout-seconds 1200` 即可调整。超时后会终止子进程，并以退出码 `124` 结束。其他退出码和输出处理规则见 [CLI 文档](ApkPatchLibraryServer/README.md)。

### 接入 Android 合成库

示例应用直接依赖仓库中的库模块：

```groovy
dependencies {
    implementation project(':apkPatchLibrary')
}
```

在其他工程中使用时，可构建 release AAR 并将其作为本地依赖引入。核心接口如下：

```java
import com.cundong.utils.PatchUtils;

// 在后台线程执行，所有路径均须指向应用可访问的文件。
int result = PatchUtils.patch(oldApkPath, outputApkPath, patchPath);
if (result == PatchUtils.SUCCESS) {
    // 安装前，根据可信的更新元数据校验输出 APK。
} else {
    // 根据对应的 PatchUtils.ERR_* 错误码处理失败。
}
```

用于合成的旧版 APK 必须与生成差分包时的旧版文件逐字节一致。接口成功返回表示合成完成，调用方仍需校验输出 APK 并发起安装。完整流程可参考[示例源码](ApkPatchLibrarySample/app/src/main/java/com/cundong/apkpatch/example)。

## 运行离线示例

示例使用 [Apks/fixtures.json](Apks/fixtures.json) 固定的 APK 样本：

| 角色 | 本地文件 | 包名 | 版本码 |
| --- | --- | --- | ---: |
| 旧版 | `Apks/淘宝v10.65.10.apk` | `com.taobao.taobao` | 855 |
| 新版 | `Apks/淘宝v10.65.20.apk` | `com.taobao.taobao` | 856 |

仓库**不分发这些第三方 APK**。构建示例前，需要在本地提供与配置匹配的文件。APK 已由 Git 忽略，无需配置 Git LFS。单独构建合成库和 CLI 不需要这些文件。

```sh
./gradlew verifyFixtures :app:assembleDebug :app:lintDebug
# 安装到已连接并授权 USB 调试的 Android 设备：
./gradlew :app:installDebug
```

示例构建会自动准备资源：先校验两个输入文件的 SHA-256，再生成 `update.patch`，使用共享的补丁实现合成目标 APK，最后校验 SHA-256 并逐字节比较。任一校验失败都会阻止构建。

示例 assets 只打包 `old.apk` 和 `update.patch`；完整新版 APK 用于构建阶段的对照验证。真机运行时，应用校验输入、合成 `taobao-10.65.20.apk`、检查摘要、包名和版本，然后打开系统安装界面。安装需要用户确认，并受 Android 签名和版本规则约束。

生成资源位于 `ApkPatchLibrarySample/app/build/generated/fixtures/current/`，请勿手工修改。生成过程使用有超时限制的文件锁，并以原子方式发布完整、已验证的结果；失败时保留上一份结果。缓存检查覆盖输入文件、生成脚本、构建配置及编译产物。

主机性能较低时，可适当增加超时：

```sh
./gradlew verifyFixtures -PfixtureTimeoutSeconds=1200 -PfixtureLockTimeoutSeconds=1200
```

操作超时与锁等待超时默认均为 900 秒。首次生成可能耗时数分钟，并占用较多内存。`:app:clean` 会清理历史生成结果，请勿在其他构建运行期间执行清理。

## 测试与验证

不依赖本地 APK 的公开源码检查：

```sh
./gradlew fixtureScriptTest :apkPatchLibrary:check :server:check
python3 scripts/check_repository.py
git diff --check
```

包含示例的完整本地检查与构建需要两个 APK：

```sh
./gradlew check build
./gradlew :app:assembleRelease :app:lintRelease
```

[GitHub Actions 工作流](.github/workflows/ci.yml) 覆盖原生代码与 JNI 回归、资源生成流程、CLI 差分合成闭环与分发包迁移测试，以及 Android 库构建。CI 不下载第三方 APK，也不执行真实 APK 的示例流程。小型合成数据的边界测试与真实 APK 验证相互补充。

## 适用范围与限制

- 支持单个 APK 的合成，当前不包含 split APK 安装或 Android App Bundle 分发方案。
- 不提供更新托管、下载调度、更新元数据签名或回滚策略。生产接入需要自行建立可信的更新分发机制；示例中的固定摘要不能替代元数据认证。
- 合成与安装是两个独立步骤。即使 APK 已正确还原，Android 仍可能因设备兼容性、签名或版本限制拒绝安装。
- 面向 32 位 ABI 时，文件应小于 2 GB。差分生成所需内存可能显著高于输入文件大小。

## 参与贡献

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)，编码代理使用的仓库约定见 [AGENTS.md](AGENTS.md)。提交问题或改动时，请附上复现步骤以及相关构建、测试结果。

## 许可证

项目代码采用 [Apache License 2.0](LICENSE)。内置 bzip2 和源自 bsdiff 的代码保留各自的许可声明，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [NOTICE](NOTICE)。
