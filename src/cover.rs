use crate::model::{BackgroundMode, CoverSettings};
use anyhow::{Context, Result};
use image::{imageops::FilterType, ImageBuffer, Rgb, RgbImage};
use std::path::Path;

pub const CANVAS_WIDTH: u32 = 1920;
pub const CANVAS_HEIGHT: u32 = 1080;

pub fn generate(source: &Path, output: &Path, settings: CoverSettings) -> Result<()> {
    let image = image::open(source)
        .with_context(|| format!("无法读取封面：{}", source.display()))?
        .to_rgb8();
    render(&image, settings, CANVAS_WIDTH, CANVAS_HEIGHT)
        .save(output)
        .with_context(|| format!("无法写入封面：{}", output.display()))?;
    Ok(())
}

pub fn render(source: &RgbImage, settings: CoverSettings, width: u32, height: u32) -> RgbImage {
    render_with_filter(source, settings, width, height, FilterType::Lanczos3)
}

pub fn render_preview(
    source: &RgbImage,
    settings: CoverSettings,
    width: u32,
    height: u32,
) -> RgbImage {
    render_with_filter(source, settings, width, height, FilterType::Triangle)
}

fn render_with_filter(
    source: &RgbImage,
    settings: CoverSettings,
    width: u32,
    height: u32,
    filter: FilterType,
) -> RgbImage {
    let settings = settings.normalized();
    let scale = width as f32 / CANVAS_WIDTH as f32;
    let background = match settings.background_mode {
        BackgroundMode::Auto => average_color(source),
        BackgroundMode::Custom => settings.custom_color,
        mode => mode.rgb().unwrap_or(settings.custom_color),
    };
    let blur = settings.shadow_blur * scale;
    let offset = settings.shadow_offset_y * scale;
    let requested_height = settings.image_height * scale;
    let vertical_margin = blur * 2.0 + offset;
    let max_height = (height as f32 - vertical_margin * 2.0).max(1.0) as u32;
    let max_width = (width as f32 - blur * 4.0).max(1.0) as u32;
    let (cover_w, cover_h) = fit_size(
        source.width(),
        source.height(),
        requested_height.min(max_height as f32) as u32,
        max_width,
    );
    let resized = image::imageops::resize(source, cover_w, cover_h, filter);
    let x = (width - cover_w) / 2;
    let y = (height - cover_h) / 2;
    let radius = (settings.corner_radius * scale).min(cover_w.min(cover_h) as f32 / 2.0);
    let mut canvas: RgbImage = ImageBuffer::from_pixel(width, height, Rgb(background));

    if settings.shadow_alpha > 0.0 && (blur > 0.0 || offset > 0.0) {
        apply_shadow(
            &mut canvas,
            x as f32,
            y as f32 + offset,
            cover_w as f32,
            cover_h as f32,
            radius,
            blur,
            settings.shadow_alpha,
        );
    }

    for local_y in 0..cover_h {
        for local_x in 0..cover_w {
            if inside_rounded_rect(local_x as f32, local_y as f32, cover_w, cover_h, radius) {
                canvas.put_pixel(
                    x + local_x,
                    y + local_y,
                    *resized.get_pixel(local_x, local_y),
                );
            }
        }
    }
    canvas
}

