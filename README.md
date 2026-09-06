# SmartAppUpdates

Android APK delta updates using BSDIFF40. [中文说明](README.zh-CN.md).

## Projects

- `ApkPatchLibrary`: Android AAR; Java/JNI API with bounded bspatch parsing and error codes.
- `ApkPatchLibraryServer`: local Java/JNI CLI; generates patches and uses the same bspatch/bzip2 source as Android.
- `ApkPatchLibrarySample`: offline Taobao 10.65.10 (855) → 10.65.20 (856) reconstruction and system install confirmation.
- `Apks/fixtures.json`: authoritative local APK filenames, versions and SHA-256 digests.

## Build from the repository root

Use JDK 17 or 21, Python 3, a host C compiler, SDK 36, NDK 29.0.14206865 and CMake 3.31.6.
The single root Wrapper pins Gradle 8.11.1 and its official SHA-256; AGP is 8.10.1.
Set `ANDROID_HOME` or root `local.properties` (`sdk.dir=...`). No personal paths are committed.

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
./gradlew verifyFixtures :app:assembleDebug :app:lintDebug
# All checks and primary artifacts (requires the two local APKs):
./gradlew check build
```

## Local APK pair

Place the supplied files at `Apks/淘宝v10.65.10.apk` and `Apks/淘宝v10.65.20.apk`.
These are local inputs, ignored by Git; CI does not download or publish third-party APKs.
No Git LFS setup is needed. `verifyFixtures` hashes both inputs against `Apks/fixtures.json`,
generates `update.patch`, reconstructs the new APK with the shared client algorithm, and compares
both bytes and SHA-256. Verified results are cached by input/source content.

Sample assets and Java digest constants are generated under `ApkPatchLibrarySample/app/build/generated/fixtures/`.
Only the old APK and patch are bundled, not a second full new APK. All APK filenames in the runtime
are aliases of this same pair. The output is `taobao-10.65.20.apk` in the app's private external files directory.
The app verifies the output hash, package name and version before asking Android to install it.
Installation still needs user confirmation and may be rejected if a newer/incompatibly signed app is installed.

## CLI

Gradle's CLI task runs from `ApkPatchLibraryServer`, so use absolute input paths or `../Apks/...`:

```sh
./gradlew :server:run --args='diff ../Apks/淘宝v10.65.10.apk ../Apks/淘宝v10.65.20.apk /tmp/taobao.patch'
./gradlew :server:run --args='patch ../Apks/淘宝v10.65.10.apk /tmp/taobao-rebuilt.apk /tmp/taobao.patch'
```

Outputs must not already exist. `verifyFixtures` manages temporary outputs automatically.
For reusable distributions: `./gradlew :server:build`; the JAR/ZIP includes the native library
for the current host OS/architecture. It is not a universal cross-platform binary.

## Verification and limits

GitHub Actions checks native regression tests, CLI round trips, relocated CLI distributions,
and Android AAR debug/release builds on public source alone. Sample checks require the local
APKs and run via the commands above; CI does not claim to test the proprietary fixture flow.
Small synthetic malformed-input tests remain intentionally independent of the APK pair.

This is a single-APK demonstration, not a complete OTA delivery system. Production metadata
needs authenticated signatures and rollback policy; fixed test hashes are not a trust service.
32-bit native file offsets limit APKs to below 2 GB. Diff generation can require substantially
more memory than the input size. Legacy diff errors are isolated in a worker JVM; patch parsing
returns error codes. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

## License

Project code: Apache-2.0, see [LICENSE](LICENSE). Vendored bzip2 and bsdiff-derived code retain
their own notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
