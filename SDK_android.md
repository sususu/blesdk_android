# BluetoothSDK 使用文档

**版本**：2.5.4.126  
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

> 账号为厂商只读账号；坐标与版本号以交付清单为准。以下版本为现网常用示例。

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
      BluetoothSDK-2.5.4.126.aar
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
    implementation files('libs/BluetoothSDK-2.5.4.126.aar')

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
    implementation(files("libs/BluetoothSDK-2.5.4.126.aar"))
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

> **音乐文件推送**见 [§14](#14-音乐文件推送)；**固件 OTA** 见 [§17](#17-ota-固件升级)。仅做 WL / 瑞昱通道时，只集成 `BluetoothSDK` 即可。

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
String ver = BluetoothSDK.getVersion(); // 如 "2.5.4.126"
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
| `ActivityDataCallback` | `onSports` / `onSleeps` / `onHeartrates` / `onHrvs` / `onPais` |
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

按需指定类型（枚举在 `com.huawo.sdk.bluetoothsdk.wl.models.ActivityDataType`）：

| ActivityDataType | 含义 | 回调落点 |
|------------------|------|----------|
| `STEP` | 步数活动 | `onSports(List<Sport>)` |
| `SLEEP` | 睡眠 | `onSleeps(List<Sleep>)` |
| `HEART_RATE` | 心率 | `onHeartrates(List<Heartrate>)` |
| `STRESS` | 压力 | 进 `onHrvs`（`Hrv.stress`） |
| `BLOOD_OXYGEN` | 血氧 | 进 `onHrvs`（`Hrv.spo2`） |
| `WORKOUTS` | 运动记录 | 一般用 `getWorkoutV2`，不建议指望本批量接口 |
| `AIRECORD` | AI 录音 | 另有专用接口 |

**压力 + 血氧同时请求时**：SDK 会按 `time` 合并为 `Hrv` 再 `onHrvs`；只请求其中一种时，也会包装成 `Hrv` 列表（另一字段为默认 0）。

```java
ActivityDataType[] types = new ActivityDataType[]{
        ActivityDataType.STEP,
        ActivityDataType.SLEEP,
        ActivityDataType.HEART_RATE,
        ActivityDataType.STRESS,
        ActivityDataType.BLOOD_OXYGEN
};
BluetoothSDK.getActivityDataV2(types, new ActivityDataCallback() {
    @Override public void onSports(List<Sport> sportList) { }
    @Override public void onSleeps(List<Sleep> sleepList) { }
    @Override public void onHeartrates(List<Heartrate> list) { }
    @Override public void onHrvs(List<Hrv> hrvList) { /* 血氧+压力 */ }
    @Override public void onPais(List<PAI> paiList) { /* V2 批量通常无 PAI，可忽略 */ }
    @Override public void onFail(int code) { }
});
```

```java
if (BluetoothSDK.isWlProtocol()) {
    // 优先 getActivityDataV2
} else {
    BluetoothSDK.getActivityData(...);
}
```

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

```java
for (Heartrate hr : heartrateList) {
    int bpm = hr.getBpm();
    long at = hr.getTime();
}
```

---

#### 实体说明：`Hrv`（压力 / 血氧 / 疲劳等）

**含义**：老协议里常用一张结构承载压力、血氧、疲劳；类注释为 *stress/spo2 health data*。WL V2 在同时拉 `STRESS`+`BLOOD_OXYGEN` 时，也会合并进该结构再回调 `onHrvs`。  
**回调**：`onHrvs`  
**删除**：老协议 `delHrv`；WL 侧血氧/压力也可能对应 `delBlood` / 压力删除接口，以产品协议为准。

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

**含义**：Personal Activity Intelligence（个人活动智能指数）日/段统计。批量接口主要在**老协议** `getActivityData` 的 `onPais` 中出现；WL 批量 V2 通常不走 PAI，需产品确认是否另有接口。  
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

1. 先 `isWlProtocol()`，再选 `getActivityData` 或 `getActivityDataV2`。  
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

// WL V2
BluetoothSDK.getSpo2V2(spo2Callback);
BluetoothSDK.getStressV2(stressCallback);
BluetoothSDK.getHeartRateV2(heartratesCallback);
BluetoothSDK.getSleepV2(sleepsCallback);
BluetoothSDK.getStepV2(sportsCallback);
```

### 9.3 监测开关与告警

```java
BluetoothSDK.setHeartrateMonitorInterval(10, boolCallback); // 分钟等，以产品定义为准
BluetoothSDK.setHeartrateAlarm(/*enable*/ true, /*low*/ 50, /*high*/ 180, boolCallback);

BluetoothSDK.setSpO2MonitorEnable(true, boolCallback);
BluetoothSDK.setStressMonitorEnable(true, boolCallback);
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

if (BluetoothSDK.isWlProtocol()) {
    BluetoothSDK.addAlarmV2(alarm, boolCallback);
} else {
    BluetoothSDK.addAlarm(alarm, boolCallback); // SDK 内会自动分配 id
}

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
BluetoothSDK.editReminder(reminder, boolCallback);
BluetoothSDK.delReminderBy(id, boolCallback);

BluetoothSDK.getQuickReplies(quickRepliesCallback);
BluetoothSDK.editQuickReply(quickReply, boolCallback);
```

### 11.4 手机日程同步

```java
List<PhoneScheduleEvent> events = ...;
BluetoothSDK.syncPhoneSchedules(events, boolCallback);
```

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

| 通道 | API | 是否在 BluetoothSDK AAR 内 | 前置条件 |
|------|-----|---------------------------|----------|
| **A. SPP（经典蓝牙）** | `SppFilesTransferTask.sendMusicFiles` | 是 | 产品支持 SPP；经典蓝牙已配对且已连接 |
| **B. WL BLE** | `WlMediaTransferManager.transferMusic` | 是 | `isWlProtocol() == true`（通常 `protocolVersion >= 100`） |
| **C. 思澈 BLE ZIP** | `SifliWatchSDK.syncZipFile(..., type=4, ...)` | **否，需额外 AAR** | 思澈/QJS 机型；BLE 已连接 |

推荐决策（与现网一致）：

```text
设备已绑定且 BLE 已连接
        │
        ├─ 产品 hasSPP=true 且经典 BT 已连接 ──► 方案 A：SPP
        │         └─ 失败且 isWlProtocol() ──► 方案 B：WL BLE
        │         └─ 失败且非 WL ───────────► 方案 C：思澈 ZIP
        │
        ├─ 无 SPP / BT 未连，且 isWlProtocol() ──► 方案 B：WL BLE
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
  BluetoothSDK-2.5.4.126.aar
  qjs-watchface-15.0.16.aar      # 产出自 watchface 模块，含 SifliWatchSDK
  sifliezipsdk-2.3.9.aar
  siflicore-1.2.7.aar
  sifliwatchfacesdk-2.1.6.aar
```

```groovy
implementation files('libs/BluetoothSDK-2.5.4.126.aar')
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

建议在 `Application` 初始化后注册，在 `destroy` 时成对移除。常用监听：

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
| 无后缀 | 通用 / 老协议（固件 OTA 走 `BluetoothSDK.ota`） |
| 产品配置 `hasSPP` | 是否走经典蓝牙 SPP 推送音乐/相册/离线地图 |

不确定时以产品对接文档或 `getDeviceFeatures` 能力位为准。

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

## 附录 A：主要包路径

| 内容 | 包名 |
|------|------|
| 入口 | `com.huawo.sdk.bluetoothsdk.BluetoothSDK` |
| 扫描设备模型 | `com.huawo.sdk.bluetoothsdk.core.model.Device` |
| 业务模型 | `com.huawo.sdk.bluetoothsdk.interfaces.ops.models.*` |
| 业务回调 | `com.huawo.sdk.bluetoothsdk.interfaces.callback.*` |
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
6. AI、WhatsApp、对讲机等高阶能力需对应固件支持，接入前向厂商确认；AI 能力若另附独立 AAR，按其独立说明集成。
7. **音乐推送**按产品选择 SPP / WL / 思澈三通道；仅思澈 ZIP 需要额外 `qjs-watchface`（及 sifli）依赖。
8. **固件 OTA** 按产品选择通用 `ota` / WL `starWlOta` / 思澈 `SifliDFUService`；仅思澈固件升级需要额外 SifliDFU 依赖。升级前检查电量与 `getDeviceUpgradeStatus`。