fn apply_shadow(
    canvas: &mut RgbImage,
    x: f32,
    y: f32,
    width: f32,
    height: f32,
    radius: f32,
    blur: f32,
    alpha: f32,
) {
    let spread = blur.max(1.0) * 3.0;
    let left = (x - spread).floor().max(0.0) as u32;
    let top = (y - spread).floor().max(0.0) as u32;
    let right = (x + width + spread).ceil().min(canvas.width() as f32) as u32;
    let bottom = (y + height + spread).ceil().min(canvas.height() as f32) as u32;
    let center_x = x + width / 2.0;
    let center_y = y + height / 2.0;
    let inner_half_w = (width / 2.0 - radius).max(0.0);
    let inner_half_h = (height / 2.0 - radius).max(0.0);
    let sigma = blur.max(0.5);
    for py in top..bottom {
        for px in left..right {
            let qx = ((px as f32 + 0.5 - center_x).abs() - inner_half_w).max(0.0);
            let qy = ((py as f32 + 0.5 - center_y).abs() - inner_half_h).max(0.0);
            let distance = (qx * qx + qy * qy).sqrt() - radius;
            let falloff = if distance <= 0.0 {
                1.0
            } else if blur <= 0.0 {
                0.0
            } else {
                (-0.5 * (distance / sigma).powi(2)).exp()
            };
            let opacity = alpha * falloff;
            if opacity > 0.001 {
                let pixel = canvas.get_pixel_mut(px, py);
                for channel in 0..3 {
                    pixel[channel] = (pixel[channel] as f32 * (1.0 - opacity)).round() as u8;
                }
            }
        }
    }
}

fn inside_rounded_rect(x: f32, y: f32, width: u32, height: u32, radius: f32) -> bool {
    if radius <= 0.0 {
        return true;
    }
    let right = width as f32 - 1.0;
    let bottom = height as f32 - 1.0;
    let center_x = if x < radius {
        radius
    } else if x > right - radius {
        right - radius
    } else {
        x
    };
    let center_y = if y < radius {
        radius
    } else if y > bottom - radius {
        bottom - radius
    } else {
        y
    };
    let dx = x - center_x;
    let dy = y - center_y;
    dx * dx + dy * dy <= radius * radius
}

fn fit_size(source_w: u32, source_h: u32, max_h: u32, max_w: u32) -> (u32, u32) {
    if source_w == 0 || source_h == 0 {
        return (1, 1);
    }
    let scale = (max_w as f64 / source_w as f64).min(max_h as f64 / source_h as f64);
    (
        (source_w as f64 * scale).round().max(1.0) as u32,
        (source_h as f64 * scale).round().max(1.0) as u32,
    )
}

fn average_color(image: &RgbImage) -> [u8; 3] {
    let (thumb_w, thumb_h) = fit_size(image.width(), image.height(), 64, 64);
    let thumb = image::imageops::resize(image, thumb_w, thumb_h, FilterType::Triangle);
    let mut sums = [0_u64; 3];
    for pixel in thumb.pixels() {
        for channel in 0..3 {
            sums[channel] += pixel[channel] as u64;
        }
    }
    let count = u64::from(thumb.width()) * u64::from(thumb.height());
    let average = sums.map(|sum| (sum / count.max(1)) as u8);
    average.map(|channel| ((channel as f32 * 0.52).round() as u8).max(28))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fits_square_cover_to_requested_height() {
        assert_eq!(fit_size(1000, 1000, 800, 1800), (800, 800));
    }

    #[test]
    fn rounded_corner_excludes_corner_pixel() {
        assert!(!inside_rounded_rect(0.0, 0.0, 100, 100, 20.0));
        assert!(inside_rounded_rect(20.0, 20.0, 100, 100, 20.0));
    }

    #[test]
    fn custom_background_and_rounded_cover_are_rendered() {
        let source = ImageBuffer::from_pixel(10, 10, Rgb([255, 0, 0]));
        let settings = CoverSettings {
            background_mode: BackgroundMode::Custom,
            custom_color: [10, 20, 30],
            image_height: 80.0,
            corner_radius: 20.0,
            shadow_alpha: 0.0,
            ..CoverSettings::default()
        };
        let result = render(&source, settings, 192, 108);
        assert_eq!(result.get_pixel(0, 0).0, [10, 20, 30]);
        assert_eq!(result.get_pixel(96, 54).0, [255, 0, 0]);
    }

    #[test]
    fn derives_darkened_average_color() {
        let image = ImageBuffer::from_pixel(4, 4, Rgb([100, 150, 200]));
        assert_eq!(average_color(&image), [52, 78, 104]);
    }
}
