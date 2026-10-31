//! The description of one `addToVolume` call, free of any FFI types.

/// A value per axis.
#[derive(Clone, Copy, Debug)]
pub struct Triple<T> {
    pub x: T,
    pub y: T,
    pub z: T,
}

/// `DensityVolume`: the sampled grid. The index of a point is `y + size.y * (x + z * size.x)`.
#[derive(Clone, Copy, Debug)]
pub struct Volume {
    pub size: Triple<usize>,
    pub min: Triple<i32>,
    pub step: Triple<i32>,
}

/// The noise instance and the scales of this call.
pub struct Noise<'a> {
    /// The dual permutation table `p(i) | p(i + 1) << 8` (`PerlinNoise.pAdj`).
    pub p_adj: &'a [u16; 256],
    pub offset: Triple<f64>,
    pub xz_scale: f64,
    pub y_scale: f64,
    pub amplitude: f32,
}

/// How the Y axis is sampled: plain Perlin or the smeared variant of `BlendedNoise`.
#[derive(Clone, Copy, Debug)]
pub enum YAxis {
    Perlin,
    Smeared { fudge_y_scale: f64 },
}

pub struct Job<'a> {
    pub volume: Volume,
    pub noise: Noise<'a>,
    pub y_axis: YAxis,
}

/// Which implementation runs the job.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Engine {
    Scalar,
    Simd,
}
