//! Per-axis coordinate caches (`NoiseGradients.CoordCache`), kept on the stack.
//!
//! Only the filled prefix of each cache is ever read, so the arrays stay
//! uninitialised: a call with small sizes must not pay for 36 KiB of zeroing.

use std::mem::MaybeUninit;
use std::slice;

use crate::job::{Job, YAxis};
use crate::math::{compute_fudge_y, smoothstep, wrap};

/// The largest size of one axis the native code accepts.
pub const MAX_AXIS: usize = 1024;

/// What the kernels need to know about one coordinate.
struct AxisSample {
    floor: i32,
    alpha: f32,
    relative: f32,
}

/// The samples of one axis: lattice floor, smoothstep alpha and relative position.
pub struct AxisCache {
    len: usize,
    floor: [MaybeUninit<i32>; MAX_AXIS],
    alpha: [MaybeUninit<f32>; MAX_AXIS],
    relative: [MaybeUninit<f32>; MAX_AXIS],
}

struct AxisSpec {
    count: usize,
    min: i32,
    step: i32,
    scale: f64,
    offset: f64,
}

impl AxisCache {
    #[inline(always)]
    fn empty() -> Self {
        Self {
            len: 0,
            floor: [const { MaybeUninit::uninit() }; MAX_AXIS],
            alpha: [const { MaybeUninit::uninit() }; MAX_AXIS],
            relative: [const { MaybeUninit::uninit() }; MAX_AXIS],
        }
    }

    #[inline(always)]
    pub fn floors(&self) -> &[i32] {
        // SAFETY: `push` initialised the first `len` entries.
        unsafe { slice::from_raw_parts(self.floor.as_ptr().cast::<i32>(), self.len) }
    }

    #[inline(always)]
    pub fn alphas(&self) -> &[f32] {
        // SAFETY: `push` initialised the first `len` entries.
        unsafe { slice::from_raw_parts(self.alpha.as_ptr().cast::<f32>(), self.len) }
    }

    #[inline(always)]
    pub fn relatives(&self) -> &[f32] {
        // SAFETY: `push` initialised the first `len` entries.
        unsafe { slice::from_raw_parts(self.relative.as_ptr().cast::<f32>(), self.len) }
    }

    #[inline(always)]
    fn push(&mut self, sample: AxisSample) {
        self.floor[self.len].write(sample.floor);
        self.alpha[self.len].write(sample.alpha);
        self.relative[self.len].write(sample.relative);
        self.len += 1;
    }

    /// The coordinate advances by repeated addition, exactly like the Java loop.
    #[inline(always)]
    fn fill_with(&mut self, spec: &AxisSpec, sample: impl Fn(f64) -> AxisSample) {
        let mut coordinate = f64::from(spec.min);
        let step = f64::from(spec.step);
        for _ in 0..spec.count {
            self.push(sample(coordinate * spec.scale));
            coordinate += step;
        }
    }

    #[inline(always)]
    fn fill_plain(&mut self, spec: &AxisSpec) {
        let offset = spec.offset;
        self.fill_with(spec, |scaled| plain_sample(scaled, offset));
    }

    #[inline(always)]
    fn fill_smeared(&mut self, spec: &AxisSpec, fudge_y_scale: f64) {
        let offset = spec.offset;
        self.fill_with(spec, |scaled| smeared_sample(scaled, offset, fudge_y_scale));
    }
}

#[inline(always)]
fn plain_sample(scaled: f64, offset: f64) -> AxisSample {
    let value = wrap(scaled) + offset;
    let floor_d = value.floor();
    let relative = (value - floor_d) as f32;
    AxisSample {
        floor: floor_d as i32,
        alpha: smoothstep(relative),
        relative,
    }
}

/// The smeared Y axis keeps `relative` in double, fudges it, and takes the alpha
/// from the unfudged value.
#[inline(always)]
fn smeared_sample(scaled: f64, offset: f64, fudge_y_scale: f64) -> AxisSample {
    let value = wrap(scaled) + offset;
    let floor_d = value.floor();
    let relative = value - floor_d;
    let fudged = (relative - compute_fudge_y(scaled, relative, fudge_y_scale)) as f32;
    AxisSample {
        floor: floor_d as i32,
        alpha: smoothstep(relative as f32),
        relative: fudged,
    }
}

/// The three caches of one call.
pub struct Axes {
    pub x: AxisCache,
    pub y: AxisCache,
    pub z: AxisCache,
}

impl Axes {
    #[inline(always)]
    pub fn empty() -> Self {
        Self {
            x: AxisCache::empty(),
            y: AxisCache::empty(),
            z: AxisCache::empty(),
        }
    }

    #[inline(always)]
    pub fn fill(&mut self, job: &Job) {
        let (volume, noise) = (&job.volume, &job.noise);
        self.x.fill_plain(&AxisSpec {
            count: volume.size.x,
            min: volume.min.x,
            step: volume.step.x,
            scale: noise.xz_scale,
            offset: noise.offset.x,
        });
        let y_spec = AxisSpec {
            count: volume.size.y,
            min: volume.min.y,
            step: volume.step.y,
            scale: noise.y_scale,
            offset: noise.offset.y,
        };
        match job.y_axis {
            YAxis::Perlin => self.y.fill_plain(&y_spec),
            YAxis::Smeared { fudge_y_scale } => self.y.fill_smeared(&y_spec, fudge_y_scale),
        }
        self.z.fill_plain(&AxisSpec {
            count: volume.size.z,
            min: volume.min.z,
            step: volume.step.z,
            scale: noise.xz_scale,
            offset: noise.offset.z,
        });
    }
}
