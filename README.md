# ZeroTierProxy Android

Fresh Android Kotlin + Compose project scaffold for `arm64-v8a`, SDK 34.

## What this project includes

- **Proxy mode** via `PylonRunner`:
  - Copies `app/src/main/assets/bin/pylon_arm64` into internal storage.
  - Sets executable permission.
  - Starts with `ProcessBuilder`.
- **Split tunnel mode** via `ZTVpnService`:
  - Starts Android `VpnService`.
  - Adds ZeroTier-oriented routes.
- **SettingsManager**:
  - Imports and stores `planet` and `moon` files in app internal storage.
- **Compose UI**:
  - Toggle for Proxy vs Split Tunnel.
  - Buttons for `Import Planet`, `Import Moon`.
  - Start/Stop button and status text.

## Required local files

1. Place AAR dependency:
   - `app/libs/libzt-release.aar`
2. Place binary:
   - `app/src/main/assets/bin/pylon_arm64`

## Build

Use Android Studio or command line:

- Windows: `gradlew.bat :app:assembleDebug`
- Linux/macOS: `./gradlew :app:assembleDebug`

> Note: if `gradle-wrapper.jar` is missing, run `gradle wrapper` once to regenerate wrapper binaries.

## CI

Workflow file: `.github/workflows/android.yml`

- Verifies `app/libs/libzt-release.aar` exists
- Builds release APK
- Uploads APK artifact
