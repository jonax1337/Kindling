//! The pieces shared by the scalar and the AVX2 engine, and the scalar engine itself.

use crate::axes::Axes;
use crate::gradient::{dot_xz, grad_y};
use crate::job::Job;
use crate::math::lerp3;

const PERM_MASK: i32 = 255;
const PERM_SHIFT: u32 = 8;
const GRADIENT_MASK: i32 = 15;

/// The eight lattice corners of one cell in the order of `Mth.lerp3`:
/// 000, 100, 010, 110, 001, 101, 011, 111 (the bits are x, y, z).
#[derive(Clone, Copy)]
pub struct Corners {
    /// The X/Z part of the gradient dot product of each corner.
    pub dxz: [f32; 8],
    /// The Y component of the gradient of each corner.
    pub gy: [f32; 8],
}

/// Everything that is constant along one (x, z) column of the volume.
pub struct Column {
    x0: i32,
    x1: i32,
    floor_z: i32,
    rel_x: f32,
    rel_z: f32,
    pub alpha_x: f32,
    pub alpha_z: f32,
    /// Index of the first Y value of the column in the output.
    pub base: usize,
}

#[inline(always)]
fn perm(p_adj: &[u16; 256], index: i32) -> i32 {
    i32::from(p_adj[(index & PERM_MASK) as usize])
}

impl Column {
    /// The hash lookups of the X lattice lines depend only on the column.
    #[inline(always)]
    fn corners(&self, p_adj: &[u16; 256], floor_y: i32) -> Corners {
        let mut corners = Corners { dxz: [0.0; 8], gy: [0.0; 8] };
        for x_bit in 0..2 {
            let (x_hash, rel_x) = if x_bit == 0 {
                (self.x0, self.rel_x)
            } else {
                (self.x1, self.rel_x - 1.0)
            };
            let xy_hashes = perm(p_adj, x_hash.wrapping_add(floor_y));
            for y_bit in 0..2 {
                let xy = if y_bit == 0 { xy_hashes } else { xy_hashes >> PERM_SHIFT };
                let z_hashes = perm(p_adj, xy.wrapping_add(self.floor_z));
                for z_bit in 0..2 {
                    let rel_z = if z_bit == 0 { self.rel_z } else { self.rel_z - 1.0 };
                    let gradient = ((z_hashes >> (PERM_SHIFT * z_bit)) & GRADIENT_MASK) as usize;
                    let corner = (x_bit + 2 * y_bit + 4 * z_bit) as usize;
                    corners.dxz[corner] = dot_xz(gradient, rel_x, rel_z);
                    corners.gy[corner] = grad_y(gradient);
                }
            }
        }
        corners
    }
}

/// Iterates the (x, z) columns with X outer and Z inner, like the Java loops.
pub struct Columns<'a> {
    axes: &'a Axes,
    p_adj: &'a [u16; 256],
    size_y: usize,
    size_z: usize,
    index_x: usize,
    index_z: usize,
}

impl<'a> Columns<'a> {
    #[inline(always)]
    pub fn new(axes: &'a Axes, job: &'a Job) -> Self {
        Self {
            axes,
            p_adj: job.noise.p_adj,
            size_y: job.volume.size.y,
            size_z: job.volume.size.z,
            index_x: 0,
            index_z: 0,
        }
    }
}

impl Iterator for Columns<'_> {
    type Item = Column;

    #[inline(always)]
    fn next(&mut self) -> Option<Column> {
        let size_x = self.axes.x.floors().len();
        if self.index_x >= size_x {
            return None;
        }
        let (index_x, index_z) = (self.index_x, self.index_z);
        self.index_z += 1;
        if self.index_z == self.size_z {
            self.index_z = 0;
            self.index_x += 1;
        }
        // The table is arithmetic mod 256, so the high byte needs no masking.
        let x0 = perm(self.p_adj, self.axes.x.floors()[index_x]);
        Some(Column {
            x0,
            x1: x0 >> PERM_SHIFT,
            floor_z: self.axes.z.floors()[index_z],
            rel_x: self.axes.x.relatives()[index_x],
            rel_z: self.axes.z.relatives()[index_z],
            alpha_x: self.axes.x.alphas()[index_x],
            alpha_z: self.axes.z.alphas()[index_z],
            base: self.size_y * (index_x + index_z * size_x),
        })
    }
}

/// Recomputes the corners only when the Y lattice cell changes within a column.
///
/// Like the Java locals, the corners outlive a column: only `last_floor_y` is
/// reset, so a first `floor_y` of `Integer.MIN_VALUE` would reuse the previous
/// column's corners, exactly as Java does.
pub struct CornerTracker {
    last_floor_y: i32,
    corners: Corners,
}

impl CornerTracker {
    #[inline(always)]
    pub fn new() -> Self {
        Self {
            last_floor_y: i32::MIN,
            corners: Corners { dxz: [0.0; 8], gy: [0.0; 8] },
        }
    }

    #[inline(always)]
    pub fn start_column(&mut self) {
        self.last_floor_y = i32::MIN;
    }

    #[inline(always)]
    pub fn corners_at(&mut self, p_adj: &[u16; 256], column: &Column, floor_y: i32) -> &Corners {
        if self.last_floor_y != floor_y {
            self.last_floor_y = floor_y;
            self.corners = column.corners(p_adj, floor_y);
        }
        &self.corners
    }
}

/// `lerp3` over the eight corner terms `dxz + gy * relativeY` (or `relativeY - 1`).
#[inline(always)]
pub fn interpolate(corners: &Corners, column: &Column, alpha_y: f32, relative_y: f32) -> f32 {
    let relative_y_minus_one = relative_y - 1.0;
    let mut terms = [0.0_f32; 8];
    for (corner, term) in terms.iter_mut().enumerate() {
        let relative = if corner & 2 == 0 { relative_y } else { relative_y_minus_one };
        *term = corners.dxz[corner] + corners.gy[corner] * relative;
    }
    lerp3(column.alpha_x, alpha_y, column.alpha_z, terms)
}

/// The faithful port of `PerlinNoise.addToVolume` / `SmearedPerlinNoise.addToVolume`.
pub fn add_to_volume_scalar(job: &Job, values: &mut [f32]) {
    let mut axes = Axes::empty();
    axes.fill(job);
    let p_adj = job.noise.p_adj;
    let amplitude = job.noise.amplitude;
    let (floors_y, alphas_y, relatives_y) = (axes.y.floors(), axes.y.alphas(), axes.y.relatives());
    let mut tracker = CornerTracker::new();
    for column in Columns::new(&axes, job) {
        tracker.start_column();
        let column_values = &mut values[column.base..column.base + floors_y.len()];
        for (index_y, value) in column_values.iter_mut().enumerate() {
            let corners = tracker.corners_at(p_adj, &column, floors_y[index_y]);
            *value += amplitude * interpolate(corners, &column, alphas_y[index_y], relatives_y[index_y]);
        }
    }
}
