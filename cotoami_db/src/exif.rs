use image::{imageops, DynamicImage};

pub(crate) trait DynamicImageExifExt {
    fn apply_orientation(&mut self, orientation: Orientation);

    fn flipv_in_place(&mut self);

    fn fliph_in_place(&mut self);

    fn rotate180_in_place(&mut self);
}

impl DynamicImageExifExt for DynamicImage {
    fn apply_orientation(&mut self, orientation: Orientation) {
        let image = self;
        match orientation {
            Orientation::NoTransforms => (),
            Orientation::Rotate90 => *image = image.rotate90(),
            Orientation::Rotate180 => image.rotate180_in_place(),
            Orientation::Rotate270 => *image = image.rotate270(),
            Orientation::FlipHorizontal => image.fliph_in_place(),
            Orientation::FlipVertical => image.flipv_in_place(),
            Orientation::Rotate90FlipH => {
                let mut new_image = image.rotate90();
                new_image.fliph_in_place();
                *image = new_image;
            }
            Orientation::Rotate270FlipH => {
                let mut new_image = image.rotate270();
                new_image.fliph_in_place();
                *image = new_image;
            }
        }
    }

    fn flipv_in_place(&mut self) { imageops::flip_vertical_in_place(self) }

    fn fliph_in_place(&mut self) { imageops::flip_horizontal_in_place(self) }

    fn rotate180_in_place(&mut self) { imageops::rotate180_in_place(self) }
}

/// Describes the transformations to be applied to the image.
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub(crate) enum Orientation {
    /// Do not perform any transformations.
    NoTransforms,
    /// Rotate by 90 degrees clockwise.
    Rotate90,
    /// Rotate by 180 degrees. Can be performed in-place.
    Rotate180,
    /// Rotate by 270 degrees clockwise. Equivalent to rotating by 90 degrees counter-clockwise.
    Rotate270,
    /// Flip horizontally. Can be performed in-place.
    FlipHorizontal,
    /// Flip vertically. Can be performed in-place.
    FlipVertical,
    /// Rotate by 90 degrees clockwise and flip horizontally.
    Rotate90FlipH,
    /// Rotate by 270 degrees clockwise and flip horizontally.
    Rotate270FlipH,
}

impl Orientation {
    pub fn from_exif(exif_orientation: u8) -> Option<Self> {
        match exif_orientation {
            1 => Some(Self::NoTransforms),
            2 => Some(Self::FlipHorizontal),
            3 => Some(Self::Rotate180),
            4 => Some(Self::FlipVertical),
            5 => Some(Self::Rotate90FlipH),
            6 => Some(Self::Rotate90),
            7 => Some(Self::Rotate270FlipH),
            8 => Some(Self::Rotate270),
            0 | 9.. => None,
        }
    }
}
