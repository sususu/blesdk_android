# BluetoothSDK 使用文档

**BluetoothSDK 版本**：2.5.4.129（当前 Demo 本地 AAR）

**AI 扩展版本**：1.5.1.29（可选，见 §23）

**入口类**：`com.huawo.sdk.bluetoothsdk.BluetoothSDK`  
**最低 Android 版本**：minSdk 24  
**说明**：本 SDK 通过 BLE 与华沃系智能手表/手环通信。对外统一使用 `BluetoothSDK` 静态方法，调用结果通过 Callback 异步返回。

---

## 目录

1. [集成方式（本地 AAR / 私有 Maven）](#1-集成方式本地-aar--私有-maven)
2. [权限配置](#2-权限配置)
3. [快速开始](#3-快速开始)
4. [回调约定与错误码](#4-回调约定与错误码)
5. [扫描 / 连接 / 断开 / 重连](#5-扫描--连接--断开--重连)
6. [配对与绑定](#6-配对与绑定)
7. [设备信息与环境同步](#7-设备信息与环境同步)
8. [目标设置](#8-目标设置)
9. [健康数据同步](#9-健康数据同步)
10. [运动 Workout](#10-运动-workout)
11. [闹钟与提醒](#11-闹钟与提醒)
12. [通知 / 来电 / 通讯录](#12-通知--来电--通讯录)
13. [表盘](#13-表盘)
14. [音乐文件推送](#14-音乐文件推送)
15. [天气](#15-天气)
16. [查找设备 / 遥控拍照](#16-查找设备--遥控拍照)
17. [OTA 固件升级](#17-ota-固件升级)
18. [GPS / 地图](#18-gps--地图)
19. [其他常用设置](#19-其他常用设置)
20. [全局监听器](#20-全局监听器)
21. [协议分支说明](#21-协议分支说明)
22. [推荐集成流程](#22-推荐集成流程)
23. [AI 分析（无屏 Demo）](#23-ai-分析无屏-demo)

---

## 1. 集成方式（本地 AAR / 私有 Maven）

`BluetoothSDK`、思澈相关库（`com.sifli:*`）、表盘库（如 `qjs-watchface`）等**均支持两种交付方式**，二选一即可（也可混用，但同一制品不要重复依赖）：

| 方式 | 适用场景 |
|------|----------|
| **本地 AAR 文件** | 厂商直接交付 `.aar`（放入工程 `libs/`） |
| **私有 Nexus Maven** | 通过华沃私有仓库拉取，含 `BluetoothSDK` 与全部 `com.sifli` 依赖 |

### 1.1 私有 Maven 仓库（推荐有外网/内网可达 Nexus 时使用）

在**根工程** `build.gradle` 的 `allprojects.repositories`（或 `settings.gradle` 的 `dependencyResolutionManagement.repositories`）中增加：

```groovy
maven {
    credentials {
        username 'huaworead'
        password 'huawo202301'
    }
    url 'https://nexus.huawo-wear.com/repository/maven-releases/'
}
```

**`settings.gradle`（Gradle 7+ 推荐写法）示例：**

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            credentials {
                username 'huaworead'
                password 'huawo202301'
            }
            url 'https://nexus.huawo-wear.com/repository/maven-releases/'
        }
    }
}
```

> 账号为厂商只读账号；坐标与版本号以交付清单为准。文中 Maven 的 `2.5.4.126` 为历史坐标示例，不代表具备本文新增接口；使用无屏新增接口时需厂商交付的对应版本。当前 Demo 本地包为 `2.5.4.129`，不据此承诺该版本已发布到 Maven。

#### 模块 `build.gradle` 依赖示例（Maven 方式）

```groovy
android {
    compileSdk 34   // 建议 >= 34
    defaultConfig {
        minSdk 24
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    // 蓝牙 SDK 入口（版本与交付一致，示例为 2.5.4.126）
    implementation 'com.huawo.sdk:BluetoothSDK:2.5.4.126'

    // 运行时依赖（可按工程现有版本对齐）
    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation 'androidx.lifecycle:lifecycle-process:2.9.2'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'com.google.code.gson:gson:2.13.1'

    // ---- 按产品能力按需引入（思澈 / 表盘等）----
    // 思澈固件 DFU OTA
    // implementation 'com.sifli:SifliDFU:1.1.99@aar'

    // 思澈表盘 / 音乐 ZIP 推送相关（若未使用 fat 表盘包，需单独引入）
    // implementation 'com.sifli:sifliezipsdk:2.3.9@aar'
    // implementation 'com.sifli:siflicore:1.2.7@aar'
    // implementation 'com.sifli:sifliwatchfacesdk:2.1.6@aar'
    // implementation 'com.huawo.watchface:qjs-watchface:15.0.16'@aar  // 版本以交付为准；也可改用本地 aar
}
```

常用 Maven 坐标对照（版本以交付为准）：

| 制品 | 坐标示例 |
|------|----------|
| BluetoothSDK | `com.huawo.sdk:BluetoothSDK:2.5.4.126` |
| 思澈 DFU | `com.sifli:SifliDFU:1.1.99` |
| 思澈 ezip | `com.sifli:sifliezipsdk:2.3.9@aar` |
| 思澈 core | `com.sifli:siflicore:1.2.7` |
| 思澈 watchface sdk | `com.sifli:sifliwatchfacesdk:2.1.6@aar` |
| QJS 表盘封装包 | 厂商交付的 `qjs-watchface-*`（Maven 坐标或本地 AAR） |

### 1.2 本地 AAR 文件方式

将厂商提供的 AAR 放入宿主工程：

```text
YourApp/
  app/
    libs/
      BluetoothSDK-2.5.4.129.aar
      qjs-watchface-15.0.16.aar          # 思澈表盘/音乐推送需要时
      SifliDFU-1.1.99.aar                # 思澈固件 OTA 需要时（文件名以交付为准）
      sifliezipsdk-2.3.9.aar             # 同上，按需
      siflicore-1.2.7.aar
      sifliwatchfacesdk-2.1.6.aar
```

模块 `build.gradle`：

```groovy
repositories {
    flatDir { dirs 'libs' }   // 若用 name 方式；files() 方式可不配
}

dependencies {
    implementation files('libs/BluetoothSDK-2.5.4.129.aar')

    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation 'androidx.lifecycle:lifecycle-process:2.9.2'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'com.google.code.gson:gson:2.13.1'

    // 按需：思澈相关改为本地 aar（与 Maven 二选一）
    // implementation files('libs/qjs-watchface-15.0.16.aar')
    // implementation files('libs/SifliDFU-1.1.99.aar')
    // implementation files('libs/sifliezipsdk-2.3.9.aar')
    // implementation files('libs/siflicore-1.2.7.aar')
    // implementation files('libs/sifliwatchfacesdk-2.1.6.aar')
}
```

Kotlin DSL 本地示例：

```kotlin
dependencies {
    implementation(files("libs/BluetoothSDK-2.5.4.129.aar"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
}
```

> **不要混用同一制品的两套来源**（例如既 `files('…BluetoothSDK…aar')` 又 Maven `com.huawo.sdk:BluetoothSDK`），以免类冲突。`com.sifli` 各库同理。

### 1.3 按能力可选的额外依赖

| 能力 | 是否包含在 `BluetoothSDK` | 额外依赖（Maven 或同名本地 AAR） |
|------|---------------------------|----------------------------------|
| BLE 扫描连接、健康数据、WL 音乐/相册、SPP 文件传输 | **是** | 无需额外包 |
| 瑞昱/阿波罗固件 OTA、WL 固件 OTA | **是** | 无需额外包 |
| 思澈 ZIP 音乐推送、QJS 表盘推送 | **否** | `qjs-watchface` + 通常还需 `sifliezipsdk` / `siflicore` / `sifliwatchfacesdk`（见 [§14.5](#145-方案-c思澈sifli-ble-zip-推送需额外-aar)） |
| 思澈固件 DFU OTA | **否** | `com.sifli:SifliDFU`（见 [§17.5](#175-方案-c思澈siflidfu-固件升级需额外依赖)） |
| 无屏 AI 健康分析、洞察、身体状态、睡眠报告 | **否** | `ai-1.5.1.29.aar`、配套 AFlash / 表盘 / 杰理库及 OmniAgent Maven 依赖，见 §23.1 |

> **音乐文件推送**见 [§14](#14-音乐文件推送)；**固件 OTA** 见 [§17](#17-ota-固件升级)。仅做 WL / 瑞昱基础蓝牙能力时无需 AI 扩展；需要 AI 分析时必须额外集成 §23 的依赖。

### 1.4 ABI

SDK 内置 so，当前支持：

- `armeabi-v7a`
- `arm64-v8a`

如工程开启 `abiFilters`，请确保包含上述 ABI。`qjs-watchface` / `com.sifli` 相关 AAR 也通常含 native so，请一并保留。

### 1.5 混淆

Release 已开启混淆。客户工程若启用 ProGuard/R8，请至少 keep 入口与回调：

```proguard
-keep class com.huawo.sdk.bluetoothsdk.** { *; }
-dontwarn com.huawo.sdk.bluetoothsdk.**

# 若使用思澈音乐/表盘推送
-keep class com.huawo.watchface.** { *; }
-keep class com.sifli.** { *; }
-dontwarn com.sifli.**
```

---

## 2. 权限配置

### 2.1 AndroidManifest.xml

请在 **App** 的 Manifest 中声明（SDK 库 Manifest 会合并一部分，建议 App 侧仍显式声明）：

```xml
<!-- Android 11 及以下 -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 扫描兼容（低版本 / 部分机型仍需要） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

> 若 App 使用天气定位、运动轨迹等，定位权限本身也为业务所需。

读取本地音乐并推送到手表时，还需存储/音频读取权限：

```xml
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<!-- Android 12 及以下 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

### 2.2 运行时申请

| 系统版本 | 扫描 / 连接前申请 |
|---------|------------------|
| API ≥ 31 (Android 12+) | `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` |
| API &lt; 31 | `ACCESS_FINE_LOCATION`（或至少粗定位），并建议开启系统定位服务 |

| 场景 | 运行时权限 |
|------|------------|
| 推送本地音乐文件 | API ≥ 33：`READ_MEDIA_AUDIO`；更低版本：`READ_EXTERNAL_STORAGE`（必要时含写权限） |

**Demo（Fragment / Activity）：**

```java
String[] perms;
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    perms = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };
} else {
    perms = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION
    };
}
ActivityCompat.requestPermissions(this, perms, REQUEST_BLE);
```

调用前可用：

```java
if (!BluetoothSDK.isBluetoothEnabled()) {
    BluetoothSDK.enableBluetooth(); // 或引导用户到系统设置打开蓝牙
}
```

---

## 3. 快速开始

### 3.1 初始化

在自定义 `Application.onCreate()` 中初始化（**必须**）：

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // maxMTU：按具体产品与厂商确认，常见取值 243 / 247
        BluetoothSDK.init(this, 247);
        BluetoothSDK.setLogEnable(BuildConfig.DEBUG);
    }

    @Override
    public void onTerminate() {
        BluetoothSDK.destroy();
        super.onTerminate();
    }
}
```

| 参数 | 说明 |
|------|------|
| `Application` | 全局上下文，用于 BLE 管理、广播、重连等 |
| `maxMTU` | 最大 MTU，影响分包大小；**不同产品可能不同，请向厂商确认** |

查询版本：

```java
String ver = BluetoothSDK.getVersion(); // 当前 Demo 如 "2.5.4.129"
```

### 3.2 最小闭环流程

```text
init → 申请权限 → scan → connect →（createBond 可选）→ startBind → 业务 API → disconnect → destroy
```

---

## 4. 回调约定与错误码

### 4.1 回调模式

绝大多数业务回调继承 `BaseInterfaceCallback`，失败统一为：

```java
void onFail(int code); // 参见 ErrorCode
```

常见成功形态：

| 回调 | 成功方法 |
|------|----------|
| `BoolCallback` | `onSuccess()` |
| `IntValueCallback` / `StringValueCallback` / `BoolValueCallback` | `onSuccess(value)` |
| `ConnectCallback` | `onSuccess(Device device)` |
| `ScanCallback` | `onStarted` / `onResult` / `onFinished` |
| `ActivityDataCallback` | `onSports` / `onSleeps` / `onHeartrates` / `onHrvs` / `onStress` / `onSpo2` / `onPais`；V2 健康数据失败还可回调 `onFail(ActivityDataType, code)` |
| `OtaCallback` | `onReady` / `onUpload(progress)` / `onSuccess` / `onFail` |

包路径：

- 业务回调：`com.huawo.sdk.bluetoothsdk.interfaces.callback.*`
- 连接：`com.huawo.sdk.bluetoothsdk.callback.ConnectCallback` / `DisconnectCallback`
- 扫描：`com.huawo.sdk.bluetoothsdk.core.callback.ScanCallback`
- 错误码：`com.huawo.sdk.bluetoothsdk.error.ErrorCode`

### 4.2 常用错误码

| 常量 | 值 | 含义 |
|------|----|------|
| `OK` | 0 | 成功 |
| `BLUETOOTH_OFF` | 9 | 手机蓝牙关闭 |
| `DISCONNECTED` | 13 | BLE 已断开 |
| `DEVICE_CAN_NOT_BE_SCANNED` | 15 | 扫描不到设备 |
| `NO_PERMISSIONS` | 400 | 缺少权限 |
| `TASK_TIME_OUT` | 403 | 任务超时 |
| `BIND_TIMEOUT` | 59 | 绑定超时 |
| `BIND_CANCELED_BY_USER` | 60 | 用户取消绑定 |
| `CONNECT_TIMEOUT` | 20 | 连接超时 |
| `ALREADY_CONNECTING` | 22 | 正在连接中 |
| `OTA_WORKING` | 2010 | OTA 进行中 |

完整列表见 `ErrorCode.java`。

---

## 5. 扫描 / 连接 / 断开 / 重连

### 5.1 扫描

```java
BluetoothSDK.scan(8_000L, new ScanCallback() {
    @Override
    public void onStarted(boolean success) {
        // success=false 可能是权限不足或蓝牙未开
    }

    @Override
    public void onResult(Device device) {
        String name = device.getName();
        String mac = device.getMac();
        // 更新 UI 列表
    }

    @Override
    public void onFinished(List<Device> scanResultList) {
        // 扫描结束
    }
});

// 提前停止
BluetoothSDK.stopScan();
```

其它重载：

```java
BluetoothSDK.scan(callback);                                  // 默认超时
BluetoothSDK.scan("MyWatch", callback);                       // 名称过滤
BluetoothSDK.scan(8_000L, "MyWatch", "AA:BB:...", callback);  // 名称 + MAC
```

设备模型：`com.huawo.sdk.bluetoothsdk.core.model.Device`

### 5.2 连接

```java
// 方式一：扫描到的 Device
BluetoothSDK.connect(device, new ConnectCallback() {
    @Override
    public void onSuccess(Device device) {
        // 已连接，可继续绑定 / 拉设备信息
    }

    @Override
    public void onFail(int code) {
        // 处理 ErrorCode
    }
});

// 方式二：已知 MAC（已绑定设备重连常用）
BluetoothSDK.connect("AA:BB:CC:DD:EE:FF", new ConnectCallback() {
    @Override public void onSuccess(Device device) { }
    @Override public void onFail(int code) { }
});

// 带超时（毫秒）
BluetoothSDK.connect(mac, 30_000L, callback);
```

状态查询：

```java
boolean connected = BluetoothSDK.isConnected();
Device cur = BluetoothSDK.getConnectedDevice();
boolean scanning = BluetoothSDK.isScanning();
boolean connecting = BluetoothSDK.isConnecting();
```

### 5.3 重连 / 断开

```java
BluetoothSDK.reconnect(mac, new ConnectCallback() {
    @Override public void onSuccess(Device device) { }
    @Override public void onFail(int code) { }
});

BluetoothSDK.disconnect(new DisconnectCallback() {
    @Override
    public void onSuccess() {
        // 已断开并清理
    }
});
```

连接状态监听（建议在 Application 注册，应用退出时移除）：

```java
BluetoothSDK.addConnectionStateListener(new ConnectionStateCallback() {
    @Override
    public void onConnectionStateChange(boolean connected) {
        // false：断连，可触发业务层重连策略
    }
});
```

---

## 6. 配对与绑定

### 6.1 经典蓝牙配对（部分机型需要）

BLE 连接成功后，若产品要求 A2DP/HFP 等，可先配对：

```java
BluetoothSDK.createBond(new CreateBondCallback() {
    @Override
    public void onSuccess() {
        // 再执行 startBind
    }

    @Override
    public void onFail(int code) { }
});

boolean bonded = BluetoothSDK.isBonded();
```

解绑时取消配对：

```java
BluetoothSDK.removeBond(new RemoveBondCallback() {
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});
```

### 6.2 设备绑定

标准绑定（多数机型）：

```java
BluetoothSDK.startBind(new BoolCallback() {
    @Override
    public void onSuccess() {
        // 绑定成功后：同步时间 / 用户信息 / 单位 / 语言，再 endBind
        syncEnvironmentAndEndBind();
    }

    @Override
    public void onFail(int code) { }
});

BluetoothSDK.endBind(new BoolCallback() {
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});

// 本地绑定标记（断连重连逻辑依赖）
BluetoothSDK.setBind(true);
boolean bind = BluetoothSDK.isBind();
```

思澈 / QJS 机型：

```java
BluetoothSDK.startSifliBind(callback);
// 或不需要表端确认：
BluetoothSDK.startSifliBindWithoutConfirm(callback);
```

扫码绑定：

```java
BluetoothSDK.startQRCodeBind(callback);
```

绑定状态监听：

```java
BluetoothSDK.addBindStateListener(new BindStateCallback() {
    @Override
    public void onBindStateChange(boolean bind) { }
});
```

---

## 7. 设备信息与环境同步

连接成功后建议先拉设备信息，再下发环境。

### 7.1 设备信息 / 电量

```java
BluetoothSDK.getDeviceInfo(new DeviceInfoCallback() {
    @Override
    public void onSuccess(DeviceInfo info) {
        // id / type / firmwareVersion / battery / protocolVersion / features ...
        int protocol = info.getProtocolVersion();
        // protocolVersion >= 100 通常为 WL 协议
    }

    @Override
    public void onFail(int code) { }
});

BluetoothSDK.getBattery(new IntValueCallback() {
    @Override public void onSuccess(int battery) { /* 0~100 */ }
    @Override public void onFail(int code) { }
});

BluetoothSDK.getFirmwareVersion(new StringValueCallback() {
    @Override public void onSuccess(String version) { }
    @Override public void onFail(int code) { }
});
```

电量变化监听：

```java
BluetoothSDK.addBatteryChangedListener(new IntValueCallback() {
    @Override public void onSuccess(int battery) { }
    @Override public void onFail(int code) { }
});
```

### 7.2 用户信息 / 时间 / 单位 / 语言

```java
UserInfo user = new UserInfo();
user.setId("uid_001");
user.setGender(Gender.Male);
user.setAge(28);
user.setHeight(175);   // cm
user.setWeight(700);   // 0.1kg，700 = 70.0kg
user.setBirthdayYear(1996);
user.setBirthdayMonth(5);
user.setBirthdayDay(20);

BluetoothSDK.setUserInfo(user, new BoolCallback() {
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});

BluetoothSDK.setDeviceTime(new Date(), boolCallback);
BluetoothSDK.setDeviceTimeAndStyle(new Date(), 1 /* 1=24小时制 */, boolCallback);

BluetoothSDK.setUnit(Unit.Metric, boolCallback);   // Metric / Imperial
BluetoothSDK.setLanguage("zh", boolCallback);      // 具体码表以设备支持列表为准
BluetoothSDK.getDeviceSupportedLanguages(stringListCallback);
```

---

## 8. 目标设置

用于向设备同步 / 查询用户每日目标。包路径：`com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Goal`、`GoalType`。

**写入**按类型单项设置：`setGoal(GoalType, value, BoolCallback)`  
**读取**一次性返回完整 `Goal`：`getGoals(GoalCallback)`  
**监听**表端改目标：`addGoalUpdatedListener(GoalUpdatedCallback)`

> 注意：`setGoal` 的 `value` 单位与 `Goal` 字段单位一致；**步数特别是「百步」**，不是步数本身。详见下文。

### 8.1 实体说明：`Goal`

一次 `getGoals` 成功时返回设备上全部目标字段：

| 字段 | 类型 | 单位（协议值） | UI / App 常用换算 |
|------|------|----------------|-------------------|
| `step` | `int` | **百步**（1 = 100 步） | 展示步数 = `step * 100`；下发时 `setGoal(Step, 步数/100)` |
| `calorie` | `int` | **千卡（kcal）** | 与 UI 千卡一致，直接使用 |
| `distance` | `int` | **公里或英里**（随设备单位制） | 公制为公里，英制为英里，需结合 `getUnit` / `Unit` |
| `sleep` | `int` | **小时** | 如目标睡 8 小时则值为 `8` |
| `duration` | `int` | **运动/活动时长，分钟** | 如目标活动 30 分钟则值为 `30` |
| `otDistance` | `int` | **0.1 公里** | 展示公里 = `otDistance / 10.0` |
| `otDistanceMile` | `int` | **0.1 英里** | 展示英里 = `otDistanceMile / 10.0` |

```java
BluetoothSDK.getGoals(new GoalCallback() {
    @Override
    public void onSuccess(Goal goal) {
        int stepsDisplay = goal.getStep() * 100;          // 百步 → 步
        int kcal = goal.getCalorie();                     // 千卡
        int distanceUnit = goal.getDistance();            // 公里或英里（看单位制）
        int sleepHours = goal.getSleep();                 // 小时
        int activeMinutes = goal.getDuration();           // 分钟
        double otKm = goal.getOtDistance() / 10.0;        // 0.1km → km
        double otMile = goal.getOtDistanceMile() / 10.0;  // 0.1mi → mi
    }

    @Override
    public void onFail(int code) { }
});
```

### 8.2 `GoalType` 与 `setGoal` 取值

| GoalType | 协议值 | 对应 Goal 字段 | `setGoal` 的 value 含义 |
|----------|--------|----------------|-------------------------|
| `Step` | 0x00 | `step` | 百步。目标 8000 步 → 传 `80` |
| `Calorie` | 0x01 | `calorie` | 千卡。目标 400 kcal → 传 `400` |
| `Distance` | 0x02 | `distance` | 公里或英里整型。目标 5 km → 传 `5`（单位随设备） |
| `Sleep` | 0x03 | `sleep` | 小时。目标 8 h → 传 `8` |
| `Duration` | 0x04 | `duration` | 分钟。目标 30 min → 传 `30` |
| `OTDistance` | 0x05 | `otDistance` | 0.1 公里。目标 5.2 km → 传 `52` |
| `OTDistanceMile` | 0x06 | `otDistanceMile` | 0.1 英里。目标 3.1 mi → 传 `31` |
| `Unknown` | 0xff | — | 无效 |

```java
// 正确：8000 步目标 → 百步单位 80
BluetoothSDK.setGoal(GoalType.Step, 80, new BoolCallback() {
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});

BluetoothSDK.setGoal(GoalType.Calorie, 400, boolCallback);   // 400 千卡
BluetoothSDK.setGoal(GoalType.Distance, 5, boolCallback);    // 5 公里或英里
BluetoothSDK.setGoal(GoalType.Sleep, 8, boolCallback);       // 8 小时
BluetoothSDK.setGoal(GoalType.Duration, 30, boolCallback);   // 30 分钟
BluetoothSDK.setGoal(GoalType.OTDistance, 52, boolCallback); // 5.2 公里
```

现网 App 换算示例（与 HaWoFit 一致）：

```text
下发步数目标：setGoal(Step, appStepGoal / 100)
读回步数目标：appStepGoal = goal.getStep() * 100
```

### 8.3 表端改目标监听

用户可在手表上改目标，App 通过监听同步本地。回调方法按类型拆分（**不是**单一的 `onGoalUpdated`）：

```java
BluetoothSDK.addGoalUpdatedListener(new GoalUpdatedCallback() {
    @Override
    public void onStepUpdated(int step) {
        // 单位：百步 → 实际步数 step * 100
    }

    @Override
    public void onCalorieUpdated(int calorie) {
        // 单位：千卡
    }

    @Override
    public void onDistanceUpdated(int distance) {
        // 单位：公里或英里
    }

    @Override
    public void onDurationUpdated(int duration) {
        // 单位：分钟
    }
});

// 退出时移除
BluetoothSDK.removeGoalUpdatedListener(callback);
// 或 BluetoothSDK.removeAllGoalUpdatedListener();
```

> 监听回调目前覆盖步数 / 卡路里 / 距离 / 时长；`Sleep`、`OTDistance*` 是否有表端主动推送以固件为准，不确定时用 `getGoals` 全量刷新。

### 8.4 使用建议

1. 距离类目标依赖设备公英制（`setUnit` / `getUnit`），读写前确认当前单位。  
2. `OTDistance` / `OTDistanceMile` 为更高精度距离目标（0.1 单位），与 `Distance` 整公里（英里）并存时，以产品需求选用。  
3. 多项目标需分别多次 `setGoal`，没有「一次写入整个 Goal 对象」的接口。  
4. 绑定后环境同步阶段建议 `getGoals` 拉齐，再按业务决定是否覆盖为云端目标。

---

## 9. 健康数据同步

### 9.1 批量拉取（推荐）

一次请求拉齐设备缓存中的活动健康数据。回调可能分多次触发（每种类型一组），App 应在各回调里分别入库；全部处理完后再执行对应的 `del*`（见 §9.2）。

包路径：`com.huawo.sdk.bluetoothsdk.interfaces.ops.models.*`  
回调：`com.huawo.sdk.bluetoothsdk.interfaces.callback.ActivityDataCallback`

#### 老协议：`getActivityData`

```java
BluetoothSDK.getActivityData(new ActivityDataCallback() {
    @Override
    public void onSports(List<Sport> sportList) { /* 步数活动，见下表 Sport */ }

    @Override
    public void onSleeps(List<Sleep> sleepList) { /* 睡眠摘要，见 Sleep */ }

    @Override
    public void onHeartrates(List<Heartrate> heartrateList) { /* 心率采样点 */ }

    @Override
    public void onHrvs(List<Hrv> hrvList) { /* 压力/血氧/疲劳等合并结构，见 Hrv */ }

    @Override
    public void onPais(List<PAI> paiList) { /* PAI */ }

    @Override
    public void onFail(int code) { }
});
```

> 内部实现：睡眠先拉 `SleepPoint` 列表，再经 `Sleep.addUp(sleepPointList)` 汇总成 `Sleep` 再回调 `onSleeps`。某种类型失败时，现网 SDK 常对该类型回调空列表，不完全中断其它类型。

#### WL 协议 V2：`getActivityDataV2`

接口签名：

```java
BluetoothSDK.getActivityDataV2(
        ActivityDataType[] dataTypes,
        ActivityDataCallback callback
);
```

该接口用于杰理  协议。调用方按需传入数据类型数组，SDK 严格按数组顺序串行执行每一种类型：先通过 `GeActivityHead` 获取头包；存在数据时，再通过 `GetActivityDataV2` 拉取数据包，经 `ParseWlDataUtils` 解析后进入对应业务回调；当前类型处理结束后才请求下一种类型。

枚举路径：`com.huawo.sdk.bluetoothsdk.wl.models.ActivityDataType`。

| ActivityDataType | 含义 | `getActivityDataV2` 回调落点 |
|------------------|------|-------------------------------|
| `STEP` | 步数活动 | `onSports(List<Sport>)` |
| `SLEEP` | 睡眠 | `onSleeps(List<Sleep>)`；内部先解析 `SleepPoint`，再通过 `Sleep.addUp` 汇总 |
| `HEART_RATE` | 心率 | `onHeartrates(List<Heartrate>)` |
| `STRESS` | 压力 | `onStress(List<Stress>)` |
| `BLOOD_OXYGEN` | 血氧 | `onSpo2(List<Spo2>)` |
| `HRV` | HRV | `onHrvs(List<Hrv>)` |
| `BREATHS` | 呼吸频率 | `onBreaths(List<Breaths>)` |
| `SKIN_TEMPERATURE` | 皮肤温度 | `onSkinTemperatures(List<SkinTemperature>)` |
| `HEART_RES` | 静息心率 | `onHeartrates(List<Heartrate>)`；复用心率模型及 `resting` 字段 |
| `BODY_TEMPERATURE` | 体温枚举 | 当前批量管理器没有对应解析及业务回调，不能按已支持类型使用 |
| `WORKOUTS` | 运动记录 | 当前批量管理器不解析、不回调；请使用 `getWorkoutV2` |
| `AIRECORD` | AI 录音 | 当前批量管理器不解析、不回调；请使用 `getAiRecordDataV2` |

`STRESS`、`BLOOD_OXYGEN`、`HRV` 是三个独立的数据类型，分别回调 `Stress`、`Spo2`、`Hrv`，SDK 不会在 `getActivityDataV2` 中按时间合并它们。

```java
ActivityDataType[] types = new ActivityDataType[]{
        ActivityDataType.STEP,
        ActivityDataType.SLEEP,
        ActivityDataType.HEART_RATE,
        ActivityDataType.STRESS,
        ActivityDataType.BLOOD_OXYGEN,
        ActivityDataType.HRV,
        ActivityDataType.BREATHS,
        ActivityDataType.SKIN_TEMPERATURE
};
BluetoothSDK.getActivityDataV2(types, new ActivityDataCallback() {
    @Override public void onSports(List<Sport> sportList) { }
    @Override public void onSleeps(List<Sleep> sleepList) { }
    @Override public void onHeartrates(List<Heartrate> list) { }
    @Override public void onStress(List<Stress> stressList) { }
    @Override public void onSpo2(List<Spo2> spo2List) { }
    @Override public void onHrvs(List<Hrv> hrvList) { /* 独立的 HRV 数据 */ }
    @Override public void onBreaths(List<Breaths> list) { /* 呼吸频率，时间为毫秒 */ }
    @Override public void onSkinTemperatures(List<SkinTemperature> list) { /* 浮点摄氏度 */ }
    @Override public void onPais(List<PAI> paiList) { /* V2 不回调 PAI */ }

    @Override
    public void onFail(ActivityDataType dataType, int code) {
        // HRV、压力或血氧的头包/数据包请求失败
    }

    @Override public void onFail(int code) { }
});
```

回调边界：

1. 已支持的九种类型（原六类加 `BREATHS`、`SKIN_TEMPERATURE`、`HEART_RES`）无数据时，SDK 回调该类型对应的空列表，并继续下一类型。
2. 当前只有 `HRV`、`STRESS`、`BLOOD_OXYGEN` 的头包或数据包请求失败时，会先回调 `onFail(ActivityDataType, code)`，再回调对应空列表，并继续下一类型。其余已支持类型失败时进入对应空列表回调；不能把空列表一律认定为“设备无记录”，也不能假定新增类型都会进入类型化或通用 `onFail`。
3. 已支持类型发生数据解析异常时，SDK 回调该类型对应的空列表，并继续下一类型。
4. `dataTypes` 为 `null` 或空数组时，不会使用默认类型；SDK 依次回调心率、HRV、压力、血氧、睡眠、步数、呼吸频率、皮肤温度八个空列表后直接返回。这里不额外为 `HEART_RES` 再回调一次心率。
5. 当前没有“全部类型处理完成”的统一回调；如业务需要统一收尾，应按实际请求的已支持类型及顺序统计。`HEART_RATE` 和 `HEART_RES` 共享 `onHeartrates`，同时请求时不能只按回调方法名去重计数；失败空列表也不能用于判断请求来源。
6. 该接口不自动判断或切换平台：杰理平台使用 `getActivityDataV2`，思澈平台使用 `getActivityData`。

`HEART_RES` 需要调用方显式加入请求数组；普通 `getHeartRateV2()` 只请求 `HEART_RATE`，不会自动附加静息心率。当前 Demo 的“同步数据（杰里）”未请求 `HEART_RES`。`WORKOUTS`、`AIRECORD` 仍使用各自专用接口，不能因为枚举存在就视为批量同步已覆盖。

---

#### 实体说明：`Sport`（步数 / 活动量）

**含义**：一段时间（常为按小时或按设备采样区间）的活动摘要，不是 `Workout` 运动课程记录。  
**回调**：`onSports`  
**删除**：`delSports` / 老协议同步后删除；WL 步数也可 `getStepV2` + 业务侧清理策略以产品为准。

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `index` | `int` | 设备侧数据序号 |
| `time` | `long` | **毫秒**时间戳 |
| `step` | `long` | 步数 |
| `calorie` | `long` | 卡路里，单位 **cal（卡）**，不是 kcal |
| `staticCalorie` | `long` | 静态消耗，单位同 `calorie`（cal） |
| `distance` | `long` | 距离，单位 **米** |
| `duration` | `long` | 活动时长，单位 **分钟** |
| `heartAvg` | `int` | **WL 新增**平均心率；未开自动检测时可能为 0 |
| `sportType` | `WorkoutType` | **WL 新增**活动/运动类型枚举 |

```java
for (Sport s : sportList) {
    long steps = s.getStep();
    double kcal = s.getCalorie() / 1000.0; // 若 UI 要显示千卡
    long meters = s.getDistance();
}
```

---

#### 实体说明：`Heartrate`（心率采样点）

**含义**：单次心率测量点。  
**回调**：`onHeartrates`  
**删除**：`delHeartrates`

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `index` | `int` | 序号 |
| `time` | `long` | **毫秒**时间戳 |
| `bpm` | `int` | 心率，次/分钟 |
| `resting` | `boolean` | 静息标记，读取 `isResting()`；是否有此字段由数据来源决定，普通 WL 心率点不能代替单独的 `HEART_RES` 数据 |

```java
for (Heartrate hr : heartrateList) {
    int bpm = hr.getBpm();
    long at = hr.getTime();
}
```

---

#### 实体说明：`Hrv`（HRV / 老协议复合健康数据）

**含义**：老协议里常用一张结构承载压力、血氧、疲劳；类注释为 *stress/spo2 health data*。杰理 V2 的 `HRV` 类型会独立解析为该结构并回调 `onHrvs`；`STRESS`、`BLOOD_OXYGEN` 分别回调 `onStress`、`onSpo2`，不会合并进 `Hrv`。
**回调**：老协议复合数据及 杰理 V2 的 `HRV` 类型使用 `onHrvs`
**删除**：老协议使用 `delHrv`；杰理 V2 的 HRV、压力、血氧分别使用 `delHrvV2`、`delStress`、`delBlood`。

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `index` | `int` | 序号 |
| `time` | `long` | **毫秒**时间戳 |
| `fatigue` | `int` | 疲劳度（固件定义，注意取值范围） |
| `stress` | `int` | 压力值 |
| `spo2` | `int` | 血氧饱和度，通常 0~100（%） |

```java
// -255 表示没有值
for (Hrv h : hrvList) {
    int stress = h.getStress();
    int spo2 = h.getSpo2();      // 血氧
    int fatigue = h.getFatigue();
}
```

> WL 也可单独调用 `getStressV2` / `getSpo2V2`，得到独立的 `Stress`、`Spo2` 模型（字段分别为 `stress`/`spo2` + `time` + `index`）。

---

#### 实体说明：`Sleep`（睡眠摘要）+ `SleepPoint`（睡眠明细点）

**含义**：一次完整睡眠段的汇总；明细在 `sleepPointList`。老协议下 SDK 用 `Sleep.addUp(List<SleepPoint>)` 把连续 `SleepPoint` 聚合成 `Sleep`。  
**回调**：`onSleeps`（已是汇总后的 `Sleep` 列表）  
**删除**：`delSleeps`；明细也可 `getSleepPoints` / `delSleepPoints`（若业务需要原始点）

**`Sleep` 字段：**

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `startTime` | `long` | 入睡开始，**毫秒** |
| `endTime` | `long` | 醒来结束，**毫秒** |
| `awakeDuration` | `long` | 清醒累计，**毫秒** |
| `lightDuration` | `long` | 浅睡累计，**毫秒** |
| `deepDuration` | `long` | 深睡累计，**毫秒** |
| `remDuration` | `long` | REM 累计，**毫秒** |
| `totalDuration` | `long` | 总时长（清醒+浅+深+REM），**毫秒** |
| `awakeCount` | `int` | 夜间清醒次数 |
| `sleepPointList` | `List<SleepPoint>` | 状态变化明细 |

**`SleepPoint` 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | `int` | 序号 |
| `time` | `long` | 该状态点时间，**毫秒** |
| `state` | `SleepPointState` | 睡眠状态枚举 |

**`SleepPointState` 常用值：**

| 枚举 | 含义 |
|------|------|
| `Enter` | 进入睡眠 |
| `Exit` | 退出睡眠 |
| `Deep` | 深睡 |
| `Light` | 浅睡 |
| `Awake` | 清醒 |
| `REM` | 快速眼动 |
| `PRE` / `PRESET` / `SUMMARY` / `ACTIVITY` | WL 扩展态，按固件兼容处理 |

```java
for (Sleep sleep : sleepList) {
    long deepMin = sleep.getDeepDuration() / 60_000L;
    long totalMin = sleep.getTotalDuration() / 60_000L;
    for (SleepPoint p : sleep.getSleepPointList()) {
        SleepPointState st = p.getState();
        long t = p.getTime();
    }
}
```

---

#### 实体说明：`PAI`

**含义**：Personal Activity Intelligence（个人活动智能指数）日/段统计。批量接口主要在**老协议** `getActivityData` 的 `onPais` 中出现；WL 批量 V2 中暂无 PAI。  
**回调**：`onPais`  
**删除**：`delPAIs`

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `time` | `long` | 时间（协议原始值经 `getTime()` 返回；对接时建议与样机核对是秒还是毫秒） |
| `totalValue` | `int` | PAI 总值 |
| `lowValue` / `medialValue` / `highValue` | `int` | 低 / 中 / 高强度对应 PAI 分量 |
| `lowDuration` / `medialDuration` / `highDuration` | `int` | 对应强度累计时长，单位 **秒** |

```java
for (PAI pai : paiList) {
    int total = pai.getTotalValue();
    int highSec = pai.getHighDuration();
}
```

---

#### 相关但未进批量回调的实体（按需单拉）

| 模型 | 说明 | 常用 API |
|------|------|----------|
| `Spo2` | 仅血氧点：`index` / `time(ms)` / `spo2` | `getSpo2V2` |
| `Stress` | 仅压力点：`index` / `time(ms)` / `stress` | `getStressV2` |
| `BloodPressure` | 血压：`systolic` 收缩压 / `diastolic` 舒张压 / `time(ms)` | `getBPs` / `delBPs` |
| `Workout` | 运动课程（含轨迹等） | `getWorkouts` / `getWorkoutV2`（见 §10） |

#### 使用建议

1. 杰理平台调用 `getActivityDataV2`， 思澈平台调用 `getActivityData`。  
2. 各 `onXxx` **独立入库**；不要假设回调顺序固定。  
3. 入库成功后再 `del*`，避免丢数与重复。  
4. 卡路里单位是 **cal**；展示千卡需 `/1000`。  
5. 睡眠时长字段多为 **毫秒**；UI 展示分钟时注意换算。

### 9.2 分类型拉取与删除

同步到 App 后，通常应删除设备端缓存，避免重复同步：

```java
BluetoothSDK.getHeartrates(new HeartratesCallback() {
    @Override
    public void onSuccess(List<Heartrate> list) {
        // 入库后删除设备侧
        BluetoothSDK.delHeartrates(boolCallback);
    }
    @Override public void onFail(int code) { }
});

BluetoothSDK.getSleeps(sleepsCallback);
BluetoothSDK.delSleeps(boolCallback);

BluetoothSDK.getSports(sportsCallback);
BluetoothSDK.delSports(boolCallback);

BluetoothSDK.getBPs(bloodPressuresCallback);
BluetoothSDK.delBPs(boolCallback);

BluetoothSDK.getHrvs(hrvsCallback);
BluetoothSDK.delHrv(boolCallback);

//jieli V2
// 以下为读取入口清单；每次等待本次回调后，再发起下一类读取。
BluetoothSDK.getSpo2V2(spo2Callback);
BluetoothSDK.getStressV2(stressCallback);
BluetoothSDK.getHeartRateV2(heartratesCallback);
BluetoothSDK.getSleepV2(sleepsCallback);
BluetoothSDK.getStepV2(sportsCallback);
BluetoothSDK.getHrvV2(hrvsCallback);
BluetoothSDK.getSkinTemperature(skinTemperatureCallback);
BluetoothSDK.getBreaths(breathsCallback);
```

新增历史数据示例（回调包路径同 §9.1）：

```java
BluetoothSDK.getSkinTemperature(new SkinTemperatureCallback() {
    @Override public void onSuccess(List<SkinTemperature> data) {
        for (SkinTemperature item : data) {
            long timeMs = item.getTime();
            float celsius = item.getCelsius(); // 摄氏度；保留小数，不再次缩放
        }
        // 生产 App：保存成功后才调用 delSkinTemperature，不以页面已显示代替入库完成。
    }
    @Override public void onFail(int code) { /* 保留接口名、错误码及上下文 */ }
});

BluetoothSDK.getBreaths(new BreathsCallback() {
    @Override public void onSuccess(List<Breaths> data) {
        for (Breaths item : data) {
            long timeMs = item.getTime();
            int rate = item.getRate(); // 设备返回的呼吸频率；单位以固件对接说明为准
        }
    }
    @Override public void onFail(int code) { /* 保留原始错误码 */ }
});
```

`SkinTemperature` 提供 `index: int`、`time: long`（毫秒）、`celsius: float`（摄氏度）；`Breaths` 提供 `index: int`、`time: long`（毫秒）、`rate: int`。SDK 已将设备秒时间戳转换成毫秒，App 不要再乘以 1000。

新增类型的清理入口是 `delHrvV2(BoolCallback)`、`delSkinTemperature(BoolCallback)`、`delBreaths(BoolCallback)`。删除与读取分开；生产同步流程应先可靠保存，读取失败时不要自动删除。下方 Demo 的删除按钮是独立指令，不要求此前读取成功，也不由读取动作自动触发。

当前 Demo 首页“同步数据（杰理）”只打开 `JLDataSyncFragment` 独立指令页，传递已选设备 MAC，不立即读取、删除或重连。页面采用卡片布局，18 个读取／删除按钮按三列排列，长文字保持字号换行，删除按钮标红；操作区与执行结果区分别滚动。其中九个读取按钮、九个删除按钮：原同步链八类数据的读／删接口保持不变，另补充 Workout 运动记录的独立读／删指令：

```text
步数／活动：getStepV2          / delSports
心率：     getHeartRateV2     / delHeartrates
睡眠：     getSleepV2         / delSleeps
HRV：      getHrvV2           / delHrvV2
血氧：     getSpo2V2          / delBlood
压力：     getStressV2        / delStress
皮肤温度： getSkinTemperature / delSkinTemperature
呼吸频率： getBreaths         / delBreaths
运动记录： getWorkoutV2      / delWorkouts
```

18 个按钮分别调用 Repository 的单项接口封装，不串联其他数据类型，也不使用 §9.1 的批量接口。读取仅显示本次类型、条数和明细；返回空列表明确显示 0 条，读取成功或失败均不自动发送删除。删除只由相应按钮触发，不依赖上一次是否读取或是否返回非空列表；成功时展示对应删除指令已被设备确认，不自动回读或伪造剩余数量。删除会清理设备端对应类型的历史数据，生产 App 应在保存需要的数据后再删除。`delWorkouts()` 删除设备端全部 Workout 历史记录，不是删除某一条记录，也不是清理步数／活动摘要。

`JLDataSyncViewModel` 在每次实际操作前检查连接；未连接时按首页传入的已选 MAC 重连，缺少 MAC 或重连失败时不发送该数据指令。页面同时只处理一个操作，等待期间禁用全部指令按钮，开始操作清除上一结果。退出／ViewModel 清理时仅取消本页等待，以请求版本屏蔽迟到结果及旧任务的忙碌状态更新，不撤销已经排队或发送的 SDK 命令，也不清理全局 SDK 任务。日志保留接口、类型、重连、条数、成功／失败及原始错误码上下文。

数据来自设备，不是本地模拟记录；保留现有模型的毫秒时间戳、浮点摄氏度及其他单位，不增加平台判断。新页结果不回写为首页“杰理同步完成”，不清空首页已有汇总；通用 `sync()`、其自动清理和首页底部汇总保持原行为。`getWorkoutV2()` 以 SDK 原始 `Workout` 模型展示运动记录及随回包提供的点列，和 `getStepV2()` 的步数／活动摘要分开；读取不自动删除，也不额外请求 `getWorkoutPoints()`。本页不读取 `HEART_RES` 或体温 `BODY_TEMPERATURE`；运动接口详见 §10。

### 9.3 监测开关与告警

```java
BluetoothSDK.setHeartrateMonitorInterval(10, boolCallback); // 分钟等，以产品定义为准
BluetoothSDK.setHeartrateMonitorInterval(0, boolCallback); // 关闭心率自动监测，页面开关关闭时传 0
BluetoothSDK.getHeartrateMonitorInterval(intValueCallback);
BluetoothSDK.setHeartrateAlarm(/*enable*/ true, /*low*/ 50, /*high*/ 180, boolCallback);

BluetoothSDK.setSpO2MonitorEnable(true, boolCallback);
BluetoothSDK.setSpO2MonitorEnable(false, boolCallback);
BluetoothSDK.getSpO2MonitorEnable(boolValueCallback);
BluetoothSDK.setStressMonitorEnable(true, boolCallback);
BluetoothSDK.setStressMonitorEnable(false, boolCallback);
BluetoothSDK.getStressMonitorEnable(boolValueCallback);
BluetoothSDK.setSpO2Alert(true, 90, boolCallback);

// 即时血氧
BluetoothSDK.startSpO2Monitoring(boolCallback);
BluetoothSDK.stopSpO2Monitoring(boolCallback);
```

实时心率监听：

```java
BluetoothSDK.addRealtimeHeartrateListener(new HeartrateValueChangedCallback() {
    @Override
    public void onHeartrateValueChanged(int bpm) { }
});
```

Demo 的“自动监测（杰理）”采用逐项开关布局，每次页面可见时串行读取心率、血氧、压力、皮肤温度和呼吸频率的真实设备设置；右上角刷新按钮可重新读取。未确认或读取失败的项禁用，不以灰色开关伪装成已确认关闭；暂不显示 HRV，当前 SDK 未提供其自动监测读取／设置接口，不借用心率或压力接口代替。UI 回填开关状态不会触发设置，用户切换只调用对应指标的设置接口，不增加平台判断。心率开启沿用现有示例 `10` 分钟、关闭传 `0`；血氧和压力使用上述布尔开关接口。

保存应答成功后才更新本页确认值；保存失败恢复原显示状态，并禁用该项直到刷新确认设备状态，不发送补偿／回滚设置。读取／保存互斥执行，接口名、参数及 SDK 原始错误码保留在日志与错误文案中。断连或切换设备使本页旧状态失效；退出只取消自身等待并隔离迟到结果，不清空 SDK 全局队列、不关闭设备自动监测，已排队／发送的设置仍可能生效。重新进入页面会重新读取设备状态。

```java
// 皮肤温度：读取、开启、关闭
BluetoothSDK.getSkinTemperatureMonitorInterval(intValueCallback);
BluetoothSDK.setSkinTemperatureMonitorInterval(60, boolCallback);
BluetoothSDK.setSkinTemperatureMonitorInterval(0, boolCallback);

// 呼吸频率：读取、开启、关闭
BluetoothSDK.getRespiratoryRateMonitorInterval(intValueCallback);
BluetoothSDK.setRespiratoryRateMonitorInterval(60, boolCallback);
BluetoothSDK.setRespiratoryRateMonitorInterval(0, boolCallback);
```

这两个接口当前使用单字节参数，Demo 校验取值为 `0..255`。源码明确的开关语义为：`0` 关闭，正数开启；`60` 只是与 HaWoFit 一致的示例值，不能在未确认产品协议时解释为“每 60 分钟采样”。设置成功表示设备确认本次设置，不表示已经产生一条监测数据；需要监测结果时再使用历史读取或实时监听接口。

### 9.4 手动测量（杰里）

无屏 Demo 通过 `ManualMeasurementFragment` 演示六种独立测量入口和“停止当前测量／重试停止”。`DataType` 包路径为 `com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DataType`，支持的枚举为 `HeartRate`、`SpO2`、`Stress`、`HRV`、`RespiratoryRate`、`SkinTemperature`。枚举及 SDK 入口存在，不代表所有设备固件都支持对应测量。

```java
// 以下变量由当前页面持有；注册与移除必须使用同一实例。
IntValueWithTimeCallback intValues = (value, timeMs) -> {
    // 心率、血氧、压力、HRV、呼吸频率：整数值 + 设备毫秒时间戳
};
FloatValueWithTimeCallback floatValues = (value, timeMs) -> {
    // 皮肤温度：浮点摄氏度 + 设备毫秒时间戳
};
MonitorStateChangedCallback stopped = new MonitorStateChangedCallback() {
    @Override public void onStop(DataType type) { /* 仅处理当前测量类型的结束事件 */ }
};
NotProperlyWornCallback worn = new NotProperlyWornCallback() {
    @Override public void onAppear() { /* 提示正确佩戴，不显示测量完成 */ }
};

// 本例启动心率；皮肤温度只注册 floatValues，其余类型只注册 intValues。
BluetoothSDK.addRealtimeIntWithTimeListener(intValues);
BluetoothSDK.addMonitorStateChangedCallback(stopped);
BluetoothSDK.addNotProperlyWornCallback(worn);
BluetoothSDK.startMonitoring(DataType.HeartRate, new BoolCallback() {
    @Override public void onSuccess() { /* 开始指令已确认，继续等待读数及设备结束 */ }
    @Override public void onFail(int code) { /* 记录 code，区分明确失败与结果不确定 */ }
});

// 用户停止时使用同一个当前类型；不要同时调用多个开始接口。
BluetoothSDK.stopMonitoring(DataType.HeartRate, boolCallback);

// 完成本次会话后再移除自身监听；不要调用 removeAll* 影响其他页面。
BluetoothSDK.removeRealtimeIntWithTimeListener(intValues);
BluetoothSDK.removeMonitorStateChangedCallback(stopped);
BluetoothSDK.removeNotProperlyWornCallback(worn);
// 皮肤温度对应 addRealtimeFloatWithTimeListener(floatValues)
//              / removeRealtimeFloatWithTimeListener(floatValues)。
```

上例是接口调用片段，停止与清理不是紧跟开始后立即执行。Demo 的会话顺序是：注册本次监听 → 发送开始指令 → 指令确认 → 接收读数 → 设备结束或主动停止 → 清理本次会话。同一时间只保留一个测量会话；带时间的数值回调不携带 `DataType`，不能同时开启多个类型后再根据回调自行猜测来源。UI 更新切回主线程，迟到回调按会话标识丢弃。

- 开始成功只是指令确认，不是“测量完成”。数值、设备时间、佩戴异常和设备停止分别处理。
- 明确拒绝开始时，Demo 保留原错误、清理本地会话并恢复开始按钮，不再追加停止命令。超时、发送或解析异常等不确定结果不能直接视为设备从未开始。
- 用户在等待开始确认时退出，Demo 记录停止意图，开始确认后再发送停止。正常测量退出时请求停止，保留必要的停止事件监听直至收尾；断连时清理本地会话。
- 主动停止的 Demo 成功条件是停止应答和对应设备停止事件都已收到；未确认时保留重试停止，禁止开启新会话。这是 Demo 的保护策略，不是所有 SDK API 共用的完成约定。
- Demo 开始／停止确认超时为 30 秒，测量等待超时为 60 秒；这是页面控制参数，不是固件协议常量。
- 皮肤温度历史同步、自动监测和手动测量是不同能力，不能互相推断支持情况。当前验证设备不支持皮肤温度手动测量；示例按钮仍保留，失败后应允许选择其他测量。设备返回码 `1` 没有在此统一定义为“不支持”，必须保留原始错误和测量类型。

---

## 10. 运动 Workout

用于同步设备上的**运动课程记录**，以及 App 发起/控制运动、接收实时数据。

包路径：`com.huawo.sdk.bluetoothsdk.interfaces.ops.models.*`

### 10.1 同步历史运动

```java
BluetoothSDK.getWorkouts(new WorkoutsCallback() {
    @Override
    public void onSuccess(List<Workout> list) {
        for (Workout w : list) {
            // 摘要字段见 §10.3；明细/轨迹可能已在对象内，或需再拉点
            List<WorkoutPoint> points = w.getWorkoutPointList();
            List<WorkoutGpsPoint> gps = w.getWorkoutGpsPointList();
        }
        BluetoothSDK.delWorkouts(boolCallback); // 入库后再删，避免重复同步
    }
    @Override public void onFail(int code) { }
});

// WL
BluetoothSDK.getWorkoutV2(workoutsCallback);

// 按需单独拉轨迹/明细点（部分机型 getWorkouts 不含完整点列）
BluetoothSDK.getWorkoutPoints(new WorkoutPointsCallback() {
    @Override
    public void onSuccess(List<WorkoutPoint> points, List<WorkoutGpsPoint> gpsPoints) { }
    @Override public void onFail(int code) { }
});

BluetoothSDK.getWorkoutCount(intValueCallback);
BluetoothSDK.getWorkoutPointCount(intValueCallback);
```

### 10.2 App 控制运动

```java
// 开始（可指定表端实时上报间隔，秒）
BluetoothSDK.startWorkout(WorkoutType.OutdoorRuning, boolCallback);
BluetoothSDK.startWorkout(WorkoutType.OutdoorRuning, /*dataUpdateInterval*/ 1, boolCallback);

BluetoothSDK.suspendWorkout(boolCallback);
BluetoothSDK.resumeWorkout(boolCallback);
BluetoothSDK.stopWorkout(boolCallback);

// 手机侧实时数据下发到表（如 App GPS 运动）
BluetoothSDK.setWorkoutRealtimeData(realtimeData, boolCallback);
BluetoothSDK.getWorkoutRealtimeData(workoutRealtimeDataCallback);

// 表端推送实时数据（建议 Application 期注册）
BluetoothSDK.addWorkoutRealtimeDataUpdatedListener(
        new WorkoutRealtimeDataUpdatedCallback() {
            @Override
            public void onWorkoutRealtimeDataUpdated(WorkoutRealtimeData data) {
                WorkoutState st = data.getState();
                int bpm = data.getBpm();
                double lat = data.getLatitude();
                double lon = data.getLongitude();
            }
        });

BluetoothSDK.addWorkoutStatusChangedCallback(new WorkoutStatusChangedCallback() {
    @Override
    public void onUpdate(WorkoutStatus status) {
        // Working：运动中；Stopped：已停止
        if (status == WorkoutStatus.Working) { /* ... */ }
        if (status == WorkoutStatus.Stopped) { /* ... */ }
    }
});
```

`WorkoutAction`（内部控制指令，一般用上面封装方法即可）：

| 枚举 | 值 | 含义 |
|------|----|------|
| `NONE` | 0x00 | 无操作 |
| `START` | 0x01 | 开始 |
| `SUSPEND` | 0x02 | 暂停 |
| `STOP` | 0x03 | 结束 |
| `RESUME` | 0x04 | 继续 |

`WorkoutStatus`（状态监听回调参数，较粗粒度）：

| 枚举 | 值 | 含义 |
|------|----|------|
| `Stopped` | 0 | 运动已停止 |
| `Working` | 1 | 运动进行中 |

> 更细的开始/暂停/继续见实时数据里的 `WorkoutState`（`WorkoutRealtimeData.state`）。

---

### 10.3 实体说明：`Workout`（运动摘要）

一次完整运动课程的结算数据。`calories` 单位为 **cal（卡）**，不是 kcal。

#### 基础信息

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `index` | `int` | 数据序号（可能重复，勿单独当唯一键） |
| `startTime` / `endTime` | `long` | 起止时间，**毫秒** |
| `type` | `WorkoutType` | 运动类型枚举 |
| `typeForLS` | `int` | LS 机型固件类型值（与 App 枚举可能不一致） |
| `step` | `long` | 步数 |
| `calories` | `long` | 消耗，**cal** |
| `distance` | `long` | 距离，**米** |
| `duration` | `long` | 时长，**分钟**（摘要）；明细点里多为秒，见 `WorkoutPoint` |
| `pace` | `long` | 配速，**秒/公里** |
| `speed` | `long` | 速度，单位 **0.01 km/h**（展示 km/h = `speed / 100.0`） |
| `lapDuration` | `long` | 单圈秒数（部分产品无值） |
| `bpm` / `maxBpm` / `minBpm` | `int` | 平均 / 最大 / 最小心率 |
| `cadence` | `int` | 步频，**步/分钟** |

#### 心率区间时长（秒）

| 字段 | 说明 |
|------|------|
| `restingDuration` / `warmupDuration` / `burningDuration` | 静息 / 热身 / 燃脂 |
| `aerobicDuration` / `anaerobicDuration` / `limitDuration` | 有氧 / 无氧 / 极限 |
| `restDuration` | 休息时长 |

#### 爬升 / 气压 / 训练效果

| 字段 | 单位 / 说明 |
|------|-------------|
| `elevationGain` / `elevationLoss` / `elevationNow` | 爬升 / 下降 / 当前海拔，**米** |
| `strike` | 步幅，单位 **0.01 米** |
| `recoveryTime` | 建议恢复时长，**秒** |
| `aerobicTrainingEffect` / `anaerobicTrainingEffect` | 训练效果：`>200` 很好，`>100` 好，`≤100` 一般 |
| `vo2max` | 最大摄氧量相关；等级可用 `vo2max/100`：>90→7 … ≤15→1|
| `maxPressure` / `minPressure` | 气压，**Pa** |
| `supportedNav` | 是否支持导航类能力 |
| `paceMax` / `paceMaxValue` / `paceMinValue` | **WL 新增**配速相关极值 |

#### 游泳 / 划船 / 跳绳等专项

| 字段 | 说明 |
|------|------|
| `actionCount` | 游泳划水次数，或划船桨次 |
| `swolf` | 游泳 SWOLF |
| `actionPosture` | 泳姿：`0`未知 `1`自由 `2`蛙 `3`仰 `4`蝶 `5`混合 |
| `laps` | 游泳趟数 |
| `actionRate` | 每分钟动作次数 |
| `maxConsecutiveActionCount` | 跳绳最大连续次数 |
| `interruptActionCount` | 跳绳中断次数 |
| `actualDuration1` | 游泳运动时长 |
| `actualDuration2` | 游泳休息时长 |
| `actualDuration3` | 预留 |
| `poolLength` | 泳池长度，**米** |

#### 嵌套列表

| 字段 | 类型 | 说明 |
|------|------|------|
| `workoutPointList` | `List<WorkoutPoint>` | 过程采样点 |
| `workoutGpsPointList` | `List<WorkoutGpsPoint>` | GPS 轨迹点 |
| `suspendedSections` | `List<WorkoutSuspendedSection>` | 暂停区间（点暂停产生） |

```java
double kmh = w.getSpeed() / 100.0;
double kcal = w.getCalories() / 1000.0;
long durationMin = w.getDuration();
```

---

### 10.4 实体说明：`WorkoutPoint`（运动过程点）

到某一时刻的累计过程数据（用于配速曲线、心率曲线等）。默认构造会把若干数值置为 `-255` 表示无效。

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `time` | `long` | **毫秒** |
| `offsetTime` | `long` | 内部偏移，业务可忽略 |
| `step` / `calories` / `distance` | `long` | 至该点累计：步数 / **cal** / **米** |
| `duration` | `long` | 至该点累计时长，**秒** |
| `bpm` | `int` | 与上一点之间的平均心率 |
| `pace` | `int` | **秒/公里** |
| `speed` | `int` | **0.01 km/h** |
| `actionPosture` / `currentSwolf` / `actionCount` | `int` | 游泳等专项 |
| `currentDuration` / `avgActionRate` / `maxActionRate` | `int` | 专项过程量 |
| `currentAvgPace` / `consecutiveActionCount` | `int` | 配速 / 连续动作 |
| `state` | `int` | `1`开始 `2`暂停 `3`继续 `4`结束；`-1` 未设置 |

辅助判断：

```java
if (point.isSuspended()) { /* 暂停中：字段=-2 或 state==2 */ }
if (point.isSuspendedEnd()) { /* 暂停结束继续：字段=-1 或 state==3 */ }
```

---

### 10.5 实体说明：`WorkoutGpsPoint`（GPS 轨迹点）

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `time` | `long` | **毫秒** |
| `offsetTime` | `long` | 内部用 |
| `valid` | `boolean` | 点是否有效 |
| `longitude` / `latitude` | `double` | 经度 / 纬度（东经正、西经负；北纬正、南纬负） |
| `speed` | `double` | **m/s** |
| `altitude` | `double` | 海拔，**米** |
| `accuracy` | `float` | 精度，**米** |
| `suspended` | `boolean` | 是否处于暂停段 |
| `rssi` | `int` | 信号相关 |
| `currentSteps` | `int` | 调试用步数 |
| `turnBack` | `boolean` | 折返标记 |

---

### 10.6 实体说明：`WorkoutSuspendedSection`

| 字段 | 类型 | 说明 |
|------|------|------|
| `startTime` | `long` | 暂停开始，**毫秒** |
| `endTime` | `long` | 暂停结束，**毫秒** |

用于画轨迹时跳过暂停段或折算有效运动时间。

---

### 10.7 实体说明：`WorkoutRealtimeData`（实时运动数据）

运动进行中表端上报或 App 下发的实时包。与历史 `Workout` 相比：**`duration` 为秒**；速度单位仍为 **0.01 km/h**。

| 字段 | 类型 | 单位 / 说明 |
|------|------|-------------|
| `time` | `long` | **毫秒** |
| `state` | `WorkoutState` | 见下表 |
| `type` / `typeForLS` | `WorkoutType` / `int` | 运动类型 |
| `step` / `calories` / `distance` | `int` | 步数 / **cal** / **米** |
| `duration` | `int` | **秒** |
| `pace` | `int` | **秒/公里** |
| `speed` | `int` | **0.01 km/h** |
| `bpm` | `int` | 当前心率 |
| `heartRateCaution` | `boolean` | 是否需要高心率提醒 |
| `longitude` / `latitude` / `altitude` | `double` | GPS；海拔 **米** |
| `accuracy` | `float` | GPS 精度，**米** |
| `gpsSpeed` | `double` | GPS 速度，**m/s** |
| `suspended` | `boolean` | 是否已暂停 |
| `laps` / `actionCount` / `swolf` / `actionPosture` | `int` | 游泳等 |
| `consecutiveActionCount` / `interruptActionCount` / `actionRate` | `int` | 跳绳等 |
| `elevation` / `elevationGain` / `elevationLoss` | `int` | 海拔相关，**米** |
| `strike` | `int` | 步幅，**0.01 米** |
| `cadence` | `int` | 步频，步/分 |
| `pressure` | `int` | 气压，**Pa** |
| `lapDuration` | `long` | 单圈秒数（部分产品无值） |
| `actualDuration1/2/3` | `int` | 游泳等专项时长 |

`WorkoutState`：

| 枚举 | 值 | 含义 |
|------|----|------|
| `NONE` | 0x00 | 无 |
| `STARTED` | 0x01 | 已开始 |
| `SUSPENDED` | 0x02 | 已暂停 |
| `STOPPED` | 0x03 | 已结束 |
| `RESUMED` | 0x04 | 已继续 |
| `DATAUPDATED` | 0x05 | 数据更新 |

---

### 10.8 `WorkoutType`（运动类型）

枚举项很多（户外跑/走/骑、室内、游泳、瑜伽等）。常用示例：

| 枚举 | 说明 |
|------|------|
| `OutdoorWalking` | 户外步行 |
| `OutdoorRuning` | 户外跑步（注意拼写为 `Runing`） |
| `OutdoorCycling` | 户外骑行 |
| `IndoorWalking` / `IndoorRunning` / `IndoorCycling` | 室内 |
| `Swimming` | 游泳 |
| `Yoga` | 瑜伽 |
| `OutdoorRun` | LS 项目用的户外跑类型值 |

完整列表见 `WorkoutType.java`；LS 机型另用 `typeForLS` / `startWorkoutForLS(int type, ...)`。

---

### 10.9 使用建议

1. **与 `Sport` 区分**：`Sport` 是日常活动量；`Workout` 是一次明确开停的锻炼。  
2. 卡路里统一按 **cal** 解析；速度摘要/实时多为 **0.01 km/h**；GPS `gpsSpeed` 为 **m/s**。  
3. `Workout.duration`（摘要）多为**分钟**，`WorkoutPoint`/`WorkoutRealtimeData.duration` 多为**秒**，换算时勿混用。  
4. 历史同步入库后调用 `delWorkouts`；轨迹不完整时再 `getWorkoutPoints`。  
5. App GPS 运动：监听表状态 + `setWorkoutRealtimeData` 把手机定位回传表端。

---

## 11. 闹钟与提醒

### 11.1 闹钟

`Alarm.id` 是设备端闹钟的唯一标识，编辑和删除都依赖该值。通用接口与杰理 接口的新增 ID 逻辑不同。

基础数据与查询：

```java
Alarm alarm = new Alarm();
alarm.setOn(true);
alarm.setContent("起床");
// timePointList / week / repeatPeriodUnit 等按产品协议填充
alarm.setMonday(true);
alarm.setTuesday(true);
alarm.setWednesday(true);
alarm.setThursday(true);
alarm.setFriday(true);

BluetoothSDK.getAlarms(new AlarmsCallback() {
    @Override public void onSuccess(List<Alarm> list) { }
    @Override public void onFail(int code) { }
});
```

通用/老协议新增闹钟时，直接调用 `addAlarm`，SDK 会先向设备查询可用 ID，并将设备返回的 ID 写入 `alarm`：

```java
BluetoothSDK.addAlarm(alarm, boolCallback);
```

当前 Demo 在独立的“闹钟与提醒（杰里）”页面演示杰里新增闹钟，预留 ID `1~5` 给闹钟、`6~10` 给自定义提醒；数量和区间仅为 Demo 约定，实际产品需按设备能力调整。闹钟和提醒共享 ID 空间，`addAlarmV2` 不会查询、生成或校验 ID，因此 Demo 先读取两类列表，从闹钟预留范围选择最小未占用 ID，再写入 `Alarm.id` 后发送：

```java
private static int findAvailableJlAlarmId(List<Alarm> alarms, List<Reminder> reminders) {
    boolean[] used = new boolean[6];
    if (alarms != null) {
        for (Alarm item : alarms) {
            int id = item.getId();
            if (id >= 1 && id <= 5) {
                used[id] = true;
            }
        }
    }
    if (reminders != null) {
        for (Reminder item : reminders) {
            int id = item.getId();
            if (id >= 1 && id <= 5) used[id] = true;
        }
    }
    for (int id = 1; id <= 5; id++) {
        if (!used[id]) return id;
    }
    return -1;
}

BluetoothSDK.getAlarms(new AlarmsCallback() {
    @Override
    public void onSuccess(List<Alarm> alarms) {
        BluetoothSDK.getReminders(new RemindersCallback() {
            @Override public void onSuccess(List<Reminder> reminders) {
                int alarmId = findAvailableJlAlarmId(alarms, reminders);
                if (alarmId == -1) {
                    // Demo 闹钟 ID 1~5 已占满，不覆盖旧记录，也不调用 addAlarmV2。
                    return;
                }
                alarm.setId(alarmId);
                BluetoothSDK.addAlarmV2(alarm, boolCallback);
            }
            @Override public void onFail(int code) { /* 读取失败，不继续新增 */ }
        });
    }

    @Override
    public void onFail(int code) {
        // 读取失败时无法安全分配 ID，不要继续新增。
    }
});
```

当前 Demo 的杰里 ID 使用规则：

1. 闹钟从 Demo 预留的 `1~5` 分配，自定义提醒从 `6~10` 分配；不能使用列表数量直接作为 ID，也不调用通用 `Alarm.generateId`／`Reminder.generateId`。
2. 必须使用 `getAlarms` 与 `getReminders` 返回 ID 的并集；例如已有 ID `1、3` 时，新闹钟应使用 `2`。
3. 对应预留范围全部占用时，提示没有可用 ID，不覆盖已有闹钟或提醒；这不是对所有固件最大数量的声明。
4. 新增成功后建议重新调用 `getAlarms` 刷新本地数据；不要并发执行多个“查询 ID → 新增”流程，避免选中相同 ID。
5. 编辑时保留原 `Alarm.id` 调用 `editAlarm`；删除时将同一 ID 传给 `delAlarmBy`。

编辑与删除：

```java
BluetoothSDK.editAlarm(alarm, boolCallback);
BluetoothSDK.delAlarmBy(alarmId, boolCallback);
BluetoothSDK.delAllAlarms(boolCallback);
```

### 11.2 久坐 / 喝水 / 洗手

```java
SedentaryReminder sedentary = new SedentaryReminder();
sedentary.setOn(true);
sedentary.setStartTime(new TimePoint(9, 0));
sedentary.setEndTime(new TimePoint(18, 0));
sedentary.setInterval(60 * 60); // 秒
sedentary.setWeek(/* 按 WeekDay 位或设置方法 */);
BluetoothSDK.setSedentaryReminder(sedentary, boolCallback);
BluetoothSDK.getSedentaryReminder(sedentaryReminderCallback);

DrinkWaterReminder drink = new DrinkWaterReminder();
drink.setOn(true);
drink.setStartTime(new TimePoint(8, 0));
drink.setEndTime(new TimePoint(20, 0));
drink.setInterval(60 * 60);
BluetoothSDK.setDrinkWaterReminder(drink, boolCallback);

WashHandReminder wash = new WashHandReminder();
// ... 填充字段
BluetoothSDK.setWashHandReminder(wash, boolCallback);
```

### 11.3 一般提醒 / 快捷回复

```java
BluetoothSDK.addReminder(reminder, boolCallback);
BluetoothSDK.addReminderV2(reminder, boolCallback); // WL
BluetoothSDK.getReminders(remindersCallback);
BluetoothSDK.editReminder(reminder, boolCallback);
BluetoothSDK.delReminderBy(id, boolCallback);
BluetoothSDK.delAllReminder(boolCallback); // 只删除提醒，不删除闹钟

BluetoothSDK.getQuickReplies(quickRepliesCallback);
BluetoothSDK.editQuickReply(quickReply, boolCallback);
```

“闹钟与提醒（杰里）”页面提供提醒读取、添加工作日 10:00 示例及删除全部提醒。新增示例使用 `ReminderType.Custom`、开关开启、周一至周五，先成功读取闹钟和提醒两类列表，从未占用的 Demo 提醒 ID `6~10` 中选择最小值，再调用 `addReminderV2`；读取失败或范围用满时不发送新增。闹钟示例保持工作日 07:30、稍后提醒 10。两个“删除全部”按钮分别调用 `delAllAlarms`（`0x9C` 操作 `0x05`）和 `delAllReminder`（操作 `0x04`），不使用删除闹钟与提醒合集的操作 `0x03`。

### 11.4 手机日程同步

```java
List<PhoneScheduleEvent> events = ...;
BluetoothSDK.syncPhoneSchedules(events, boolCallback);
```

---

### 11.5 低电量提醒（无屏示例）

```java
BluetoothSDK.getLowBatteryReminderSwitch(new BoolValueCallback() {
    @Override public void onSuccess(boolean on) { /* 展示当前设备设置 */ }
    @Override public void onFail(int code) { /* 展示读取失败和原始错误码 */ }
});
BluetoothSDK.setLowBatteryReminderSwitch(true, boolCallback);
BluetoothSDK.setLowBatteryReminderSwitch(false, boolCallback);
```

读取、开启、关闭是三个独立示例，直接放在 Demo 的“闹钟与提醒（杰里）”页面，不再跳转低电量提醒详情页。设置成功不表示设备已经产生低电量提醒；主动提醒事件使用 §11.7 的另一组监听接口。

### 11.6 振动强度（无屏示例）

```java
BluetoothSDK.getVibrationSettings(new VibrationSettingsCallback() {
    @Override public void onSuccess(VibrationSettings settings) {
        boolean on = settings.isOn();
        int intensity = settings.getIntensity();
    }
    @Override public void onFail(int code) { /* 保留错误上下文 */ }
});

// 设置中档示例：开关开启，强度为 66。
VibrationSettings medium = new VibrationSettings();
medium.setOn(true);
medium.setIntensity(66);
BluetoothSDK.setVibrationSettings(medium, boolCallback);

// 关闭示例：传值仍为 66，通过 on=false 关闭，不用强度 0 替代开关。
VibrationSettings off = new VibrationSettings();
off.setOn(false);
off.setIntensity(66);
BluetoothSDK.setVibrationSettings(off, boolCallback);
```

当前 Demo 在“闹钟与提醒（杰里）”页面的振动分组只提供读取、关闭和“中（66）”设置按钮；不提供弱／强设置按钮。读取结果仍可识别 `33`／`66`／`99` 等级，未知值按原值展示，不能把按钮范围当成 SDK 的完整取值限制。开关关闭时展示“振动强度：关闭”，不把残留的强度值显示成仍在生效。

### 11.7 提醒监听（杰里）

SDK 将设备主动上报的 TLV `0x1C` 提醒转为 `ReminderNotifyCallback`，类型为 `com.huawo.sdk.bluetoothsdk.interfaces.event.ReminderNotifyType`。开启监听不发送 BLE 开关或读取命令，只订阅后续通知；也不会因此补发历史提醒。

```java
ReminderNotifyCallback reminderListener = new ReminderNotifyCallback() {
    @Override public void onNotify(ReminderNotifyType type, byte[] datas) {
        long receivedAtMs = System.currentTimeMillis(); // 手机接收时间，不是设备事件时间
        byte[] payload = datas == null ? new byte[0] : datas.clone();
        // 展示 type.name()、type.getValue()、payload 长度/十六进制和 receivedAtMs。
        // 若投递到主线程，先复制字节数组；附带数据按对应固件定义解释。
    }
};
BluetoothSDK.addReminderNotifyListener(reminderListener);

// 点击停止或页面退出时，移除自己注册的同一个实例。
BluetoothSDK.removeReminderNotifyListener(reminderListener);
```

当前枚举覆盖以下类型，实际是否上报由固件决定：

- `LowBattery(0x01)`、`Sedentary(0x02)`、`MenstrualFertile(0x03)`、`MenstrualPeriod(0x04)`、`DrinkWater(0x05)`、`WashHand(0x06)`。
- 自定义提醒 `0x07..0x0E`：`CustomMeeting`、`CustomSleep`、`CustomSchedule`、`CustomWorkout`、`CustomMedicine`、`CustomMeal`、`CustomBill`、`Custom`。
- 日常目标 `0x0F..0x12`：`GoalStep`、`GoalDistance`、`GoalCalorie`、`GoalDuration`。
- 心率提醒 `0x13..0x15`：`DailyLowHeartRate`、`DailyHighHeartRate`、`WorkoutHighHeartRate`。
- 运动目标 `0x16..0x19`：`WorkoutGoalStep`、`WorkoutGoalCalorie`、`WorkoutGoalDistance`、`WorkoutGoalDuration`。
- `Alarm(0x1A)`；未识别值映射为 `Unknown(0xFF)`，不要直接丢弃附带数据。

`datas` 是附带字节，不承诺每种提醒都有相同字段布局或事件时间。Demo 展示原始数据与手机接收时间，最多保留最近 100 条；重复点击开始不重复注册，停止后丢弃旧会话已经排队的回调，页面停止时只移除本页监听。不要使用 `removeAllReminderNotifyListeners()` 清除其他模块订阅。

同一页面另提供“开始运动识别监听／停止运动识别监听”，使用独立的 `WorkoutTypeRecognitionCallback`，不合并进 `ReminderNotifyCallback`：

```java
WorkoutTypeRecognitionCallback recognitionListener = new WorkoutTypeRecognitionCallback() {
    @Override public void onUpdate(int cate) {
        long receivedAtMs = System.currentTimeMillis(); // 手机接收时间；回调没有设备时间或附带数据。
        // 展示 cate 原值及类别名称；投递到主线程后检查本组会话是否仍有效。
    }
};
BluetoothSDK.addWorkoutTypeRecognitionCallback(recognitionListener);

// 独立停止或退出页面时，移除本页注册的同一个实例。
BluetoothSDK.removeWorkoutTypeRecognitionCallback(recognitionListener);
```

运动识别来自设备主动通知 TLV `0x1B`，SDK 检查数据非空后取第一个字节，调用 `onUpdate(int cate)`；提醒仍走 `0x1C`。注册／移除运动识别回调仅操作本地订阅，不发送设备自动识别开关、历史查询或启动运动命令，也不补发此前事件。页面不增加平台判断，不照搬生产 App 的识别弹窗、提醒偏好或启动运动业务。

类别展示沿用当前 HaWoFit 的映射：`1` 步行、`2` 跑步、`3` 骑行（这三类均为室内／室外候选），`4` 椭圆机、`5` 划船机。`cate` 不是 `WorkoutType` 枚举值，不能直接用该枚举转换；未知类别仍展示原始整数值。

两组监听可以同时开启，分别维护回调实例及会话版本；停止其中一组不影响另一组，状态区同时显示两组状态。事件列表按来源区分设备提醒与运动识别，合计保留最近 100 条；停止／重新开启后屏蔽本组旧回调，`onStop()`／`onCleared()` 只移除本页拥有的两组实例，不调用 `removeAllWorkoutTypeRecognitionCallback()` 或清除其他模块的提醒监听。注册、移除失败保留接口上下文并记录日志；移除失败保留原实例供重试停止。本页采用状态、功能操作、执行结果卡片，四个监听按钮按三列排列，长文字保持字号换行；两组订阅及事件内容保持原样。设备运动自识别开关直接在“闹钟与提醒（杰里）”页面的自动识别分组操作，见 §11.8。

### 11.8 运动自识别开关（杰里）

“闹钟与提醒（杰里）”页面的自动识别锻炼分组直接提供“读取运动自识别开关／开启运动自识别／关闭运动自识别”三个按钮，不跳转详情页。按钮实际调用 SDK，设置分别使用固定参数 `true`／`false`；读取和设置互不串联，不自动回读，不增加平台判断。“提醒监听（杰里）”页面只处理事件订阅，自动监测页不放置这三个按钮。

```java
BluetoothSDK.getAutoDetectWorkoutsSwitch(new BoolValueCallback() {
    @Override public void onSuccess(boolean enabled) {
        // 设备读取结果；不是本地是否注册了运动识别监听。
    }
    @Override public void onFail(int code) {
        // 展示原始错误码与 getAutoDetectWorkoutsSwitch 上下文，不转成关闭。
    }
});

// 以下分别对应两个独立设置按钮，不连续执行。
BluetoothSDK.setAutoDetectWorkoutsSwitch(true, boolCallback);
BluetoothSDK.setAutoDetectWorkoutsSwitch(false, boolCallback);
```

协议使用通用设置指令 `0x63`、属性 `0x15`：读取为 `0x70`，设置为 `0x71`，属性值为单字节 `0`（关闭）／`1`（开启）。读取任务先沿用 `GetTask` 的完整帧／分包处理；已返回 `0x15` 时，数据长度必须为 1、值只能为 `0`／`1`，非法长度或非法值仍通过现有失败链回调 `ErrorCode.DATA_PARSE_ERROR`（`1000`）。有效完整回包缺少 `0x15` 时，按当前源码保留默认 `false`，不额外返回解析错误；这是已接受的 SDK 回退边界，不代表设备明确回报关闭，也不能用于判断固件是否支持该能力。Demo 只展示 SDK 返回值，不据此自动停止监听或屏蔽识别事件。未完成分包继续沿用原等待逻辑，其他扩展属性不影响合法目标属性读取。

设备开关与本地订阅互不绑定：开启设备自识别不自动调用 `addWorkoutTypeRecognitionCallback()`；关闭设备自识别不移除监听；停止监听或退出页面不发送关闭设备自识别的指令。设置成功仅表示设备确认本次 `enabled` 参数，不表示已经回读或收到 TLV `0x1B` 识别事件；需要核对设备值时再单独点击读取。

“闹钟与提醒（杰里）”页面统一持有一个 `JLAlarmsViewModel`，15 个 SDK 操作按钮共用忙碌状态，同时只处理一个操作，不再拆分三个设置详情页。页面采用状态、功能操作、执行结果的卡片布局；共享 ID 说明保留在操作卡片内，原有设置分组与按钮不变，按钮每行三项，删除文字标红，长文字保持字号换行。读取结果／设置确认直接显示在本页结果区，不混入提醒监听页面的事件列表或订阅状态。请求使用自身 Job 和版本控制；离开页面／ViewModel 清理时取消自身等待并忽略迟到结果，旧请求不能覆盖新操作的忙碌状态。取消等待不撤销 SDK 已排队或发送的命令，不发送自动关闭或回滚。

当前 Demo 的 `BluetoothSDK-2.5.4.129.aar` 按当前源码重新打包，包含本节接口，缺少 `0x15` 时保留默认 `false`；同版本号不代表同一产物，更新交付包时应核对 API 和 SHA-256，不混入重复 AAR。设备默认开关状态、固件支持情况与识别触发条件需通过设备验证，不通过 Demo 平台判断或本地偏好推断。

---

## 12. 通知 / 来电 / 通讯录

### 12.1 通知开关

```java
SocialAppSwitch sw = new SocialAppSwitch(SocialType.WeChat, true);
BluetoothSDK.setSocialAppSwitch(sw, boolCallback);

BluetoothSDK.getSocialAppSwitches(new SocialAppSwitchesCallback() {
    @Override public void onSuccess(List<SocialAppSwitch> list) { }
    @Override public void onFail(int code) { }
});

// 批量配置（部分机型）
BluetoothSDK.setSocialApps(socialAppList, setSocialAppsCallback);
```

### 12.2 推送消息 / 来电

```java
SocialMessage msg = new SocialMessage();
msg.setType(SocialType.SMS); // 或微信、WhatsApp 等
msg.setTitle("张三");
msg.setContent("你好");
msg.setTime(System.currentTimeMillis());
BluetoothSDK.pushMessage(msg, boolCallback);

// 来电
BluetoothSDK.incommingCall(name, number, boolCallback);
BluetoothSDK.hangUpCall(boolCallback);
```

### 12.3 通讯录 / 紧急联系人

```java
List<Contact> contacts = ...;
BluetoothSDK.setContacts(contacts, boolCallback);
BluetoothSDK.setEmergencyContact(contact, boolCallback);
```

表端拒接等事件：

```java
BluetoothSDK.addRingOffListener(ringOffCallback);
```

---

## 13. 表盘

```java
// 查询 / 切换
BluetoothSDK.getCurrentWatchface(intValueCallback);
BluetoothSDK.switchWatchfaceBy(watchfaceId, boolCallback);
BluetoothSDK.switchSifliWatchfaceBy(watchfaceName, boolCallback);

// 在线表盘安装（带进度）
BluetoothSDK.setOnlineWatchface(otaDataList, new OtaCallback() {
    @Override public void onReady() { }
    @Override public void onUpload(float progress) { /* 0~1 */ }
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});
//杰里在线表盘
import com.huawo.sdk.bluetoothsdk.wl.media.MediaTransferConfig;
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferCallback;
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferManager;
import com.huawo.sdk.bluetoothsdk.wl.media.models.MediaFileInfo;

File zipFile = ...;          // 已下载并校验完成的表盘包
String watchfaceName = ...;  // 表盘名称，通常来自服务端配置

MediaFileInfo fileInfo = MediaFileInfo.fromFile(
        zipFile.getAbsolutePath(),
        watchfaceName,
        0x00                 // WL 在线表盘文件类型按固件约定，现网在线表盘使用 0x00
);

List<MediaFileInfo> files = new ArrayList<>();
files.add(fileInfo);

WlMediaTransferManager.getInstance().transfer(
        MediaTransferConfig.MEDIA_TYPE_WATCH_ONLINE,
        files,
        new WlMediaTransferCallback() {
        @Override public void onReady() { }
    
        @Override public void onProgress(float progress) {
            // progress: 0.0 ~ 1.0
        }
        @Override public void onSuccess() { }
        @Override public void onFail(int code, String message) {
            // 常见：已有传输进行中、设备拒绝、连接断开、文件异常等
        }
    }
);

// 自定义表盘
CustomWatchface wf = new CustomWatchface();
// 设置 image / thumbnail / widgetList / size 等
BluetoothSDK.setCustomWatchface(wf, new SetCustomWatchfaceCallback() {
    @Override public void onSetWidgetsSuccess() { }
    @Override public void onWatchfaceImageOtaStart() { }
    @Override public void onWatchfaceImageOtaProgress(float progress) { }
    @Override public void onSuccess() { }
    @Override public void onFail(int code) { }
});

BluetoothSDK.getDeviceWatchfaceAvailableStorage(intValueCallback);
BluetoothSDK.delWatchfaceBy(watchfaceId, boolCallback);

//杰里自定义表盘
import com.huawo.sdk.bluetoothsdk.BluetoothSDK;
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferCallback;
import com.huawo.sdk.bluetoothsdk.wl.media.models.MediaFileInfo;
import com.huawo.sdk.bluetoothsdk.wl.media.utils.MediaPacketBuilder;
import com.huawo.sdk.bluetoothsdk.wl.models.V10CustomWatchfaceConfig;

List<File> resourceFiles = ...;
// resourceFiles 示例顺序：pw, bg1, bg2...
// 这些文件必须是按 WL V10 固件协议转换后的资源文件，不是普通 jpg/png 原图。

List<MediaFileInfo> files = new ArrayList<>();
for (File file : resourceFiles) {
MediaFileInfo info = MediaFileInfo.fromFile(
        file.getAbsolutePath(),
        file.getName(),
        MediaPacketBuilder.FILE_TYPE_JPG
);
    if (info != null) {
        files.add(info);
    }
            }

V10CustomWatchfaceConfig config = new V10CustomWatchfaceConfig();
config.setBgCount(bgCount); // 背景图数量，不包含 pw
config.setDisplayMode(V10CustomWatchfaceConfig.DISPLAY_MODE_SINGLE);
// 可选：DISPLAY_MODE_SEQUENCE / DISPLAY_MODE_RANDOM
config.setCoverIndex(0);
config.setPosition(V10CustomWatchfaceConfig.POSITION_TOP);
config.setPointerStyle(V10CustomWatchfaceConfig.POINTER_STYLE_NONE);
config.setTextColorRgb(0xFFFFFF);

List<V10CustomWatchfaceConfig.Element> elements = new ArrayList<>();
elements.add(new V10CustomWatchfaceConfig.Element(
                     V10CustomWatchfaceConfig.ELEMENT_TIME,
        100,
                     120
));
        config.setElements(elements);

BluetoothSDK.installWlCustomWatchfaceV10(files, config, new WlMediaTransferCallback() {
    @Override public void onReady() { }

    @Override public void onProgress(float progress) {
        // progress: 0.0 ~ 1.0
    }

    @Override public void onSuccess() { }

    @Override public void onFail(int code, String message) {
        // 固件拒绝时会返回 code/message；例如文件格式、资源数量、空间、设备状态异常等
    }
});
调用约束：
表盘安装前必须确保 BLE 已连接，且设备已完成绑定。
表盘安装、音乐传输、相册传输、OTA 不要并发执行。
WL 在线表盘 / WL 自定义表盘共用 WL 传输通道，已有传输进行中时可能返回 busy / already in progress。
安装前建议查询空间：getDeviceWatchfaceAvailableStorage。
安装失败时应记录 code、message、文件名、文件大小、设备型号、固件版本、协议版本，方便定位固件拒绝原因。
退出安装页面时，应取消当前业务订阅；WL 媒体传输可按业务需要调用 WlMediaTransferManager.getInstance().cancelCurrentTransfer()。
具体表盘包格式、资源文件名、组件坐标、图片尺寸和文件类型以产品补充文档为准。

```



思澈 QJS 推送：

```java
BluetoothSDK.pushQjsOnlineWatchface(/*...*/, sifliDfuCallback);
BluetoothSDK.stopPushingQjsOnlineWatchface();
```

---

## 14. 音乐文件推送

将手机本地音乐文件同步到手表（对应 App 侧 `MusicSelectActivity` 能力）。与「把手机正在播放的曲目信息显示到表端」（`setDeviceMusicInfo`）不同，本节讲的是**文件传输**。

### 14.1 通道选择（与产品配置对齐）

| 通道 | API | 是否在 BluetoothSDK AAR 内 | 前置条件                 |
|------|-----|---------------------------|----------------------|
| **A. SPP（经典蓝牙）** | `SppFilesTransferTask.sendMusicFiles` | 是 | 产品支持 SPP；经典蓝牙已配对且已连接 |
| **B. WL BLE** | `WlMediaTransferManager.transferMusic` | 是 | 杰理机型；BLE 已连接         |
| **C. 思澈 BLE ZIP** | `SifliWatchSDK.syncZipFile(..., type=4, ...)` | **否，需额外 AAR** | 思澈/QJS 机型；BLE 已连接    |

推荐决策（与现网一致）：

```text
设备已绑定且 BLE 已连接
        │
        ├─ 产品 hasSPP=true 且经典 BT 已连接 ──► 方案 A：SPP
        │         └─ 失败且 杰理 ──► 方案 B：WL BLE
        │         └─ 失败且非 杰理 ───────────► 方案 C：思澈 ZIP
        │
        ├─ 无 SPP / BT 未连，且杰理 ──► 方案 B：WL BLE
        │
        └─ 其它（思澈老链路）──────────────────► 方案 C：思澈 ZIP
```

### 14.2 查询音乐存储空间

推送前建议先查询可用空间，避免表端空间不足：

```java
BluetoothSDK.getDeviceMusicAvailableStorage(new AvailableStorageCallback() {
    @Override
    public void onSuccess(int available, int total) {
        // 单位：KB（与现网展示逻辑一致）
        // available：可用；total：总容量
    }

    @Override
    public void onFail(int code) { }
});

// 空闲空间（如产品需要）
BluetoothSDK.getDeviceMusicIdleStorage(new IntValueCallback() {
    @Override public void onSuccess(int idleKb) { }
    @Override public void onFail(int code) { }
});

// 表端空间变化监听
BluetoothSDK.addDeviceMusicStorageChangedListener(new AvailableStorageCallback() {
    @Override public void onSuccess(int available, int total) { }
    @Override public void onFail(int code) { }
});
```

### 14.3 方案 A：SPP 推送（BluetoothSDK 内置）

**类路径**：`com.huawo.sdk.bluetoothsdk.spp.SppFilesTransferTask`  
**回调**：`com.huawo.sdk.bluetoothsdk.interfaces.ota.OtaCallback`

```java
// 可选：打开 SPP 日志
SppLog.setSppLog(log -> Log.i("SPP", log));

// 确认经典蓝牙已连接（未连接时不要走 SPP）
BluetoothSDK.getBTConnectionState(new BoolValueCallback() {
    @Override
    public void onSuccess(boolean btConnected) {
        if (!btConnected) {
            // 改走 WL / 思澈，或引导用户打开手表蓝牙媒体通道
            return;
        }
        startSppMusicTransfer(musicFiles);
    }

    @Override
    public void onFail(int code) { }
});
```

```java
private SppFilesTransferTask sppTask;

private void startSppMusicTransfer(List<File> musicFiles) {
    if (SppFilesTransferTask.isTransferring()) {
        return; // 已有传输在进行
    }
    sppTask = new SppFilesTransferTask();
    sppTask.sendMusicFiles(musicFiles, new OtaCallback() {
        @Override
        public void onReady() {
            // 即将开始
        }

        @Override
        public void onUpload(float progress) {
            // progress: 0.0 ~ 1.0
            int percent = (int) (progress * 100);
        }

        @Override
        public void onSuccess() {
            // 推送完成，可刷新空间
            BluetoothSDK.getDeviceMusicAvailableStorage(...);
        }

        @Override
        public void onFail(int code) {
            // 常见：SPP_SOCKET_CONNECT_FAILED(18)、SPP_WRITE_DATA_FAILED(21)、SPP_TASK_CANCELLED(26)
            // 可降级到 WL BLE 或思澈 ZIP
            if (BluetoothSDK.isWlProtocol()) {
                startWlMusicTransfer(musicFiles);
            }
        }
    });
}

// 用户取消
sppTask.stopSending();
```

也支持 `InputStream + 文件名` 列表重载：

```java
sppTask.sendMusicFiles(inputStreamList, nameList, otaCallback);
```

同任务还可推相册 / 离线地图（类型不同）：

```java
sppTask.sendAblumFiles(albumFiles, otaCallback);   // type=相册
sppTask.sendOfflineMap(mapFiles, otaCallback);     // type=离线地图
```

> SPP 依赖经典蓝牙 Socket，连接前通常需完成 `createBond`；部分产品还依赖 Profile 连接（`connectProfiles`）。

### 14.4 方案 B：WL BLE 媒体传输（BluetoothSDK 内置）

**类路径**：

- `com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferManager`
- `com.huawo.sdk.bluetoothsdk.wl.media.models.MediaFileInfo`
- `com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferCallback`
- 文件类型常量：`MediaPacketBuilder.FILE_TYPE_MP3` / `FILE_TYPE_WAV`

```java
List<MediaFileInfo> files = new ArrayList<>();
for (String path : selectedPaths) {
    int type = path.toLowerCase().endsWith(".wav")
            ? MediaPacketBuilder.FILE_TYPE_WAV
            : MediaPacketBuilder.FILE_TYPE_MP3;
    MediaFileInfo info = MediaFileInfo.fromFile(path, type);
    // 也可自定义展示名：MediaFileInfo.fromFile(path, "歌名.mp3", type);
    if (info != null) {
        files.add(info);
    }
}

WlMediaTransferManager.getInstance().transferMusic(files, new WlMediaTransferCallback() {
    @Override
    public void onReady() { }

    @Override
    public void onProgress(float progress) {
        // 0.0 ~ 1.0
    }

    @Override
    public void onSuccess() { }

    @Override
    public void onFail(int code, String message) {
        // 如：文件列表空、已有传输进行中、OTA/表盘传输冲突等
    }
});

// 取消
WlMediaTransferManager.getInstance().cancelCurrentTransfer();
```

约束：

- OTA / AI 表盘等关键传输进行中会被拒绝（错误信息见 `onFail`）。
- 可用 `WlMediaTransferManager.getInstance().isTransferInProgress()` 判断是否忙碌。

同管理器也可推相册：

```java
WlMediaTransferManager.getInstance().transferPhotos(photoFiles, callback);
```

### 14.5 方案 C：思澈（Sifli）BLE ZIP 推送（需额外 AAR）

现网 `MusicSelectActivity` 在非 SPP、非 WL 场景，会把所选音乐压成 zip，再通过思澈通道推送：

```java
// type = 4 表示音乐
SifliWatchSDK.getInstance().syncZipFile(
        true,           // needByteAlign
        deviceMac,      // 已绑定设备 MAC
        zipPath,        // 本地 zip 路径（多文件需先压缩）
        4,              // 业务类型：音乐
        new Callback() {
            @Override public void onCancel() { }
            @Override public void onManagerStatusChanged(int status) {
                // 0 空闲，1 连接中，2 工作中
            }
            @Override public void onError(int code) { }
            @Override public void onProgress(long currentBytes, long totalBytes) { }
            @Override public void onSuccess() { }
        }
);
```

取消：

```java
SFSDK.getInstance().stop(); // 或按思澈 stop API
```

#### 额外依赖说明（客户侧）

依赖可通过 **私有 Nexus** 或 **本地 AAR** 引入（配置见 [§1](#1-集成方式本地-aar--私有-maven)），例如：

**方式 A：私有 Maven**

```groovy
// 已配置 nexus.huawo-wear.com 只读仓库后：
implementation 'com.huawo.sdk:BluetoothSDK:2.5.4.126'
implementation 'com.sifli:sifliezipsdk:2.3.9@aar'
implementation 'com.sifli:siflicore:1.2.7'
implementation 'com.sifli:sifliwatchfacesdk:2.1.6@aar'
// qjs-watchface 坐标以交付清单为准；若仓库未发布，请用方式 B
```

**方式 B：本地 AAR**

```text
libs/
  BluetoothSDK-2.5.4.129.aar
  qjs-watchface-15.0.16.aar      # 产出自 watchface 模块，含 SifliWatchSDK
  sifliezipsdk-2.3.9.aar
  siflicore-1.2.7.aar
  sifliwatchfacesdk-2.1.6.aar
```

```groovy
implementation files('libs/BluetoothSDK-2.5.4.129.aar')
implementation files('libs/qjs-watchface-15.0.16.aar')
implementation files('libs/sifliezipsdk-2.3.9.aar')
implementation files('libs/siflicore-1.2.7.aar')
implementation files('libs/sifliwatchfacesdk-2.1.6.aar')
```

> 仅做 **WL / SPP 音乐推送** 时**不必**集成本节思澈依赖。版本号以交付为准。

### 14.6 手机正在播放曲目信息（非文件传输）

手表显示手机音乐名、播放状态、音量等，使用：

```java
DeviceMusicInfo info = new DeviceMusicInfo();
// 填充歌名、歌手、时长、播放进度等
BluetoothSDK.setDeviceMusicInfo(info);

BluetoothSDK.setMusicState(MusicState.Playing, "Song Name");
BluetoothSDK.setMusicVolumn(50);

// 表端按键控制手机播放（上/下首、暂停等）
BluetoothSDK.addMusicEventListener(musicEventCallback);
```

该能力同样在 `BluetoothSDK` AAR 内，与文件推送相互独立。

### 14.7 实现注意

1. 传输前确认 BLE 已连接；SPP 通道还需经典 BT 已连接。  
2. 与固件 OTA、推表盘、相册推送等冲突时，应排队或提示稍后重试。  
3. 校验文件格式（现网以 mp3/wav 为主），过大时先检查 `getDeviceMusicAvailableStorage`。  
4. 用户取消时，必须调用对应通道的 `stopSending` / `cancelCurrentTransfer` / 思澈 `stop`。  
5. 错误码见 `ErrorCode` 中 `SPP_*`、`PUSHING_MUSIC`、`OTA_WORKING`、`NOT_ENOUGH_SPACE` 等。

---

## 15. 天气

```java
BluetoothSDK.setWeatherUnit(WeatherUnit.Centigrade, boolCallback);

Weather today = new Weather();
// 气温、天气类型、湿度、日出日落等字段按模型填充
List<Weather> forecast = new ArrayList<>();
BluetoothSDK.setWeather("深圳", today, forecast, boolCallback);
```

设备可能通过 `DeviceEvent` 主动索要天气；请注册设备事件监听后在回调里调用 `setWeather`。

---

## 16. 查找设备 / 遥控拍照

```java
// App 找手表：手表震动/响铃
BluetoothSDK.findDevice(boolCallback);

// 手表找手机：先监听，用户在 App 确认后回执
BluetoothSDK.addLookingForPhoneListener(new LookingForPhoneCallback() {
    @Override
    public void onLookingFor() {
        // 弹提示 / 播放铃声
        BluetoothSDK.phoneFound(boolCallback);
    }

    @Override
    public void onStopLookingFor() {
        // 停止响铃
    }
});

// 遥控拍照
BluetoothSDK.enterCameraControl(boolCallback);
BluetoothSDK.addCameraOpListener(cameraOpCallback);
BluetoothSDK.exitCameraControl(boolCallback);
```

---

## 17. OTA 固件升级

对应 App 业务入口 `WatchUpgradeNewActivity`（经 `DeviceUpgradeManager.upgrade()` 分发）。固件包通常由服务器下发 URL + MD5；**SDK 只负责把已准备好的固件字节/文件传到设备**，下载、校验、版本比较由 App 侧完成。

### 17.1 总体流程（推荐）

```text
BLE 已连接且已绑定
  → 查询电量（现网建议 ≥ 30%）
  → getDeviceUpgradeStatus / canOtaNow（可升级才继续）
  → App 下载固件包并 MD5 校验
  → 按协议选择通道开始 OTA（见下表）
  → 进度回调更新 UI（升级过程建议保持屏幕常亮）
  → 成功：设备可能重启，App 需重新连接并刷新固件版本
  → 失败 / 页面销毁：取消任务并清理会话（尤其 WL）
```

| 通道 | 判断条件（现网） | SDK / 外部 API | 是否在 BluetoothSDK AAR |
|------|------------------|----------------|-------------------------|
| **A. 通用 OTA** | 非思澈、非 WL（瑞昱/阿波罗等） | `BluetoothSDK.ota(List<OtaData>, OtaCallback)` | 是 |
| **B. WL OTA** | `BluetoothSDK.isWlProtocol()` | `BluetoothSDK.starWlOta(...)` | 是 |
| **C. 思澈 DFU** | 产品具备 QJS/Sifli 能力 | `SifliDFUService.startActionDFUNand(...)` | **否**，需 SifliDFU |

**API 速查：**

| 方法 | 说明 |
|------|------|
| `getDeviceUpgradeStatus(UpgradeStatusCallback)` | 查询设备升级态：`Normal` / `Recovering` / `WaitOta` / `OTAing` |
| `ota(List<OtaData>, OtaCallback)` | 通用多文件固件升级 |
| `starWlOta(byte[] / String path, WlOtaCallback)` | WL02 固件升级 |
| `WlOtaManager.forceReset()` | 强制清理 WL OTA 会话（页面退出兜底） |
| `enterOTA(byte[] address, BoolCallback)` | 进入 OTA 模式（一般无需直接调用，由 `ota` 内部处理） |
| `getOtaAddressBy(long id, BytesCallback)` | 查询写入地址（表盘 OTA 等会用到） |
| `rebootDeviceWhenWaitingOTA(BoolCallback)` | 等待 OTA 场景下重启设备（按产品需要） |

### 17.2 升级前检查

#### 电量 / 连接

```java
// 现网 WatchUpgradeNewViewModel：电量 < 30% 禁止升级
BluetoothSDK.getBattery(new IntValueCallback() {
    @Override
    public void onSuccess(int battery) {
        if (battery < 30) {
            // 提示用户充电后再升级
            return;
        }
        checkUpgradeStatusThenStart();
    }
    @Override public void onFail(int code) { }
});

if (!BluetoothSDK.isConnected()) {
    // 先 reconnect / connect
}
```

#### 设备升级状态

```java
BluetoothSDK.getDeviceUpgradeStatus(new UpgradeStatusCallback() {
    @Override
    public void onSuccess(UpgradeStatus status) {
        // Normal(0)      可升级
        // Recovering(1)  恢复中，勿打断
        // WaitOta(2)     等待进入 OTA
        // OTAing(3)      正在 OTA，勿重复发起
        if (status == UpgradeStatus.Normal) {
            startFirmwareOta();
        } else {
            // 提示稍后重试
        }
    }

    @Override
    public void onFail(int code) {
        // 出异常时建议不要强行 OTA
    }
});
```

> 思澈老固件还可能用 `getBindState`：值为 `129`（`0x81`）表示处于 OTA 相关态，应禁止再开新任务。具体以产品说明为准。

### 17.3 方案 A：通用 OTA（瑞昱 / 阿波罗等）

**适用**：非 WL、非思澈 DFU 的平台。现网从服务器拉多段 firmware，每段带 `type` + 文件内容，组装为 `List<OtaData>` 后调用。

#### `OtaData` / `OtaDataType`

| 类型 | 值 | 含义 |
|------|----|------|
| `Platform` | 0x01 | 主固件 |
| `TouchPanel` | 0x02 | 触摸驱动 |
| `Heartrate` | 0x03 | 心率驱动 |
| `Picture` | 0x04 | 图片资源（表盘在线包也可能走此类型） |
| `AGPS` | 0x06 | AGPS 数据 |
| `Patch` | 0x0A | MCU SDK patch |
| `Bootloader` | 0x0B | Bootloader |

`OtaData.data` 字节格式（现网直接使用下载后的 bin）：

```text
[0..3]  : OTA 写入地址（4 字节）
[4..n-1]: 固件内容本体
```

SDK 会按 2048 字节切包传输，并计算 CRC。若返回的包已含地址头，直接 `new OtaData(type, bytes)` 即可。

#### Demo

```java
// 1) App 下载固件（伪代码），并 MD5 校验
byte[] platformBin = downloadAndVerify(urlPlatform, md5Platform);
byte[] hrBin = downloadAndVerify(urlHr, md5Hr); // 若有多段

List<OtaData> list = new ArrayList<>();
list.add(new OtaData(OtaDataType.Platform, platformBin));
if (hrBin != null) {
    list.add(new OtaData(OtaDataType.Heartrate, hrBin));
}

// 2) 开始升级
BluetoothSDK.ota(list, new OtaCallback() {
    @Override
    public void onReady() {
        // 已进入发送准备
    }

    @Override
    public void onUpload(float progress) {
        // 0.0 ~ 1.0，刷新进度条
    }

    @Override
    public void onSuccess() {
        // 升级完成：等待设备重启后 reconnect，再 getFirmwareVersion / getDeviceInfo
    }

    @Override
    public void onFail(int code) {
        // CALLING_OTA_ERROR / OTA_WORKING / OTA_DATA_EMPTY / DISCONNECTED 等
    }
});
```

注意：

- OTA 进行中勿并发推表盘、推音乐、SPP 大文件传输。
- `enterOTA` 一般**不必**单独调用。
- 多段按服务器下发顺序加入 `List`，顺序可能影响固件依赖。

### 17.4 方案 B：WL OTA（`isWlProtocol()`）

**适用**：`protocolVersion >= 100` / `BluetoothSDK.isWlProtocol() == true`。  
现网流程：下载单个 OTA 文件 → MD5 校验 → 把**本地文件路径**传给 `starWlOta`。

```java
if (!BluetoothSDK.isWlProtocol()) {
    // 走方案 A 或 C
    return;
}

// 下载完成后得到本地路径，例如：
String otaFilePath = "/data/user/0/.../cache/device/wl_ota/xxx.bin";

BluetoothSDK.starWlOta(otaFilePath, new WlOtaCallback() {
    @Override
    public void onReady() {
        // 开始前
    }

    @Override
    public void onProgress(float progress) {
        // 0.0 ~ 1.0
    }

    @Override
    public void onSuccess() {
        // 成功后设备可能重启，需重新连接并刷新版本号
    }

    @Override
    public void onFail(int code, String message) {
        // 媒体/表盘传输进行中会被拦截；文件不存在、解析失败等
    }
});

// 也可用内存字节：
// BluetoothSDK.starWlOta(firmwareBytes, callback);
```

#### 取消与清理（重要）

WL OTA 有独立会话。用户退出升级页、进程保活后重进，建议：

```java
// 页面 onDestroy / 取消按钮
WlOtaManager.forceReset(); // 静默释放 BLE 通道与会话，不回调业务层
```

约束（`WlOtaManager` 内置）：

- 正在进行 WL 媒体传输（音乐/相册）时，OTA 会被拒绝。
- 正在进行 WL 表盘传输时，OTA 会被拒绝。
- 请勿并发启动第二次 `starWlOta`。

### 17.5 方案 C：思澈（Sifli）DFU 固件升级（需额外依赖）

**固件 DFU 不走 `BluetoothSDK.ota` / `starWlOta`**，而是走思澈官方 `SifliDFU`。现网编排：

`WatchUpgradeNewActivity` → `DeviceUpgradeManager.upgrade()`（下载/解压/组装）→ `EventBus(OtaEvent)` → Activity 绑定 `SifliDFUService` → `startActionDFUNand`。

客户集成时，核心是自己实现与现网等价的 **「下载 ZIP → MD5 → 解压 → 按文件名映射 IMAGE_ID → 组装 `ArrayList<DFUImagePath>` → 启动 DFU」**。

#### 17.5.1 端到端流水线

```text
① 服务器下发升级信息
     firmwares[0].url / md5      → 主固件 ZIP（必有）
     resource.url / md5 / name   → 差分资源 ZIP（仅差分模式需要）
        │
② 下载 firmwares[0] 到 cache（建议按 md5 命名）
③ 校验 MD5（大小写不敏感）
④ 解压 ZIP → 得到本地绝对路径列表 List<String>
⑤ 按「文件名前缀 + .bin 后缀」识别镜像，暂存各 DFUImagePath
⑥ 判定模式：
     · 存在 diff_ctrl*.bin  → 差分模式（优先）
     · 否则存在 ctrl*.bin   → 全量模式
⑦ 按模式规则把镜像加入 ArrayList<DFUImagePath>（顺序很重要，见下）
⑧ bindService(SifliDFUService)
⑨ startActionDFUNand(context, mac, dfuImagePaths, DFU_MODE_NORMAL, 0)
⑩ LocalBroadcast 接收进度 / 成功失败
```

#### 17.5.2 服务器数据结构（App 需自己对接）

思澈升级不只依赖固件 URL，还可能附带差分资源：

```text
DeviceUpgradeInfo
├── version / build / updateContent
├── firmwares[]          // 思澈现网只取 firmwares.get(0)
│   └── Firmware
│       ├── url          // 主 ZIP 下载地址
│       └── md5          // 主 ZIP 的 MD5
└── resource             // 差分模式必填；全量可不下发
    ├── name             // 本地保存文件名
    ├── url              // 差分资源包下载地址（通常是 zip）
    ├── md5
    ├── fromVersion
    └── toVersion
```

> App 侧需保证差分模式下 `resource != null && resource.url != null && resource.md5` 非空，否则现网会直接失败。

#### 17.5.3 下载主 ZIP 并 MD5 校验

```java
import com.sifli.siflidfu.DFUImagePath;
import com.sifli.siflidfu.Protocol;
import static com.sifli.siflidfu.Protocol.*;

// 缓存目录建议与现网一致
File cacheDir = new File(context.getCacheDir(), "device/qjs");
cacheDir.mkdirs();
File zipFile = new File(cacheDir, firmwareMd5); // 用 md5 当文件名，便于复用

byte[] bytes = /* 从 firmware.url 下载的完整字节 */;
String actualMd5 = md5Hex(bytes);               // 自行实现 MD5
if (!actualMd5.equalsIgnoreCase(firmwareMd5)) {
    throw new IllegalStateException("firmware zip md5 mismatch");
}
writeBytes(zipFile, bytes);
```

#### 17.5.4 解压 ZIP（必须保留绝对路径列表）

现网 `unzip(zipFilePath, destDir)` 要点：

1. `destDir` 通常就是 `cacheDir`（与 zip 同目录）。  
2. Zip 内若有子目录，先 `mkdirs` 父目录再写文件。  
3. **只把文件（非目录）的绝对路径**加入返回列表；目录本身不进列表。  
4. 解压失败应视为 OTA 失败，不要继续组装。

```java
public static ArrayList<String> unzip(String zipFilePath, String destDir) throws Exception {
    ArrayList<String> files = new ArrayList<>();
    File dir = new File(destDir);
    if (!dir.exists()) dir.mkdirs();

    try (FileInputStream fis = new FileInputStream(zipFilePath);
         ZipInputStream zis = new ZipInputStream(fis)) {
        byte[] buffer = new byte[1024];
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            File out = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                out.mkdirs();
            } else {
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                files.add(out.getAbsolutePath()); // 关键：绝对路径
            }
            zis.closeEntry();
        }
    }
    return files;
}

// 调用
ArrayList<String> extractedPaths = unzip(zipFile.getAbsolutePath(), cacheDir.getAbsolutePath());
```

解压后典型文件（文件名以**前缀**匹配，后缀必须 `.bin`；中间可有版本号等）：

```text
device/qjs/
├── <md5>                 # 下载的 zip 本身
├── ctrl_xxx.bin          # 全量控制镜像
├── diff_ctrl_xxx.bin     # 差分控制镜像（有则走差分）
├── hcpu_xxx.bin
├── lcpu_xxx.bin
├── patch_xxx.bin         # 或 patch_lcpu_xxx.bin
├── outdyn_xxx.bin        # 全量可选
└── outroot_xxx.bin       # 全量可选
```

#### 17.5.5 文件名 → IMAGE_ID 映射规则（核心）

`DFUImagePath` 构造（SifliDFU）：

```java
new DFUImagePath(localAbsolutePath, /* secondPath */ null, imageId);
```

现网用 `file.getName()` 做判断（**只看文件名，不看路径**）：

| 文件名规则（同时满足） | 暂存变量 | IMAGE_ID（`com.sifli.siflidfu.Protocol`） | 首次扫描是否立刻加入列表 |
|------------------------|----------|------------------------------------------|--------------------------|
| `startsWith("ctrl") && endsWith(".bin")` | `ctrlPath` | `IMAGE_ID_CTRL` | **否**（模式判定后再加） |
| `startsWith("diff_ctrl") && endsWith(".bin")` | `diffCtrlPath` | `IMAGE_ID_CTRL`（与 ctrl 同 ID） | **否**（模式判定后再加） |
| `startsWith("hcpu") && endsWith(".bin")` | `hcpuPath` | `IMAGE_ID_HCPU` | **是** |
| `startsWith("lcpu") && endsWith(".bin")` | `lcpuPath` | `IMAGE_ID_LCPU` | **是** |
| `startsWith("patch") && endsWith(".bin")` | `patchPath` | `IMAGE_ID_NAND_LCPU_PATCH` | **是** |
| `startsWith("outdyn") && endsWith(".bin")` | `outdynPath` | `IMAGE_ID_DYN` | **否**（仅全量再加） |
| `startsWith("outroot") && endsWith(".bin")` | `outrootPath` | `IMAGE_ID_RES` | **否**（仅全量再加） |
| 差分额外下载的 resource 文件 | — | `IMAGE_ID_NAND_RES` | 差分模式最后加入 |

注意：

1. **`diff_ctrl` 不会误匹配 `ctrl`**：因为 `diff_ctrlxxx`.startsWith(`"ctrl"`) 为 false。  
2. **同前缀多文件时以最后一次为准**（循环覆盖）；正常包内每种前缀一个。  
3. `ctrl` 与 `diff_ctrl` 镜像 ID 都是 `IMAGE_ID_CTRL`，靠「有没有 `diff_ctrl`」区分模式，而不是靠 ID。  
4. 注释语义：有 `ctrl` → 倾向于全量；有 `diff_ctrl` → 差分，并额外更新 `resource`；同时会推送 `hcpu` / `lcpu` / `patch`。

#### 17.5.6 首次扫描：识别并部分入表

```java
ArrayList<DFUImagePath> dfuImagePaths = new ArrayList<>();
DFUImagePath ctrlPath = null;
DFUImagePath diffCtrlPath = null;
DFUImagePath hcpuPath = null;
DFUImagePath lcpuPath = null;
DFUImagePath patchPath = null;
DFUImagePath outdynPath = null;
DFUImagePath outrootPath = null;

for (String absolutePath : extractedPaths) {
    File f = new File(absolutePath);
    String name = f.getName();

    if (name.startsWith("ctrl") && name.endsWith(".bin")) {
        ctrlPath = new DFUImagePath(absolutePath, null, IMAGE_ID_CTRL);
    }
    if (name.startsWith("diff_ctrl") && name.endsWith(".bin")) {
        diffCtrlPath = new DFUImagePath(absolutePath, null, IMAGE_ID_CTRL);
    }
    if (name.startsWith("hcpu") && name.endsWith(".bin")) {
        hcpuPath = new DFUImagePath(absolutePath, null, IMAGE_ID_HCPU);
        dfuImagePaths.add(hcpuPath);          // 立即加入
    }
    if (name.startsWith("lcpu") && name.endsWith(".bin")) {
        lcpuPath = new DFUImagePath(absolutePath, null, IMAGE_ID_LCPU);
        dfuImagePaths.add(lcpuPath);          // 立即加入
    }
    if (name.startsWith("patch") && name.endsWith(".bin")) {
        patchPath = new DFUImagePath(absolutePath, null, IMAGE_ID_NAND_LCPU_PATCH);
        dfuImagePaths.add(patchPath);         // 立即加入
    }
    if (name.startsWith("outdyn") && name.endsWith(".bin")) {
        outdynPath = new DFUImagePath(absolutePath, null, IMAGE_ID_DYN);
        // 暂不加入
    }
    if (name.startsWith("outroot") && name.endsWith(".bin")) {
        outrootPath = new DFUImagePath(absolutePath, null, IMAGE_ID_RES);
        // 暂不加入
    }
}
```

此时列表中间态可能是：`[hcpu, lcpu, patch]`（缺哪个就没有哪个）。

#### 17.5.7 模式判定与最终组装（重点）

**优先差分**：只要扫描到 `diffCtrlPath != null`，就走差分，不再走全量。

##### A. 差分模式（存在 `diff_ctrl*.bin`）

最终列表形态：

```text
[ hcpu?, lcpu?, patch?,  diff_ctrl,  resourceZip ]
  IMAGE_ID_HCPU / LCPU / NAND_LCPU_PATCH / CTRL / NAND_RES
```

组装步骤：

```java
if (diffCtrlPath != null) {
    // 1) 把差分控制镜像追加进去（hcpu/lcpu/patch 已在列表中）
    dfuImagePaths.add(diffCtrlPath);

    // 2) 必须再下载服务器下发的 resource（差分资源包）
    if (resource == null || TextUtils.isEmpty(resource.url) || TextUtils.isEmpty(resource.md5)) {
        throw new IllegalStateException("diff mode requires resource url/md5");
    }

    File diffDir = new File(context.getCacheDir(), "device/qjs_diff");
    diffDir.mkdirs();
    File resourceFile = new File(diffDir, resource.name); // 用服务器给的 name

    byte[] resBytes = /* 下载 resource.url */;
    String resMd5 = md5Hex(resBytes);
    if (!resMd5.equalsIgnoreCase(resource.md5)) {
        throw new IllegalStateException("resource md5 mismatch");
    }
    writeBytes(resourceFile, resBytes);

    // 3) resource 作为 NAND 资源镜像加入（注意：这里是整个 zip 文件路径，不再二次解压）
    dfuImagePaths.add(new DFUImagePath(
            resourceFile.getAbsolutePath(),
            null,
            IMAGE_ID_NAND_RES
    ));

    // 4) 交给 SifliDFU
    startDfu(dfuImagePaths);
    return;
}
```

##### B. 全量模式（无 `diff_ctrl`，但有 `ctrl*.bin`）

最终列表形态：

```text
[ hcpu?, lcpu?, patch?,  ctrl,  outdyn?,  outroot? ]
```

```java
if (ctrlPath == null) {
    throw new IllegalStateException("neither diff_ctrl nor ctrl found in zip");
}

dfuImagePaths.add(ctrlPath);
if (outdynPath != null) {
    dfuImagePaths.add(outdynPath);
}
if (outrootPath != null) {
    dfuImagePaths.add(outrootPath);
}
startDfu(dfuImagePaths);
```

##### 组装结果对照表

| 模式 | 必有 | 常见组成（按现网加入顺序） | 失败条件 |
|------|------|---------------------------|----------|
| 差分 | `diff_ctrl` + `resource` | hcpu → lcpu → patch → **diff_ctrl** → **resource(zip)** | 无 resource / resource MD5 失败 |
| 全量 | `ctrl` | hcpu → lcpu → patch → **ctrl** → outdyn? → outroot? | zip 内既无 diff_ctrl 也无 ctrl |

#### 17.5.8 完整参考实现（下载→解压→组装）

```java
public ArrayList<DFUImagePath> buildSifliDfuImagePaths(
        Context context,
        String firmwareUrl,
        String firmwareMd5,
        /* 可为 null */ String resourceName,
        /* 可为 null */ String resourceUrl,
        /* 可为 null */ String resourceMd5
) throws Exception {

    // ---- 1. 主 ZIP ----
    File qjsDir = new File(context.getCacheDir(), "device/qjs");
    qjsDir.mkdirs();
    File zipFile = new File(qjsDir, firmwareMd5);
    byte[] zipBytes = httpDownload(firmwareUrl);
    if (!md5Hex(zipBytes).equalsIgnoreCase(firmwareMd5)) {
        throw new IllegalStateException("firmware zip md5 mismatch");
    }
    writeBytes(zipFile, zipBytes);

    // ---- 2. 解压 ----
    ArrayList<String> extracted = unzip(zipFile.getAbsolutePath(), qjsDir.getAbsolutePath());

    // ---- 3. 扫描映射 ----
    ArrayList<DFUImagePath> list = new ArrayList<>();
    DFUImagePath ctrlPath = null, diffCtrlPath = null;
    DFUImagePath outdynPath = null, outrootPath = null;

    for (String path : extracted) {
        String name = new File(path).getName();
        if (name.startsWith("ctrl") && name.endsWith(".bin")) {
            ctrlPath = new DFUImagePath(path, null, IMAGE_ID_CTRL);
        }
        if (name.startsWith("diff_ctrl") && name.endsWith(".bin")) {
            diffCtrlPath = new DFUImagePath(path, null, IMAGE_ID_CTRL);
        }
        if (name.startsWith("hcpu") && name.endsWith(".bin")) {
            list.add(new DFUImagePath(path, null, IMAGE_ID_HCPU));
        }
        if (name.startsWith("lcpu") && name.endsWith(".bin")) {
            list.add(new DFUImagePath(path, null, IMAGE_ID_LCPU));
        }
        if (name.startsWith("patch") && name.endsWith(".bin")) {
            list.add(new DFUImagePath(path, null, IMAGE_ID_NAND_LCPU_PATCH));
        }
        if (name.startsWith("outdyn") && name.endsWith(".bin")) {
            outdynPath = new DFUImagePath(path, null, IMAGE_ID_DYN);
        }
        if (name.startsWith("outroot") && name.endsWith(".bin")) {
            outrootPath = new DFUImagePath(path, null, IMAGE_ID_RES);
        }
    }

    // ---- 4. 模式组装 ----
    if (diffCtrlPath != null) {
        list.add(diffCtrlPath);
        if (TextUtils.isEmpty(resourceUrl) || TextUtils.isEmpty(resourceMd5)
                || TextUtils.isEmpty(resourceName)) {
            throw new IllegalStateException("diff OTA needs resource name/url/md5");
        }
        File diffDir = new File(context.getCacheDir(), "device/qjs_diff");
        diffDir.mkdirs();
        File resFile = new File(diffDir, resourceName);
        byte[] resBytes = httpDownload(resourceUrl);
        if (!md5Hex(resBytes).equalsIgnoreCase(resourceMd5)) {
            throw new IllegalStateException("resource md5 mismatch");
        }
        writeBytes(resFile, resBytes);
        list.add(new DFUImagePath(resFile.getAbsolutePath(), null, IMAGE_ID_NAND_RES));
        return list;
    }

    if (ctrlPath != null) {
        list.add(ctrlPath);
        if (outdynPath != null) list.add(outdynPath);
        if (outrootPath != null) list.add(outrootPath);
        return list;
    }

    throw new IllegalStateException("zip missing ctrl / diff_ctrl");
}
```

#### 17.5.9 启动 DFU 与进度回调

```java
// Activity.onCreate
Intent svc = new Intent(this, SifliDFUService.class);
bindService(svc, connection, BIND_AUTO_CREATE);

// 组装完成后（需 service 已 onServiceConnected）
registerDfuBroadcast(); // 先注册再启动，避免丢进度
new Handler(Looper.getMainLooper()).postDelayed(() -> {
    sifliDFUService.startActionDFUNand(
            this,
            deviceMac,                 // 已绑定设备 MAC
            dfuImagePaths,
            Protocol.DFU_MODE_NORMAL,
            0
    );
}, 1500); // 现网延迟约 1.5s，确保 Broadcast 已就绪
```

LocalBroadcast（`LocalBroadcastManager`）：

| Action | Extra | 说明 |
|--------|-------|------|
| `BROADCAST_DFU_PROGRESS` | `EXTRA_DFU_PROGRESS` (0~100), `EXTRA_DFU_PROGRESS_TYPE` | NAND 时资源 zip 与 hcpu bin **进度分开**；需按 type 自行汇总或分栏展示 |
| `BROADCAST_DFU_STATE` | `EXTRA_DFU_STATE`, `EXTRA_DFU_STATE_RESULT` | `DFU_SERVICE_EXIT` 且 `result==0` 成功；`result!=0` 失败 |
| `BROADCAST_DFU_LOG` | `EXTRA_LOG_MESSAGE` | 调试日志 |

```java
case BROADCAST_DFU_STATE:
    int state = intent.getIntExtra(EXTRA_DFU_STATE, 0);
    int result = intent.getIntExtra(EXTRA_DFU_STATE_RESULT, 0);
    if (state == DFU_SERVICE_EXIT) {
        if (result == 0) {
            // 成功：设备将重启，建议延迟再 reconnect / 刷新版本
        } else {
            // 失败：result 为思澈错误码
        }
    }
    break;
```

#### 17.5.10 额外依赖

`SifliDFU` 从私有 Nexus 或本地 AAR 引入均可（仓库配置见 [§1.1](#11-私有-maven-仓库推荐有外网内网可达-nexus-时使用)）：

```groovy
// 方式 A：私有 Maven
implementation 'com.sifli:SifliDFU:1.1.99'

// 方式 B：本地 AAR（二选一）
// implementation files('libs/SifliDFU-1.1.99.aar')
```

```proguard
-keep class com.sifli.siflidfu.** { *; }
-dontwarn com.sifli.siflidfu.**
```

Manifest 需注册 `SifliDFUService`（以该 AAR 说明为准）。升级过程建议保持屏幕常亮；进行中禁止返回。

> 仅对接瑞昱/WL 固件升级时**不必**集成本节。

### 17.6 App 侧完整示例（对齐现网编排）

以下展示「检查 → 选通道 → 回调」骨架（固件下载省略为伪代码）：

```java
public void startFirmwareUpgrade(FirmwarePackage pkg) {
    if (!BluetoothSDK.isBluetoothEnabled() || !BluetoothSDK.isConnected()) {
        toast("请先连接设备");
        return;
    }

    BluetoothSDK.getBattery(new IntValueCallback() {
        @Override
        public void onSuccess(int battery) {
            if (battery < 30) {
                toast("电量低于 30%，请充电后再升级");
                return;
            }
            BluetoothSDK.getDeviceUpgradeStatus(new UpgradeStatusCallback() {
                @Override
                public void onSuccess(UpgradeStatus status) {
                    if (status != UpgradeStatus.Normal) {
                        toast("设备忙，请稍后重试");
                        return;
                    }
                    dispatchOta(pkg);
                }
                @Override public void onFail(int code) { toast("无法查询升级状态"); }
            });
        }
        @Override public void onFail(int code) { }
    });
}

private void dispatchOta(FirmwarePackage pkg) {
    if (isSifliProduct()) {
        // 方案 C：解压 DFU 镜像后 startActionDFUNand（见 §17.5）
        startSifliDfu(pkg);
        return;
    }
    if (BluetoothSDK.isWlProtocol()) {
        // 方案 B
        String path = downloadToLocal(pkg.url, pkg.md5);
        BluetoothSDK.starWlOta(path, wlCallback);
        return;
    }
    // 方案 A
    List<OtaData> list = new ArrayList<>();
    for (FirmwarePart part : pkg.parts) {
        byte[] bytes = downloadToBytes(part.url, part.md5);
        list.add(new OtaData(OtaDataType.valueOf(part.type), bytes));
    }
    BluetoothSDK.ota(list, otaCallback);
}
```

### 17.7 进度与 UI 建议

| 阶段 | 建议 |
|------|------|
| 下载固件 | App 自行展示下载进度（与 SDK 进度分开） |
| SDK 传输 | `onUpload` / `onProgress` 映射到 0~100% |
| 进行中 | `FLAG_KEEP_SCREEN_ON`，隐藏返回或二次确认 |
| 成功 | 提示「设备将重启」，延时后 `reconnect` + `getDeviceInfo` |
| 失败 | 展示 `ErrorCode` / `message`，允许重试；WL 需 `forceReset` |
| 页面销毁 | 取消订阅；WL 调用 `WlOtaManager.forceReset()` |

### 17.8 相关错误码

| 常量 | 含义 |
|------|------|
| `CALLING_OTA_ERROR` (58) | OTA 调用失败（瑞昱等） |
| `OTA_WORKING` / `ALREADY_OTAING` | 已有 OTA 任务 |
| `OTA_DATA_EMPTY` / `OTA_ADDRESS_NULL` / `OTA_START_FAILED` | 数据包或地址异常 |
| `POWER_LOW` / `POWER_CHARGING` / `POWER_SAVE_MODE` | 电量/充电/省电限制（视固件） |
| `PUSHING_WATCHFACE` / `PUSHING_MUSIC` / `AGPS_UPDATING` | 其它传输占用 |
| `DISCONNECTED` / `BLUETOOTH_OFF` | 连接中断 |
| `NOT_ENOUGH_SPACE` | 空间不足（部分机型） |

### 17.9 依赖与包路径汇总

| 内容 | 路径 / 依赖 |
|------|-------------|
| 通用 OTA | `BluetoothSDK.ota` / `interfaces.ota.OtaData` / `OtaDataType` / `OtaCallback` |
| WL OTA | `BluetoothSDK.starWlOta` / `wl.ota.WlOtaCallback` / `wl.ota.WlOtaManager` |
| 升级状态 | `getDeviceUpgradeStatus` / `ops.models.UpgradeStatus` |
| 思澈固件 DFU | `com.sifli:SifliDFU` → `SifliDFUService` / `DFUImagePath`（**额外依赖**） |

---

## 18. GPS / 地图

```java
BluetoothSDK.getDeviceGpsStatus(new GpsStatusCallback() {
    @Override public void onSuccess(GpsStatus status) { }
    @Override public void onFail(int code) { }
});

// 下发手机当前位置给手表
BluetoothSDK.setDeviceCurrentGpsLocation(location, boolCallback);

BluetoothSDK.setDeviceMapAuthCode(uuid, authCode, boolCallback);
BluetoothSDK.setDeviceMapTheme(MapTheme.xxx, boolCallback);

// 设备请求 AGPS / 定位时监听
BluetoothSDK.addDeviceAgpsShouldUpdateListener(...);
BluetoothSDK.addDeviceRequestGpsLocationListener(...);
```

---

## 19. 其他常用设置

```java
// 亮度 / 亮屏时长 / 音量
BluetoothSDK.setBrightness(3, boolCallback);
BluetoothSDK.setDisplayDuration(10, boolCallback); // 秒
BluetoothSDK.setVolumn(5, boolCallback);

// 抬腕亮屏 / 翻腕亮屏 / 息屏显示
BluetoothSDK.setFaceSreen(faceScreen, boolCallback);
BluetoothSDK.setFlipLightUpEnable(true, boolCallback);
BluetoothSDK.setOffScreenDisplayEnable(true, boolCallback);

// 密码
BluetoothSDK.setDevicePasscode("1234", boolCallback);
BluetoothSDK.delDevicePasscode(boolCallback);

// 系统控制
BluetoothSDK.rebootDevice(boolCallback);
BluetoothSDK.resetDevice(boolCallback);
BluetoothSDK.shutdownDevice(boolCallback);
BluetoothSDK.clearDeviceData(boolCallback);

// App 前后台态（部分固件用于优化连接）
BluetoothSDK.setClientState(true /* interactive */, boolCallback);

// 经典蓝牙开关
BluetoothSDK.setBTSwitch(true, boolCallback);
BluetoothSDK.turnOnBTSwitchWithOption(true /* autoConnect */, boolCallback);

// 功能开关
BluetoothSDK.setFeatureSwitch(new FeatureSwitch(FeatureSwitchType.xxx, true), boolCallback);
BluetoothSDK.getFeatureSwitches(featureSwitchesCallback);

// 世界时钟 / 生理期（按产品支持）
BluetoothSDK.setWorldClockCity(city, boolCallback);
BluetoothSDK.setPhysiologicalPeriodSetting(setting, boolCallback);
```

---

## 20. 全局监听器

需要全局接收的监听可在 `Application` 初始化后注册，在 `destroy` 时成对移除；仅服务页面的测量／提醒监听按页面会话注册及清理，不要求全部常驻。常用监听：

| API | 用途 |
|-----|------|
| `addConnectionStateListener` | 连接状态 |
| `addBindStateListener` | 绑定状态 |
| `addDeviceEventListener` | 设备主动事件（同步活动/天气/定位等） |
| `addLookingForPhoneListener` | 找手机 |
| `addCameraOpListener` | 遥控拍照 |
| `addWorkoutRealtimeDataUpdatedListener` | 运动实时数据 |
| `addBatteryChangedListener` | 电量变化 |
| `addGoalUpdatedListener` | 目标变更 |
| `addMusicEventListener` | 手表控制手机音乐 |
| `addRingOffListener` | 拒接来电 |
| `addReminderNotifyListener` | 杰理主动提醒；与 `removeReminderNotifyListener` 成对使用，见 §11.7 |
| `addRealtimeIntWithTimeListener` | 带设备毫秒时间戳的整数测量值，见 §9.4 |
| `addRealtimeFloatWithTimeListener` | 带设备毫秒时间戳的浮点测量值，见 §9.4 |
| `addNotProperlyWornCallback` | 当前测量的佩戴异常，见 §9.4 |
| `addMonitorStateChangedCallback` | 设备停止测量事件，见 §9.4 |

过滤未绑定设备事件（可选）：

```java
BluetoothSDK.setDeviceEventFilter(event -> BluetoothSDK.isBind());
```

---

## 21. 协议分支说明

不同芯片/协议线的 API 后缀不同，请按 `DeviceInfo.protocolVersion` 或厂商说明选择：

| 判断 / 后缀 | 说明 |
|-------------|------|
| `isWlProtocol()` / `protocolVersion >= 100` | WL 协议，多用 `*V2`、`starWlOta`、`addAlarmV2`、`WlMediaTransferManager` 等 |
| `ForLS` / `ForLS16` | LS 系列接口 |
| `Sifli` / `Qjs` | 思澈芯片相关（绑定、ZIP 音乐、表盘 DFU、固件 DFU）；固件 OTA 需 SifliDFU，ZIP 音乐需 watchface AAR |
| 无后缀 | 不代表一定是老协议；部分无屏 WL 新接口（如 `getBreaths`、`getSkinTemperature`、`startMonitoring`）没有 `V2` 后缀，需按接口说明使用；通用固件 OTA 仍走 `BluetoothSDK.ota` |
| 产品配置 `hasSPP` | 是否走经典蓝牙 SPP 推送音乐/相册/离线地图 |

不确定时以产品对接文档或 `getDeviceFeatures` 能力位为准。

无屏设备不是一种可由方法后缀推断的统一能力集：历史同步、自动监测、手动测量、提醒事件和云端 AI 分别核对支持情况。Demo 的“同步数据（杰里）”“手动测量（杰里）”“自动监测”“提醒监听（杰里）”及“AI”菜单内的各分析功能独立调用，示例不增加 `isWlProtocol()` 等平台判断，也不把不支持时的失败自动转成另一平台调用。上述规则不改变已有通用／思澈示例。

---

## 22. 推荐集成流程

```text
1. Application.onCreate → BluetoothSDK.init(app, maxMTU)
2. 注册全局 Listener（连接态、设备事件、找手机等）
3. 首次绑定页：申请权限 → scan → connect → createBond(如需) → startBind
4. 绑定成功：setDeviceTime / setUserInfo / setUnit / setLanguage / setGoal → endBind → setBind(true)
5. 日常：reconnect(已存 MAC) → getDeviceInfo → getActivityData(V2) / getWorkouts → del*
6. 通知、天气、表盘、音乐推送、OTA 等按业务调用
7. 解绑：清本地 → removeBond → disconnect → setBind(false)
8. 进程退出：remove Listener → BluetoothSDK.destroy()
```

### 绑定成功后环境同步 Demo

```java
private void syncEnvironmentAndEndBind() {
    BluetoothSDK.setDeviceTime(new Date(), new BoolCallback() {
        @Override
        public void onSuccess() {
            BluetoothSDK.setUserInfo(buildUserInfo(), new BoolCallback() {
                @Override
                public void onSuccess() {
                    BluetoothSDK.setUnit(Unit.Metric, new BoolCallback() {
                        @Override
                        public void onSuccess() {
                            BluetoothSDK.endBind(new BoolCallback() {
                                @Override
                                public void onSuccess() {
                                    BluetoothSDK.setBind(true);
                                    // 进入主页，开始健康数据同步
                                }
                                @Override public void onFail(int code) { }
                            });
                        }
                        @Override public void onFail(int code) { }
                    });
                }
                @Override public void onFail(int code) { }
            });
        }
        @Override public void onFail(int code) { }
    });
}
```

---

## 23. AI 分析（无屏 Demo）

主页面统一为一个“AI”入口，打开卡片菜单：“使用指引”“AI DEMO”，以及“AI 分析”分组下的运动健康分析、AI 洞察、身体状态及 AI 睡眠（天／周／月）六个入口；按钮按三列排列，长文字保持字号换行。使用指引保留完整的原集成说明。AI DEMO 采用独立卡片布局，保留设备 ID、录音模式、平台 Spinner、尺寸、原有操作按钮和图片预览，日志默认收起、可展开；复用原 `AiWatchfaceFragment` 的初始化／录音／表盘流程，不改变表盘 Tab 原布局，也不把平台选择改成仅杰理的开关。每个分析入口通过参数选择对应报告，复用 `AiAnalysisFragment`，以状态、参数配置、功能操作和执行结果卡片展示，保留原说明文字、三项身份配置、重新初始化按钮及本次功能的请求按钮。健康输入为固定模拟数据，实际调用 AI SDK 和云端服务；不是本地伪造报告，不自动读取设备健康数据，也不把结果下发手表或写入数据库。

本章三类 OmniAgent 请求的参数含义、返回字段及分级参考厂商《[琦沃健康 SDK说明文档](https://adflash.feishu.cn/wiki/HYHnwANniiAKV6kdDUIczw18nm7)》（2026-10-14 核对），并说明当前 SDK 封装与 HaWoFit 解析规则。该厂商页面覆盖睡眠、运动洞察和身体状况；运动健康报告的字段转换另以当前 `AFlashHealthAnalysisHandler`／`HealthAnalysisResult` 实现为依据。示例 JSON 用于说明结构，不是实际服务调用结果。

各按钮只发起自己的报告请求，不增加平台判断，不启动录音服务、AI 表盘安装或全局设备端 AI 工作监听。真实产品需要由 App 先整理健康输入，不能用本节的模拟数据代替真实用户记录。

### 23.1 依赖与权限

当前 Demo 的本地交付版本为 `BluetoothSDK-2.5.4.129.aar` 和 `ai-1.5.1.29.aar`。AI 包是独立扩展，并非 BluetoothSDK 内置能力；本地 AAR 不携带 Maven 的传递依赖描述，不能只复制一个 `ai-*.aar` 就认为集成完成。

当前 Demo 的配套厂商包包括 `QW_release_V4.7.2.aar`（AFlash）、`qjs-watchface-15.0.16.aar`、`BmpConvert_V1.6.0_10608_qiwo.aar`、`jl_pack_resource_V1.0.0_10_qiwo.aar`，以及所需 `sifliezipsdk`／`siflicore`／`sifliwatchfacesdk`。这是 Demo 完整运行时清单中的 AI 相关部分，不声明为任意产品都可裁剪的最小依赖；`AiCenter` 共用表盘等类型，裁剪需核对厂商交付要求。

在 `settings.gradle` 的 `dependencyResolutionManagement.repositories` 中补充（保留已有 `google()`、`mavenCentral()`）：

```groovy
maven {
    url = uri('https://maven.hanshuang.ltd/repository/maven-releases/')
    content { includeGroup('com.adf') }
}
```

模块依赖示例（同一厂商 AAR 只保留一份来源）：

```groovy
implementation fileTree(dir: 'libs', include: ['*.aar'])
implementation 'com.adf:oa-oas:2.5.0'
implementation 'com.squareup.okhttp3:okhttp:5.1.0'
implementation 'com.squareup.okhttp3:okhttp-sse:5.1.0'
implementation 'com.google.code.gson:gson:2.13.1'
implementation 'androidx.lifecycle:lifecycle-process:2.9.2'
```

OmniAgent 的 API、contracts、core、Opus 运行时由对应 Maven 依赖解析，不再手工混入不同版本的同名库。当前初始化模型包名是 `com.adf.oa.contracts.init`，不是旧示例中的 `com.adf.oa.core.init.data`。

云端请求需要 `INTERNET`，Demo 连接设备仍需 §2 的 BLE 权限。这里只做文本／健康数据分析，不需要为了本页申请录音权限或启动麦克风前台服务。

### 23.2 基础准备与 DeviceId

Application 中沿用现有 AI 基础准备：

```java
com.huawo.ai.utils.SpUtils.init(this);
com.huawo.ai.AiCenter.getInstance().setApplication(this);
```

这只是进程级准备，不等于完成 OmniAgent／AFlash 的云端初始化与授权。BLE 已连接也不代表 AI 就绪。

当前 Demo 在进入页面时，用连接设备 MAC 去掉 `:`／`-` 并转小写预填 DeviceId，例如 `DC:D9:61:4C:89:94` → `dcd9614c8994`。这是本 Demo 的默认身份约定，不是所有 AI 产品的统一 ID 生成规范；是否已授权仍由厂商渠道配置和云端校验决定。无连接设备时不伪造 MAC，可以连接后再进入或手动填写有效 DeviceId。

渠道 ID 和 AppKey 在当前 Demo 中预填 HaWoFit NoScreen 配置，三项均可编辑。客户文档仅使用 `YOUR_CHANNEL_ID`／`YOUR_APP_KEY` 占位符，不公开实际凭据；不要把不同渠道的身份与 AppKey 混用，也不要把凭据原文写入日志。

### 23.3 OmniAgent 页面初始化与就绪状态

洞察、身体状态和日／周／月睡眠使用同一套 OmniAgent 初始化流程。AI 菜单只负责导航，不初始化或提交报告；Demo 在进入选定分析功能页时沿用页面初始化，收到成功回调后才启用当前页对应的 OmniAgent 报告按钮。每页使用自己的 `AiAnalysisViewModel`，原有忙碌／就绪状态收集逻辑继续控制输入框、初始化按钮和报告按钮，通用卡片渲染不覆盖鉴权未就绪时的禁用状态。点击报告按钮只提交当前报告，不重复执行初始化。运动健康分析仍保留 §23.4 的独立 AFlash 点击初始化。

以下是 SDK 初始化调用片段。`isCurrent` 由页面以配置快照和会话版本实现；退出或修改 DeviceId／渠道／AppKey 后必须使旧请求失效，不能让迟到回调恢复旧配置的按钮。

```kotlin
import com.adf.oa.OTek
import com.adf.oa.contracts.init.DeviceConfig
import com.adf.oa.contracts.init.InitConfig
import com.huawo.ai.AiCenter

fun initializeOmniForAnalysis(
    context: android.content.Context,
    deviceId: String,
    channelId: String,
    appKey: String,
    isCurrent: () -> Boolean,
    onReady: () -> Unit,
    onFailure: (Int, String) -> Unit,
) {
    val center = AiCenter.getInstance()
    center.setOmniAgentReadyDeviceId(null)
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    val device = DeviceConfig(deviceId).apply {
        systemLanguage = java.util.Locale.getDefault().toLanguageTag()
        // resolution 可按真实产品提供；文本报告不使用虚构的表盘尺寸判断平台。
    }
    val config = InitConfig(channelId, appKey, device)
    OTek.initialize(context.applicationContext, config, {
        main.post {
            if (isCurrent()) {
                center.setOmniAgentReadyDeviceId(deviceId)
                onReady()
            }
        }
    }, { code, message ->
        main.post {
            if (isCurrent()) {
                center.setOmniAgentReadyDeviceId(null)
                onFailure(code, message)
            }
        }
    })
}
// 参数示意：channelId = "YOUR_CHANNEL_ID"，appKey = "YOUR_APP_KEY"。
```

调用片段外还需校验三项非空、处理初始化同步抛出的异常，并禁止未就绪的报告提交。Demo 由 `AiAnalysisViewModel.initialize()` 完成这些控制，初始化等待上限为 30 秒；失败保留错误信息并允许“重新初始化／重试”。修改配置立即清除本页与 SDK 的对应就绪状态，不能只改输入框后继续沿用旧授权状态。

生成前仍需检查 `AiCenter.isOmniAgentReadyFor(deviceId)`；SDK 报告 Handler 也会检查请求身份。初始化失败、超时或页面退出后，不把请求已提交显示成“初始化成功”。页面退出取消本页任务并忽略迟到回调，不因此销毁全局 `AiCenter` 或取消其他模块工作。

### 23.4 运动健康分析（AFlash）

运动健康分析走 `AFlashScreenlessHealthAnalysisHandler`，不是上述 OmniAgent 五项报告。App 直接提供 `HealthData`；不用 `registerAppHealthAnalysisRequest()` 等待设备推送，也不发起设备健康读取命令。

初始化仍沿用当前 Demo 的 `initializeHealth()`：点击运动健康分析后调用 `AiCenter.init(context, false)`、设置 `AiDeviceInfo`，并使用 AFlash 的完成回调确认初始化。`setDeviceInfo()` 内部已经会触发 AFlash 初始化，而当前 Demo 仍额外调用一次 `AFlash.initialize()` 等待确认，因此该路径存在重复初始化；本次未修改 SDK／AAR，不宣称此问题已解决，也不把这两次调用推广为所有产品的推荐初始化方式。当前 SDK 没有公开 `setDeviceInfo()` 的成功回调，不能把该方法返回视为授权完成。

下面只演示初始化完成后的模拟输入及报告提交，不是完整的初始化实现：

```kotlin
import com.huawo.ai.handler.impl.AFlashScreenlessHealthAnalysisHandler
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.HealthData

val sample = HealthData().apply {
    setGender(1)
    setAge(30)
    setWeight(700) // SDK 单位为 0.1 kg，本例 70 kg
    setHeight(175)
    setUnit(0) // 本例使用公制
    setDailyDataList(List(7) { daysAgo ->
        HealthData.DailyData().apply {
            setDay(daysAgo) // 0 为当天，6 为六天前
            setStep(8000)
            setMinHr(55); setMaxHr(125); setAvgHr(72)
            setMinSpO2(95); setMaxSpO2(99); setAvgSpO2(98)
            setMinStress(15); setMaxStress(60); setAvgStress(30)
            setSleep(480) // 分钟
            setPAI(50)
        }
    })
}
val healthOperation = AFlashScreenlessHealthAnalysisHandler { result, code, message ->
    // code == 0 且 result 非空时展示 HealthAnalysisResult；否则保留错误码及说明。
    // 回调不向设备下发分析结果；更新 UI 时切回主线程并校验页面会话。
}
healthOperation.start(sample)
// 页面退出或本页任务取消：healthOperation.cancel()。
```

`onStarted()` 表示 Handler 开始处理输入，不等于报告已生成；空日数据列表会失败。成功结果由 App 展示／按自身业务保存，本 Demo 不保存。使用无屏 Handler 而非普通 `AFlashHealthAnalysisHandler`，避免走设备结果回传分支。

#### 23.4.1 SDK 调用参数与 HealthData

调用形式为 `new AFlashScreenlessHealthAnalysisHandler(ResultCallback)`，随后调用 `start(HealthData)`；无需自行调用厂商 HTTP 地址或手工上传下面的转换 JSON。`AiDeviceInfo.id` 提供 AFlash 的设备身份，`HealthData` 中不重复设置 DeviceId。

| SDK 入参字段 | 类型 | 含义与单位 | 当前示例 |
|---|---|---|---|
| `gender` | `int` | SDK 性别编码，例如 `Gender.Female.getValue()`；`0` 男、`1` 女、`2` 未知 | `1` |
| `age` | `int` | 年龄，岁 | `30` |
| `weight` | `int` | 以 0.1 kg 存储；`700` 表示 70 kg | `700` |
| `height` | `int` | 身高，cm | `175` |
| `unit` | `int` | `0` 公制、`1` 英制；改变序列化显示单位，不改变上述原始输入单位 | `0` |
| `dailyDataList` | `List<HealthData.DailyData>` | 每日汇总列表，不能为空；当前 Demo 构造当天到前六天共七项 | 七项 |

`unit=1` 时 SDK 将同一原始重量换算成 `lb` 文本、身高换算成 `ft/in` 文本。不要先把输入改成磅／英寸再让 SDK 二次换算。SDK 不自动从设备补齐这份列表，也不把列表中缺失日期自动补成零。

| `DailyData` 字段 | 类型 | 含义与单位 | 发往 AFlash 的字段 |
|---|---|---|---|
| `day` | `int` | 距今天的天数；`0` 今天、`1` 昨天，以此类推 | `day`，转换为 `yyyy-MM-dd` |
| `step` | `int` | 当日步数 | `step` |
| `minHr` / `maxHr` / `avgHr` | `int` | 当日最小／最大／平均心率，次/分钟 | `hr`，按最小/最大/平均拼接 |
| `minSpO2` / `maxSpO2` / `avgSpO2` | `int` | 当日最小／最大／平均血氧，百分比数值 | `sao2`，按最小/最大/平均拼接 |
| `minStress` / `maxStress` / `avgStress` | `int` | 当日最小／最大／平均压力值 | `stress`，按最小/最大/平均拼接 |
| `minBP` / `maxBP` / `avgBP` | `BloodPressure` | 最小／最大／平均血压对象，读取其收缩压和舒张压 | `pressureLow` / `pressureHigh` / `pressureAvg`，每项为收缩压/舒张压 |
| `sleep` | `int` | 当日睡眠总分钟数，不是小时数或秒数 | `sleep`，转为字符串 |
| `PAI` | `int` | 当日 PAI 数值，使用 `setPAI()` | `pai`，转为字符串 |

SDK 转换时始终加入每行的 `day`、`gender`、`age`、带单位的 `weight`／`height`。`step`、`sleep`、`PAI` 只有大于零才加入；心率、血氧、压力各自的最小／最大／平均值必须全部大于零，才加入该组三值字符串；血压对象非空才加入对应字段。未提供的字段会被省略，不能把这里的行为套用成 OmniAgent 输入序列的补零规则。

SDK 最终发送结构示意（仅展示一天；完整列表由调用方提供）：

```json
{
  "analysisType": 1,
  "data": [{
    "day": "2026-10-14",
    "gender": 1,
    "age": 30,
    "weight": "70.0kg",
    "height": "175cm",
    "step": 8000,
    "hr": "55/125/72",
    "sao2": "95/99/98",
    "stress": "15/60/30",
    "sleep": "480",
    "pai": "50"
  }]
}
```

这里的 `analysisType=1` 由 SDK 固定加入；`day` 由每条 `DailyData.day` 与发送时手机日期换算，设备身份、SDK 内部请求 ID 不在这个 `data` 对象中。

#### 23.4.2 厂商回包到 HealthAnalysisResult 的映射

AFlash 原始文本的顶层对象名为 `HealthDataAnalysisReport`，内部字段按当前 SDK 的大小写解析。SDK 先转换成业务模型，再调用 `onResult(result, code, message)`；客户端拿到的不是下面这个原始包装对象。

```json
{
  "HealthDataAnalysisReport": {
    "Steps": "示例步数分析文本",
    "HeartRate": "示例心率分析文本",
    "BloodOxygen": "示例血氧分析文本",
    "Stress": "示例压力分析文本",
    "BloodPressure": "示例血压分析文本",
    "Sleep": "示例睡眠分析文本",
    "PAI": "示例活动指数分析文本",
    "Weight": "示例体重分析文本",
    "Conclusion": "示例综合结论文本"
  }
}
```

| 原始 JSON 字段 | SDK 返回字段 / Getter | 类型 | 说明 |
|---|---|---|---|
| `Steps` | `step` / `getStep()` | `String` | 步数相关分析正文，不是步数整数 |
| `HeartRate` | `heartRate` / `getHeartRate()` | `String` | 心率分析正文 |
| `BloodOxygen` | `spO2` / `getSpO2()` | `String` | 血氧分析正文，不是血氧百分比 |
| `Stress` | `stress` / `getStress()` | `String` | 压力分析正文 |
| `BloodPressure` | `bp` / `getBp()` | `String` | 血压分析正文 |
| `Sleep` | `sleep` / `getSleep()` | `String` | 睡眠分析正文，不是睡眠分钟数 |
| `PAI` | `PAI` / `getPAI()` | `String` | PAI 分析正文；SDK 配置 `paiReplaceString` 时可能替换正文中的 PAI 字样 |
| `Weight` | `weight` / `getWeight()` | `String` | 体重分析正文，不是重量数值 |
| `Conclusion` | `summary` / `getSummary()` | `String` | 综合结论正文 |

Demo 用 Gson 展示这个业务模型时，顶层字段为 `step`、`heartRate`、`spO2`、`stress`、`bp`、`sleep`、`PAI`、`weight`、`summary`，不会保留 `HealthDataAnalysisReport` 包装层。不要混用原始 JSON 字段与 Java Getter 名称。

`code=0` 且 `result!=null` 表示本次 SDK 分析成功；失败时读取回调的原始 `code`／`message`，`result` 可能为空。当前 SDK 会检查返回内容及包装对象，但没有逐个校验九段文字必须非空，缺失字段可能为 `null`，App 需按实际展示要求处理。Demo 使用的默认 Gson 会省略 `null` 字段，因此展示 JSON 中某个键缺失不代表 SDK 改了字段名。`onStarted()` 在输入完整性校验前触发，不能作为输入有效或请求成功的标志。

### 23.5 AI 洞察与身体状态

请求模型位于 `com.huawo.ai.handler.model`，各项通过独立的 `generate*Report()` 调用，不先调用其他分析功能来准备结果。

三个 OmniAgent 报告方法的公共调用参数如下；SDK 内部以 `TEXT_BLOCK_BYTES`／`TEXT` 提交 UTF-8 JSON，并将智能体的文本结果组装后回调，App 无需重复实现底层流式接收。

| 调用参数 / 返回值 | 类型 | 说明 |
|---|---|---|
| `context` | `Context` | 传 Application Context；SDK 也会保存其 Application Context |
| `request` | `AiInsightRequest` / `AiBodyStatusRequest` / `AiSleepRequest` | 本次身份与数据快照，字段分别见本节及 §23.6 |
| `callback` | 对应 Handler 的 `Callback` | `onSuccess(String json)` 返回完整文本；`onFailure(int code, String message)` 返回错误，不是统一 HTTP `code/data` 包装 |
| `logger` | `com.huawo.ai.logger.ILog` | 可选的四参数重载，用于记录本次请求日志；三参数重载无需传入 |
| 方法返回值 | 对应 `AFlash*ReportHandler` | 本次操作对象，用于 `cancel()`；不是报告内容，不能把方法返回当成生成成功 |

`onSuccess` 中的 `json` 是原始报告文本，可能包含 Markdown JSON 围栏；解析前可移除完整围栏，再检查顶层对象及所需字段。SDK 完成文本组装不等于已校验所有业务字段。以下返回字段是厂商报告结构；HaWoFit 的 `userId`、`deviceId`、`dateKey`、`generatedAt`、睡眠请求快照 `input` 等由 App 自行补充，不属于云端报告字段。

#### 23.5.1 AI 洞察请求参数

`AiInsightRequest(String deviceId, String inputJson)` 两个参数均需非空。`deviceId` 与 OmniAgent 已就绪身份一致，用于 SDK 的身份检查；它不作为字段加入下面的业务 JSON。`inputJson` 是完整对象的 JSON 字符串，SDK 包装后发送 `[{...}]`；不要自行先包装成数组再传入。

日期范围与七个槽位都从当天倒序到前六天，缺失槽位填字符串 `"0"`，不能删除槽位导致日期错位。

当前模拟对象示例（日期需替换为本次生成的日期范围）：

```json
{
  "date": "2026/10/14-2026/10/08",
  "stepGoal": "10000",
  "activityDurationGoal_min": "30",
  "activitycalorieGoal_kcal": "500",
  "sleepQuality": "85",
  "activityDuration_min": "40,40,40,40,40,40,40",
  "steps": "8000,8000,8000,8000,8000,8000,8000",
  "sleepDuration_min": "480,480,480,480,480,480,480",
  "mood": "心情平和,心情平和,心情平和,心情平和,心情平和,心情平和,心情平和",
  "calorieGoal_kcal": "350,350,350,350,350,350,350",
  "lightSleep_min": "300,300,300,300,300,300,300",
  "deepSleep_min": "120,120,120,120,120,120,120",
  "heartRate_bpm": "72,72,72,72,72,72,72",
  "heartratevariability(hrv)": "45,45,45,45,45,45,45",
  "stress": "30,30,30,30,30,30,30",
  "restingheartRate_bpm": "58,58,58,58,58,58,58"
}
```

字段名保留 SDK 约定的大小写及括号。目标值 `activitycalorieGoal_kcal` 与实际消耗序列 `calorieGoal_kcal` 不同；睡眠／活动时长字段以分钟输入。`mood` 使用约定情绪文字，不直接把本地情绪等级数字当成文字值。

以下业务字段在 JSON 中均使用字符串，包括数值、评分与多值序列。七日序列的第一个值对应 `date` 的最新日期，最后一个值对应最早日期；缺失位置使用 `"0"`，各序列不能独立删减日期。

| 字段 | 数量 / 单位 | 含义及示例值 |
|---|---|---|
| `date` | 一个日期范围 | `yyyy/MM/dd-yyyy/MM/dd`；包括当天共七天，例如 `2026/10/14-2026/10/08` |
| `stepGoal` | 一个值，步 | 每日目标步数，例如 `"10000"` |
| `activityDurationGoal_min` | 一个值，分钟 | 每日目标活动时长，例如 `"30"` |
| `activitycalorieGoal_kcal` | 一个值，千卡 | 每日目标活动消耗，例如 `"500"` |
| `activityDuration_min` | 七个值，分钟/天 | 每天实际活动时长；不是每小时活动时长 |
| `steps` | 七个值，步/天 | 每天实际步数 |
| `mood` | 七个情绪文字 | 每天记录的情绪，可为特别低落、有点低落、心情平和、有点愉悦、特别愉悦；缺失位置为 `"0"` |
| `calorieGoal_kcal` | 七个值，千卡/天 | 每天实际活动消耗；虽然名称带 Goal，不能与目标字段混用 |
| `restingheartRate_bpm` | 七个日均值，次/分钟 | 每天静息心率平均值 |
| `heartRate_bpm` | 七个日均值，次/分钟 | 每天普通心率平均值 |
| `heartratevariability(hrv)` | 七个日均值，毫秒 | 每天 HRV 平均值，保留字段名中的括号 |
| `stress` | 七个日均值，无单位评分 | 每天压力平均值；厂商分级见 §23.5.4 |
| `sleepDuration_min` | 七个值，分钟/天 | 每天总睡眠分钟数，例如 8 小时传 `480`，不是 `8` |
| `sleepQuality` | 一个评分 | 当天睡眠质量评分，不是七日序列；本例为 `"85"` |
| `lightSleep_min` | 七个值，分钟/天 | 每天浅睡时长 |
| `deepSleep_min` | 七个值，分钟/天 | 每天深睡时长 |

这些是厂商列出的完整业务参数，不表示 SDK 为每个字段实现了必填、单位或槽位校验；SDK 主要检查身份及 JSON 基本结构，真实数据的完整性仍由 App 保证。厂商参数表中 `steps` 的单位栏写成了“分钟”，与其步数描述及输入示例不一致；本文按步数语义说明，不把该单元格解释成步数需要时间换算。厂商输入示例中的日期端点也有不一致，本文统一使用与七个日槽位匹配的倒序七天范围。

```kotlin
val insightOperation = AiCenter.getInstance().generateInsightReport(
    context.applicationContext,
    com.huawo.ai.handler.model.AiInsightRequest(deviceId, insightJson),
    object : com.huawo.ai.handler.impl.AFlashInsightReportHandler.Callback {
        override fun onSuccess(json: String) { /* 展示返回正文，不重格式化返回的时间文字 */ }
        override fun onFailure(code: Int, message: String?) { /* 展示 code 与 message */ }
    },
)
// 只取消本次请求：insightOperation.cancel()。
```

#### 23.5.2 AI 洞察返回字段

返回一个 JSON 对象，包含三个字段；以下为自拟结构示例：

```json
{
  "title": "每日洞察示例",
  "remind": 3,
  "content": "示例分析正文，由实际服务返回替换。"
}
```

| 返回字段 | 厂商类型 | 含义 / 处理规则 |
|---|---|---|
| `title` | `String` | 报告标题；HaWoFit 要求非空字符串 |
| `content` | `String` | 分析及建议正文；其中出现的数值属于正文内容，不是额外结构化指标 |
| `remind` | 整数分类 | 厂商定义为 `1..5`，含义见下；这是报告提示类别，不会自动向设备设置提醒 |

厂商的 `remind` 分类为：`1` 睡眠，`2` 活动，`3` 生理压力，`4` 附加类（情绪、过高／过低心率等），`5` 一切正常。HaWoFit 对 `1` 提供睡眠提醒入口、`2` 提供久坐提醒入口、`3` 提供冥想入口；`4`、`5` 不显示该提醒区域。当前 SDK Demo 仅展示报告，不实现这些生产 App 的跳转操作。

HaWoFit 解析器另外兼容数字字符串 `"1".."5"`、`0`／`"0"`／空字符串；后三者在本地按 `0` 处理，不显示提醒区域。这是 App 的兼容规则，不能把 `0` 写成厂商定义的第六种分类。解析器拒绝布尔值及其他不认识的分类值；SDK 本身返回 JSON 文本，不替客户端做这项归一化。

#### 23.5.3 身体状态请求参数

**身体状态：**`AiBodyStatusRequest(deviceId, inputJson)` 的外层必须是只包含一个当天对象的数组，即 `[{...}]`；不要像洞察一样直接传对象。

构造参数 `deviceId`、`inputJson` 均需非空，前者用于就绪身份检查，后者是数组的 JSON 字符串。SDK 检查数组仅含一个对象，但不会自动将对象包装成数组，也不会替 App 校验每个业务字段的单位或小时数。

当前 Demo 当天对象字段为 `date`（`yyyy/M/d`）、`stepGoal`、`steps`、`activityDuration_min`、`calorieGoal_kcal`、`restingheartRate_bpm`、`heartRate_bpm`、`heartratevariability(hrv)`、`stress`、`sleep`、`sleepQuality`、`lightSleep_min`、`deepSleep_min`。`sleep`／睡眠阶段使用分钟；`calorieGoal_kcal` 是实际消耗，不是步数或卡路里目标。

当前 Demo 的 `restingheartRate_bpm`、`heartRate_bpm`、`heartratevariability(hrv)`、`stress` 改为当天平均值，每项只传一个字符串值，分别使用固定模拟值 `"58"`、`"72"`、`"45"`、`"30"`。不再组装 24 个小时槽位、拼接逗号序列或为未来小时补零；这些日均值不是设备真实统计结果，也不是对含补零槽位的旧序列求平均。以下说明的是当前 Demo 的模拟入参口径，不改变 HaWoFit 的生产请求或厂商其他产品的字段约定。

以下按当前 Demo 的模拟入参说明，业务字段在 JSON 中也均为字符串：

| 字段 | 数量 / 单位 | 含义及顺序 |
|---|---|---|
| `date` | 一个当天日期 | 当前 Demo 使用 `yyyy/M/d`，如 `2026/10/14`；不是七天范围 |
| `stepGoal` | 一个值，步 | 每日目标步数 |
| `steps` | 一个值，步 | 当天实际步数 |
| `activityDuration_min` | 一个值，分钟 | 当天实际活动时长 |
| `calorieGoal_kcal` | 一个值，千卡 | 当天实际活动消耗，不是目标值 |
| `restingheartRate_bpm` | 一个当天平均值，次/分钟 | 模拟静息心率日均值，示例 `"58"` |
| `heartRate_bpm` | 一个当天平均值，次/分钟 | 模拟普通心率日均值，示例 `"72"` |
| `heartratevariability(hrv)` | 一个当天平均值，毫秒 | 模拟 HRV 日均值，示例 `"45"` |
| `stress` | 一个当天平均值，无单位评分 | 模拟压力日均值，示例 `"30"` |
| `sleep` | 每段一个值，分钟 | 当天纳入分析的睡眠时长，可含多段；不是 24 个小时均值 |
| `sleepQuality` | 一个评分 | 当天睡眠质量评分；不是每段一个评分 |
| `lightSleep_min` | 每段一个值，分钟 | 与 `sleep` 按相同睡眠段排列 |
| `deepSleep_min` | 每段一个值，分钟 | 与 `sleep` 按相同睡眠段排列 |

上述四项在当前 Demo 中均为单值，不设置小时缺失位置。当天没有纳入分析的睡眠时，`sleep`、`lightSleep_min`、`deepSleep_min` 使用字符串 `"0"`；睡眠字段的口径不因四项日均值调整而改变。不要将分钟转换成文字“8小时”后上传。SDK 不负责查询或统计这些真实数据。

以下是当天平均值各传一个值的模拟输入；日期仅为结构示例，Demo 实际使用请求当天日期：

```json
[{
  "date": "2026/10/14",
  "stepGoal": "10000",
  "steps": "8000",
  "activityDuration_min": "40",
  "calorieGoal_kcal": "350",
  "restingheartRate_bpm": "58",
  "heartRate_bpm": "72",
  "heartratevariability(hrv)": "45",
  "stress": "30",
  "sleep": "480",
  "sleepQuality": "85",
  "lightSleep_min": "300",
  "deepSleep_min": "120"
}]
```

```kotlin
val bodyOperation = AiCenter.getInstance().generateBodyStatusReport(
    context.applicationContext,
    com.huawo.ai.handler.model.AiBodyStatusRequest(deviceId, bodyArrayJson),
    object : com.huawo.ai.handler.impl.AFlashBodyStatusReportHandler.Callback {
        override fun onSuccess(json: String) { /* 展示真实返回 */ }
        override fun onFailure(code: Int, message: String?) { /* 保留原始错误 */ }
    },
)
// 只取消本次请求：bodyOperation.cancel()。
```

#### 23.5.4 身体状态返回字段与评分

厂商原始示例使用大写开头的 `Rating`、`Level`，而 `title`、`content` 为小写；SDK 原样返回 JSON 文本，不重命名字段。结构示例：

```json
{
  "Rating": 72,
  "title": "身体状态示例",
  "Level": "好",
  "content": "示例身体状态分析正文，由实际服务返回替换。"
}
```

| 原始返回字段 | 类型 | 含义 / 当前 App 校验 |
|---|---|---|
| `Rating` | JSON 数字，值为整数 | 身体状态评分，HaWoFit 校验可精确转换为 `0..100` 的整数；字符串 `"72"` 或带非零小数部分的 `72.5` 不通过 |
| `title` | `String` | 报告标题，要求非空 |
| `Level` | `String` | 对应评分的评级文字，要求非空 |
| `content` | `String` | 分析正文，要求非空 |

HaWoFit 的 `reportField()` 兼容字段大小写，优先匹配其小写键，再进行忽略大小写查找，因此 `Rating`／`rating`、`Level`／`level` 均能被现有 App 消费；随后保存到本地模型的字段为 `rating`／`level`。该兼容逻辑属于 App，不是 SDK 已把所有回包统一成小写。

厂商给出的评级对应关系为：`90..100` 优秀、`80..89` 良好、`70..79` 好、`60..69` 一般、`0..59` 较差。`Rating=0` 在完整报告对象中仍是合法最低分，不应仅因分数是零就把整个报告视为缺失。

厂商同时给出输入指标的参考分级：压力 `0..29` 放松、`30..59` 正常、`60..79` 中等、`80..100` 偏高；睡眠质量 `>=90` 良好、低于 `90` 可提升。这些是该 AI 服务的分级口径，不是 BLE 指令应答码，也不要求客户端根据它们自行生成服务端的分析正文。

### 23.6 AI 睡眠（日／周／月）

日、周、月都使用 `AiSleepRequest` 和 `generateSleepReport()`，通过 `dimension`（`DAY`／`WEEK`／`MONTH`）选择对应智能体，不是先生成日报再由 App 拼成周报或月报。

#### 23.6.1 SDK 参数与实际上传字段

完整构造函数为 `AiSleepRequest(String deviceId, String dimension, String date, String sleep_h, String getUp_h, boolean is24Hour)`。

| SDK 参数 | 类型 | 用途 / 范围 | 是否进入业务 JSON |
|---|---|---|---|
| `deviceId` | `String` | 非空，必须与 OmniAgent 已初始化身份一致 | 否，用于 SDK 身份检查 |
| `dimension` | `String` | `DAY`／`WEEK`／`MONTH`；区分日／周／月智能体 | 否，用于选择智能体 |
| `is24Hour` | `boolean` | `true` 使用 24 小时制智能体，`false` 使用 12 小时制智能体 | 否，用于选择智能体 |
| `date` | `String` | 日日期或倒序日期范围，不能为空 | 是 |
| `sleep_h` | `String` | 入睡时刻或按日期排列的多日时刻字符串，不能为空 | 是 |
| `getUp_h` | `String` | 起床时刻或对应多日时刻字符串，不能为空 | 是 |

四参数简化构造函数默认 `dimension=DAY`、`is24Hour=true`；五参数构造函数包含 `dimension`，默认 `is24Hour=true`。如需 12 小时制，使用六参数构造明确传 `false`，不能只在时间文字后加 AM／PM 而仍选择 24 小时制智能体。

SDK 实际发送给 Agent 的 `data` 是 UTF-8 JSON 数组文本，每次只含一个周期对象：`[{"date":"...","sleep_h":"...","getUp_h":"..."}]`。不要把完整 Java 请求模型再序列化一次发给厂商；`dimension`、`deviceId`、`is24Hour`、App 的 `hourFormat`／时区／数据库键均不是这个业务对象的字段。

- `deviceId` 必须与 OmniAgent 已初始化身份一致。
- 日日期为 `yyyy/MM/dd`；周／月为“最新日期-最早日期”，数据槽位同样按日期倒序。
- `sleep_h` 与 `getUp_h` 是位置及数量一一对应的逗号分隔字符串，缺失日填 `0`。
- `is24Hour=true` 对应 `HH:mm:ss`；`false` 对应 `hh:mm:ss AM/PM`。这是生成时冻结的格式及智能体选择参数，不在 App 显示阶段重解释服务器返回的时间字符串。
- 当前 Demo 固定使用 24 小时制、日 1 个／周 7 个／月 30 个槽位，入睡 `23:00:00`、起床 `07:00:00`。月是最近 30 天的演示范围，不宣称是自然月或 SDK 自动补齐天数。

厂商文档定义月报告使用自然月数据，例如 `2026/09/30-2026/09/01`；自然月应按实际日期数量构造槽位，不能把固定 30 项套到所有月份。当前 Demo 的滚动 30 天只是模拟入参示例，本次没有修改这段业务代码。SDK 只检查两个时间序列的数量相同，不检查它们是否覆盖 `date` 的每一天，也不替 App 决定自然周、自然月或跨夜归属。

#### 23.6.2 12／24 小时制输入格式

| 字段 | 24 小时制 | 12 小时制 | 单位及说明 |
|---|---|---|---|
| `date` | `2026/10/14` 或 `2026/10/14-2026/10/08` | 同 24 小时制 | 日期；时间制不改变日期格式和倒序规则 |
| `sleep_h` | `23:00:00` | `11:00:00 PM` | 入睡时刻，多日用英文逗号分隔 |
| `getUp_h` | `07:00:00` | `07:00:00 AM` | 起床时刻，位置与入睡序列一一对应 |

Java／Kotlin 时间格式使用：日期 `yyyy/MM/dd`，24 小时制 `HH:mm:ss`，12 小时制 `hh:mm:ss a`（配合 `Locale.US` 固定英文 AM／PM）。厂商表格中的 `mm` 为文字格式说明；在 `SimpleDateFormat` 中月份必须大写 `MM`，分钟才使用小写 `mm`。

24 小时制日输入示例：

```json
[{
  "date": "2026/10/14",
  "sleep_h": "23:00:00",
  "getUp_h": "07:00:00"
}]
```

相同睡眠的 12 小时制日输入示例（SDK 构造时还需 `is24Hour=false`）：

```json
[{
  "date": "2026/10/14",
  "sleep_h": "11:00:00 PM",
  "getUp_h": "07:00:00 AM"
}]
```

周输入保留七个日期槽位，缺失时入睡和起床两个序列的同一位置都填 `0`，不缩短序列。起床时刻早于入睡时刻可以表示跨午夜，不能简单认为数据顺序错误；具体日期归属由 App 在调用前整理。

```kotlin
import com.huawo.ai.handler.model.AiSleepRequest

// 同一次生成冻结日期基准，避免跨零点导致范围与槽位错位。
val today = java.util.Calendar.getInstance()
val format = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
fun sampleSleep(dimension: String, days: Int): AiSleepRequest {
    val oldest = (today.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
    }
    val date = if (days == 1) format.format(today.time)
        else "${format.format(today.time)}-${format.format(oldest.time)}"
    return AiSleepRequest(deviceId, dimension, date,
        List(days) { "23:00:00" }.joinToString(","),
        List(days) { "07:00:00" }.joinToString(","), true)
}
val dayRequest = sampleSleep("DAY", 1)
val weekRequest = sampleSleep("WEEK", 7)
val monthRequest = sampleSleep("MONTH", 30)

// 本次只提交所点击维度的一个请求；以下以周为例。
val sleepOperation = AiCenter.getInstance().generateSleepReport(
    context.applicationContext, weekRequest,
    object : com.huawo.ai.handler.impl.AFlashSleepReportHandler.Callback {
        override fun onSuccess(json: String) { /* 展示真实返回正文 */ }
        override fun onFailure(code: Int, message: String?) { /* 展示错误码及说明 */ }
    },
)
// 只取消本次请求：sleepOperation.cancel()。
```

#### 23.6.3 返回字段与时间展示

日、周、月报告使用相同的七个返回字段；不同维度改变分析周期，不改变 JSON 顶层结构。以下两份回包为自拟格式示例：

```json
{
  "adjustment_intro": "示例睡眠调整建议文本。",
  "best_time": "23:00:00~24:00:00",
  "best_time_intro": "示例最佳就寝区间说明。",
  "chronotype": "3",
  "chronotype_intro": "示例睡眠时型说明。",
  "sleepPlan_start": "23:00:00",
  "sleepPlan_end": "07:00:00"
}
```

```json
{
  "adjustment_intro": "示例睡眠调整建议文本。",
  "best_time": "11:00:00 PM~12:00:00 AM",
  "best_time_intro": "示例最佳就寝区间说明。",
  "chronotype": "3",
  "chronotype_intro": "示例睡眠时型说明。",
  "sleepPlan_start": "11:00:00 PM",
  "sleepPlan_end": "07:00:00 AM"
}
```

| 返回字段 | 类型 | 含义 / 处理规则 |
|---|---|---|
| `adjustment_intro` | `String` | 睡眠调整建议正文，非时间数值 |
| `best_time` | `String` | 最佳就寝时间区间，以 `~` 分隔两个时刻；不是日期范围或时长 |
| `best_time_intro` | `String` | 最佳就寝区间的说明文字，可能包含时间文字，按原文展示 |
| `chronotype` | 编号，厂商 JSON 示例为 `String` | 六种睡眠时型的编号 `"1".."6"`；当前 HaWoFit 也接受 JSON 整数 `1..6` |
| `chronotype_intro` | `String` | 对应时型的说明正文 |
| `sleepPlan_start` | `String` | 建议睡眠计划的开始时刻，格式与生成时选择的时间制对应 |
| `sleepPlan_end` | `String` | 建议睡眠计划的结束时刻，允许结束早于开始以表示跨夜 |

SDK 回调返回原始文本，未转换成 App 的 `AiSleepResult`。HaWoFit 在自己的 Repository 中检查七个字段非空、时间区间有效；对时间制已明确为 12／24 的新请求，还要求 `chronotype` 为 `1..6`。当前 Demo 仅展示 SDK 正文，不能假定 Demo 已执行同等业务校验。`"Type 3"` 不符合规范编号，当前解析器不会把它自动归一化成 `"3"`；旧快照 `hourFormat=0` 的兼容处理不能作为新报告字段规范。

显示和保存保留生成时返回的时间原文，手机后来切换 12／24 小时制也不重新格式化旧报告。绘图可以单独解析时间，但不修改正文中的时间。HaWoFit 的时间解析器兼容无秒的旧时刻、`24:00:00` 和厂商部分示例的 `00:xx:xx AM/PM`；`24:00:00` 作为区间终点按午夜处理，不能当成第 25 个小时。`best_time` 必须分成两个有效且不同的时刻，睡眠计划也不能开始与结束相同。

#### 23.6.4 chronotype 编号

厂商按主要入睡时刻给出如下编号；客户端消费服务返回的编号，不在本地用新的分类算法替换报告。

| 编号 | 时型名称 | 厂商定义的 24 小时制主要入睡区间 |
|---|---|---|
| `1` | 日出型 | `12:00:00 <= t < 22:00:00` |
| `2` | 早晨型 | `22:00:00 <= t < 23:00:00` |
| `3` | 晚晨型 | `23:00:00 <= t < 24:00:00` |
| `4` | 中间型 | `00:00:00 <= t < 01:00:00` |
| `5` | 夜间型 | `01:00:00 <= t < 02:00:00` |
| `6` | 深夜型 | `02:00:00 <= t < 12:00:00` |

### 23.7 错误、取消与 Demo 边界

SDK 回调线程不能默认当成 UI 线程。页面对初始化和生成分别管理任务，更新 UI 前检查当前会话／配置；退出时只取消自身操作并忽略迟到结果，不调用全局 `cancelAll()`／`destroy()` 来清理本页。

SDK 的洞察／身体状态／睡眠 Handler 等待上限当前为 90 秒，运动健康分析 Handler 为 70 秒；Demo 另有初始化 30 秒和一次生成流程 120 秒上限，两层超时不能混为协议常量。鉴权失败、未初始化、设备身份变化、空输入、空返回和网络异常都要展示可定位信息，不能伪造成功报告。`OMNI_AGENT_NOT_INITIALIZED` 表示没有匹配的初始化状态，不表示 BLE 未连接。

厂商对睡眠、运动洞察、身体状态说明了每天一次生成、同一天再次点击返回第一次报告的规则。Demo 没有另加本地次数拦截或缓存，不意味着多次提交或修改输入一定会重新生成；具体计数及缓存口径以服务配置为准，不能把同日重复返回视为 SDK 忽略了新字段。

厂商文档有“无数据时返回 0”的说明。请求中的缺失槽位 `"0"`、完整身体状态对象中的 `Rating=0`、兼容洞察分类 `remind=0` 和整份返回只有 `0` 是不同情况。如果实际收到 `0`／`"0"`，它不符合上述报告对象结构，App 应显示无有效报告或解析失败，不能为它补造标题、正文或正常等级。SDK 的 `onSuccess(String)` 只说明已取得文本，业务对象仍需 App 校验。

三个 OmniAgent Handler 的 `cancel()` 会清理本次回调，不再交付业务结果。运动健康 Handler 在尚未完成时取消，会尝试以 `ErrorCode.CANCELED` 通知本次 `onResult` 后清理；Demo 已取消的协程会忽略这次通知。成功、失败和取消都不能被重复处理或覆盖已经切换的页面。

当前 Demo 展示模拟入参与实际 SDK 结果，原始错误码／消息保留；不实现生产 App 的每日生成次数限制、数据库缓存、云端历史记录、自动补齐真实健康数据或医疗判断。本文示例是 SDK 调用说明，不把代码编译成功等同于真实渠道鉴权、设备能力或报告生成已验证。

---

## 附录 A：主要包路径

| 内容 | 包名 |
|------|------|
| 入口 | `com.huawo.sdk.bluetoothsdk.BluetoothSDK` |
| 扫描设备模型 | `com.huawo.sdk.bluetoothsdk.core.model.Device` |
| 业务模型 | `com.huawo.sdk.bluetoothsdk.interfaces.ops.models.*` |
| 业务回调 | `com.huawo.sdk.bluetoothsdk.interfaces.callback.*` |
| WL 历史数据类型 | `com.huawo.sdk.bluetoothsdk.wl.models.ActivityDataType` |
| 手动测量类型 | `com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DataType` |
| 提醒事件类型 | `com.huawo.sdk.bluetoothsdk.interfaces.event.ReminderNotifyType` |
| AI 扩展入口 | `com.huawo.ai.AiCenter`（需独立 AI AAR） |
| AI 请求与 Handler | `com.huawo.ai.handler.model.*` / `com.huawo.ai.handler.impl.*` |
| OmniAgent 初始化 | `com.adf.oa.OTek`、`com.adf.oa.contracts.init.DeviceConfig` / `InitConfig` |
| 连接回调 | `com.huawo.sdk.bluetoothsdk.callback.*` |
| 错误码 | `com.huawo.sdk.bluetoothsdk.error.ErrorCode` |
| OTA | `com.huawo.sdk.bluetoothsdk.interfaces.ota.*`（`OtaData` / `OtaDataType` / `OtaCallback`） |
| WL OTA | `com.huawo.sdk.bluetoothsdk.wl.ota.WlOtaCallback` / `WlOtaManager` |
| 升级状态 | `com.huawo.sdk.bluetoothsdk.interfaces.ops.models.UpgradeStatus` |
| SPP 文件传输 | `com.huawo.sdk.bluetoothsdk.spp.SppFilesTransferTask` |
| WL 媒体传输 | `com.huawo.sdk.bluetoothsdk.wl.media.*` |
| 思澈 ZIP 推送 | `com.huawo.watchface.SifliWatchSDK`（需额外 AAR） |
| 思澈固件 DFU | `com.sifli.siflidfu.SifliDFUService`（需额外 SifliDFU 依赖） |

---

## 附录 B：注意事项

1. **所有业务指令在连接成功且（多数产品）绑定完成后调用**；断连时会返回 `ErrorCode.DISCONNECTED`。
2. **Callback 勿做重耗时工作**；耗时逻辑请自行切线程。
3. **maxMTU 以产品为准**，错误 MTU 可能导致传输异常。
4. **健康/运动数据同步后建议 `del*`**，避免设备端堆积与重复同步。
5. **OTA / 推表盘 / 推音乐过程中**避免并发其它耗时指令；错误码含 `OTA_WORKING`、`PUSHING_WATCHFACE`、`PUSHING_MUSIC` 等。退出 WL OTA 页时调用 `WlOtaManager.forceReset()`。
6. AI、WhatsApp、对讲机等高阶能力接入前向厂商确认。本文无屏 AI 分析见 §23：使用独立 AI AAR、云端鉴权和 App 提供的输入，不因 BLE 已连接就视为 AI 已初始化；其他设备端 AI 功能仍需相应固件支持。
7. **音乐推送**按产品选择 SPP / WL / 思澈三通道；仅思澈 ZIP 需要额外 `qjs-watchface`（及 sifli）依赖。
8. **固件 OTA** 按产品选择通用 `ota` / WL `starWlOta` / 思澈 `SifliDFUService`；仅思澈固件升级需要额外 SifliDFU 依赖。升级前检查电量与 `getDeviceUpgradeStatus`。
