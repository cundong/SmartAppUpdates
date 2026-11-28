# Contributing

Use the root Wrapper and toolchains in [README.md](README.md). Module directories no longer contain
independent wrappers. See [AGENTS.md](AGENTS.md) for API and native safety contracts.

Run relevant checks, then `git diff --check`:

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
./gradlew -PsampleFixture=public verifyFixtures :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
```

Both commands run from public source. For a local large-APK test, choose the
[Taobao case](Apks/README.md) matching your files. Keep its APKs and binary patch out of Git.
If intentionally changing the pair, inspect package/version metadata,
update the manifest SHA-256 values, regenerate and verify the byte-for-byte round trip.
GitHub Actions runs the native, CLI, Android library and public Sample checks.

Keep vendor notices and describe validation limits in changes. Project contributions use Apache-2.0;
third-party components retain their original notices.
