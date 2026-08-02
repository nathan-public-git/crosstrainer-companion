# Contributing

Thanks for helping improve Crosstrainer Companion. The project is designed to be buildable from a fresh clone without Android Studio or any checked-in local configuration.

## Prerequisites

- JDK 17
- Android SDK with platform API 36 and the matching build tools
- A physical Android device for Bluetooth testing (optional for unit tests)

Set these environment variables on your own machine. Do not add their values to project files:

```powershell
[Environment]::SetEnvironmentVariable('JAVA17_HOME', 'C:\\path\\to\\jdk-17', 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', 'C:\\path\\to\\Android\\Sdk', 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', 'C:\\path\\to\\Android\\Sdk', 'User')
```

Open a new terminal after setting them. Before every Gradle command in PowerShell, select JDK 17:

```powershell
if (-not $env:JAVA17_HOME) { throw 'JAVA17_HOME is not configured.' }
$env:JAVA_HOME = $env:JAVA17_HOME
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
```

## Verify a fresh checkout

```powershell
.\\gradlew.bat test lint
.\\gradlew.bat :app:assembleDebug
```

`local.properties`, build directories, Android Studio settings, keystores, and APK/AAB outputs are intentionally ignored. Keep secrets and machine paths outside the repository.

## Changes and pull requests

1. Create a focused branch from the current default branch.
2. Keep changes small and include tests when behavior changes.
3. Run the verification commands above.
4. Open a pull request describing the user-visible change and test results.

Codex contributors should follow the repository's `AGENTS.md`, keep SDK/JDK locations in environment variables, and avoid committing generated or local files.

## Release signing

See [docs/PRIVATE_RELEASE.md](docs/PRIVATE_RELEASE.md). A signing key and its passwords must never be committed or shared in a pull request.
