# App Remote Receiver Android build fix

This fixed project removes the two things that were causing trouble in the previous source package:

1. The bad Java string escaping in `MainActivity.java`.
2. External dependencies (`nanohttpd` and `androidx.core`) that were not needed and could fail inside custom builder environments.

This version uses only Android/Java platform APIs:

- Native `ServerSocket` receiver on port `8765`.
- Android `PackageInstaller` for the install approval flow.
- No AndroidX dependency.
- No NanoHTTPD dependency.

## Android Builder service

Upload this ZIP as the Gradle project ZIP. Do not upload a ZIP that includes `.gradle`, `.idea`, or `build` folders.

Recommended builder settings:

- Build type: Release
- Sign APK: On, using your selected keystore profile
- Keystore: any normal release key is fine, but keep using the same key for future updates of this app

## Android Studio

Open the extracted folder as a project. If Android Studio asks to trust the project, trust it. Then run:

- File > Sync Project with Gradle Files
- Build > Build APK(s)

If Android Studio says the SDK platform is missing, install Android SDK Platform 35.

## Fire TV setup

Install the APK on Fire TV once. Then open App Remote Receiver and leave it on screen. It will display:

- IP address
- Port 8765
- Pairing token

From the Docker sender UI, use Receiver mode and enter the Fire TV IP, port, and token.
