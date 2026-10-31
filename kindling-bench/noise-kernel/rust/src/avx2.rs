//! The AVX2 engine: eight Y values per step, with separate multiply and add
//! instructions in the scalar operation order. There is no fused multiply-add
//! anywhere, so the results are bit-identical to the scalar engine.

use std::arch::x86_64::{
    __m256, _mm256_add_ps, _mm256_loadu_ps, _mm256_mul_ps, _mm256_set1_ps, _mm256_storeu_ps,
    _mm256_sub_ps,
};

use crate::axes::Axes;
use crate::job::Job;
use crate::kernel::{Column, Columns, CornerTracker, interpolate};

const LANES: usize = 8;

/// The corner terms of eight consecutive Y values: `[corner][lane]`.
struct Staged {
    dxz: [[f32; LANES]; 8],
    gy: [[f32; LANES]; 8],
}

/// Runs the job with AVX2.
///
/// # Safety
/// The CPU must support AVX2.
#[target_feature(enable = "avx2")]
pub unsafe fn add_to_volume(job: &Job, values: &mut [f32]) {
    let mut axes = Axes::empty();
    axes.fill(job);
    let p_adj = job.noise.p_adj;
    let amplitude = job.noise.amplitude;
    let (floors_y, alphas_y, relatives_y) = (axes.y.floors(), axes.y.alphas(), axes.y.relatives());
    let (floor_chunks, floor_tail) = floors_y.as_chunks::<LANES>();
    let (alpha_chunks, alpha_tail) = alphas_y.as_chunks::<LANES>();
    let (relative_chunks, relative_tail) = relatives_y.as_chunks::<LANES>();
    let mut tracker = CornerTracker::new();
    for column in Columns::new(&axes, job) {
        tracker.start_column();
        let column_values = &mut values[column.base..column.base + floors_y.len()];
        let (value_chunks, value_tail) = column_values.as_chunks_mut::<LANES>();
        for (((values8, floors8), alphas8), relatives8) in value_chunks
            .iter_mut()
            .zip(floor_chunks)
            .zip(alpha_chunks)
            .zip(relative_chunks)
        {
            let staged = stage(&mut tracker, p_adj, &column, floors8);
            accumulate(&staged, &column, amplitude, (alphas8, relatives8), values8);
        }
        for (((value, &floor_y), &alpha_y), &relative_y) in value_tail
            .iter_mut()
            .zip(floor_tail)
            .zip(alpha_tail)
            .zip(relative_tail)
        {
            let corners = tracker.corners_at(p_adj, &column, floor_y);
            *value += amplitude * interpolate(corners, &column, alpha_y, relative_y);
        }
    }
}

/// Gathers the corner terms of eight Y values, recomputing them only when the Y cell changes.
#[inline(always)]
fn stage(
    tracker: &mut CornerTracker,
    p_adj: &[u16; 256],
    column: &Column,
    floors: &[i32; LANES],
) -> Staged {
    let mut staged = Staged { dxz: [[0.0; LANES]; 8], gy: [[0.0; LANES]; 8] };
    for (lane, &floor_y) in floors.iter().enumerate() {
        let corners = tracker.corners_at(p_adj, column, floor_y);
        for corner in 0..8 {
            staged.dxz[corner][lane] = corners.dxz[corner];
            staged.gy[corner][lane] = corners.gy[corner];
        }
    }
    staged
}

#[target_feature(enable = "avx2")]
fn lerp8(alpha: __m256, p0: __m256, p1: __m256) -> __m256 {
    _mm256_add_ps(p0, _mm256_mul_ps(alpha, _mm256_sub_ps(p1, p0)))
}

/// `values += amplitude * lerp3(...)` for eight Y values, mirroring `interpolate`.
#[target_feature(enable = "avx2")]
fn accumulate(
    staged: &Staged,
    column: &Column,
    amplitude: f32,
    (alphas_y, relatives_y): (&[f32; LANES], &[f32; LANES]),
    values: &mut [f32; LANES],
) {
    // SAFETY: every pointer comes from a live array of exactly eight floats.
    unsafe {
        let relative = _mm256_loadu_ps(relatives_y.as_ptr());
        let relative_minus_one = _mm256_sub_ps(relative, _mm256_set1_ps(1.0));
        let mut terms = [relative; 8];
        for (corner, term) in terms.iter_mut().enumerate() {
            let lane_relative = if corner & 2 == 0 { relative } else { relative_minus_one };
            let gy_term = _mm256_mul_ps(_mm256_loadu_ps(staged.gy[corner].as_ptr()), lane_relative);
            *term = _mm256_add_ps(_mm256_loadu_ps(staged.dxz[corner].as_ptr()), gy_term);
        }
        let alpha_x = _mm256_set1_ps(column.alpha_x);
        let alpha_y = _mm256_loadu_ps(alphas_y.as_ptr());
        let alpha_z = _mm256_set1_ps(column.alpha_z);
        let lower = lerp8(
            alpha_y,
            lerp8(alpha_x, terms[0], terms[1]),
            lerp8(alpha_x, terms[2], terms[3]),
        );
        let upper = lerp8(
            alpha_y,
            lerp8(alpha_x, terms[4], terms[5]),
            lerp8(alpha_x, terms[6], terms[7]),
        );
        let noise = lerp8(alpha_z, lower, upper);
        let scaled = _mm256_mul_ps(_mm256_set1_ps(amplitude), noise);
        let sum = _mm256_add_ps(_mm256_loadu_ps(values.as_ptr()), scaled);
        _mm256_storeu_ps(values.as_mut_ptr(), sum);
    }
}
