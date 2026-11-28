# Offline Android Sample

Run `./gradlew :app:installDebug` from the repository root and open **ApkPatchSample**.
Tap **合成并安装** to reconstruct the target APK and open the system installer.

On a fresh checkout the build signs two revisions of the [fixture app](fixture) with the local debug key,
generates the patch and verifies the reconstructed bytes. The fixture app has no launcher
activity. The Sample checks the APK digest, package and version before requesting installation.

With the three local inputs for [Taobao 10.59.20 → 10.59.40](../Apks/README.md), the same command installs the Taobao Demo. It reuses the supplied patch. The older pinned pair remains available with `-PsampleFixture=taobao`. For slow hosts, the Gradle properties
`fixtureTimeoutSeconds` and `fixtureLockTimeoutSeconds` control generation and lock timeouts.
