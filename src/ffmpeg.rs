use crate::{
    chapters, cover,
    cue::parse_file,
    model::{AudioItem, AudioOutputMode, CoverSettings, VideoCodec},
};
use anyhow::{anyhow, bail, Context, Result};
use crossbeam_channel::Sender;
#[cfg(target_os = "windows")]
use ffmpeg_sidecar::download::{auto_download_with_progress, FfmpegDownloadProgressEvent};
use ffmpeg_sidecar::{command::FfmpegCommand, event::FfmpegEvent};
use std::{
    env,
    ffi::OsString,
    fs,
    io::{BufRead, BufReader},
    path::{Path, PathBuf},
    process::{Command, Stdio},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    thread,
    time::{Duration, Instant},
};

#[derive(Clone, Debug)]
pub struct FFmpegPaths {
    pub ffmpeg: PathBuf,
    pub ffprobe: Option<PathBuf>,
}

impl FFmpegPaths {
    pub fn discover() -> Result<Self> {
        Ok(Self {
            ffmpeg: resolve_binary("ffmpeg", "CDRIPSHARE_FFMPEG")?,
            ffprobe: resolve_optional_binary("ffprobe", "CDRIPSHARE_FFPROBE")?,
        })
    }
}

#[derive(Clone, Debug)]
pub struct EncodeRequest {
    pub paths: FFmpegPaths,
    pub audio: Vec<AudioItem>,
    pub image: PathBuf,
    pub cue: Option<PathBuf>,
    pub output: PathBuf,
    pub video_codec: VideoCodec,
    pub audio_mode: AudioOutputMode,
    pub cover_settings: CoverSettings,
}

#[derive(Debug)]
pub enum WorkerMessage {
    AudioProbed(Result<AudioItem, String>),
    Log(String),
    Progress(f32),
    #[cfg(target_os = "windows")]
    DownloadProgress {
        progress: Option<f32>,
        status: String,
    },
    #[cfg(target_os = "windows")]
    DownloadFinished(Result<FFmpegPaths, String>),
    Finished(PathBuf),
    Failed(String),
    Cancelled,
}

pub fn start_probe(paths: FFmpegPaths, path: PathBuf, sender: Sender<WorkerMessage>) {
    thread::spawn(move || {
        let result = probe_audio(&paths, &path)
            .map_err(|error| format!("无法读取 {}：{error:#}", path.display()));
        let _ = sender.send(WorkerMessage::AudioProbed(result));
    });
}

pub struct EncodingHandle {
    cancel: Arc<AtomicBool>,
}

impl EncodingHandle {
    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::Relaxed);
    }
}

pub fn probe_audio(paths: &FFmpegPaths, path: &Path) -> Result<AudioItem> {
    let Some(ffprobe) = paths.ffprobe.as_deref() else {
        return probe_audio_with_ffmpeg(&paths.ffmpeg, path);
    };
    let duration = probe_value(ffprobe, path, &["-show_entries", "format=duration"])?
        .parse::<f64>()
        .with_context(|| format!("无法解析音频时长：{}", path.display()))?;
    let codec = probe_value(
        ffprobe,
        path,
        &[
            "-select_streams",
            "a:0",
            "-show_entries",
            "stream=codec_name",
        ],
    )?;
    Ok(AudioItem {
        path: path.to_owned(),
        codec,
        duration_sec: duration,
    })
}

fn probe_audio_with_ffmpeg(binary: &Path, path: &Path) -> Result<AudioItem> {
    let mut command = FfmpegCommand::new_with_path(binary);
    command
        .args(["-hide_banner", "-i"])
        .arg(path)
        .args(["-map", "0:a:0", "-t", "0", "-f", "null", "-"]);
    let mut child = command
        .spawn()
        .with_context(|| format!("无法启动 {}", binary.display()))?;
    let mut duration = None;
    let mut codec = None;
    for event in child.iter().context("无法读取 FFmpeg 媒体信息")? {
        match event {
            FfmpegEvent::ParsedDuration(value) if value.input_index == 0 => {
                duration = Some(value.duration);
            }
            FfmpegEvent::ParsedInputStream(stream)
                if stream.parent_index == 0 && stream.is_audio() && codec.is_none() =>
            {
                codec = Some(stream.format);
            }
            _ => {}
        }
    }
    Ok(AudioItem {
        path: path.to_owned(),
        codec: codec.context("FFmpeg 未检测到音频流")?,
        duration_sec: duration.context("FFmpeg 未返回音频时长")?,
    })
}

