use crate::{
    cover,
    ffmpeg::{self, EncodeRequest, EncodingHandle, FFmpegPaths, WorkerMessage},
    model::{
        format_duration, AudioItem, AudioOutputMode, BackgroundMode, CoverSettings, VideoCodec,
    },
};
use crossbeam_channel::{Receiver, Sender};
use eframe::egui::{self, ColorImage, FontData, FontDefinitions, FontFamily, TextureHandle};
use image::RgbImage;
use serde::{Deserialize, Serialize};
use std::{
    collections::VecDeque,
    fs,
    path::{Path, PathBuf},
    process::Command,
};

const SETTINGS_KEY: &str = "cdripshare-settings-v2";
const MAX_LOGS: usize = 1000;
const PREVIEW_SOURCE_MAX_EDGE: u32 = 1024;

#[derive(Clone, Serialize, Deserialize)]
struct PersistedSettings {
    video_codec: VideoCodec,
    audio_mode: AudioOutputMode,
    cover: CoverSettings,
}

impl Default for PersistedSettings {
    fn default() -> Self {
        Self {
            video_codec: VideoCodec::default(),
            audio_mode: AudioOutputMode::default(),
            cover: CoverSettings::default(),
        }
    }
}

pub struct CDRipShareApp {
    ffmpeg: Result<FFmpegPaths, String>,
    audio: Vec<AudioItem>,
    image: Option<PathBuf>,
    cover_source: Option<RgbImage>,
    cue: Option<PathBuf>,
    settings: PersistedSettings,
    preview: Option<TextureHandle>,
    show_cover_editor: bool,
    show_ffmpeg_help: bool,
    custom_hex: String,
    output: Option<PathBuf>,
    progress: f32,
    downloading_ffmpeg: bool,
    download_progress: Option<f32>,
    download_status: String,
    probing_count: usize,
    run_state: RunState,
    logs: VecDeque<String>,
    sender: Sender<WorkerMessage>,
    receiver: Receiver<WorkerMessage>,
    encoding: Option<EncodingHandle>,
}

#[derive(Debug, Default)]
enum RunState {
    #[default]
    Idle,
    Working,
    Done,
    Failed(String),
}

