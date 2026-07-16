use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct AudioItem {
    pub path: PathBuf,
    pub codec: String,
    pub duration_sec: f64,
}

impl AudioItem {
    pub fn display_name(&self) -> String {
        self.path
            .file_stem()
            .and_then(|name| name.to_str())
            .unwrap_or("未命名音轨")
            .to_owned()
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum VideoCodec {
    #[default]
    H265,
    H264,
}

impl VideoCodec {
    pub const ALL: [Self; 2] = [Self::H265, Self::H264];

    pub fn label(self) -> &'static str {
        match self {
            Self::H265 => "H.265 (HEVC)",
            Self::H264 => "H.264 (AVC)",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum AudioOutputMode {
    #[default]
    KeepRate,
    HiRes48,
    HiRes96,
    Aac256,
    Aac192,
}

impl AudioOutputMode {
    pub const ALL: [Self; 5] = [
        Self::KeepRate,
        Self::HiRes48,
        Self::HiRes96,
        Self::Aac256,
        Self::Aac192,
    ];

    pub fn label(self) -> &'static str {
        match self {
            Self::KeepRate => "保持采样率",
            Self::HiRes48 => "48 kHz / 24 bit FLAC",
            Self::HiRes96 => "96 kHz / 24 bit FLAC",
            Self::Aac256 => "AAC 256 kbps",
            Self::Aac192 => "AAC 192 kbps",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum BackgroundMode {
    #[default]
    Auto,
    Navy,
    DarkGray,
    DarkGreen,
    DarkBrown,
    Black,
    Custom,
}

impl BackgroundMode {
    pub const ALL: [Self; 7] = [
        Self::Auto,
        Self::Navy,
        Self::DarkGray,
        Self::DarkGreen,
        Self::DarkBrown,
        Self::Black,
        Self::Custom,
    ];

    pub fn label(self) -> &'static str {
        match self {
            Self::Auto => "自动取色",
            Self::Navy => "藏蓝",
            Self::DarkGray => "深灰",
            Self::DarkGreen => "墨绿",
            Self::DarkBrown => "深棕",
            Self::Black => "经典黑",
            Self::Custom => "自定义",
        }
    }

    pub fn rgb(self) -> Option<[u8; 3]> {
        match self {
            Self::Auto => None,
            Self::Navy => Some([0x3a, 0x50, 0x70]),
            Self::DarkGray => Some([0x58, 0x58, 0x58]),
            Self::DarkGreen => Some([0x42, 0x5a, 0x46]),
            Self::DarkBrown => Some([0x58, 0x4a, 0x3e]),
            Self::Black => Some([0x38, 0x38, 0x38]),
            Self::Custom => None,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct CoverSettings {
    pub background_mode: BackgroundMode,
    pub custom_color: [u8; 3],
    pub image_height: f32,
    pub corner_radius: f32,
    pub shadow_blur: f32,
    pub shadow_offset_y: f32,
    pub shadow_alpha: f32,
}

impl Default for CoverSettings {
    fn default() -> Self {
        Self {
            background_mode: BackgroundMode::Auto,
            custom_color: [0x58, 0x58, 0x58],
            image_height: 800.0,
            corner_radius: 24.0,
            shadow_blur: 24.0,
            shadow_offset_y: 8.0,
            shadow_alpha: 170.0 / 255.0,
        }
    }
}

impl CoverSettings {
    pub fn normalized(mut self) -> Self {
        self.image_height = self.image_height.clamp(480.0, 1000.0);
        self.corner_radius = self.corner_radius.clamp(0.0, 80.0);
        self.shadow_blur = self.shadow_blur.clamp(0.0, 80.0);
        self.shadow_offset_y = self.shadow_offset_y.clamp(0.0, 48.0);
        self.shadow_alpha = self.shadow_alpha.clamp(0.0, 1.0);
        self
    }
}

pub fn format_duration(seconds: f64) -> String {
    let total = seconds.max(0.0) as u64;
    format!("{}:{:02}", total / 60, total % 60)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_duration() {
        assert_eq!(format_duration(125.9), "2:05");
    }

    #[test]
    fn derives_display_name_without_extension() {
        let item = AudioItem {
            path: PathBuf::from("/music/01 - Intro.flac"),
            codec: "flac".into(),
            duration_sec: 1.0,
        };
        assert_eq!(item.display_name(), "01 - Intro");
    }
}
