# Android command-line build workflow

This project is developed and tested without Android Studio. Use command-line Gradle and Android SDK tools only.

Configure these user environment variables on each development machine. Their values are machine-specific and must not be hard-coded in this repository:

- `JAVA17_HOME`: the root directory of a JDK 17 installation.
- `ANDROID_HOME`: the root directory of the Android SDK installation.
- `ANDROID_SDK_ROOT`: set to the same value as `ANDROID_HOME`.

Before any Gradle command on Windows, select JDK 17 for that command:

```powershell
if (-not $env:JAVA17_HOME) { throw 'JAVA17_HOME is not configured.' }
$env:JAVA_HOME = $env:JAVA17_HOME
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
```

Gradle locates the Android SDK through `ANDROID_HOME`. An ignored `local.properties` file may also be used locally when needed; it must never be committed.

Build a device-testable debug APK with:

```powershell
.\gradlew.bat :app:assembleDebug
```

Before installing a build, verify the physical device is connected with `& (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') devices`. Install the debug APK with `adb install -r app\build\outputs\apk\debug\app-debug.apk`, then use `adb logcat` to collect diagnostics during real-device testing.