impl CDRipShareApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        install_cjk_font(&cc.egui_ctx);
        let (sender, receiver) = crossbeam_channel::unbounded();
        let settings: PersistedSettings = cc
            .storage
            .and_then(|storage| eframe::get_value(storage, SETTINGS_KEY))
            .unwrap_or_default();
        let custom_hex = format_hex(settings.cover.custom_color);
        let ffmpeg = FFmpegPaths::discover().map_err(|error| format!("{error:#}"));
        let mut app = Self {
            ffmpeg,
            audio: Vec::new(),
            image: None,
            cover_source: None,
            cue: None,
            settings,
            preview: None,
            show_cover_editor: false,
            show_ffmpeg_help: false,
            custom_hex,
            output: None,
            progress: 0.0,
            downloading_ffmpeg: false,
            download_progress: None,
            download_status: String::new(),
            probing_count: 0,
            run_state: RunState::Idle,
            logs: VecDeque::new(),
            sender,
            receiver,
            encoding: None,
        };
        app.log(match &app.ffmpeg {
            Ok(paths) => format!("已找到 FFmpeg：{}", paths.ffmpeg.display()),
            Err(error) => format!("FFmpeg 尚不可用：{error}"),
        });
        app
    }

    fn log(&mut self, message: impl Into<String>) {
        if self.logs.len() >= MAX_LOGS {
            self.logs.pop_front();
        }
        self.logs.push_back(message.into());
    }

    fn poll_worker(&mut self) {
        while let Ok(message) = self.receiver.try_recv() {
            match message {
                WorkerMessage::AudioProbed(result) => {
                    self.probing_count = self.probing_count.saturating_sub(1);
                    match result {
                        Ok(item) => {
                            self.log(format!(
                                "添加音频：{}（{}，{}）",
                                item.display_name(),
                                item.codec,
                                format_duration(item.duration_sec)
                            ));
                            self.audio.push(item);
                        }
                        Err(error) => self.log(error),
                    }
                }
                WorkerMessage::Log(line) => self.log(line),
                WorkerMessage::Progress(value) => self.progress = value,
                #[cfg(target_os = "windows")]
                WorkerMessage::DownloadProgress { progress, status } => {
                    self.download_progress = progress;
                    self.download_status = status.clone();
                    if progress.is_none() || matches!(progress, Some(0.0 | 1.0)) {
                        self.log(status);
                    }
                }
                #[cfg(target_os = "windows")]
                WorkerMessage::DownloadFinished(result) => {
                    self.downloading_ffmpeg = false;
                    self.download_progress = None;
                    match result {
                        Ok(paths) => {
                            self.log(format!("FFmpeg 已就绪：{}", paths.ffmpeg.display()));
                            if paths.ffprobe.is_none() {
                                self.log("未找到 ffprobe，将使用 FFmpeg 读取音频信息");
                            }
                            self.ffmpeg = Ok(paths);
                            self.download_status = "FFmpeg 可用".into();
                        }
                        Err(error) => {
                            self.log(format!("FFmpeg 下载失败：{error}"));
                            self.ffmpeg = Err(error);
                            self.download_status = "下载失败".into();
                        }
                    }
                }
                WorkerMessage::Finished(path) => {
                    self.output = Some(path.clone());
                    self.progress = 1.0;
                    self.run_state = RunState::Done;
                    self.encoding = None;
                    self.log(format!("合成完成：{}", path.display()));
                }
                WorkerMessage::Failed(error) => {
                    self.run_state = RunState::Failed(error.clone());
                    self.encoding = None;
                    self.log(format!("合成失败：{error}"));
                }
                WorkerMessage::Cancelled => {
                    self.progress = 0.0;
                    self.run_state = RunState::Idle;
                    self.encoding = None;
                    self.log("已取消合成");
                }
            }
        }
    }

    fn queue_audio(&mut self, paths: Vec<PathBuf>) {
        let Some(ffmpeg) = self.ffmpeg.as_ref().ok().cloned() else {
            self.log("请先配置或下载 FFmpeg");
            return;
        };
        for path in paths {
            self.probing_count += 1;
            ffmpeg::start_probe(ffmpeg.clone(), path, self.sender.clone());
        }
    }

    fn pick_audio(&mut self) {
        let files = rfd::FileDialog::new()
            .add_filter("音频", &["flac", "wav", "ape", "m4a", "aac", "mp3"])
            .pick_files()
            .unwrap_or_default();
        self.queue_audio(files);
    }

    fn set_image(&mut self, ctx: &egui::Context, path: PathBuf) {
        match image::open(&path) {
            Ok(image) => {
                self.cover_source = Some(make_preview_source(image.to_rgb8()));
                self.image = Some(path.clone());
                self.refresh_preview(ctx);
                self.log(format!("设置封面：{}", path.display()));
            }
            Err(error) => self.log(format!("无法解码封面：{error}")),
        }
    }

    fn pick_image(&mut self, ctx: &egui::Context) {
        if let Some(path) = rfd::FileDialog::new()
            .add_filter("图片", &["png", "jpg", "jpeg", "webp"])
            .pick_file()
        {
            self.set_image(ctx, path);
        }
    }

    fn refresh_preview(&mut self, ctx: &egui::Context) {
        let Some(source) = &self.cover_source else {
            return;
        };
        let image = cover::render_preview(source, self.settings.cover, 480, 270);
        let color_image = ColorImage::from_rgb(
            [image.width() as usize, image.height() as usize],
            image.as_raw(),
        );
        if let Some(texture) = self.preview.as_mut() {
            texture.set(color_image, egui::TextureOptions::LINEAR);
        } else {
            self.preview =
                Some(ctx.load_texture("cover-preview", color_image, egui::TextureOptions::LINEAR));
        }
    }

    fn handle_dropped_files(&mut self, ctx: &egui::Context) {
        let dropped: Vec<PathBuf> = ctx.input(|input| {
            input
                .raw
                .dropped_files
                .iter()
                .filter_map(|file| file.path.clone())
                .collect()
        });
        if dropped.is_empty() {
            return;
        }
        let mut audio = Vec::new();
        for path in dropped {
            match extension(&path).as_deref() {
                Some("png" | "jpg" | "jpeg" | "webp") => self.set_image(ctx, path),
                Some("cue") => {
                    self.log(format!("设置 CUE：{}", path.display()));
                    self.cue = Some(path);
                }
                Some("flac" | "wav" | "ape" | "m4a" | "aac" | "mp3") => audio.push(path),
                _ => self.log(format!("忽略不支持的文件：{}", path.display())),
            }
        }
        self.queue_audio(audio);
    }

    fn rediscover_ffmpeg(&mut self) {
        self.ffmpeg = FFmpegPaths::discover().map_err(|error| format!("{error:#}"));
        self.log(match &self.ffmpeg {
            Ok(paths) => format!("FFmpeg 检测成功：{}", paths.ffmpeg.display()),
            Err(error) => format!("FFmpeg 检测失败：{error}"),
        });
    }

    #[cfg(target_os = "windows")]
    fn download_ffmpeg(&mut self) {
        if self.downloading_ffmpeg || matches!(self.run_state, RunState::Working) {
            return;
        }
        self.downloading_ffmpeg = true;
        self.download_progress = Some(0.0);
        self.download_status = "准备下载 FFmpeg…".into();
        self.log("开始自动下载 FFmpeg");
        ffmpeg::start_download(self.sender.clone());
    }

    fn show_ffmpeg_install_help(&mut self, ctx: &egui::Context) {
        if !self.show_ffmpeg_help {
            return;
        }
        let mut open = true;
        egui::Window::new("安装 FFmpeg")
            .open(&mut open)
            .resizable(false)
            .collapsible(false)
            .show(ctx, |ui| {
                #[cfg(target_os = "macos")]
                {
                    ui.label("请使用 Homebrew 安装 FFmpeg：");
                    ui.horizontal(|ui| {
                        ui.monospace("brew install ffmpeg");
                        if ui.small_button("复制命令").clicked() {
                            ui.ctx().copy_text("brew install ffmpeg".into());
                        }
                    });
                }
                #[cfg(target_os = "linux")]
                {
                    ui.label("请通过系统的包管理器安装 FFmpeg，安装完成后重新检测。");
                }
                #[cfg(target_os = "windows")]
                {
                    ui.label("Windows 版本支持自动下载 FFmpeg，也可以随发行包一起提供。");
                }
                ui.add_space(8.0);
                if ui.button("重新检测").clicked() {
                    self.rediscover_ffmpeg();
                }
            });
        self.show_ffmpeg_help = open;
    }

    fn start(&mut self) {
        let paths = match &self.ffmpeg {
            Ok(paths) => paths.clone(),
            Err(error) => {
                self.run_state = RunState::Failed(error.clone());
                return;
            }
        };
        if self.audio.is_empty() || self.probing_count > 0 {
            self.run_state = RunState::Failed("请等待音频检测完成".into());
            return;
        }
        let Some(image) = self.image.clone() else {
            self.run_state = RunState::Failed("请先选择专辑封面".into());
            return;
        };
        let default_name = self
            .audio
            .first()
            .map(AudioItem::display_name)
            .unwrap_or_else(|| "CDRipShare".into());
        let Some(output) = rfd::FileDialog::new()
            .add_filter("MP4 视频", &["mp4"])
            .set_file_name(format!("{default_name}.mp4"))
            .save_file()
        else {
            return;
        };
        let request = EncodeRequest {
            paths,
            audio: self.audio.clone(),
            image,
            cue: self.cue.clone(),
            output: output.clone(),
            video_codec: self.settings.video_codec,
            audio_mode: self.settings.audio_mode,
            cover_settings: self.settings.cover,
        };
        self.progress = 0.0;
        self.output = None;
        self.run_state = RunState::Working;
        self.log(format!("开始合成：{}", output.display()));
        self.encoding = Some(ffmpeg::start_encode(request, self.sender.clone()));
    }

    fn show_cover_editor(&mut self, ctx: &egui::Context) {
        if !self.show_cover_editor {
            return;
        }
        let mut open = true;
        let mut changed = false;
        egui::Window::new("高级封面编辑器")
            .open(&mut open)
            .default_width(720.0)
            .resizable(true)
            .show(ctx, |ui| {
                if let Some(texture) = &self.preview {
                    ui.add(egui::Image::from_texture(texture).max_width(680.0));
                } else {
                    ui.label("请先选择封面图片");
                }
                ui.separator();
                ui.horizontal(|ui| {
                    egui::ComboBox::from_label("背景")
                        .selected_text(self.settings.cover.background_mode.label())
                        .show_ui(ui, |ui| {
                            for mode in BackgroundMode::ALL {
                                changed |= ui
                                    .selectable_value(
                                        &mut self.settings.cover.background_mode,
                                        mode,
                                        mode.label(),
                                    )
                                    .changed();
                            }
                        });
                    if self.settings.cover.background_mode == BackgroundMode::Custom {
                        changed |= ui
                            .color_edit_button_srgb(&mut self.settings.cover.custom_color)
                            .changed();
                        let response = ui.text_edit_singleline(&mut self.custom_hex);
                        if response.changed() {
                            if let Some(color) = parse_hex(&self.custom_hex) {
                                self.settings.cover.custom_color = color;
                                changed = true;
                            }
                        }
                    }
                });
                changed |= ui
                    .add(
                        egui::Slider::new(&mut self.settings.cover.image_height, 480.0..=1000.0)
                            .text("封面高度"),
                    )
                    .changed();
                changed |= ui
                    .add(
                        egui::Slider::new(&mut self.settings.cover.corner_radius, 0.0..=80.0)
                            .text("圆角半径"),
                    )
                    .changed();
                changed |= ui
                    .add(
                        egui::Slider::new(&mut self.settings.cover.shadow_blur, 0.0..=80.0)
                            .text("阴影模糊"),
                    )
                    .changed();
                changed |= ui
                    .add(
                        egui::Slider::new(&mut self.settings.cover.shadow_offset_y, 0.0..=48.0)
                            .text("阴影偏移"),
                    )
                    .changed();
                changed |= ui
                    .add(
                        egui::Slider::new(&mut self.settings.cover.shadow_alpha, 0.0..=1.0)
                            .text("阴影透明度"),
                    )
                    .changed();
                if ui.button("恢复默认").clicked() {
                    self.settings.cover = CoverSettings::default();
                    self.custom_hex = format_hex(self.settings.cover.custom_color);
                    changed = true;
                }
            });
        self.show_cover_editor = open;
        if changed {
            self.refresh_preview(ctx);
        }
    }
}

