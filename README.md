# CDRipShare Rust

CDRipShare 的当前主版本，以 Rust 和 egui 实现。它可以将多段抓轨音频、专辑封面和可选 CUE 合成为带章节的 1080p MP4。

原 Compose Desktop 实现已停止维护，完整保存在 [`archive/compose/`](archive/compose/) 中。

## 已实现

- 批量选择、排序和移除 FLAC/WAV/APE/M4A/AAC/MP3；
- 优先通过 `ffprobe` 检测编码与时长；下载包不带 `ffprobe` 时自动回退到 FFmpeg；
- 选择封面并生成 1920×1080 居中封面图；
- 高级封面编辑器：封面高度、圆角、阴影模糊/偏移/透明度；
- 自动取背景色、预设背景色或颜色选择器/HEX 自定义颜色；
- 解析 CUE，未选择 CUE 时按音频列表自动生成章节；
- H.264/H.265，以及保持采样率、48/96 kHz FLAC、AAC 192/256 kbps；
- 基于 `ffmpeg-sidecar` 的 FFmpeg 自动下载、统一路径发现和进程管理；
- 结构化 FFmpeg 进度、完整日志，以及先安全退出、超时后强制终止的取消流程；
- 文件拖入导入、后台音频探测和拖拽排序；
- 编码及封面设置自动持久化、FFmpeg 重新检测、日志数量限制；
- macOS、Windows、Linux 的 FFmpeg 查找和文件打开入口。

## 运行

需要 Rust 1.85+，以及带 `libx264`/`libx265` 的 FFmpeg。各平台采用不同的分发策略：

- Windows：发行包内置 FFmpeg，文件缺失时仍可在界面中自动下载；
- macOS：通过 Homebrew 执行 `brew install ffmpeg`；
- Linux：通过系统的包管理器安装 FFmpeg。

```bash
cargo run --release
```

FFmpeg/ffprobe 按以下顺序查找：

1. `CDRIPSHARE_FFMPEG` 和 `CDRIPSHARE_FFPROBE` 环境变量；
2. 应用程序同目录；
3. 应用程序同目录的 `ffmpeg/` 或 `resources/ffmpeg/`；
4. 系统 `PATH`。

Windows 发行时将 `ffmpeg.exe` 与 `ffprobe.exe` 放入应用程序旁的 `ffmpeg/` 目录。Windows 自动下载的文件由 `ffmpeg-sidecar` 放到当前可执行文件旁；如果该目录不可写，界面日志会显示具体错误。macOS 和 Linux 不提供应用内自动下载，安装完成后点击“重新检测”即可。

## 验证

```bash
cargo fmt --check
cargo test
cargo check
```

## GitHub Actions

- `CI`：每次推送和 Pull Request 在 Linux、Windows、macOS arm64、macOS x64 上检查和测试；
- `Build packages`：可在 Actions 页面手动运行，生成四个平台安装包；
- `Release`：推送 `v*` Tag 后构建全部平台并创建 GitHub Release。

Windows 包自动下载并内置 `ffmpeg.exe` 与 `ffprobe.exe`。macOS 和 Linux 包不内置 FFmpeg。macOS App 当前未签名和公证，正式分发前仍需配置 Apple Developer 签名。

本项目继续采用 GPL-2.0-or-later。分发 FFmpeg、libx264、libx265 时还需同时遵守对应许可证和源码提供义务。
