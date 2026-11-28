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
| `ApkPatchLibrarySample` | Offline Android sample using a source-built test app | [Sample](ApkPatchLibrarySample/README.md) |

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

A fresh checkout builds two revisions of a small project-owned Android app from
[the fixture source](ApkPatchLibrarySample/fixture). Both use the local Android debug
signing key. No APK download or preinstalled target app is required.

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Open **ApkPatchSample**, tap **开始本地合成**, and confirm installation when prompted.
During the build the CLI generates a BSDIFF40 patch, reconstructs the target, and checks
its SHA-256 and bytes. The Sample bundles only the old APK and patch. On the device
it reconstructs `rebuilt.apk`, checks its digest, package and version, then opens the installer.

For the **Taobao 10.59.20 → 10.59.40** Demo, place the old APK, the supplied patch and
the target APK alongside [the case manifest](Apks/taobao-10.59/fixtures.json). The next
`:app:installDebug` selects this case. The Demo displays both versions, reconstructs
Taobao on the phone and opens its system installation prompt. The build uses the full
target APK to verify the result. See [local fixtures](Apks/README.md) for filenames,
explicit case selection and the older Taobao test pair.

## Testing

Public-source checks, without the local APK pair:

```sh
./gradlew fixtureScriptTest :apkPatchLibrary:check :server:check
python3 scripts/check_repository.py
git diff --check
```

Full checks and builds, including the public sample:

```sh
./gradlew check build
./gradlew :app:assembleRelease :app:lintRelease
```

The [CI workflow](.github/workflows/ci.yml) builds and lints the public Sample, runs an
emulator upgrade test, and checks native peak RSS while reconstructing a 128 MiB test file.
To repeat the device test after assembling Debug with `-PsampleFixture=public`, select a test emulator with
`ANDROID_SERIAL`, put `adb` on PATH, and run `python3 scripts/smoke_sample.py`.

## Scope and limitations

- Supports reconstruction of a single APK. Split APK installation and Android App Bundle delivery are outside the current scope.
- Does not provide update hosting, download orchestration, signed update metadata, or rollback policy. Production integrations must define their own trust and delivery model; fixed sample hashes are not a metadata authentication service.
- Reconstruction and installation are separate steps. Android can reject an otherwise correctly reconstructed APK because of device compatibility, signing, or version constraints.
- Keep files below 2 GB when targeting 32-bit ABIs. Patch application reads and writes in 64 KiB chunks using 128 KiB of work buffers plus bzip2 state. Diff generation can use substantially more memory than the input size.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution workflow and [AGENTS.md](AGENTS.md) for repository conventions used by coding agents. Include reproduction steps and relevant build or test results when reporting bugs or proposing changes.

## License

Project code is licensed under [Apache License 2.0](LICENSE). Vendored bzip2 and bsdiff-derived code retain their respective license notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).
