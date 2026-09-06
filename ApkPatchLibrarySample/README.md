# Taobao offline Sample

From the repository root run `./gradlew verifyFixtures :app:assembleDebug :app:lintDebug`.
The two local inputs are `Apks/淘宝v10.65.10.apk` and `Apks/淘宝v10.65.20.apk`.
`Apks/fixtures.json` is authoritative. No Git LFS or hand-copied assets are used.

`verifyFixtures` depends on the current server build, validates input hashes, generates BSDIFF40,
reconstructs the exact new APK using the shared client code and compares bytes plus SHA-256.
It writes `old.apk`, `update.patch`, FixtureMetadata.java and verified.json under
`app/build/generated/fixtures/`. Failed preparation prevents the app build.

On device: copies inputs, verifies hashes, reconstructs `taobao-10.65.20.apk`, verifies its
hash/package/version and opens the system installer. Native work runs off the UI thread.
Inputs need not be installed first. Installation requires confirmation and compatible device/signing/version state.
Runtime output is in the app's private external files directory (internal storage fallback).

Target SDK 36, min SDK 21; the library supplies armeabi-v7a, arm64-v8a and x86_64.
Build success is not a substitute for device installation testing.
