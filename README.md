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
| BluetoothSDK | **2.5.4.129** (`com.huawo.sdk.bluetoothsdk.BluetoothSDK`) |
| AI extension | **1.5.1.29** (`com.huawo.ai.AiCenter`) |
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
| Health sync | Legacy one-click sync plus a separate JieLi command page with nine independent reads and eight explicit deletes |
| Goals | Read / write demo goals |
| Alarms & reminders | Generic alarms and sedentary / drink / wash-hand reminders |
| Alarms & reminders (JieLi) | Independent Home entry; alarm / custom-reminder read, add and delete, plus low-battery, vibration and workout auto-detection settings |
| Automatic monitoring | Read / set heart-rate, SpO2, stress, skin-temperature and respiratory-rate monitoring |
| Manual measurement (JieLi) | Start heart rate, SpO2, stress, HRV, respiratory rate or skin temperature; stop / retry stop with device callbacks |
| Reminder listener (JieLi) | Independent reminder / workout-recognition start / stop subscriptions; display event source, category / payload and phone receive time |
| AI analysis | Exercise / health analysis, insight, body status and sleep day / week / month reports with fixed sample inputs and real SDK requests |
| Notifications | Social switches, SMS push, call hang-up, contacts |
| Music push | Local music pick → SPP or Sifli channel |
| Album push | Photo pick → ezip/bin → SPP or Sifli channel |
| AGPS | Download 7-day XYW data → zip → Sifli push |
| OTA | Sifli NAND DFU flow plus a separate JieLi OTA page using `starWlOta` |
| Watchfaces | Existing watchface / Watch Face Studio entries and a separate JieLi watchface page |
| Language | System / Chinese / English switch at runtime |

---

## Requirements

