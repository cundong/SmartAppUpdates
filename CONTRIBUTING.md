# Contributing

Use the root Wrapper and toolchains in [README.md](README.md). Module directories no longer contain
independent wrappers. See [AGENTS.md](AGENTS.md) for API and native safety contracts.

Run relevant checks, then `git diff --check`:

```sh
./gradlew :apkPatchLibrary:check :apkPatchLibrary:assembleRelease :server:build
./gradlew verifyFixtures :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
```

Both commands run from public source. For an optional large-APK test, pass
`-PsampleFixture=taobao` and supply the two local APKs named in `Apks/fixtures.json`. Never commit their
binaries or generated assets. If intentionally changing the pair, inspect package/version metadata,
update the manifest SHA-256 values, regenerate and verify the byte-for-byte round trip.
GitHub Actions runs the native, CLI, Android library and public Sample checks.

Keep vendor notices and describe validation limits in changes. Project contributions use Apache-2.0;
third-party components retain their original notices.
