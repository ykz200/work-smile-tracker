# 上班表情追踪器 · WorkSmileTracker

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blueviolet)
![Compose Multiplatform](https://img.shields.io/badge/Compose-1.7.3-brightgreen)
![Android](https://img.shields.io/badge/Android-26%2B-3DDC84)
![iOS](https://img.shields.io/badge/iOS-16.0+-000000)
![MediaPipe](https://img.shields.io/badge/MediaPipe-tasks--vision-orange)
![License](https://img.shields.io/badge/License-MIT-green)

**打工人桌面实时表情追踪 · 数据可视化 · 跨平台复用一套代码**

用手机前置摄像头以每分钟一次的采样频率，记录上班时的表情状态（微笑概率、眼部睁开程度、头部姿态），生成每日/每周统计报表。

> 目前为 **skeleton** 状态 — gradle 配置 + 共享层逻辑完整，Android Camera+MediaPipe 链路需要开发者按实际环境接入。iOS 端为占位。

---

## 为什么是 KMP + Compose Multiplatform

打工人时间紧，需要一个"学一次代码两端共用"的方案。Kotlin Multiplatform + Compose Multiplatform 可以做到：

- **Business 逻辑** (表情分析、数据库、定时器) 一套代码，iOS / Android 双端共享，包名统一 `com.ykz200.smiletracker.domain`
- **UI** (Dashboard 仪表盘、折线图、实时状态) 一套 `@Composable` 函数，iOS / Android 双端共享
- **SQLite 数据库** SQLDelight 自动生成双端驱动，schema 一个文件定义
- **后台任务** 各端用各平台 SDK (WorkManager / BGTaskScheduler) 实现，通过 expect/actual 注入

Java 开发者上手 Kotlin 几乎零门槛 — 两天够融汇贯通。

---

## 项目结构

```
work-smile-tracker/
├─ gradle/
│  └─ libs.versions.toml                            # Gradle Version Catalog (统一版本)
├─ shared/                                          # KMP 共享模块 (业务 + UI + 数据)
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ commonMain/                                # 🟢 双端共享层
│     │  ├─ kotlin/com/ykz200/smiletracker/
│     │  │  ├─ domain/Domain.kt                    #   数据类: FaceAnalysis, MinuteStat 等
│     │  │  ├─ data/SmileRepository.kt             #   共享数据库操作 + RepositoryProvider
│     │  │  └─ ui/Dashboard.kt                     #   Compose 共享 Dashboard UI
│     │  └─ sqldelight/...db/SmileDatabase.sq      #   SQLDelight Schema (双端共享)
│     ├─ androidMain/                               # 🟠 Android 特定层
│     │  └─ kotlin/.../Platform.kt
│     └─ iosMain/                                  # 🔵 iOS 特定层 (skeleton)
│        └─ kotlin/.../Platform.kt
├─ androidApp/                                     # Android 应用入口
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ kotlin/com/ykz200/smiletracker/
│     │  ├─ MainActivity.kt                       # 主页 + WorkManager 调度
│     │  ├─ CameraAnalyzer.kt                     # CameraX + MediaPipe 端到端实现
│     │  └─ SmileyAnalysisService.kt              # 前台 Service 保活
│     ├─ AndroidManifest.xml
│     └─ res/values/strings.xml
├─ iosApp/                                        # iOS XCode 容器 (skeleton)
│  └─ iosApp/
├─ build.gradle.kts
├─ gradle.properties
├─ settings.gradle.kts
└─ README.md
```

---

## 数据流总览

```
                前置摄像头 (CameraX / AVFoundation)
                           │ (逐帧)
                  MediaPipe Face Detection
                           │ (smileProb, eyeProb, head Euler angles)
                     shared: domain.FaceAnalysis
                           │
                 ┌─────────┴──────────┐
                 │                    │
          shared: UI Layer      shared: data.Repository
      (Dashboard 实时更新)    (SQLite INSERT)
                 │                    │
                 └─────────┬──────────┘
                           │ (SQLDelight Flow)
                  Dashboard (折线图 + 数字卡片)
```

---

## 业务模型

### `FaceAnalysis` (每分钟采样一次，原始数据)

| 字段 | 类型 | 说明 |
|------|------|------|
| `tsMillis` | Long | 毫秒时间戳 |
| `smileProbability` | Float | 微笑概率 `[0..1]`，`-1` = 未检出人脸 |
| `leftEyeOpenProbability` | Float | 左眼睁开概率 |
| `rightEyeOpenProbability` | Float | 右眼睁开概率 |
| `headPitch / headYaw / headRoll` | Float | 头部三轴角度（度） |

### `MinuteStat` (Dashboard 显示用，每分钟聚合)

| 字段 | 说明 |
|------|------|
| `minuteKey` | `"2026-10-07T15:30:00"` 格式 |
| `sampleCount` | 该分钟内实际推理次数 |
| `avgSmile` | 平均微笑概率 |
| `maxSmile` | 峰值微笑概率 |
| `smileCount` | 微笑次数 (smileProb ≥ 0.7) |
| `smilePercent` | 微笑时长占比 `%` |
| `avgEyeLeft / avgEyeRight` | 眼部综合疲劳度 |

---

## 快速启动

### 环境要求

- **JDK 17+**
- **Android Studio Ladybug+** (2024.2+)
- **XCode 16+** (编译 iOS 时需要)
- **NDK** (KMP iOS 构建)
- **Git**

### Option 1: Android Studio 打开

> File → Open → 选择 `work-smile-tracker/` 文件夹

Gradle 自动下载依赖。**第一版可能耗时 20 分钟+**（首次下载 Compose Multiplatform、MediaPipe AAR、SQLDelight）。

### Option 2: 命令行

```bash
cd work-smile-tracker
./gradlew :androidApp:assembleDebug    # 构建 debug APK

# 安装到 Pixel 3a (已开 USB 调试后)
./gradlew :androidApp:installDebug
```

### 安装后验证

打开 App → 授权摄像头权限 → 进入 Dashboard：

- 显示实时微笑概率（每秒更新）
- 每分钟 1 条数据写入 SQLite
- 顶部卡片显示今日统计数字
- 中下方折线图展示全天每分钟微笑占比趋势

---

## Android CameraX + MediaPipe 集成要点

### Step 1: 下载 tflite 模型

从 [Google Face Detection Models](https://ai.google.dev/edge/mediapipe/solutions/vision) 下载：

- `face_detection_short_range.tflite` 放在 `androidApp/src/main/assets/`

### Step 2: build.gradle 依赖已就绪

```kotlin
// shared/build.gradle.kts
implementation(libs.mediapipe.tasks.vision)
implementation(libs.camerax.core)
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
```

### Step 3: CameraX bindToLifecycle 调用

在 `MainActivity` 中预览 SurfaceProvider 交给 CameraAnalyzer，按 [CameraX official docs](https://developer.android.com/media/camera/camerax) 完成相机生命周期绑定。

### Step 4: Permission Launcher

使用 `rememberLauncherForActivityResult` 请求 `Manifest.permission.CAMERA`，授权后再起 `Analysis UseCase`。

---

## iOS 端集成 - 待做 (skeleton)

iOS 端由于 Apple 限制，KMP 共享逻辑可以直接复用，Camera + MediaPipe 需要在 XCode 工程侧额外注入。

### 方案 A：通过 CocoaPods

1. 在 `iosApp/Podfile` 添加：
   ```ruby
   pod 'MediaPipeTasksVision', '~> 0.10.0'
   ```
2. 在 `iosApp/...ViewController` 或 SwiftUI 入口中，调用 `IosCameraAnalyzer().start()`（已实现 `actual` 后）
3. BGTaskScheduler 每 15 分钟触发后台任务时，需 `AppDelegate` 注册 background mode

### 方案 B：通过 Swift Package Manager

```swift
// Package.swift
.package(url: "https://github.com/google/mediapipe-tasks-vision", from: "0.10.0")
```

### iOS 隐私声明 (iOS 14.5+)

Info.plist 需要加：

```xml
<key>NSCameraUsageDescription</key>
<string>上班时记录表情统计数据</string>
```

---

## 开发者提示

### 启用 SQLDelight 自动生成代码

```bash
./gradlew :shared:generateSmileDatabaseInterface
```

会生成 `SmileDatabase.kt` + `SmileDatabaseQueries.kt`，放在 `shared/build/generated/sqldelight/`。

### 查看数据库 (Android)

使用 Android Studio 的 **App Inspection → Database Inspector** 实时看到数据写入。

### 数据导出为 CSV (接 KMP 共享代码)

导出功能预留接口，可在 `SmileRepository.kt` 增加：

```kotlin
suspend fun exportCsv(startMillis: Long, endMillis: Long): String
```

用 Ktor 把 CSV 文件通过 HTTP POST 推送到 GitHub Gist 或你自己的服务器（GitHub Actions 每小时拉一次做趋势分析）。

---

## Roadmap

- [x] Gradle 工程 + Version Catalog
- [ ] CameraX + MediaPipe 端到端打透
- [ ] 工作日 (Mon-Fri) 09:00-17:00 自动启停分析
- [ ] 每日/每周 CSV 报告 push GitHub
- [ ] 烧屏保护: 每 30 秒偏移像素 + 亮度呼吸
- [ ] iOS 端 AVFoundation + MediaPipe CocoaPods 接入
- [ ] 图表交互: 点击折线查看分钟级详情

---

## License

© 2026 ykz200. Released under the **MIT License**.
