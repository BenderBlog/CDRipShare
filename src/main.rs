mod app;
mod chapters;
mod cover;
mod cue;
mod ffmpeg;
mod model;

use app::CDRipShareApp;
use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1040.0, 780.0])
            .with_min_inner_size([900.0, 680.0]),
        ..Default::default()
    };

    eframe::run_native(
        "CDRipShare - Rust",
        options,
        Box::new(|cc| Ok(Box::new(CDRipShareApp::new(cc)))),
    )
}
