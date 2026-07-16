# 图片和音频转成视频 — 设计文档

## 概述

一个桌面 GUI 工具，接受若干相同编码的音频文件和一张图片，合成一个 MKV 视频。视频流为 H.265（stillimage profile），音频流保持原始编码不变。

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose Multiplatform (Desktop)
- **构建工具**：Gradle (Kotlin DSL)
- **异步**：kotlinx-coroutines
- **FFmpeg**：捆绑 FFmpeg 8.x 二进制，通过 ProcessBuilder 调用
- **目标平台**：macOS (aarch64/x86_64)、Windows (x86_64)

## 项目结构

```
图片和音频转成视频/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── src/main/kotlin/com/benderblog/videoaudio/
│   ├── Main.kt                    # 应用入口 + Window
│   ├── ui/
│   │   ├── App.kt                 # 根 Composable (Scaffold)
│   │   ├── AudioListPanel.kt      # 可拖拽排序的音频列表
│   │   ├── ImagePickerPanel.kt    # 图片选择
│   │   ├── ControlPanel.kt        # 开始按钮 + 进度条
│   │   └── LogPanel.kt            # 日志/错误输出面板
│   ├── viewmodel/
│   │   └── MainViewModel.kt       # 状态管理
│   ├── ffmpeg/
│   │   ├── FFmpegLocator.kt       # FFmpeg 二进制定位
│   │   ├── FFmpegRunner.kt        # 进程执行 + 进度解析
│   │   ├── AudioProbe.kt          # ffprobe 获取音频时长/编码信息
│   │   ├── AudioConcat.kt         # 阶段1: 音频无损拼接
│   │   └── VideoMuxer.kt          # 阶段2: 图片+音频→MKV
│   ├── model/
│   │   ├── AppState.kt            # 应用状态 data class
│   │   └── AudioItem.kt           # 音频文件数据类
│   └── util/
│       └── TempFileManager.kt     # 临时文件管理
├── src/main/resources/ffmpeg/
│   ├── macos-aarch64/ffmpeg
│   ├── macos-x86_64/ffmpeg
│   └── win-x86_64/ffmpeg.exe
```

## 状态模型

```kotlin
enum class Phase { Idle, Working, Error }

data class AppState(
    val phase: Phase = Phase.Idle,
    val progress: Float = 0f,           // 0.0 ~ 1.0
    val phaseLabel: String = "",         // 当前阶段描述
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val outputFile: File? = null,        // 完成后记录输出文件路径
)
```

状态语义：
- `Idle` + `outputFile == null`：初始状态，无产物
- `Idle` + `outputFile != null`：完成状态，显示"打开文件/打开文件夹"按钮
- `Working`：合成中，progress 和 phaseLabel 有意义
- `Error`：errorMessage 和 errorDetail 有意义，进度条飘红

## UI 设计

```
┌─────────────────────────────────────────────────┐
│  图片和音频转成视频                  [设置输出目录] │  ← TopAppBar
├─────────────────────────────────────────────────┤
│                                                 │
│  📎 音频文件列表（可拖拽排序）                    │
│  ┌───────────────────────────────────────────┐  │
│  │ 1. track01.flac  3:42   FLAC  [✕]       │  │
│  │ 2. track02.flac  4:15   FLAC  [✕]       │  │
│  │ 3. track03.flac  5:01   FLAC  [✕]       │  │
│  └───────────────────────────────────────────┘  │
│  [＋ 添加音频]                                   │
│                                                 │
│  🖼️ 图片：cover.jpg                      [选择] │
│                                                 │
│  [▶ 开始合成]                                    │
│  ■■■■■■■■■■□□□□□□  60%  合成视频中...            │
│                                                 │
│  📋 日志                                         │
│  ┌───────────────────────────────────────────┐  │
│  │ [12:01] 开始拼接音频...                   │  │
│  │ [12:01] 音频拼接完成 (12:58)              │  │
│  │ [12:02] 合成视频中...                     │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

交互：
- **Idle**：所有输入控件可用，进度条空
- **Working**：输入控件禁用，进度条显示百分比和阶段
- **Error**：进度条飘红，日志显示错误详情，提供"重试"和"清除"按钮
- **Idle + outputFile != null**：提供"打开文件"和"打开文件夹"按钮
- 输出目录未设置时点击"开始合成" → 弹出系统保存对话框

## FFmpeg 流程

### 阶段 1：音频无损拼接（进度 0%~30%）

1. 用 `ffprobe` 获取每个音频文件的时长和编码信息
2. 校验所有音频编码一致
3. 生成 concat 列表临时文件
4. 执行：
   ```
   ffmpeg -f concat -safe 0 -i list.txt -c copy -progress pipe:1 merged_audio.flac
   ```
5. 解析 `-progress` 输出的 `out_time_us`，除以总时长计算百分比

### 阶段 2：图片 + 音频 → MKV（进度 30%~100%）

1. 执行：
   ```
   ffmpeg -loop 1 -i cover.jpg -i merged_audio.flac \
     -c:v libx265 -preset fast -profile:v mainstillpicture \
     -c:a copy -shortest \
     -progress pipe:1 \
     -stats_period 0.5 \
     output.mkv
   ```
2. 同样解析 `out_time_us` 计算百分比

### FFmpegRunner 接口

```kotlin
class FFmpegRunner(private val ffmpegPath: Path) {
    suspend fun probeDuration(file: File): Double  // 秒
    suspend fun probeCodec(file: File): String      // 编码名称

    suspend fun execute(
        args: List<String>,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit
    ): Int  // 退出码
}
```

### 进度输出格式 (FFmpeg 8.x)

```
out_time_us=123456000
out_time_ms=123456000
out_time=00:02:03.456
speed=2.5x
progress=continue
```

解析 `out_time_us` / (totalDuration * 1_000_000) = progress

## FFmpeg 二进制捆绑

- 构建时放入 `src/main/resources/ffmpeg/{os}-{arch}/`
- 运行时解压到 `java.io.tmpdir`
- 自动检测平台选择对应二进制
- 注册 shutdownHook 清理临时文件

## 临时文件管理

```kotlin
object TempFileManager {
    private val tempDir = Files.createTempDirectory("videoaudio-")
    fun createConcatList(): Path
    fun createMergedAudio(): Path
    fun cleanup()  // 递归删除
}
```

## 依赖

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}
```

## 分发打包

- macOS: `packageDmg` → .dmg (含 .app bundle)
- Windows: `packageMsi` → .msi
- FFmpeg 二进制随应用打包

## 错误处理

- ffprobe 失败 → 检测编码不一致或文件损坏，进入 Error 状态
- FFmpeg 退出码非 0 → 进入 Error，stderr 收集到日志
- 磁盘空间不足 → 捕获异常进入 Error
- `-progress` 输出 `progress=end` → 阶段完成

## FFmpeg 8.x 文档依据

- FFmpeg 8.1.2 为当前最新稳定版
- concat demuxer 使用 `-f concat -safe 0 -i list.txt -c copy` 实现无损拼接
- `-progress pipe:1` 输出结构化进度 key-value
- `libx265` 支持 `-profile:v mainstillpicture`（简称 msp）静态图像 profile
- `-shortest` 以音频长度为准截断视频流