impl eframe::App for CDRipShareApp {
    fn save(&mut self, storage: &mut dyn eframe::Storage) {
        eframe::set_value(storage, SETTINGS_KEY, &self.settings);
    }

    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.poll_worker();
        self.handle_dropped_files(ctx);
        self.show_ffmpeg_install_help(ctx);
        if matches!(self.run_state, RunState::Working)
            || self.probing_count > 0
            || self.downloading_ffmpeg
        {
            ctx.request_repaint_after(std::time::Duration::from_millis(100));
        }
        let enabled = !matches!(self.run_state, RunState::Working);
        egui::TopBottomPanel::top("header").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.heading("CDRipShare");
                ui.label("拖入音频、图片或 CUE 即可导入");
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if !self.downloading_ffmpeg && ui.small_button("重新检测").clicked() {
                        self.rediscover_ffmpeg();
                    }
                    if self.ffmpeg.is_err() && !self.downloading_ffmpeg {
                        #[cfg(target_os = "windows")]
                        if ui.small_button("下载 FFmpeg").clicked() {
                            self.download_ffmpeg();
                        }
                        #[cfg(any(target_os = "macos", target_os = "linux"))]
                        if ui.small_button("安装说明").clicked() {
                            self.show_ffmpeg_help = true;
                        }
                    }
                    if self.downloading_ffmpeg {
                        match self.download_progress {
                            Some(value) => {
                                ui.add(egui::ProgressBar::new(value).desired_width(120.0));
                            }
                            None => {
                                ui.spinner();
                            }
                        }
                    }
                    ui.colored_label(
                        if self.ffmpeg.is_ok() {
                            egui::Color32::LIGHT_GREEN
                        } else if self.downloading_ffmpeg {
                            egui::Color32::LIGHT_BLUE
                        } else {
                            egui::Color32::LIGHT_RED
                        },
                        if self.ffmpeg.is_ok() {
                            "FFmpeg 可用"
                        } else if self.downloading_ffmpeg {
                            self.download_status.as_str()
                        } else {
                            "FFmpeg 未配置"
                        },
                    );
                });
            });
        });
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.add_enabled_ui(enabled, |ui| {
                ui.horizontal_top(|ui| {
                    let spacing = ui.spacing().item_spacing.x;
                    let right_width = 380.0_f32.min(ui.available_width() * 0.46);
                    let left_width = (ui.available_width() - right_width - spacing).max(320.0);
                    let row_height = 282.0;

                    ui.allocate_ui_with_layout(
                        egui::vec2(left_width, row_height),
                        egui::Layout::top_down(egui::Align::Min),
                        |left| {
                            left.horizontal(|ui| {
                                ui.heading("音频列表");
                                if ui.button("添加").clicked() {
                                    self.pick_audio();
                                }
                                if ui.button("清空").clicked() {
                                    self.audio.clear();
                                }
                                if self.probing_count > 0 {
                                    ui.spinner();
                                    ui.label(format!("检测中 {}", self.probing_count));
                                }
                            });
                            let mut action = None;
                            if self.audio.is_empty() {
                                let placeholder_width = left.available_width();
                                left.group(|ui| {
                                    ui.set_min_size(egui::vec2(placeholder_width, 220.0));
                                    ui.centered_and_justified(|ui| {
                                        if self.probing_count > 0 {
                                            ui.label("正在读取音频信息…");
                                        } else {
                                            ui.label("拖入音频文件，或点击“添加”");
                                        }
                                    });
                                });
                            } else {
                                egui::ScrollArea::vertical()
                                    .max_height(240.0)
                                    .show(left, |ui| {
                                        for (index, item) in self.audio.iter().enumerate() {
                                            let frame = egui::Frame::group(ui.style());
                                            let drag_id = egui::Id::new(("audio-row", index));
                                            let is_dragging = ui.ctx().is_being_dragged(drag_id);
                                            let (_, dropped) =
                                                ui.dnd_drop_zone::<usize, _>(frame, |ui| {
                                                    ui.dnd_drag_source(drag_id, index, |ui| {
                                                        let mut row = |ui: &mut egui::Ui| {
                                                            ui.horizontal(|ui| {
                                                                ui.label("☰");
                                                                ui.vertical(|ui| {
                                                                    ui.label(item.display_name());
                                                                    ui.small(format!(
                                                                        "{} · {}",
                                                                        item.codec,
                                                                        format_duration(
                                                                            item.duration_sec
                                                                        )
                                                                    ));
                                                                });
                                                                if is_dragging {
                                                                    if ui
                                                                        .small_button("×")
                                                                        .clicked()
                                                                    {
                                                                        action = Some(
                                                                            AudioAction::Remove(
                                                                                index,
                                                                            ),
                                                                        );
                                                                    }
                                                                } else {
                                                                    ui.with_layout(
                                                                        egui::Layout::right_to_left(
                                                                            egui::Align::Center,
                                                                        ),
                                                                        |ui| {
                                                                            if ui
                                                                                .small_button("×")
                                                                                .clicked()
                                                                            {
                                                                                action = Some(
                                                                                    AudioAction::Remove(
                                                                                        index,
                                                                                    ),
                                                                                );
                                                                            }
                                                                        },
                                                                    );
                                                                }
                                                            });
                                                        };
                                                        if is_dragging {
                                                            egui::Frame::group(ui.style())
                                                                .show(ui, row);
                                                        } else {
                                                            row(ui);
                                                        }
                                                    });
                                                });
                                            if let Some(source) = dropped {
                                                action = Some(AudioAction::Move(*source, index));
                                            }
                                        }
                                    });
                            }
                            if let Some(action) = action {
                                apply_audio_action(&mut self.audio, action);
                            }
                        },
                    );

                    ui.allocate_ui_with_layout(
                        egui::vec2(right_width, row_height),
                        egui::Layout::top_down(egui::Align::Min),
                        |right| {
                            right.horizontal(|ui| {
                                ui.heading("专辑封面");
                                if ui.button("选择图片").clicked() {
                                    self.pick_image(ctx);
                                }
                                if ui.button("高级编辑").clicked() {
                                    self.show_cover_editor = true;
                                }
                            });
                            if let Some(texture) = &self.preview {
                                right.add(egui::Image::from_texture(texture).fit_to_exact_size(
                                    egui::vec2(right_width, right_width * 9.0 / 16.0),
                                ));
                            } else {
                                right.group(|ui| {
                                    ui.set_min_size(egui::vec2(
                                        right_width,
                                        right_width * 9.0 / 16.0,
                                    ));
                                    ui.centered_and_justified(|ui| ui.label("拖入或选择封面"));
                                });
                            }
                        },
                    );
                });
                ui.separator();
                ui.horizontal(|ui| {
                    ui.label(format!(
                        "CUE：{}",
                        self.cue
                            .as_ref()
                            .and_then(|p| p.file_name())
                            .and_then(|n| n.to_str())
                            .unwrap_or("未选择")
                    ));
                    if ui.button("选择 CUE").clicked() {
                        if let Some(path) = rfd::FileDialog::new()
                            .add_filter("CUE", &["cue"])
                            .pick_file()
                        {
                            self.cue = Some(path);
                        }
                    }
                    if self.cue.is_some() && ui.button("清除").clicked() {
                        self.cue = None;
                    }
                });
                ui.horizontal(|ui| {
                    egui::ComboBox::from_label("视频编码")
                        .selected_text(self.settings.video_codec.label())
                        .show_ui(ui, |ui| {
                            for value in VideoCodec::ALL {
                                ui.selectable_value(
                                    &mut self.settings.video_codec,
                                    value,
                                    value.label(),
                                );
                            }
                        });
                    egui::ComboBox::from_label("音频输出")
                        .selected_text(self.settings.audio_mode.label())
                        .show_ui(ui, |ui| {
                            for value in AudioOutputMode::ALL {
                                ui.selectable_value(
                                    &mut self.settings.audio_mode,
                                    value,
                                    value.label(),
                                );
                            }
                        });
                });
            });
            ui.separator();
            ui.add(
                egui::ProgressBar::new(self.progress)
                    .show_percentage()
                    .animate(matches!(self.run_state, RunState::Working)),
            );
            ui.horizontal(|ui| match &self.run_state {
                RunState::Working => {
                    ui.label("FFmpeg 正在编码…");
                    if ui.button("取消").clicked() {
                        if let Some(handle) = &self.encoding {
                            handle.cancel();
                        }
                    }
                }
                RunState::Done => {
                    ui.colored_label(egui::Color32::LIGHT_GREEN, "合成完成");
                    if ui.button("打开输出").clicked() {
                        if let Some(path) = &self.output {
                            open_path(path);
                        }
                    }
                    if ui.button("再次生成").clicked() {
                        self.start();
                    }
                }
                RunState::Failed(error) => {
                    ui.colored_label(egui::Color32::LIGHT_RED, error);
                    if ui.button("重试").clicked() {
                        self.start();
                    }
                }
                RunState::Idle => {
                    if ui
                        .add_enabled(
                            self.ffmpeg.is_ok() && self.probing_count == 0,
                            egui::Button::new("生成 MP4"),
                        )
                        .clicked()
                    {
                        self.start();
                    }
                }
            });
            ui.separator();
            ui.horizontal(|ui| {
                ui.heading("日志");
                if ui.small_button("清空日志").clicked() {
                    self.logs.clear();
                }
            });
            egui::ScrollArea::vertical()
                .stick_to_bottom(true)
                .show(ui, |ui| {
                    for line in &self.logs {
                        ui.monospace(line);
                    }
                });
        });
        self.show_cover_editor(ctx);
    }
}

