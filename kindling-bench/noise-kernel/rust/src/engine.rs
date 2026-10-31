//! Chooses the implementation that runs a job.

use crate::job::{Engine, Job};
use crate::kernel::add_to_volume_scalar;

/// The SIMD levels reported by `kn_simd_level`.
pub const SIMD_LEVEL_SCALAR: i32 = 0;
pub const SIMD_LEVEL_AVX2: i32 = 2;

#[cfg(target_arch = "x86_64")]
fn avx2_available() -> bool {
    is_x86_feature_detected!("avx2")
}

#[cfg(not(target_arch = "x86_64"))]
fn avx2_available() -> bool {
    false
}

pub fn simd_level() -> i32 {
    if avx2_available() { SIMD_LEVEL_AVX2 } else { SIMD_LEVEL_SCALAR }
}

/// `values.len()` must be at least the number of cells of the volume.
pub fn run(engine: Engine, job: &Job, values: &mut [f32]) {
    if engine == Engine::Simd && avx2_available() {
        run_avx2(job, values);
    } else {
        add_to_volume_scalar(job, values);
    }
}

#[cfg(target_arch = "x86_64")]
fn run_avx2(job: &Job, values: &mut [f32]) {
    // SAFETY: the caller checked `avx2_available`.
    unsafe { crate::avx2::add_to_volume(job, values) }
}

#[cfg(not(target_arch = "x86_64"))]
fn run_avx2(job: &Job, values: &mut [f32]) {
    add_to_volume_scalar(job, values);
}
