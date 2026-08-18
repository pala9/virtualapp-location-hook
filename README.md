# VirtualApp 定位 Hook 骨架

基于 **VirtualApp 开源版**（asLody/VirtualApp，支持 Android 5-11，Android 9 完美兼容）的免 root 虚拟定位骨架工程。

**目标**：在一个虚拟沙箱内运行目标 App（如微信），并通过 Hook 定位 API 返回**无 mock 标记**的伪造位置，从而规避 `Location.isMock()` 检测。

---

## 一、为什么这个方案能绕过微信检测

| 对比 | 影梭（mock provider） | VirtualApp（沙箱 Hook） |
|---|---|---|
| 位置来源 | 系统 `addTestProvider` 注入 | 直接构造 `Location` 对象返回 |
| `isMock()` | **true**（系统标记） | **false**（不经过 test provider） |
| 微信检测 | ✅ 检测到 mock | ❌ 检测不到 |
| 多源定位 | 读真实 WiFi/基站 | 沙箱内可一并 Hook |
| 需要 root | 否 | 否 |

---

## 二、架构

```
┌───────────────────────────── 宿主 App (app 模块) ─────────────────────────────┐
│                                                                               │
│  VirtualCore.get().startup()         引擎启动（加载虚拟环境）                 │
│  VirtualCore.get().installPackageAsUser(...)   安装目标 App 到沙箱            │
│  VActivityManager.get().launchApp(...)         启动目标 App                   │
│                                                                               │
│  VirtualLocationSettings（虚拟定位设置界面）                                   │
│    └── VirtualLocationManager.get().setLocation(userId, pkg, VLocation)      │
└───────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────── lib 模块（Hook 核心，473 文件）──────────────────┐
│  com.lody.virtual.client.hook.proxies.location                                │
│    ├── LocationManagerStub.java     LocationManager 的 Binder 代理入口        │
│    ├── MethodProxies.java           拦截 getLastLocation/requestLocationUpdates│
│    ├── MockLocationHelper.java      伪造 GPS NMEA 数据（卫星信号）            │
│    ├── GPSListenerThread.java       持续向沙箱内 App 推送模拟位置             │
│    └── GPSStatusListenerThread.java 模拟 GPS 状态变化                          │
│                                                                               │
│  com.lody.virtual.client.ipc.VirtualLocationManager  虚拟定位管理中心          │
│  com.lody.virtual.remote.vloc.VLocation              位置数据模型              │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、定位 Hook 关键文件（lib 模块）

路径：`lib/src/main/java/com/lody/virtual/client/hook/proxies/location/`

| 文件 | 作用 |
|---|---|
| `LocationManagerStub.java` | Hook `LocationManager` 的 Binder 服务，替换目标 App 拿到的系统服务代理 |
| `MethodProxies.java` | 核心拦截逻辑：`getLastLocation`、`requestLocationUpdates`、`addGpsStatusListener` 等方法被替换，返回伪造位置 |
| `MockLocationHelper.java` | 构造 NMEA 语句（`$GPGGA/$GPRMC` 等），让 App 认为 GPS 卫星信号正常 |
| `GPSListenerThread.java` | 后台线程持续推送模拟位置到监听器 |
| `VirtualLocationManager.java` | 虚拟定位管理：`setMode/setLocation/getLocation` |

**虚拟定位 API**（在宿主 App 中调用）：

```java
// 设置目标 App 的虚拟定位模式（2 = 使用虚拟位置）
VirtualLocationManager.get().setMode(userId, packageName, 2);
// 设置虚拟位置坐标
VLocation location = new VLocation();
location.latitude = 39.908823;    // 北京
location.longitude = 116.397470;
VirtualLocationManager.get().setLocation(userId, packageName, location);
```

---

## 四、Android 9（API 28）编译指引

VirtualApp 开源版是 2017-2018 年的老工程（Gradle 4.1 + AGP 3.0.1 + compileSdk 26）。**编译它的关键在于工具链，而非代码**（运行时兼容 Android 9 无问题，支持到 Android 11）。

### 方案 A：老工具链（推荐，改动最小）

用原工程的工具链版本，兼容性最好：

| 组件 | 版本 |
|---|---|
| JDK | 8 |
| Android Studio | 3.x（支持 AGP 3.0.1） |
| compileSdk / buildTools | 26 / 26.0.2 |
| Gradle | 4.1（工程自带 wrapper） |
| NDK | 老版本（含 ndkBuild，如 r14b） |

> 工程含 native 代码（`lib/src/main/jni`），需要 NDK 环境。Android Studio 3.x 打开后按提示安装缺失的 SDK/NDK 组件即可。

### 方案 B：升级工具链（现代环境）

如果必须用新版 Android Studio（2023+），需要升级：

1. **根 `build.gradle`**：AGP 3.0.1 → 4.2.x 或 7.x；jcenter() 已停用，替换为 `mavenCentral()` + `google()`
2. **`gradle/wrapper/gradle-wrapper.properties`**：Gradle 4.1 → 对应 AGP 的版本
3. **lib `build.gradle`**：compileSdkVersion 26 → 28，`compile` → `implementation`，NDK 构建迁移到 `ndkVersion`
4. **老 API 兼容**：升级后可能遇到 `addProvider`/隐藏 API 反射等编译告警，按错误逐个适配

> ⚠️ 方案 B 工作量较大（老代码用了大量过时 API），**优先尝试方案 A**。

---

## 五、运行与使用

1. 编译安装宿主 App 到 Android 9 设备（无需 root）
2. 打开宿主 App → 点击右下角「+」→ 选择**微信** → 添加到沙箱
3. 长按微信图标 → **虚拟定位** → 打开开关 → 选点/输入坐标
4. 在沙箱内启动微信 → 微信定位读取到虚拟位置，且无 mock 标记

---

## 六、定制：对微信的增强建议

VirtualApp 开源版已内置虚拟定位，但针对微信还可增强：

1. **同步 Hook WiFi/基站**（可选）：在 `MethodProxies` 中增加对 `WifiManager`、`TelephonyManager` 的拦截，防止微信用多源定位读到真实位置
2. **随机偏移**：仿照影梭的 `applyRandomOffset`，在 `VirtualLocationManager.setLocation` 中做 ±100 米随机偏移
3. **位置连续性**：伪造 `Location` 时同步设置 `time`、`elapsedRealtimeNanos`、`speed`、`bearing`，避免"位置合理性质检"被识破

---

## 七、风险提示（重要）

1. **微信可能检测虚拟框架环境**：微信通过包名/进程名/ClassLoader/文件系统痕迹可识别"运行在多开沙箱"，可能功能受限或封号风险。自研骨架**没有持续对抗能力**，稳定性无法与商业多开软件相比
2. **许可**：VirtualApp 开源版需 **商业授权** 才可合法商用；个人自用/学习不受限制
3. **版本**：开源版仅支持 Android 5-11；Android 12+ 需用社区分支或商业版

---

## 八、参考链接

- VirtualApp 开源版：https://github.com/asLody/VirtualApp
- 本项目源码位置：`D:\work\agent\codebuddy\VirtualApp-LocationHook\`