enum AudioAction {
    Move(usize, usize),
    Remove(usize),
}

fn apply_audio_action(audio: &mut Vec<AudioItem>, action: AudioAction) {
    match action {
        AudioAction::Remove(index) if index < audio.len() => {
            audio.remove(index);
        }
        AudioAction::Move(from, to) if from < audio.len() && to < audio.len() && from != to => {
            let item = audio.remove(from);
            audio.insert(to, item);
        }
        _ => {}
    }
}

fn extension(path: &Path) -> Option<String> {
    path.extension()
        .and_then(|value| value.to_str())
        .map(str::to_ascii_lowercase)
}

fn make_preview_source(source: RgbImage) -> RgbImage {
    let max_edge = source.width().max(source.height());
    if max_edge <= PREVIEW_SOURCE_MAX_EDGE {
        return source;
    }
    let width =
        ((source.width() as u64 * PREVIEW_SOURCE_MAX_EDGE as u64) / max_edge as u64).max(1) as u32;
    let height =
        ((source.height() as u64 * PREVIEW_SOURCE_MAX_EDGE as u64) / max_edge as u64).max(1) as u32;
    image::imageops::resize(
        &source,
        width,
        height,
        image::imageops::FilterType::Triangle,
    )
}

fn format_hex(color: [u8; 3]) -> String {
    format!("#{:02X}{:02X}{:02X}", color[0], color[1], color[2])
}