#[cfg(target_os = "windows")]
pub fn start_download(sender: Sender<WorkerMessage>) {
    thread::spawn(move || {
        let result = auto_download_with_progress(|event| {
            let (progress, status) = match event {
                FfmpegDownloadProgressEvent::Starting => (Some(0.0), "准备下载 FFmpeg…".into()),
                FfmpegDownloadProgressEvent::Downloading {
                    total_bytes,
                    downloaded_bytes,
                } => {
                    let progress = (total_bytes > 0).then_some(
                        (downloaded_bytes as f64 / total_bytes as f64).clamp(0.0, 1.0) as f32,
                    );
                    let status = format!(
                        "正在下载 FFmpeg：{:.1}/{:.1} MB",
                        downloaded_bytes as f64 / 1_048_576.0,
                        total_bytes as f64 / 1_048_576.0
                    );
                    (progress, status)
                }
                FfmpegDownloadProgressEvent::UnpackingArchive => (None, "正在解压 FFmpeg…".into()),
                FfmpegDownloadProgressEvent::Done => (Some(1.0), "FFmpeg 安装完成".into()),
            };
            let _ = sender.send(WorkerMessage::DownloadProgress { progress, status });
        })
        .context("自动下载 FFmpeg 失败")
        .and_then(|()| FFmpegPaths::discover())
        .map_err(|error| format!("{error:#}"));
        let _ = sender.send(WorkerMessage::DownloadFinished(result));
    });
}

