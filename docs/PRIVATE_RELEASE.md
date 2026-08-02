# Private Android release

Cross Trainer Companion 1.0 is configured for private APK distribution. The repository does not contain a signing key, passwords, or a release signing configuration.

## Create the signed APK in Android Studio

1. Open the project and allow Gradle sync to finish using JDK 17.
2. Choose **Build > Generate Signed Bundle / APK**.
3. Select **APK**, then choose **Next**.
4. Under **Key store path**, choose **Create new** for the first release, or select the same existing release keystore for later updates.
5. Save the keystore outside this repository. Use a strong keystore password, key password, private key alias, and a long validity period. Store the passwords in a password manager.
6. Back up the keystore and its recovery information in at least one separate secure location. Losing this private key prevents future APKs from updating an installed copy of the app.
7. Select the `release` build variant, enable both **V1 (Jar Signature)** and **V2 (Full APK Signature)**, and finish the wizard.
8. Share the resulting signed APK privately. On the phone, allow installation from the chosen file-sharing or browser app if Android prompts for it.

Before distributing an update, increment `versionCode` and update `versionName` in `app/build.gradle.kts`. Always sign updates with the same keystore and key alias.

## Release behavior

- The release build includes the normal Polar H10 and E95 discovery, connect, reconnect, and disconnect controls.
- FTMS service inspection and raw-packet diagnostics are compile-time disabled for release builds and remain available in debug builds.
- The app requires Android 11 or later (`minSdk 30`) and currently targets SDK 36.
- Version 1.0 uses `versionCode 1` and `versionName 1.0.0`.
