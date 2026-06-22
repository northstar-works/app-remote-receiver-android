# F-Droid `resources.arsc` / `res1 must be zero` fix

This build bumps App Remote Receiver to:

- Version name: `1.0.3`
- Version code: `4`
- Android Gradle Plugin: `8.7.0`

Why: the prior APK was built with Android Gradle Plugin `8.7.3`. Some F-Droid/androguard runners fail while parsing that APK's `resources.arsc` with:

```text
androguard.core.bytecodes.axml.ResParserError: res1 must be zero!
```

The APK can still install on Android/Fire TV, but `fdroid update` fails before rebuilding the repo index. When that happens, the website still deploys but the new app does not appear in the repo listing.

## Build

```bash
./gradlew clean assembleRelease
```

Then sign the release APK with your same release keystore.

## Publish to your F-Droid TV repo

1. Remove the failed old APK from the repo folder:

```bash
rm -f repo/App_Remote_Receiver-1.0.2-release.apk
```

2. Add the new signed APK, for example:

```bash
cp App_Remote_Receiver-1.0.3-release.apk repo/
```

3. Update the metadata current version:

```yaml
CurrentVersion: 1.0.3
CurrentVersionCode: 4
```

4. Run:

```bash
fdroid update --verbose
```

