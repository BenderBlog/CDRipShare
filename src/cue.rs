use anyhow::{Context, Result};
use std::{fs, path::Path};

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CueTrack {
    pub number: u32,
    pub title: String,
    pub performer: Option<String>,
    pub index_01_ms: u64,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct CueSheet {
    pub title: Option<String>,
    pub performer: Option<String>,
    pub tracks: Vec<CueTrack>,
}

pub fn parse_file(path: &Path) -> Result<CueSheet> {
    let text =
        fs::read_to_string(path).with_context(|| format!("无法读取 CUE：{}", path.display()))?;
    Ok(parse(&text))
}

pub fn parse(text: &str) -> CueSheet {
    let mut sheet = CueSheet::default();
    let mut current: Option<CueTrack> = None;

    for raw in text.lines() {
        let line = raw.trim();
        if let Some(rest) = line.strip_prefix("TRACK ") {
            if let Some(track) = current.take() {
                sheet.tracks.push(track);
            }
            let number = rest
                .split_whitespace()
                .next()
                .and_then(|value| value.parse().ok())
                .unwrap_or(0);
            current = Some(CueTrack {
                number,
                title: format!("Track {number}"),
                performer: sheet.performer.clone(),
                index_01_ms: 0,
            });
        } else if let Some(rest) = line.strip_prefix("TITLE ") {
            let value = unquote(rest);
            if let Some(track) = current.as_mut() {
                track.title = value;
            } else {
                sheet.title = Some(value);
            }
        } else if let Some(rest) = line.strip_prefix("PERFORMER ") {
            let value = unquote(rest);
            if let Some(track) = current.as_mut() {
                track.performer = Some(value);
            } else {
                sheet.performer = Some(value);
            }
        } else if let Some(rest) = line.strip_prefix("INDEX 01 ") {
            if let Some(track) = current.as_mut() {
                track.index_01_ms = parse_timestamp(rest).unwrap_or(0);
            }
        }
    }

    if let Some(track) = current {
        sheet.tracks.push(track);
    }
    sheet
}

fn unquote(value: &str) -> String {
    value.trim().trim_matches('"').to_owned()
}

fn parse_timestamp(value: &str) -> Option<u64> {
    let mut parts = value.trim().split(':');
    let minutes: u64 = parts.next()?.parse().ok()?;
    let seconds: u64 = parts.next()?.parse().ok()?;
    let frames: u64 = parts.next()?.parse().ok()?;
    if parts.next().is_some() || seconds >= 60 || frames >= 75 {
        return None;
    }
    Some(minutes * 60_000 + seconds * 1_000 + frames * 1_000 / 75)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_album_and_tracks() {
        let cue = parse(
            r#"
            PERFORMER "Artist"
            TITLE "Album"
            TRACK 01 AUDIO
              TITLE "First"
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              TITLE "Second"
              PERFORMER "Guest"
              INDEX 01 03:12:37
            "#,
        );
        assert_eq!(cue.title.as_deref(), Some("Album"));
        assert_eq!(cue.tracks.len(), 2);
        assert_eq!(cue.tracks[0].performer.as_deref(), Some("Artist"));
        assert_eq!(cue.tracks[1].performer.as_deref(), Some("Guest"));
        assert_eq!(cue.tracks[1].index_01_ms, 192_493);
    }

    #[test]
    fn rejects_invalid_timestamp() {
        assert_eq!(parse_timestamp("00:99:00"), None);
    }
}
