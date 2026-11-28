# Offline Android Sample

Run `./gradlew :app:installDebug` from the repository root and open **ApkPatchSample**.
Tap **开始本地合成** to reconstruct the target APK and open the system installer.

The build signs two revisions of the [fixture app](fixture) with the local debug key,
generates the patch and verifies the reconstructed bytes. The fixture app has no launcher
activity. The Sample checks the APK digest, package and version before requesting installation.

For the optional Taobao inputs, see [Apks](../Apks/README.md) and pass
`-PsampleFixture=taobao` when building or installing. For slow hosts, the Gradle properties
`fixtureTimeoutSeconds` and `fixtureLockTimeoutSeconds` control generation and lock timeouts.