fn parse_hex(value: &str) -> Option<[u8; 3]> {
    let value = value.trim().trim_start_matches('#');
    if value.len() != 6 {
        return None;
    }
    Some([
        u8::from_str_radix(&value[0..2], 16).ok()?,
        u8::from_str_radix(&value[2..4], 16).ok()?,
        u8::from_str_radix(&value[4..6], 16).ok()?,
    ])
}

fn install_cjk_font(ctx: &egui::Context) {
    let candidates = if cfg!(target_os = "macos") {
        vec!["/System/Library/Fonts/Supplemental/Arial Unicode.ttf"]
    } else if cfg!(target_os = "windows") {
        vec![
            "C:\\Windows\\Fonts\\msyh.ttc",
            "C:\\Windows\\Fonts\\simhei.ttf",
        ]
    } else {
        vec![
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        ]
    };
    let Some(bytes) = candidates.into_iter().find_map(|path| fs::read(path).ok()) else {
        return;
    };
    let mut fonts = FontDefinitions::default();
    fonts
        .font_data
        .insert("cdripshare-cjk".into(), FontData::from_owned(bytes).into());
    for family in [FontFamily::Proportional, FontFamily::Monospace] {
        fonts
            .families
            .entry(family)
            .or_default()
            .insert(0, "cdripshare-cjk".into());
    }
    ctx.set_fonts(fonts);
}

fn open_path(path: &Path) {
    #[cfg(target_os = "macos")]
    let _ = Command::new("open").arg(path).spawn();
    #[cfg(target_os = "windows")]
    let _ = Command::new("explorer").arg(path).spawn();
    #[cfg(all(unix, not(target_os = "macos")))]
    let _ = Command::new("xdg-open").arg(path).spawn();
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parses_custom_hex_color() {
        assert_eq!(parse_hex("#3A5070"), Some([0x3a, 0x50, 0x70]));
    }
    #[test]
    fn rejects_invalid_hex_color() {
        assert_eq!(parse_hex("#xyz"), None);
    }
    #[test]
    fn limits_preview_source_longest_edge() {
        let source = image::ImageBuffer::from_pixel(4000, 2000, image::Rgb([1, 2, 3]));
        let preview = make_preview_source(source);
        assert_eq!((preview.width(), preview.height()), (1024, 512));
    }
}
