# Watch SDK Demo

Android sample app that demonstrates end-to-end integration with the **Huawo BluetoothSDK** for BLE smartwatches / bands.

It covers the full product flow used in production apps:

**Scan → Connect → Bind → Sync → Device features → Unbind**, plus app-side binding persistence and automatic reconnect.

| Item | Value |
|------|--------|
| App name | Watch SDK Demo |
| Package / `applicationId` | `com.huawo.nt.sdkdemo` |
| Language | Kotlin (JVM 11) |
| minSdk / targetSdk / compileSdk | 28 / 36 / 36 |
| versionName / versionCode | `1.0` / `1` |
| BluetoothSDK | **2.5.4.126** (`com.huawo.sdk.bluetoothsdk.BluetoothSDK`) |
| API reference | [`SDK_android.md`](./SDK_android.md) (Chinese, full SDK usage) |

---

## Table of contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Project structure](#project-structure)
4. [Architecture](#architecture)
5. [Build & run](#build--run)
6. [Permissions](#permissions)
7. [Feature flows](#feature-flows)
8. [OTA (Sifli DFU)](#ota-sifli-dfu)
9. [AGPS](#agps)
10. [Localization](#localization)
11. [Configuration checklist](#configuration-checklist)
12. [Dependencies (local AARs)](#dependencies-local-aars)
13. [Related documentation](#related-documentation)

---

## Features

| Area | What the demo shows |
|------|---------------------|
| Connection | BLE scan, connect by MAC, disconnect |
| Binding | Full bind / unbind step flows with on-watch confirmation |
| Persistence | Locally store bound MAC / name / device info |
| Auto-reconnect | Cold-start restore + unexpected disconnect retry |
| Health sync | Pull activities / heart rates / sleeps, then clear on device |
| Goals | Read / write demo goals |
| Alarms & reminders | Alarms + sedentary / drink / wash-hand reminders |
| Notifications | Social switches, SMS push, call hang-up, contacts |
| Music push | Local music pick → SPP or Sifli channel |
| Album push | Photo pick → ezip/bin → SPP or Sifli channel |
| AGPS | Download 7-day XYW data → zip → Sifli push |
| OTA | Check server → download → Sifli NAND DFU upgrade |
| Language | System / Chinese / English switch at runtime |

---

## Requirements

- Android Studio with **AGP 9.2.1** / **Gradle 9.4.1** (see `gradle/libs.versions.toml`)
- Physical Android device with Bluetooth LE (emulator is not sufficient)
- Watch / band that speaks the Huawo BluetoothSDK protocol
- Vendor AARs present under [`app/libs/`](./app/libs/) (see [Dependencies](#dependencies-local-aars))

---

## Project structure

```text
SDKDemo/
├── app/
│   ├── libs/                          # Vendor AARs (BluetoothSDK, Sifli*, …)
│   └── src/main/java/com/huawo/nt/sdkdemo/
│       ├── SdkDemoApp.kt              # Application; owns BleRepository
│       ├── data/
│       │   ├── local/                 # BoundDeviceStore (SharedPreferences)
│       │   ├── model/                 # UI / domain models
│       │   ├── remote/                # OtaFirmwareApi (HTTP check / download)
│       │   └── repository/            # BleRepository (SDK façade)
│       ├── ui/
│       │   ├── main/                  # MainActivity, HomeFragment, HomeViewModel
│       │   ├── scan/                  # Scan & connect
│       │   ├── bind/ / unbind/        # Bind / unbind bottom-sheet flows
│       │   ├── features/              # Goals, Alarms, Notify, Music, Album, AGPS, OTA
│       │   ├── common/                # Log / flow-step adapters
│       │   └── ViewModelFactory.kt
│       └── util/                      # Locale, permissions, OTA/AGPS/album helpers
├── SDK_android.md                     # Official BluetoothSDK usage guide
├── settings.gradle
└── gradle/
```

Single Gradle module: `:app`.

---

## Architecture

```text
SdkDemoApp
  └── BleRepository
        ├── BluetoothSDK          (BLE GATT / bind / health / features)
        ├── SifliWatchSDK         (music / album / AGPS zip push)
        └── BoundDeviceStore      (app-side bind persistence)

UI (Fragment) ──► ViewModel ──► BleRepository / OtaFirmwareApi / helpers
                 ▲
                 └── StateFlow<UiState> + optional SharedFlow events
```

### Key design points

- **MVVM + Kotlin Coroutines / StateFlow** — each screen collects a single `uiState`.
- **`BleRepository`** — wraps callback-based SDK APIs into suspend functions / Flow events; shared as a process singleton from `SdkDemoApp`.
- **`BoundDeviceStore`** — SharedPreferences (`bound_device`) stores MAC, name, and `BleDeviceInfo` JSON. This is **independent** of `BluetoothSDK.setBind(boolean)`.
- **Auto-reconnect** (`HomeViewModel`):
  - On cold start, if a bound device exists → `setBind(true)` → `connect(mac)`.
  - On unexpected disconnect → retry after **2 s**; on failure retry every **5 s**.
  - Disabled after **manual disconnect** or during **unbind**.
- **`DevicePhase`**: `IDLE` → `CONNECTED` → `BOUND` → (`SYNCING` / `UNBINDING`).

Feature screens generally require an active BLE connection (`prepareFeature()` / `isConnected()`).

---

## Build & run

1. Clone / open this project in Android Studio.
2. Confirm AARs exist under `app/libs/` (especially `BluetoothSDK-*.aar` and the Sifli stack).
3. Sync Gradle, then Run **app** on a physical device (`armeabi-v7a` / `arm64-v8a`).
4. Grant Bluetooth (and location, when prompted) permissions on first scan.

### Init sequence (app)

On Home bootstrap:

1. `BluetoothSDK.init(application, maxMtu = 247)`
2. `SifliWatchSDK.getInstance().init(application)`
3. Load local bound record (if any) and attempt reconnect

> **Note:** This demo loads SDKs from **local AARs** (`fileTree` on `app/libs`).  
> Production apps may instead use the Huawo Nexus Maven repository described in `SDK_android.md` §1. Do **not** mix the same artifact from both sources.

---

## Permissions

Declared in `AndroidManifest.xml` and requested at runtime where needed:

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30) | Legacy Bluetooth |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Android 12+ scan |
| `BLUETOOTH_CONNECT` | Android 12+ connect / GATT |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Scan compatibility |
| `INTERNET` | OTA check / firmware download / AGPS download |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Music scan (older Android) |
| `READ_MEDIA_AUDIO` | Music scan (Android 13+) |

Also: `android:usesCleartextTraffic="true"` (AGPS HTTP host), and `android.hardware.bluetooth_le` required.

Runtime scan permissions (API 31+) are requested via `PermissionHelper` before scanning.

---

## Feature flows

### Home

- Status banner, optional device card (name / MAC / bound state)
- Actions: **Scan & Connect**, **Bind**, **Sync**, **Unbind**, **Disconnect**
- Feature entries: Goals, Alarms & Reminders, Notifications, Music, Album, AGPS, OTA
- Rolling operation log (newest first)
- Toolbar language menu: System / 中文 / English

### Scan & connect

1. Request permissions.
2. `BluetoothSDK.scan` (default timeout **10 s**).
3. Tap a device → `connect(mac, 30s)`.
4. Return MAC / name / RSSI to Home via Fragment Result.

### Bind

Bottom-sheet step flow (aligned with SDK §6):

1. `startBind()` — user confirms on watch  
2. `setDeviceTime()`  
3. `setUserInfo()`  
4. `setUnit(metric = true)`  
5. `setLanguage(0)`  
6. `getDeviceInfo()` — persisted locally  
7. `endBind()`  
8. Optional `createBond()` if not already bonded  
9. `setBind(true)`  

Home then writes MAC / name / info into `BoundDeviceStore` and enables auto-reconnect.

### Unbind

1. Soft: `removeBond()`, `disconnect()`  
2. Hard: `setBind(false)`, clear `BoundDeviceStore`  
3. Auto-reconnect is disabled for the session  

### Sync

1. Ensure connected (reconnect by MAC if needed).  
2. Query health data counts → pull activities / heartrates / sleeps.  
3. Show summary; delete pulled records from the device.  

### Goals / Alarms / Notify

Action-list screens built on `BaseFeatureFragment` + feature ViewModels.  
Each button maps to one or more `BluetoothSDK` ops (see `FeatureFragments.kt` / `FeatureViewModels.kt`).

### Music file push

1. Scan local audio (storage / media permission).  
2. Optionally pair classic Bluetooth (SPP path).  
3. Query watch music storage.  
4. Push selected files:
   - **SPP** when classic BT is connected  
   - otherwise **Sifli** `syncZipFile` with type **4**  
   - SPP failure (except cancel) may fall back to Sifli  

### Album file push

1. Pick photos (UI limit **10** per push).  
2. Allocate watch slot IDs in **1..50**.  
3. Convert images (ezip / bin), then push via SPP or Sifli zip type **3**.  

---

## OTA (Sifli DFU)

This demo’s OTA UI uses the **Sifli NAND DFU** path (aligned with production QJS / Sifli upgrade).  
Generic `BluetoothSDK.ota` and WL `starWlOta` remain wrapped in `BleRepository` for other device families (see `SDK_android.md` §17).

### End-to-end sequence

```text
Refresh device info (MAC, firmware, productCode, deviceId)
        │
        ▼
POST check-upgrade API  ──headers──► appId, appVersion
        │
        ▼
Compare server version/build vs watch (FirmwareVersionUtils)
        │  (enable "Start upgrade" only if newer + firmwares non-empty)
        ▼
Pre-checks: battery ≥ 30%, UpgradeStatus.Normal
        │
        ▼
Download firmwares[0] zip (+ optional diff resource) → MD5 → unzip
        │
        ▼
Map bins → DFUImagePath list (SifliOtaHelper)
  · diff_ctrl present → DIFF (resource required; ctrl ignored)
  · else ctrl → FULL
        │
        ▼
bindService(SifliDFUService) → pause auto-reconnect → delay 1.5s
        │
        ▼
ISifliDFUService.startActionDFUNand(boundMac, paths, DFU_MODE_NORMAL, 0)
        │
        ▼
LocalBroadcast progress / log / exit → UI
```

Progress bar: **0–40%** download/prepare, **40–100%** DFU transfer.

While OTA is busy (check / download / DFU):

- Toolbar up button is hidden  
- System back, gesture back, and virtual back are blocked (`OnBackPressedCallback`)  
- Screen stays on (`FLAG_KEEP_SCREEN_ON`)

### Server endpoints (demo / test)

| Constant | Value |
|----------|--------|
| Check API | `POST https://test.huawo-wear.com/api/v1/devices/upgrades` |
| File CDN | `https://test.huawo-wear.com/files/` |
| `customerCode` | `Huawo` |

**Request body:** `currentVersion`, `currentBuild`, `productCode`, `customerCode`, `deviceId`  
**Request headers:** `appId` = package name, `appVersion` = `versionName`  
**Response:** `{ ok: true, data: { version, build, firmwares[], resource? } }`

Configured in [`OtaFirmwareApi.kt`](./app/src/main/java/com/huawo/nt/sdkdemo/data/remote/OtaFirmwareApi.kt).

### Diff vs full package

- **Full:** zip contains `ctrl*.bin` (+ `hcpu` / `lcpu` / `patch` / optional `outdyn` / `outroot`).  
- **Diff:** zip contains `diff_ctrl*.bin`; server must also provide `resource` (`name` / `url` / `md5`) → `IMAGE_ID_NAND_RES`.

---

## AGPS

Built by [`AgpsXywBuilder`](./app/src/main/java/com/huawo/nt/sdkdemo/util/AgpsXywBuilder.kt):

1. Download 7-day XYW `.pgl` files from  
   `http://starcourse.rx-networks.cn/IYMx9qGm7H/`  
2. Append validity-time trailer.  
3. Zip entries under `music/gps/agps/` → `agps_xyw.zip`.  
4. Push with `SifliWatchSDK.syncZipFile` type **3**.

UI also shows GPS chip / firmware / AGPS validity via `getDeviceGpsStatus`.

---

## Localization

| Resource set | Language |
|--------------|----------|
| `res/values/strings.xml` | Default (English) |
| `res/values-en/strings.xml` | English |
| `res/values-zh/strings.xml` | Chinese |

Runtime switch via Home toolbar → `LocaleHelper` (`system` / `zh` / `en`) → Activity `recreate()`.  
Applied in `SdkDemoApp.attachBaseContext` and `MainActivity.attachBaseContext`.

---

## Configuration checklist

Change these before pointing at production backends or shipping:

| What | Where | Demo value |
|------|--------|------------|
| OTA API host | `OtaFirmwareApi.BASE_URL` | `https://test.huawo-wear.com/` |
| Firmware CDN | `OtaFirmwareApi.FILE_BASE_URL` | `https://test.huawo-wear.com/files/` |
| Customer code | `OtaFirmwareApi.CUSTOMER_CODE` | `Huawo` |
| App id (OTA header) | `applicationId` in `app/build.gradle` | `com.huawo.nt.sdkdemo` |
| App version (OTA header) | `versionName` | `1.0` |
| AGPS download host | `AgpsXywBuilder.BASE_URL` | `http://starcourse.rx-networks.cn/IYMx9qGm7H/` |
| Init MTU | `HomeViewModel` / `BleRepository.init` | `247` |
| AAR vs Maven | `app/build.gradle` / Nexus | Local `app/libs` |

Also ensure `productCode` matches the real device `type` from `getDeviceInfo`.

---

## Dependencies (local AARs)

Loaded from `app/libs/` (`BmpConvert*.aar` is excluded):

| AAR | Role |
|-----|------|
| `BluetoothSDK-2.5.4.126.aar` | Main BLE SDK |
| `SifliDFU-1.1.99.aar` | Sifli NAND DFU OTA service (`ISifliDFUService` / bindService) |
| `qjs-watchface-15.0.16.aar` | `SifliWatchSDK` (zip push for music / album / AGPS) |
| `sifliezipsdk-2.3.9.aar` | Sifli ezip |
| `siflicore-1.2.11.aar` | Sifli core |
| `sifliwatchfacesdk-2.1.6.aar` | Sifli watchface SDK |

AndroidX / Material / Coroutines versions are managed in `gradle/libs.versions.toml`.

---

## Related documentation

| Document | Description |
|----------|-------------|
| [`SDK_android.md`](./SDK_android.md) | Full BluetoothSDK guide (integration, permissions, bind, health, music, OTA §17, AGPS, recommended flow §22) |
| This README | Demo app map: how the sample wires the SDK into UI |

Recommended reading order for integrators:

1. This README — how the demo is organized  
2. `SDK_android.md` §1–§2 — integrate SDK + permissions  
3. `SDK_android.md` §6 / §22 — bind & recommended product flow  
4. `SDK_android.md` §14 / §17 — music push & OTA channels  

---

## License / notice

Vendor SDKs and AARs are property of their respective owners (Huawo / Sifli).  
This repository is a **sample / demonstration** app for SDK evaluation and internal integration reference.