- Android Studio with **AGP 9.1.0** / **Gradle 9.4.1** (see `gradle/libs.versions.toml`)
- Physical Android device with Bluetooth LE (emulator is not sufficient)
- Watch / band that speaks the Huawo BluetoothSDK protocol
- Vendor AARs present under [`app/libs/`](./app/libs/) (see [Dependencies](#dependencies-local-aars))
- Network access and valid vendor-issued DeviceId / channel credentials for cloud AI reports

---

## Project structure

```text
SDKDemo/
├── app/
│   ├── libs/                          # Vendor AARs (BluetoothSDK, AI, AFlash, Sifli*, …)
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
│       │   ├── features/              # Device features, monitoring, reminder listener, AI analysis
│       │   ├── watchface/             # Watchfaces, JieLi watchfaces, Watch Face Studio
│       │   ├── common/                # Log / flow-step adapters
│       │   └── ViewModelFactory.kt
│       └── util/                      # Locale, permissions, OTA/AGPS/album helpers
├── imagepicker/                       # Local image selection module
├── imagecrop/                         # Local image cropping module
├── SDK_android.md                     # SDK usage and AI request / response reference
├── settings.gradle
└── gradle/
```

Gradle modules: `:app`, `:imagepicker`, `:imagecrop`. Vendor SDKs remain local AARs; this project does not include their source modules.

---

## Architecture

```text
SdkDemoApp
  └── BleRepository
        ├── BluetoothSDK          (BLE GATT / bind / health / features)
        ├── SifliWatchSDK         (music / album / AGPS zip push)
        └── BoundDeviceStore      (app-side bind persistence)

UI (Fragment) ──► ViewModel ──► BleRepository / OtaFirmwareApi / AI SDK / helpers
                 ▲
                 └── StateFlow<UiState> + optional SharedFlow events
```

### Key design points

- **MVVM + Kotlin Coroutines / StateFlow** — feature screens render `uiState`; AI Analysis also observes OmniAgent readiness to enable its report buttons.
- **`BleRepository`** — wraps callback-based SDK APIs into suspend functions / Flow events; shared as a process singleton from `SdkDemoApp`.
- **Independent feature entries** — the new monitoring, reminder and AI buttons demonstrate their own SDK calls without adding page-level platform detection.
- **`AiAnalysisViewModel`** — calls the cloud AI SDK directly, separately manages initialization / report jobs, and ignores results from canceled page operations.
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

`SdkDemoApp` also prepares the watchface SDKs and AI process context (`SpUtils.init` / `AiCenter.setApplication`). Cloud AI initialization happens on the relevant feature page; BLE initialization and connection alone do not make AI reports ready.

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
| `INTERNET` | OTA check / firmware download / AGPS download / cloud AI reports |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Music scan (older Android) |
| `READ_MEDIA_AUDIO` | Music scan (Android 13+) |

Also: `android:usesCleartextTraffic="true"` (AGPS HTTP host), and `android.hardware.bluetooth_le` required.

Runtime scan permissions (API 31+) are requested via `PermissionHelper` before scanning.

---

## Feature flows

### Home

- Status banner, optional device card (name / MAC / bound state)
- Actions: **Scan & Connect**, **Bind**, **Sync**, **Sync (JieLi)**, **Unbind**, **Disconnect**
- Feature entries include automatic monitoring, manual measurement (JieLi), reminder listener (JieLi) and a single **AI** menu, alongside the existing device / watchface features
- Legacy sync summary is pinned below the scrollable controls at the bottom of Home; it appears when a summary is available and remains visible while scrolling the buttons
- Home groups the existing actions into five cards: connection, health, daily reminders, watchfaces / media / AI, and maintenance. Each row has three compact buttons; the existing extra feature entries remain available.
- Operation logs are retained in Home state; the fixed-bottom log panel is collapsed by default and can be expanded / collapsed without affecting SDK operations or log collection
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

**Legacy Sync** keeps its existing connection / reconnection and one-click synchronization flow. **Sync (JieLi)** only opens an independent command page and passes the selected device MAC; entering that page does not initiate a read, delete or reconnect.

- **Legacy Sync:** query health data counts, then read activities, heart rates, sleeps and HRV through the existing generic chain. Deletion failures are currently ignored, and the demo still reports sync completion.
- **Sync (JieLi):** a card-based page displays nine read buttons and nine delete buttons in three columns, with delete labels in red and independently scrollable action / result areas. Each button calls only its corresponding data API; reads show the type, count and full model details without deleting anything. Deletes are explicit and do not depend on a preceding non-empty read; acknowledgement does not fabricate a remaining-record count or trigger automatic readback.
- Legacy Sync still summarizes data and attempts to clear non-empty types. The JieLi command page checks the connection when an operation is clicked, reconnects to the selected MAC if needed, serializes page operations, and cancels only its own wait on exit while ignoring late results. Its results stay on that page and do not replace the Home summary. This demo has no health database; save required device history reliably before explicitly deleting it.

The JieLi page covers step / activity, heart rate, sleep, HRV, SpO2, stress, skin temperature, respiratory rate and Workout history. Workout uses its independent `getWorkoutV2` read, displays the SDK model and any point lists returned with it, and provides a separate `delWorkouts` action to delete all device Workout history. Reading never triggers deletion or a separate point request. The page does not request the separate resting-heart-rate type and adds no platform detection. The read / delete mappings and deletion boundaries are documented in [SDK guide §9](./SDK_android.md#9-健康数据同步).

### Goals / Alarms / Notify

Action-list screens built on `BaseFeatureFragment` + feature ViewModels.  
Each button maps to one or more `BluetoothSDK` ops (see `FeatureFragments.kt` / `FeatureViewModels.kt`).

Goals, JieLi sync, manual measurement, both alarm / reminder pages and notifications use card-based layouts with fixed-size wrapping labels and three-column actions. Delete labels are red. Manual measurement retains six direct-start actions and the independent stop / retry-stop action. The JieLi alarm ID explanation remains inside its actions card. Notifications retain all six existing actions, including SMS push and call hang-up. Automatic monitoring has its own actual-device switch UI described below; the other pages do not add type-selection steps, new settings-detail navigation or automatic SDK reads.

The generic Alarms & Reminders page retains generic alarm operations and sedentary / drink / wash-hand examples. JieLi-specific examples are on the separate **Alarms & reminders (JieLi)** Home entry; no page-level platform detection is added.

The JieLi page directly displays low-battery read / on / off, vibration read / off / medium (66), and workout auto-detection read / on / off buttons, followed by six independent alarm / reminder actions. There are no settings-detail pages. Example schedules are 07:30 weekdays for alarms and 10:00 weekdays for custom reminders. Alarms and reminders share the ID space: this demo reserves IDs 1–5 for alarms and 6–10 for reminders, reading both collections before allocating an unused ID. These ranges are demo conventions, not universal firmware limits. The delete-all actions call their respective SDK APIs and do not delete the other category. See [SDK guide §11](./SDK_android.md#11-闹钟与提醒).

All fifteen SDK action buttons use the same page and ViewModel, with one operation at a time. Each setting uses its own fixed sample arguments; settings do not register a reminder / recognition listener or start a workout. Leaving the page cancels its request wait and ignores late results, but does not undo queued / sent device commands or automatically turn off a setting. A valid switch reply without attribute `0x15` retains the accepted default `false` fallback. Details are in [SDK guide §11.8](./SDK_android.md#118-运动自识别开关杰里).

### Automatic monitoring

The JieLi automatic-monitoring page shows one row per metric with a real-device switch. Each visible session reads heart-rate, SpO2, stress, skin-temperature and respiratory-rate settings serially; the toolbar Refresh action retries reads. Unknown / failed reads remain disabled and are not presented as confirmed off. Programmatic switch rendering never sends setters. User changes call only the selected metric setter; pending operations disable controls, and failed saves restore the previous display and require Refresh to confirm the device state. No device rollback command is sent. Heart-rate on retains the existing 10-minute sample (off=0); skin temperature / respiratory rate retain 60 for on and 0 for off, without claiming that 60 is minutes. HRV is not displayed because the current SDK has no corresponding automatic-monitoring API; it is not backed by heart-rate or stress settings. Leaving the page cancels only its wait, while already queued / sent SDK commands may still take effect; returning refreshes device settings. No platform detection is added. See [SDK guide §9.3](./SDK_android.md#93-监测开关与告警).

### Manual measurement (JieLi)

Six start buttons call the SDK for heart rate, SpO2, stress, HRV, respiratory rate and skin temperature. The page keeps one measurement session, receives timestamped integer / float values, handles wear and device-stop events, and allows retrying an unconfirmed stop. A start acknowledgement is not measurement completion; unsupported device capabilities remain SDK errors. See [SDK guide §9.4](./SDK_android.md#94-手动测量杰里).

### Reminder listener (JieLi)

The page uses status, actions and results cards. Its four independent device-reminder / workout-recognition start / stop buttons use the same three-column layout and fixed-size wrapping labels as the other redesigned pages. The two subscriptions can run together; stopping one does not stop the other. Reminder events retain their type and payload; workout recognition shows the raw category and its name (1 walking, 2 running, 3 cycling, 4 elliptical training, 5 rowing machine; unknown values remain visible). Both display the phone receive time and event source in a shared list of up to 100 recent events.

Registration only subscribes to future events; it does not enable a device reminder / recognition switch or start a workout. Leaving the page removes both callback instances owned by this page, and old-session queued events are ignored independently. No platform check is added. See [SDK guide §11.7](./SDK_android.md#117-提醒监听杰里).

### AI Analysis

Open **AI** from Home. Its card-based menu provides **Usage guide**, **AI DEMO**, then an **AI analysis** group with exercise / health analysis, insight, body status and sleep day / week / month entries. Buttons use three columns and fixed-size wrapping labels. The guide retains all existing integration instructions in cards. AI DEMO uses a standalone card layout for device information, recording / platform selectors, geometry, actions, image previews and collapsible logs; it reuses the existing watchface / recording logic without changing the embedded watchface tab layout. Each analysis entry opens its own selected-report page with status, parameters, actions and results cards, retaining the existing instructions, editable configuration, matching request button, inputs, results and errors. Health inputs remain fixed samples, and requests still use the real cloud SDK.

- DeviceId defaults to the currently connected MAC in lowercase without separators; DeviceId, channel ID and AppKey remain editable.
- The AI menu only navigates; OmniAgent initialization still starts when an analysis feature page opens. Insight, body status and sleep request buttons become available after successful initialization. Their existing busy / readiness collector continues to own button availability; the shared card renderer must not re-enable an unauthorized report. Editing the configuration invalidates readiness; use **Reinitialize / retry** before requesting the selected report again.
- Exercise / health analysis retains its separate AFlash initialization on click. See §23.4 of the SDK guide for the current initialization limitation.
- Leaving the page cancels its waiting / report jobs and ignores late results. This page does not record audio, install watchfaces, save health reports or send analysis results to the watch.

Full dependencies, initialization, request fields, units, JSON examples and response mappings are in [SDK guide §23](./SDK_android.md#23-ai-分析无屏-demo). This README keeps the demo flow overview; detailed API schemas are maintained in that guide.

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

The existing **OTA** page uses the **Sifli NAND DFU** path described below. Home also has an independent **OTA (JieLi)** page calling WL `starWlOta`; it does not add a platform check. Generic `BluetoothSDK.ota` remains wrapped in `BleRepository` for other integrations. Channel details are in `SDK_android.md` §17.

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
| AI DeviceId | AI Analysis page | Connected MAC, lowercase without separators; authorization still depends on the vendor channel |
| AI channel ID / AppKey | AI Analysis page / `AiAnalysisViewModel` | Vendor demo defaults; replace with issued values, do not publish credentials in documentation |

Also ensure `productCode` matches the real device `type` from `getDeviceInfo`.

---

## Dependencies (local AARs)

All `*.aar` files in `app/libs/` are loaded by `fileTree`; `BmpConvert` is included. Selected vendor dependencies are listed below; the complete runtime dependency set is defined by `app/libs/` and `app/build.gradle`.

| AAR | Role |
|-----|------|
| `BluetoothSDK-2.5.4.129.aar` | Main BLE SDK |
| `ai-1.5.1.29.aar` | AI analysis and other AI integration APIs |
| `QW_release_V4.7.2.aar` | AFlash runtime used by AI |
| `BmpConvert_V1.6.0_10608_qiwo.aar` | JieLi watchface image conversion runtime |
| `jl_pack_resource_V1.0.0_10_qiwo.aar` | JieLi resource packing runtime |
| `SifliDFU-1.1.99.aar` | Sifli NAND DFU OTA service (`ISifliDFUService` / bindService) |
| `qjs-watchface-15.0.16.aar` | `SifliWatchSDK` (zip push for music / album / AGPS) |
| `sifliezipsdk-2.3.9.aar` | Sifli ezip |
| `siflicore-1.2.11.aar` | Sifli core |
| `sifliwatchfacesdk-2.1.6.aar` | Sifli watchface SDK |

AndroidX / Material / Coroutines versions are managed in `gradle/libs.versions.toml`.

The local AI AAR also needs `com.adf:oa-oas:2.5.0` from the vendor Maven repository configured in [`settings.gradle`](./settings.gradle). OkHttp / OkHttp-SSE 5.1.0 and Gson are declared in [`app/build.gradle`](./app/build.gradle) through the version catalog. For repository setup and the AI dependency explanation, see [SDK guide §23.1](./SDK_android.md#231-依赖与权限). Do not combine local AAR and Maven copies of the same artifact.

---

## Related documentation

| Document | Description |
|----------|-------------|
| [`SDK_android.md`](./SDK_android.md) | Detailed SDK guide: integration, binding, health / monitoring §9, reminders §11, OTA §17 and AI request / response fields §23 |
| This README | Demo app map: how the sample wires the SDK into UI |

Recommended reading order for integrators:

1. This README — how the demo is organized  
2. `SDK_android.md` §1–§2 — integrate SDK + permissions  
3. `SDK_android.md` §6 / §22 — bind & recommended product flow
4. [SDK guide §9](./SDK_android.md#9-健康数据同步) / [§11](./SDK_android.md#11-闹钟与提醒) — sync, automatic / manual monitoring and reminder examples
5. [SDK guide §23](./SDK_android.md#23-ai-分析无屏-demo) — AI setup, request / response fields and error boundaries
6. `SDK_android.md` §14 / §17 — music push & OTA channels

---

## License / notice

Vendor SDKs and AARs are property of their respective owners (Huawo / Sifli).  
This repository is a **sample / demonstration** app for SDK evaluation and internal integration reference.
