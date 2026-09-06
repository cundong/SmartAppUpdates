# Android patch library

Build at the repository root: `./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease`.
AAR output: `ApkPatchLibrary/build/outputs/aar/`.

`src/main/cpp/bspatch.c` is the single shared bounded BSDIFF40 parser used by Android and server.
JNI adapts the stable `com.cundong.utils.PatchUtils` API; see bspatch.h for error codes.
The bzip2 source directory is shared by both builds; preserve its original notices.
Host regressions: `sh ApkPatchLibrary/scripts/run-host-native-tests.sh`.
The real two-APK integration is `./gradlew verifyFixtures`; see the root README.
