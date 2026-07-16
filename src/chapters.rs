use crate::{cue::CueSheet, model::AudioItem};

pub fn generate(cue: Option<&CueSheet>, audio: &[AudioItem]) -> String {
    let mut output = String::from(";FFMETADATA1\n");
    let total_ms = (audio.iter().map(|item| item.duration_sec).sum::<f64>() * 1000.0) as u64;

    if let Some(sheet) = cue.filter(|sheet| !sheet.tracks.is_empty()) {
        for (index, track) in sheet.tracks.iter().enumerate() {
            let end = sheet
                .tracks
                .get(index + 1)
                .map(|next| next.index_01_ms)
                .unwrap_or(total_ms)
                .max(track.index_01_ms);
            let title = match track.performer.as_deref().or(sheet.performer.as_deref()) {
                Some(artist) if !artist.is_empty() => format!("{artist} - {}", track.title),
                _ => track.title.clone(),
            };
            append_chapter(&mut output, track.index_01_ms, end, &title);
        }
    } else {
        let mut offset = 0_u64;
        for item in audio {
            let end = offset + (item.duration_sec * 1000.0) as u64;
            append_chapter(&mut output, offset, end, &item.display_name());
            offset = end;
        }
    }
    output
}

fn append_chapter(output: &mut String, start: u64, end: u64, title: &str) {
    output.push_str("[CHAPTER]\nTIMEBASE=1/1000\n");
    output.push_str(&format!("START={start}\nEND={end}\n"));
    output.push_str("title=");
    output.push_str(&escape(title));
    output.push_str("\n\n");
}

fn escape(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('\n', "\\n")
        .replace('=', "\\=")
        .replace(';', "\\;")
        .replace('#', "\\#")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    #[test]
    fn generates_chapters_from_audio_files() {
        let audio = vec![
            AudioItem {
                path: PathBuf::from("01.flac"),
                codec: "flac".into(),
                duration_sec: 60.0,
            },
            AudioItem {
                path: PathBuf::from("02.flac"),
                codec: "flac".into(),
                duration_sec: 30.5,
            },
        ];
        let result = generate(None, &audio);
        assert!(result.contains("START=60000\nEND=90500"));
        assert!(result.contains("title=02"));
    }

    #[test]
    fn escapes_ffmetadata_title() {
        assert_eq!(escape("A=B; #1"), "A\\=B\\; \\#1");
    }
}
