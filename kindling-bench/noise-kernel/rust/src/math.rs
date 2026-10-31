//! Scalar helpers copied operation by operation from `Mth` and `GradientNoise`.

/// `Math.nextDown(1.6777216E7)`.
pub const HALF_ROUND_OFF: f64 = f64::from_bits(16_777_216.0_f64.to_bits() - 1);

const ROUND_OFF_PERIOD: f64 = 3.3554432E7;

/// The float literal `1.0E-7F` of `SmearedPerlinNoise`, widened to double as Java does.
pub const SHIFT_UP_EPSILON: f64 = 1.0E-7_f32 as f64;

/// `GradientNoise.wrap` with `DEBUG_ENABLE_FARLANDS == false`.
#[inline(always)]
pub fn wrap(x: f64) -> f64 {
    if x >= -HALF_ROUND_OFF && x < HALF_ROUND_OFF {
        x
    } else {
        x - (x / ROUND_OFF_PERIOD + 0.5).floor() * ROUND_OFF_PERIOD
    }
}

/// `Mth.floor(double)`: `(int)Math.floor(v)`; `as` saturates and maps NaN to 0 like Java.
#[inline(always)]
pub fn floor_to_int(v: f64) -> i32 {
    v.floor() as i32
}

/// `Mth.smoothstep(float)`: `((x * x) * x) * ((x * ((x * 6) - 15)) + 10)`.
#[inline(always)]
pub fn smoothstep(x: f32) -> f32 {
    x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
}

/// `Mth.lerp(float, float, float)`.
#[inline(always)]
pub fn lerp(alpha: f32, p0: f32, p1: f32) -> f32 {
    p0 + alpha * (p1 - p0)
}

/// `Mth.lerp3` for the corners in the order 000, 100, 010, 110, 001, 101, 011, 111.
#[inline(always)]
pub fn lerp3(alpha_x: f32, alpha_y: f32, alpha_z: f32, x: [f32; 8]) -> f32 {
    let lower = lerp2(alpha_x, alpha_y, [x[0], x[1], x[2], x[3]]);
    let upper = lerp2(alpha_x, alpha_y, [x[4], x[5], x[6], x[7]]);
    lerp(alpha_z, lower, upper)
}

#[inline(always)]
fn lerp2(alpha1: f32, alpha2: f32, x: [f32; 4]) -> f32 {
    lerp(alpha2, lerp(alpha1, x[0], x[1]), lerp(alpha1, x[2], x[3]))
}

/// `SmearedPerlinNoise.computeFudgeY`.
#[inline(always)]
pub fn compute_fudge_y(original_y: f64, relative_y: f64, fudge_y_scale: f64) -> f64 {
    let fudge_limit = if original_y >= 0.0 && original_y < relative_y {
        original_y
    } else {
        relative_y
    };
    f64::from(floor_to_int(fudge_limit / fudge_y_scale + SHIFT_UP_EPSILON)) * fudge_y_scale
}
