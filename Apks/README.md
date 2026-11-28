# Local Taobao fixtures

## Taobao 10.59.20 → 10.59.40

Keep the following three files beside [the case manifest](taobao-10.59/fixtures.json).

| Local file | Purpose |
| --- | --- |
| `taobao-10.59.20.apk` | Baseline APK, version code 834 |
| `update.patch` | Existing BSDIFF40 patch, 71,845,578 bytes |
| `taobao-10.59.40.apk` | Expected output, version code 836 |

The version names and codes come from the two APK manifests. Both APKs use
`com.taobao.taobao`. The build validates all three hashes, replays the supplied patch
and compares the result byte for byte with the target. Only the baseline and patch go
into the Demo. Opening the Demo and tapping **开始本地合成** reconstructs the target on
the phone and launches the Android installer.

`./gradlew :app:installDebug` selects this case when its local inputs are present.
Use `-PsampleFixture=taobao-10.59` to require these inputs or `-PsampleFixture=public`
to select the small public fixture. A partial local set fails validation.

## Taobao 10.65.10 → 10.65.20

Keep these supplied files here (ignored by Git):

- 淘宝v10.65.10.apk: old, versionCode 855
- 淘宝v10.65.20.apk: new, versionCode 856

Both are com.taobao.taobao. `fixtures.json` pins the hashes and metadata.
Run `./gradlew -PsampleFixture=taobao verifyFixtures` from the repository root. Generated patches, copied assets and
round-trip outputs belong under build directories, not here. APK contents are test data only.
