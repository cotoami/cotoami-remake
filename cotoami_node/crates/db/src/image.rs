use std::{borrow::Cow, io::Cursor};

use anyhow::Result;
use image::{
    imageops::FilterType, metadata::Orientation, DynamicImage, ImageDecoder, ImageFormat,
    ImageReader,
};
use tracing::debug;

pub(crate) fn process_image<'a>(
    image_bytes: Cow<'a, [u8]>,
    max_size: Option<u32>,
    format: Option<ImageFormat>,
) -> Result<Cow<'a, [u8]>> {
    let mut decoder = ImageReader::new(Cursor::new(image_bytes.as_ref()))
        .with_guessed_format()?
        .into_decoder()?;
    let orientation = decoder.orientation()?;
    let mut image = DynamicImage::from_decoder(decoder)?;
    let new_size = determine_new_size(&image, max_size);

    // Return the input bytes as is if no processing is needed.
    if matches!(orientation, Orientation::NoTransforms) && new_size.is_none() && format.is_none() {
        debug!("No processing is needed for the image.");
        return Ok(image_bytes);
    }

    // Apply Exif orientation to the image
    debug!("Applying Exif orientation {orientation:?} ...");
    image.apply_orientation(orientation);

    // Resize the image if it is larger than the max_size
    if let Some(new_size) = new_size {
        debug!(
            "Resizing an image ({} * {}) to fit within the bounds ({new_size})",
            image.width(),
            image.height()
        );
        image = image.resize(new_size, new_size, FilterType::Lanczos3);
    }

    // Return the bytes of the resized image.
    let format = if let Some(format) = format {
        format
    } else {
        image::guess_format(image_bytes.as_ref())?
    };
    let mut bytes: Vec<u8> = Vec::new();
    image.write_to(&mut Cursor::new(&mut bytes), format)?;
    Ok(Cow::from(bytes))
}

pub(crate) fn determine_new_size(image: &DynamicImage, max_size: Option<u32>) -> Option<u32> {
    if let Some(max_size) = max_size {
        if image.width() > max_size || image.height() > max_size {
            Some(max_size)
        } else {
            None
        }
    } else {
        None
    }
}
