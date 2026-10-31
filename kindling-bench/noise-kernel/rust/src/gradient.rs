//! The 16 gradient vectors of `GradientNoise.GRADIENT` as floats
//! (`NoiseGradients.GRADIENTF_FLAT`).

const GRADIENTS: [[f32; 3]; 16] = [
    [1.0, 1.0, 0.0],
    [-1.0, 1.0, 0.0],
    [1.0, -1.0, 0.0],
    [-1.0, -1.0, 0.0],
    [1.0, 0.0, 1.0],
    [-1.0, 0.0, 1.0],
    [1.0, 0.0, -1.0],
    [-1.0, 0.0, -1.0],
    [0.0, 1.0, 1.0],
    [0.0, -1.0, 1.0],
    [0.0, 1.0, -1.0],
    [0.0, -1.0, -1.0],
    [1.0, 1.0, 0.0],
    [0.0, -1.0, 1.0],
    [-1.0, 1.0, 0.0],
    [0.0, -1.0, -1.0],
];

/// `NoiseGradients.dotXz`: `gx * x + gz * z`.
#[inline(always)]
pub fn dot_xz(hash: usize, x: f32, z: f32) -> f32 {
    let gradient = &GRADIENTS[hash & 15];
    gradient[0] * x + gradient[2] * z
}

/// `NoiseGradients.gradY`.
#[inline(always)]
pub fn grad_y(hash: usize) -> f32 {
    GRADIENTS[hash & 15][1]
}

/// `NoiseGradients.dot`: `gx * x + gy * y + gz * z`. Used by the test reference only.
#[cfg(test)]
pub fn dot(hash: usize, x: f32, y: f32, z: f32) -> f32 {
    let gradient = &GRADIENTS[hash & 15];
    gradient[0] * x + gradient[1] * y + gradient[2] * z
}
