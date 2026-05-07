# ZeroTierProxy Android

Fresh Android Kotlin + Compose project scaffold for `arm64-v8a`, SDK 34.

## What this project includes

- **Proxy mode** via `LocalSocks5Proxy` + `LibztBridge`:
  - Starts a local SOCKS5 service on `127.0.0.1:1080`.
  - Handles SOCKS5 `CONNECT` and opens upstream sockets through the libzt node bridge.
- **Split tunnel mode** via `ZTVpnService`:
  - Starts Android `VpnService`.
  - Initializes/stops the libzt node.
  - Adds ZeroTier-oriented routes.
- **SettingsManager**:
  - Imports and stores `planet` and `moon` files in app internal storage.
  - Paths are passed to libzt initialization.
- **Compose UI**:
  - Toggle for Proxy vs Split Tunnel.
  - Buttons for `Import Planet`, `Import Moon`.
  - Start/Stop button and status text.

## Required local files

1. Place AAR dependency:
   - `app/libs/libzt-release.aar`

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
