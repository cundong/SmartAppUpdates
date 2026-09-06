# Taobao APK fixtures

These two APK samples are tracked directly in Git:

- 淘宝v10.65.10.apk: old, versionCode 855
- 淘宝v10.65.20.apk: new, versionCode 856

Both are com.taobao.taobao. `fixtures.json` pins the hashes and metadata.
Run `./gradlew verifyFixtures` from the repository root. Generated patches, copied assets and
round-trip outputs belong under build directories, not here. APK contents are test data only.
