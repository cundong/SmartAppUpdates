# ApkPatchLibraryServer

`ApkPatchLibraryServer` 是本地 Java + JNI 命令行工具，生成和应用与 Android
客户端兼容的 `BSDIFF40` 补丁。它不是网络服务，不包含固定 APK 路径。

## 环境

- JDK 17
- macOS 或 Linux
- 系统 C 编译器（macOS 使用 `/usr/bin/gcc`，实际为 Apple Clang）
- 项目自带 Gradle Wrapper

## 命令

在仓库根目录执行：

```bash
./gradlew :server:run --args='diff ../Apks/淘宝v10.65.10.apk ../Apks/淘宝v10.65.20.apk /tmp/taobao.patch'
./gradlew :server:run --args='patch ../Apks/淘宝v10.65.10.apk /tmp/taobao-rebuilt.apk /tmp/taobao.patch'
```

参数顺序固定：

```text
diff  <old> <new> <patch>
patch <old> <new> <patch>
```

输入必须是存在、可读、非空的普通文件。输出父目录必须已存在且可写；为防止误覆盖，
输出文件必须尚不存在，并且不能与任一输入指向同一文件。CLI 只让 native 写同目录临时
文件，成功后才原子发布；失败或正常中断会清理临时文件，不会暴露半成品目标路径。

公开退出码：

| 退出码 | 含义 |
| ---: | --- |
| `0` | 成功 |
| `64` | 命令或参数数量错误 |
| `66` | 输入路径无效 |
| `70` | native worker 加载或执行失败 |
| `73` | 输出路径无效或已存在 |

原始 bsdiff 实现会在部分错误上直接结束进程，因此 CLI 把 native 调用隔离到子
JVM，并将其非零结果统一映射为 `70`。

## 构建与验证

```bash
./gradlew :server:clean :server:e2eTest
./gradlew :server:build
```

`e2eTest` 在独立 JVM 中生成确定性小文件，执行完整的 `diff -> patch`，逐字节比较结果，
并验证已有输出不会被覆盖、native 失败不残留目标或临时文件。`distributionE2e` 还会复制
`installDist` 到新目录，通过分发脚本再跑同一闭环。`check` 和 `build` 自动执行两套测试。

native 产物位于：

- macOS：`build/native/libApkPatchLibraryServer.dylib`
- Linux：`build/native/libApkPatchLibraryServer.so`

默认 JAR 主类是 `com.cundong.cli.ApkPatchCli`。native 库已打进 JAR，运行时会提取到
进程私有临时目录，因此 JAR 和 `installDist`/ZIP 均可整体搬运：

```bash
java -jar build/libs/ApkPatchLibraryServer-2.0-all.jar \
  diff ../Apks/淘宝v10.65.10.apk ../Apks/淘宝v10.65.20.apk /tmp/taobao.patch
```

Patch and bzip2 sources are shared with the Android library. The CLI remains generic;
`./gradlew verifyFixtures` runs the real Taobao pair from `Apks/fixtures.json`.
