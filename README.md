# SmartAppUpdates

**APK delta generation and reconstruction for Android.**

English | [简体中文](README.zh-CN.md)

SmartAppUpdates generates a BSDIFF40 patch from two APKs and reconstructs the new APK from the exact old APK and that patch. It provides an Android library, a host command-line tool, and an offline sample covering reconstruction, integrity checks, and Android's installation flow.

Patch size depends on the differences between the APKs; a smaller download is not guaranteed.

## Components

| Component | Purpose | Documentation |
| --- | --- | --- |
| `ApkPatchLibrary` | Android AAR exposing the Java/JNI patch API | [Library](ApkPatchLibrary/README.md) |
| `ApkPatchLibraryServer` | Java/JNI CLI for generating and applying patches on macOS and Linux | [CLI](ApkPatchLibraryServer/README.md) |
| `ApkPatchLibrarySample` | Offline Android sample using a locally supplied APK pair | [Sample](ApkPatchLibrarySample/README.md) |

The CLI and Android library share the same patch parser and bzip2 sources. The parser validates patch structure and bounds and reports native failures through error codes. The CLI isolates native operations in worker JVMs, with timeouts and temporary-output cleanup.

## Build requirements

Run commands from the repository root on **macOS or Linux**. Native Windows builds are not currently supported.

| Dependency | Version / requirement |
| --- | --- |
| JDK | 17 or 21 |
| Gradle | 8.11.1, supplied by the Wrapper |
| Android Gradle Plugin | 8.10.1 |
| Android SDK | Platform 36 |
| Android NDK | 29.0.14206865 |
| CMake | 3.31.6 |
| Host tools | Python 3 and a C compiler |

Configure the Android SDK through `ANDROID_HOME` or `sdk.dir` in a local, Git-ignored `local.properties` file. The first build requires access to dependency repositories.

The Android library supports API 21+ and builds `armeabi-v7a`, `arm64-v8a`, and `x86_64` native libraries with 16 KB page alignment. The sample targets API 36.

## Quick start

### Build the library and CLI

These commands do **not** require the sample APKs:

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
```

Build outputs:

- Android AAR: `ApkPatchLibrary/build/outputs/aar/`
- Executable CLI JAR: `ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar`
- CLI distributions: `ApkPatchLibraryServer/build/distributions/`

The CLI artifacts include a native library for the build host's OS and JVM architecture. Build separately for each target platform.

### Generate and apply a patch

Use absolute paths for inputs and outputs. The output parent directory must exist, and the output file must not already exist.

```sh
java -jar ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar \
  diff /path/to/old.apk /path/to/new.apk /path/to/update.patch

java -jar ApkPatchLibraryServer/build/libs/ApkPatchLibraryServer-2.0-all.jar \
  patch /path/to/old.apk /path/to/rebuilt.apk /path/to/update.patch
```

The argument order is `diff <old> <new> <patch>` and `patch <old> <new> <patch>`. The CLI accepts any suitable input pair; it is not tied to the sample APKs. `ApkPatchLibraryServer` is a local command-line tool, not an HTTP update service.

Native operations default to a 900-second timeout. Add `--timeout-seconds 1200` before `diff` or `patch` to override it. A timeout exits with code `124` after terminating the worker. See the [CLI reference](ApkPatchLibraryServer/README.md) for other exit codes and output handling.

### Use the Android library

The sample consumes the library directly from this checkout:

```groovy
dependencies {
    implementation project(':apkPatchLibrary')
}
```

For another project, build the release AAR and add it as a local dependency. The patch API is:

```java
import com.cundong.utils.PatchUtils;

// Run on a background thread. Paths must refer to app-accessible files.
int result = PatchUtils.patch(oldApkPath, outputApkPath, patchPath);
if (result == PatchUtils.SUCCESS) {
    // Verify the output against trusted update metadata before installation.
} else {
    // Handle the corresponding PatchUtils.ERR_* code.
}
```

The old APK must match the version used to generate the patch byte for byte. A successful return indicates reconstruction completed; the caller is responsible for validating the resulting APK and initiating installation. See the [sample implementation](ApkPatchLibrarySample/app/src/main/java/com/cundong/apkpatch/example) for the complete flow.

## Run the offline sample

The sample uses the APK pair pinned in [Apks/fixtures.json](Apks/fixtures.json):

| Role | Local file | Package | Version code |
| --- | --- | --- | ---: |
| Old | `Apks/淘宝v10.65.10.apk` | `com.taobao.taobao` | 855 |
| New | `Apks/淘宝v10.65.20.apk` | `com.taobao.taobao` | 856 |

These two third-party APK samples are tracked directly in Git; Git LFS is not required. Their exact digests are pinned in the fixture manifest. Other APKs and generated binaries remain excluded. The library and CLI can also be built without the samples.

```sh
./gradlew verifyFixtures :app:assembleDebug :app:lintDebug
# Install on a connected, authorized Android device:
./gradlew :app:installDebug
```

Sample builds automatically prepare the assets. Preparation verifies both input SHA-256 digests, generates `update.patch`, reconstructs the target with the shared patch implementation, and checks both SHA-256 and byte-for-byte equality. A failed check stops the build.

Only `old.apk` and `update.patch` are bundled as sample assets. The full new APK is used for build-time verification. On device, the sample verifies its inputs, reconstructs `taobao-10.65.20.apk`, checks its digest, package name, and version, and opens the system installer. Installation requires user confirmation and remains subject to Android's signature and version checks.

Generated resources are stored under `ApkPatchLibrarySample/app/build/generated/fixtures/current/`. Do not edit them manually. Preparation uses a bounded file lock and publishes complete verified generations atomically; failed preparation preserves the previous generation. Cache validation includes inputs, generator code, build configuration, and compiled artifacts.

For slower hosts:

```sh
./gradlew verifyFixtures -PfixtureTimeoutSeconds=1200 -PfixtureLockTimeoutSeconds=1200
```

Both limits default to 900 seconds. First-time generation can take several minutes and substantial memory. Old generations are removed by `:app:clean`; do not run cleanup concurrently with a build.

## Testing

Public-source checks, without the local APK pair:

```sh
./gradlew fixtureScriptTest :apkPatchLibrary:check :server:check
python3 scripts/check_repository.py
git diff --check
```

Full local checks and builds, including the sample, require both APKs:

```sh
./gradlew check build
./gradlew :app:assembleRelease :app:lintRelease
```

The [GitHub Actions workflow](.github/workflows/ci.yml) covers native and JNI regressions, fixture-generation infrastructure, CLI round trips and relocated distributions, and Android library builds. Checkout includes the tracked APK samples, but the workflow does not run the real-APK sample flow. Synthetic boundary tests complement the real-APK checks.

## Scope and limitations

- Supports reconstruction of a single APK. Split APK installation and Android App Bundle delivery are outside the current scope.
- Does not provide update hosting, download orchestration, signed update metadata, or rollback policy. Production integrations must define their own trust and delivery model; fixed sample hashes are not a metadata authentication service.
- Reconstruction and installation are separate steps. Android can reject an otherwise correctly reconstructed APK because of device compatibility, signing, or version constraints.
- Keep files below 2 GB when targeting 32-bit ABIs. Diff generation can use substantially more memory than the input size.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution workflow and [AGENTS.md](AGENTS.md) for repository conventions used by coding agents. Include reproduction steps and relevant build or test results when reporting bugs or proposing changes.

## License

Project code is licensed under [Apache License 2.0](LICENSE). Vendored bzip2 and bsdiff-derived code retain their respective license notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).
