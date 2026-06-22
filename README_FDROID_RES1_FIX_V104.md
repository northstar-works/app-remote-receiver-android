# App Remote Receiver v1.0.4 / b5 F-Droid parser fix

This package changes two things:

1. Downgrades Android Gradle Plugin from 8.7.0 to 8.6.1. This avoids the `androguard.core.bytecodes.axml.ResParserError: res1 must be zero!` failure that F-Droid can hit while parsing `resources.arsc` from APKs built with AGP 8.7.x.
2. Adds release signing support to `app/build.gradle` using the same property names used by the GitHub Actions workflow:
   - `FDROID_STORE_FILE`
   - `FDROID_STORE_PASSWORD`
   - `FDROID_KEY_ALIAS`
   - `FDROID_KEY_PASSWORD`

Default version if built locally without workflow properties:

- `versionName`: `1.0.4`
- `versionCode`: `5`

The GitHub Actions workflow can still override these with:

- `APP_REMOTE_RECEIVER_VERSION_CODE`
- `APP_REMOTE_RECEIVER_VERSION_NAME`