fn probe_value(binary: &Path, file: &Path, selection: &[&str]) -> Result<String> {
    let output = Command::new(binary)
        .args(["-v", "error"])
        .args(selection)
        .args(["-of", "default=noprint_wrappers=1:nokey=1"])
        .arg(file)
        .output()
        .with_context(|| format!("无法启动 {}", binary.display()))?;
    if !output.status.success() {
        bail!(
            "ffprobe 读取失败：{}",
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    let value = String::from_utf8_lossy(&output.stdout).trim().to_owned();
    if value.is_empty() {
        bail!("ffprobe 没有返回媒体信息：{}", file.display());
    }
    Ok(value)
}

pub fn start_encode(request: EncodeRequest, sender: Sender<WorkerMessage>) -> EncodingHandle {
    let cancel = Arc::new(AtomicBool::new(false));
    let worker_cancel = Arc::clone(&cancel);
    thread::spawn(
        move || match run_encode(&request, &sender, &worker_cancel) {
            Ok(()) if worker_cancel.load(Ordering::Relaxed) => {
                let _ = sender.send(WorkerMessage::Cancelled);
            }
            Ok(()) => {
                let _ = sender.send(WorkerMessage::Finished(request.output));
            }
            Err(_) if worker_cancel.load(Ordering::Relaxed) => {
                let _ = sender.send(WorkerMessage::Cancelled);
            }
            Err(error) => {
                let _ = sender.send(WorkerMessage::Failed(format!("{error:#}")));
            }
        },
    );
    EncodingHandle { cancel }
}

fn run_encode(
    request: &EncodeRequest,
    sender: &Sender<WorkerMessage>,
    cancel: &AtomicBool,
) -> Result<()> {
    if request.audio.is_empty() {
        bail!("没有选择音频文件");
    }
    let temp = tempfile::Builder::new()
        .prefix("cdripshare-")
        .tempdir()
        .context("无法创建临时目录")?;
    let cover_path = temp.path().join("cover.png");
    let chapter_path = temp.path().join("chapters.ffmeta");

    send_log(sender, "正在生成 1920×1080 封面…");
    cover::generate(&request.image, &cover_path, request.cover_settings)?;
    let cue = request
        .cue
        .as_deref()
        .map(parse_file)
        .transpose()
        .context("CUE 解析失败")?;
    fs::write(
        &chapter_path,
        chapters::generate(cue.as_ref(), &request.audio),
    )
    .context("无法写入章节文件")?;

    let total_duration_us = (request
        .audio
        .iter()
        .map(|item| item.duration_sec)
        .sum::<f64>()
        * 1_000_000.0) as u64;
    let args = build_args(request, &cover_path, &chapter_path);
    send_log(
        sender,
        &format!(
            "执行：{} {}",
            request.paths.ffmpeg.display(),
            display_args(&args)
        ),
    );

    let mut command = FfmpegCommand::new_with_path(&request.paths.ffmpeg);
    command.args(&args);
    command
        .as_inner_mut()
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::piped());
    let mut child = command
        .spawn()
        .with_context(|| format!("无法启动 {}", request.paths.ffmpeg.display()))?;
    let stdout = child.take_stdout().context("无法读取 FFmpeg 进度")?;
    let stderr = child.take_stderr().context("无法读取 FFmpeg 日志")?;

    let progress_sender = sender.clone();
    let stdout_thread = thread::spawn(move || {
        let mut tracker = ProgressTracker::default();
        for line in BufReader::new(stdout).lines().map_while(Result::ok) {
            let update = tracker.consume(&line, total_duration_us);
            if let Some(progress) = update.progress {
                let _ = progress_sender.send(WorkerMessage::Progress(progress));
            }
            if let Some(log) = update.log {
                let _ = progress_sender.send(WorkerMessage::Log(log));
            }
        }
    });

    let log_sender = sender.clone();
    let stderr_thread = thread::spawn(move || {
        for line in BufReader::new(stderr).lines().map_while(Result::ok) {
            if !line.trim().is_empty() {
                let _ = log_sender.send(WorkerMessage::Log(line));
            }
        }
    });

    let mut quit_requested_at = None;
    let mut forced = false;
    let status = loop {
        if cancel.load(Ordering::Relaxed) && quit_requested_at.is_none() {
            send_log(sender, "正在请求 FFmpeg 安全停止…");
            if let Err(error) = child.quit() {
                send_log(sender, &format!("无法发送安全停止命令：{error:#}"));
                let _ = child.kill();
                forced = true;
            }
            quit_requested_at = Some(Instant::now());
        }
        if !forced
            && quit_requested_at
                .is_some_and(|requested| requested.elapsed() >= Duration::from_secs(3))
        {
            send_log(sender, "FFmpeg 未及时退出，正在强制终止…");
            let _ = child.kill();
            forced = true;
        }
        if let Some(status) = child
            .as_inner_mut()
            .try_wait()
            .context("无法获取 FFmpeg 状态")?
        {
            break status;
        }
        thread::sleep(Duration::from_millis(100));
    };
    let _ = stdout_thread.join();
    let _ = stderr_thread.join();

    if cancel.load(Ordering::Relaxed) {
        let _ = fs::remove_file(&request.output);
        return Ok(());
    }
    if !status.success() {
        bail!("FFmpeg 编码失败，退出状态：{status}");
    }
    Ok(())
}

#[derive(Default)]
struct ProgressTracker {
    progress: f32,
    out_time: String,
    speed: String,
    fps: String,
    bitrate: String,
    frame: String,
}

#[derive(Default)]
struct ProgressUpdate {
    progress: Option<f32>,
    log: Option<String>,
}

impl ProgressTracker {
    fn consume(&mut self, line: &str, total_duration_us: u64) -> ProgressUpdate {
        let Some((key, value)) = line.split_once('=') else {
            return ProgressUpdate::default();
        };
        let mut update = ProgressUpdate::default();
        match key {
            "out_time_us" => {
                if let Ok(value) = value.parse::<u64>() {
                    if total_duration_us > 0 {
                        self.progress =
                            (value as f64 / total_duration_us as f64).clamp(0.0, 1.0) as f32;
                        update.progress = Some(self.progress);
                    }
                }
            }
            "out_time" => self.out_time = value.to_owned(),
            "speed" => self.speed = value.to_owned(),
            "fps" => self.fps = value.to_owned(),
            "bitrate" => self.bitrate = value.to_owned(),
            "frame" => self.frame = value.to_owned(),
            "progress" => {
                let finished = value == "end";
                if finished {
                    self.progress = 1.0;
                    update.progress = Some(1.0);
                }
                let percent = (self.progress * 100.0).clamp(0.0, 100.0);
                update.log = Some(self.format_log(percent));
            }
            _ => {}
        }
        update
    }

    fn format_log(&self, percent: f32) -> String {
        let mut details = Vec::new();
        if !self.out_time.is_empty() && self.out_time != "N/A" {
            details.push(format!("时间 {}", self.out_time));
        }
        if !self.speed.is_empty() && self.speed != "N/A" {
            details.push(format!("速度 {}", self.speed));
        }
        if !self.fps.is_empty() && self.fps != "0.00" {
            details.push(format!("FPS {}", self.fps));
        }
        if !self.bitrate.is_empty() && self.bitrate != "N/A" {
            details.push(format!("码率 {}", self.bitrate));
        }
        if !self.frame.is_empty() {
            details.push(format!("帧 {}", self.frame));
        }
        if details.is_empty() {
            format!("[进度] {percent:.1}%")
        } else {
            format!("[进度] {percent:.1}% · {}", details.join(" · "))
        }
    }
}

fn build_args(request: &EncodeRequest, cover: &Path, chapters: &Path) -> Vec<OsString> {
    let count = request.audio.len();
    let mut args = strings(["-hide_banner", "-nostats", "-y"]);
    for item in &request.audio {
        args.push("-i".into());
        args.push(item.path.as_os_str().to_owned());
    }
    args.extend(strings(["-loop", "1", "-i"]));
    args.push(cover.as_os_str().to_owned());
    args.push("-i".into());
    args.push(chapters.as_os_str().to_owned());

    let audio_source = if count == 1 {
        "[0:a]anull[aconcat]".to_owned()
    } else {
        format!(
            "{}concat=n={count}:v=0:a=1[aconcat]",
            (0..count)
                .map(|index| format!("[{index}:a]"))
                .collect::<String>()
        )
    };
    let (audio_filter, audio_label) = match request.audio_mode {
        AudioOutputMode::HiRes48 => (
            ";[aconcat]aformat=sample_rates=48000:sample_fmts=s32[aout]",
            "[aout]",
        ),
        AudioOutputMode::HiRes96 => (
            ";[aconcat]aformat=sample_rates=96000:sample_fmts=s32[aout]",
            "[aout]",
        ),
        _ => ("", "[aconcat]"),
    };
    let filter = format!("{audio_source};[{count}:v]format=yuv420p[vout]{audio_filter}");
    args.extend(strings(["-filter_complex"]));
    args.push(filter.into());
    args.extend(strings(["-map", "[vout]", "-map"]));
    args.push(audio_label.into());

    match request.video_codec {
        VideoCodec::H265 => args.extend(strings([
            "-c:v",
            "libx265",
            "-preset",
            "fast",
            "-crf",
            "35",
            "-x265-params",
            "profile=mainstillpicture",
            "-pix_fmt",
            "yuv420p",
        ])),
        VideoCodec::H264 => args.extend(strings([
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            "23",
            "-tune",
            "stillimage",
            "-profile:v",
            "high",
            "-pix_fmt",
            "yuv420p",
        ])),
    }
    args.extend(strings([
        "-color_range",
        "tv",
        "-colorspace",
        "bt709",
        "-color_primaries",
        "bt709",
        "-color_trc",
        "bt709",
    ]));
    match request.audio_mode {
        AudioOutputMode::KeepRate => {
            args.extend(audio_codec_args(&request.audio[0].codec));
        }
        AudioOutputMode::HiRes48 | AudioOutputMode::HiRes96 => {
            args.extend(strings(["-c:a", "flac"]));
        }
        AudioOutputMode::Aac256 => args.extend(strings(["-c:a", "aac", "-b:a", "256k"])),
        AudioOutputMode::Aac192 => args.extend(strings(["-c:a", "aac", "-b:a", "192k"])),
    }
    let chapter_index = (count + 1).to_string();
    args.extend(strings(["-map_metadata"]));
    args.push(chapter_index.clone().into());
    args.push("-map_chapters".into());
    args.push(chapter_index.into());
    args.extend(strings([
        "-shortest",
        "-movflags",
        "+faststart",
        "-progress",
        "pipe:1",
    ]));
    args.push(request.output.as_os_str().to_owned());
    args
}

fn audio_codec_args(codec: &str) -> Vec<OsString> {
    match codec.to_ascii_lowercase().as_str() {
        "flac" => strings(["-c:a", "flac"]),
        "alac" => strings(["-c:a", "alac"]),
        "aac" => strings(["-c:a", "aac", "-b:a", "256k"]),
        "mp3" => strings(["-c:a", "libmp3lame", "-b:a", "320k"]),
        "ape" => strings(["-c:a", "flac"]),
        _ => strings(["-c:a", "flac"]),
    }
}

fn strings<const N: usize>(items: [&str; N]) -> Vec<OsString> {
    items.into_iter().map(OsString::from).collect()
}

fn display_args(args: &[OsString]) -> String {
    args.iter()
        .map(|arg| format!("{:?}", arg.to_string_lossy()))
        .collect::<Vec<_>>()
        .join(" ")
}

fn send_log(sender: &Sender<WorkerMessage>, message: &str) {
    let _ = sender.send(WorkerMessage::Log(message.to_owned()));
}

fn resolve_binary(name: &str, env_name: &str) -> Result<PathBuf> {
    resolve_optional_binary(name, env_name)?.ok_or_else(|| {
        anyhow!("找不到 {name}。请安装到 PATH，放到程序同目录的 ffmpeg/ 中，或设置 {env_name}")
    })
}

fn resolve_optional_binary(name: &str, env_name: &str) -> Result<Option<PathBuf>> {
    if let Some(path) = env::var_os(env_name).map(PathBuf::from) {
        if path.is_file() {
            return Ok(Some(path));
        }
        return Err(anyhow!("{env_name} 指向的文件不存在：{}", path.display()));
    }
    let executable_name = if cfg!(windows) {
        format!("{name}.exe")
    } else {
        name.to_owned()
    };
    if let Ok(current) = env::current_exe() {
        if let Some(directory) = current.parent() {
            for candidate in [
                directory.join(&executable_name),
                directory.join("ffmpeg").join(&executable_name),
                directory
                    .join("resources")
                    .join("ffmpeg")
                    .join(&executable_name),
            ] {
                if candidate.is_file() {
                    return Ok(Some(candidate));
                }
            }
        }
    }
    #[cfg(target_os = "macos")]
    for directory in [Path::new("/opt/homebrew/bin"), Path::new("/usr/local/bin")] {
        let candidate = directory.join(&executable_name);
        if candidate.is_file() {
            return Ok(Some(candidate));
        }
    }
    if let Some(paths) = env::var_os("PATH") {
        for directory in env::split_paths(&paths) {
            let candidate = directory.join(&executable_name);
            if candidate.is_file() {
                return Ok(Some(candidate));
            }
        }
    }
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossbeam_channel::unbounded;
    use image::{ImageBuffer, Rgb};

    fn request(audio_count: usize) -> EncodeRequest {
        EncodeRequest {
            paths: FFmpegPaths {
                ffmpeg: "ffmpeg".into(),
                ffprobe: Some("ffprobe".into()),
            },
            audio: (0..audio_count)
                .map(|index| AudioItem {
                    path: format!("{index}.flac").into(),
                    codec: "flac".into(),
                    duration_sec: 10.0,
                })
                .collect(),
            image: "source.png".into(),
            cue: None,
            output: "result.mp4".into(),
            video_codec: VideoCodec::H265,
            audio_mode: AudioOutputMode::KeepRate,
            cover_settings: CoverSettings::default(),
        }
    }

    #[test]
    fn builds_concat_and_correct_chapter_input_index() {
        let args = build_args(
            &request(2),
            Path::new("cover.png"),
            Path::new("chapter.ffmeta"),
        );
        let text = display_args(&args);
        assert!(text.contains("[0:a][1:a]concat=n=2:v=0:a=1[aconcat]"));
        let metadata = args.iter().position(|arg| arg == "-map_metadata").unwrap();
        assert_eq!(args[metadata + 1], "3");
    }

    #[test]
    fn uses_anull_for_single_audio_input() {
        let args = build_args(
            &request(1),
            Path::new("cover.png"),
            Path::new("chapter.ffmeta"),
        );
        assert!(display_args(&args).contains("[0:a]anull[aconcat]"));
    }

    #[test]
    fn formats_every_structured_progress_update() {
        let mut tracker = ProgressTracker::default();
        tracker.consume("out_time_us=25000000", 100_000_000);
        tracker.consume("out_time=00:00:25.000000", 100_000_000);
        tracker.consume("speed=1.25x", 100_000_000);
        tracker.consume("fps=30.00", 100_000_000);
        tracker.consume("bitrate=850.0kbits/s", 100_000_000);
        let update = tracker.consume("progress=continue", 100_000_000);
        let log = update.log.expect("25% should produce a progress log");
        assert!(log.contains("[进度] 25.0%"));
        assert!(log.contains("时间 00:00:25.000000"));
        assert!(log.contains("速度 1.25x"));
        assert!(log.contains("FPS 30.00"));
        assert!(log.contains("码率 850.0kbits/s"));

        tracker.consume("out_time_us=26000000", 100_000_000);
        let next = tracker.consume("progress=continue", 100_000_000);
        assert!(next.log.unwrap().contains("[进度] 26.0%"));
    }

    #[test]
    fn always_logs_final_progress() {
        let mut tracker = ProgressTracker::default();
        let update = tracker.consume("progress=end", 100_000_000);
        assert_eq!(update.progress, Some(1.0));
        assert!(update.log.unwrap().contains("100.0%"));
    }

    #[test]
    #[ignore = "requires FFmpeg with libx264 installed"]
    fn encodes_two_audio_files_and_cover_end_to_end() {
        let paths = FFmpegPaths::discover().expect("FFmpeg must be installed for this test");
        let temp = tempfile::tempdir().unwrap();
        let mut audio = Vec::new();
        for index in 0..2 {
            let path = temp.path().join(format!("tone-{index}.wav"));
            let status = Command::new(&paths.ffmpeg)
                .args([
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    &format!("sine=frequency={}:duration=0.25", 440 + index * 220),
                    "-c:a",
                    "pcm_s16le",
                    "-y",
                ])
                .arg(&path)
                .status()
                .unwrap();
            assert!(status.success());
            let probe_paths = FFmpegPaths {
                ffmpeg: paths.ffmpeg.clone(),
                ffprobe: (index == 0).then(|| paths.ffprobe.clone()).flatten(),
            };
            audio.push(probe_audio(&probe_paths, &path).unwrap());
        }
        let image_path = temp.path().join("cover.png");
        let image = ImageBuffer::from_pixel(64, 64, Rgb([120_u8, 70, 180]));
        image.save(&image_path).unwrap();
        let output = temp.path().join("smoke.mp4");
        let (sender, receiver) = unbounded();
        let request = EncodeRequest {
            paths,
            audio,
            image: image_path,
            cue: None,
            output: output.clone(),
            video_codec: VideoCodec::H264,
            audio_mode: AudioOutputMode::Aac192,
            cover_settings: CoverSettings::default(),
        };

        run_encode(&request, &sender, &AtomicBool::new(false)).unwrap();

        assert!(output.is_file());
        assert!(fs::metadata(output).unwrap().len() > 1_000);
        let logs: Vec<String> = receiver
            .try_iter()
            .filter_map(|message| match message {
                WorkerMessage::Log(line) => Some(line),
                _ => None,
            })
            .collect();
        assert!(logs.iter().any(|line| line.contains("[进度]")));
        assert!(logs.iter().any(|line| line.contains("100.0%")));
    }
}
