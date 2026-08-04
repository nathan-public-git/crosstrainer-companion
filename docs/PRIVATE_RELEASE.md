# Private Android release

The repository does not contain a signing key, signing passwords, a signing configuration file, SDK paths, or generated release artifacts. A fresh clone can build an unsigned release; each distributor supplies their own signing material through environment variables.

## Prepare your machine

Follow [CONTRIBUTING.md](../CONTRIBUTING.md) to install JDK 17 and the Android SDK. Keep your keystore outside this repository and back it up securely. Losing the key prevents future updates from replacing an installed APK signed with it.

Set the signing values for the current PowerShell session:

```powershell
$env:RELEASE_STORE_FILE = 'C:\\secure-location\\crosstrainer-release.jks'
$env:RELEASE_STORE_PASSWORD = 'your-keystore-password'
$env:RELEASE_KEY_ALIAS = 'your-key-alias'
$env:RELEASE_KEY_PASSWORD = 'your-key-password'
```

Do not put these values in `gradle.properties`, `local.properties`, a shell script, a commit, a pull request, or an issue. Use a password manager or your CI provider's encrypted secrets for persistent storage.

## Build and verify

Before Gradle commands, select JDK 17 as described in [CONTRIBUTING.md](../CONTRIBUTING.md). Then run:

```powershell
.\\gradlew.bat test lint
.\\gradlew.bat :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/`. It is ignored by Git.

If the four `RELEASE_*` variables are absent, `assembleRelease` still produces an unsigned APK for local verification. It must not be distributed as a trusted release.

Before distributing an update, increment `versionCode` and update `versionName` in `app/build.gradle.kts`. Always sign updates with the same keystore and key alias.

## Release behavior

- The release build includes the normal Polar H10 and E95 discovery, connect, reconnect, and disconnect controls.
- FTMS service inspection and raw-packet diagnostics are compile-time disabled for release builds and remain available in debug builds.
- The app requires Android 11 or later (`minSdk 30`) and currently targets SDK 36.
