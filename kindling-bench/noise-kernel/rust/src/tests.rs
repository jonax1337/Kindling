//! Tests: hand-checked helpers, scalar against a naive reference of the vanilla
//! (unoptimised) Java loops, SIMD against scalar bit for bit, and the C ABI.

use crate::engine::{SIMD_LEVEL_AVX2, SIMD_LEVEL_SCALAR, run, simd_level};
use crate::ffi::*;
use crate::gradient::dot;
use crate::job::{Engine, Job, Noise, Triple, Volume, YAxis};
use crate::math::{
    HALF_ROUND_OFF, SHIFT_UP_EPSILON, compute_fudge_y, floor_to_int, lerp, lerp3, smoothstep, wrap,
};

// ---------------------------------------------------------------------------
// Hand-checked values, derived from the Java formulas
// ---------------------------------------------------------------------------

#[test]
fn smoothstep_matches_the_java_polynomial() {
    assert_eq!(smoothstep(0.0), 0.0);
    assert_eq!(smoothstep(1.0), 1.0); // 1 * (1 * (6 - 15) + 10) = 1
    assert_eq!(smoothstep(0.5), 0.5); // 0.125 * (0.5 * (3 - 15) + 10) = 0.125 * 4
    assert_eq!(smoothstep(0.25), 0.103515625); // 0.015625 * (0.25 * -13.5 + 10) = 0.015625 * 6.625
}

#[test]
fn lerp_and_lerp3_match_the_java_formulas() {
    assert_eq!(lerp(0.25, 2.0, 6.0), 3.0);
    // The corner values 000..111 are x + 2y + 4z, so the centre of the cell is 3.5.
    let corners = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0];
    assert_eq!(lerp3(0.5, 0.5, 0.5, corners), 3.5);
    assert_eq!(lerp3(0.0, 0.0, 0.0, corners), 0.0);
    assert_eq!(lerp3(1.0, 1.0, 1.0, corners), 7.0);
    assert_eq!(lerp3(1.0, 0.0, 0.0, corners), 1.0);
    assert_eq!(lerp3(0.0, 1.0, 0.0, corners), 2.0);
    assert_eq!(lerp3(0.0, 0.0, 1.0, corners), 4.0);
}

#[test]
fn wrap_leaves_the_safe_range_and_folds_the_rest() {
    assert_eq!(HALF_ROUND_OFF, 16_777_215.999999998);
    assert_eq!(wrap(1.0e7), 1.0e7);
    assert_eq!(wrap(-1.0e7), -1.0e7);
    assert_eq!(wrap(HALF_ROUND_OFF - 1.0), HALF_ROUND_OFF - 1.0);
    assert_eq!(wrap(-HALF_ROUND_OFF), -HALF_ROUND_OFF);
    // 2e7 / 3.3554432e7 + 0.5 floors to 1.
    assert_eq!(wrap(2.0e7), 2.0e7 - 33_554_432.0);
    // -2e7 / 3.3554432e7 + 0.5 = -0.096 floors to -1.
    assert_eq!(wrap(-2.0e7), -2.0e7 + 33_554_432.0);
    // 16777216 is the first value outside the range: 0.5 + 0.5 floors to 1.
    assert_eq!(wrap(16_777_216.0), -16_777_216.0);
    assert!(wrap(f64::NAN).is_nan());
}

#[test]
fn floor_to_int_saturates_and_maps_nan_to_zero() {
    assert_eq!(floor_to_int(-0.5), -1);
    assert_eq!(floor_to_int(2.75), 2);
    assert_eq!(floor_to_int(-3.0), -3);
    assert_eq!(floor_to_int(f64::NAN), 0);
    assert_eq!(floor_to_int(1.0e30), i32::MAX);
    assert_eq!(floor_to_int(-1.0e30), i32::MIN);
}

#[test]
fn fudge_epsilon_is_the_widened_float_literal() {
    assert_eq!(SHIFT_UP_EPSILON, 1.0000000116860974e-7);
    assert_ne!(SHIFT_UP_EPSILON, 1.0e-7);
}

#[test]
fn compute_fudge_y_picks_the_limit_and_floors_in_steps() {
    // original >= 0 but not below relative: the limit is relative. 0.4 / 0.25 = 1.6 floors to 1.
    assert_eq!(compute_fudge_y(0.9, 0.4, 0.25), 0.25);
    // original in [0, relative): the limit is original. 0.3 / 0.25 = 1.2 floors to 1.
    assert_eq!(compute_fudge_y(0.3, 0.4, 0.25), 0.25);
    // negative original: the limit is relative.
    assert_eq!(compute_fudge_y(-5.0, 0.2, 0.25), 0.0);
    // The epsilon lifts a hair below a step: (0.5 - 1e-8) / 0.25 = 2 - 4e-8, plus 1e-7 floors to 2.
    assert_eq!(compute_fudge_y(-1.0, 0.5 - 1.0e-8, 0.25), 0.5);
    // A zero scale divides by zero: floor saturates, and the product is 0 again.
    assert_eq!(compute_fudge_y(-1.0, 0.5, 0.0), 0.0);
}

// ---------------------------------------------------------------------------
// Random cases
// ---------------------------------------------------------------------------

struct Rng(u64);

impl Rng {
    fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }

    fn unit(&mut self) -> f64 {
        (self.next_u64() >> 11) as f64 / (1u64 << 53) as f64
    }

    fn range(&mut self, low: f64, high: f64) -> f64 {
        low + (high - low) * self.unit()
    }

    fn below(&mut self, bound: usize) -> usize {
        (self.next_u64() % bound as u64) as usize
    }

    fn pick<T: Copy>(&mut self, choices: &[T]) -> T {
        choices[self.below(choices.len())]
    }
}

/// `GradientNoise`: a shuffled byte permutation and the dual table derived from it.
fn random_p_adj(rng: &mut Rng) -> [u16; 256] {
    let mut perms: Vec<u16> = (0..256).collect();
    for index in (1..256).rev() {
        perms.swap(index, rng.below(index + 1));
    }
    let mut p_adj = [0u16; 256];
    for (index, entry) in p_adj.iter_mut().enumerate() {
        *entry = perms[index] | (perms[(index + 1) & 255] << 8);
    }
    p_adj
}

struct Case {
    p_adj: [u16; 256],
    offset: Triple<f64>,
    volume: Volume,
    xz_scale: f64,
    y_scale: f64,
    amplitude: f32,
    y_axis: YAxis,
}

impl Case {
    fn cells(&self) -> usize {
        self.volume.size.x * self.volume.size.y * self.volume.size.z
    }

    fn job(&self) -> Job<'_> {
        Job {
            volume: self.volume,
            noise: Noise {
                p_adj: &self.p_adj,
                offset: self.offset,
                xz_scale: self.xz_scale,
                y_scale: self.y_scale,
                amplitude: self.amplitude,
            },
            y_axis: self.y_axis,
        }
    }
}

fn random_min(rng: &mut Rng) -> i32 {
    match rng.below(4) {
        0 => rng.range(-64.0, 64.0) as i32,
        1 => rng.range(-100_000.0, 100_000.0) as i32,
        2 => rng.range(-2.0e9, 2.0e9) as i32,
        _ => rng.pick(&[i32::MIN, i32::MAX - 500, 33_554_432, -33_554_432, 16_777_216, -16_777_216]),
    }
}

fn random_scale(rng: &mut Rng) -> f64 {
    match rng.below(3) {
        0 => rng.range(0.0005, 0.05),
        1 => rng.range(0.05, 5.0),
        _ => rng.range(5.0, 700.0),
    }
}

fn random_case(rng: &mut Rng, size: Triple<usize>, smeared: bool) -> Case {
    let steps = [1, 4, 8, 2, -3, 0];
    let y_axis = if smeared {
        YAxis::Smeared { fudge_y_scale: rng.pick(&[0.0625, 0.25, 0.5, 1.0, 3.0, -2.0, 0.0]) }
    } else {
        YAxis::Perlin
    };
    Case {
        p_adj: random_p_adj(rng),
        offset: Triple { x: rng.range(0.0, 256.0), y: rng.range(0.0, 256.0), z: rng.range(0.0, 256.0) },
        volume: Volume {
            size,
            min: Triple { x: random_min(rng), y: random_min(rng), z: random_min(rng) },
            step: Triple { x: rng.pick(&steps), y: rng.pick(&steps), z: rng.pick(&steps) },
        },
        xz_scale: random_scale(rng),
        y_scale: random_scale(rng),
        amplitude: rng.range(-2.0, 2.0) as f32,
        y_axis,
    }
}

/// The sizes of the brief on every axis, with small companions on the other axes,
/// plus a few mixed volumes.
fn test_sizes(rng: &mut Rng) -> Vec<Triple<usize>> {
    let companions = [1, 3, 7, 8, 9, 16, 17];
    let mut sizes = Vec::new();
    for big in [1, 7, 49, 384] {
        for axis in 0..3 {
            let mut size = [rng.pick(&companions), rng.pick(&companions), rng.pick(&companions)];
            size[axis] = big;
            sizes.push(Triple { x: size[0], y: size[1], z: size[2] });
        }
    }
    sizes.push(Triple { x: 49, y: 7, z: 49 });
    sizes.push(Triple { x: 5, y: 49, z: 5 });
    sizes.push(Triple { x: 17, y: 384, z: 3 });
    sizes.push(Triple { x: 1, y: 1, z: 1 });
    sizes
}

fn random_values(rng: &mut Rng, len: usize) -> Vec<f32> {
    (0..len).map(|_| rng.range(-1.0, 1.0) as f32).collect()
}

fn run_engine(engine: Engine, case: &Case, initial: &[f32]) -> Vec<f32> {
    let mut values = initial.to_vec();
    run(engine, &case.job(), &mut values);
    values
}

fn assert_same_bits(expected: &[f32], actual: &[f32], what: &str) {
    assert_eq!(expected.len(), actual.len(), "{what}: length");
    if let Some(index) = (0..expected.len()).find(|&i| expected[i].to_bits() != actual[i].to_bits()) {
        panic!(
            "{what}: first difference at {index}: expected {:e} ({:#010x}) actual {:e} ({:#010x})",
            expected[index],
            expected[index].to_bits(),
            actual[index],
            actual[index].to_bits()
        );
    }
}

// ---------------------------------------------------------------------------
// Naive reference: the vanilla loops of PerlinNoise.addToVolumeVanilla and
// SmearedPerlinNoise.addToVolumeVanilla, every coordinate recomputed per point
// and the full three-term gradient dot product.
// ---------------------------------------------------------------------------

fn vanilla_permute(p_adj: &[u16; 256], index: i32) -> i32 {
    i32::from(p_adj[(index & 255) as usize] & 255)
}

fn vanilla_gradient_dot(hash: i32, x: f32, y: f32, z: f32) -> f32 {
    dot((hash & 15) as usize, x, y, z)
}

/// The block coordinate of an index; exact in double, so it cannot overflow like int math.
fn block(min: i32, step: i32, index: usize) -> f64 {
    f64::from(min) + index as f64 * f64::from(step)
}

fn vanilla(case: &Case, values: &mut [f32]) {
    let (p, volume) = (&case.p_adj, &case.volume);
    for index_z in 0..volume.size.z {
        let z = wrap(block(volume.min.z, volume.step.z, index_z) * case.xz_scale) + case.offset.z;
        let floor_z = floor_to_int(z);
        let relative_z = (z - f64::from(floor_z)) as f32;
        let alpha_z = smoothstep(relative_z);
        for index_x in 0..volume.size.x {
            let x = wrap(block(volume.min.x, volume.step.x, index_x) * case.xz_scale) + case.offset.x;
            let floor_x = floor_to_int(x);
            let relative_x = (x - f64::from(floor_x)) as f32;
            let alpha_x = smoothstep(relative_x);
            let x0 = vanilla_permute(p, floor_x);
            let x1 = vanilla_permute(p, floor_x.wrapping_add(1));
            for index_y in 0..volume.size.y {
                let original_y = block(volume.min.y, volume.step.y, index_y) * case.y_scale;
                let y = wrap(original_y) + case.offset.y;
                let floor_y = floor_to_int(y);
                let relative_y = y - f64::from(floor_y);
                let (alpha_y, used_y) = match case.y_axis {
                    YAxis::Perlin => {
                        let relative = relative_y as f32;
                        (smoothstep(relative), relative)
                    }
                    YAxis::Smeared { fudge_y_scale } => (
                        smoothstep(relative_y as f32),
                        (relative_y - compute_fudge_y(original_y, relative_y, fudge_y_scale)) as f32,
                    ),
                };
                let xy00 = vanilla_permute(p, x0.wrapping_add(floor_y));
                let xy01 = vanilla_permute(p, x0.wrapping_add(floor_y).wrapping_add(1));
                let xy10 = vanilla_permute(p, x1.wrapping_add(floor_y));
                let xy11 = vanilla_permute(p, x1.wrapping_add(floor_y).wrapping_add(1));
                let hash = |xy: i32, dz: i32| vanilla_permute(p, xy.wrapping_add(floor_z).wrapping_add(dz));
                let d000 = vanilla_gradient_dot(hash(xy00, 0), relative_x, used_y, relative_z);
                let d100 = vanilla_gradient_dot(hash(xy10, 0), relative_x - 1.0, used_y, relative_z);
                let d010 = vanilla_gradient_dot(hash(xy01, 0), relative_x, used_y - 1.0, relative_z);
                let d110 = vanilla_gradient_dot(hash(xy11, 0), relative_x - 1.0, used_y - 1.0, relative_z);
                let d001 = vanilla_gradient_dot(hash(xy00, 1), relative_x, used_y, relative_z - 1.0);
                let d101 = vanilla_gradient_dot(hash(xy10, 1), relative_x - 1.0, used_y, relative_z - 1.0);
                let d011 = vanilla_gradient_dot(hash(xy01, 1), relative_x, used_y - 1.0, relative_z - 1.0);
                let d111 = vanilla_gradient_dot(hash(xy11, 1), relative_x - 1.0, used_y - 1.0, relative_z - 1.0);
                let noise = lerp3(alpha_x, alpha_y, alpha_z, [d000, d100, d010, d110, d001, d101, d011, d111]);
                let index = index_y + volume.size.y * (index_x + index_z * volume.size.x);
                values[index] += case.amplitude * noise;
            }
        }
    }
}

fn check_against_vanilla(smeared: bool, seed: u64, rounds: usize) {
    let mut rng = Rng(seed);
    for size in test_sizes(&mut rng) {
        for _ in 0..rounds {
            let case = random_case(&mut rng, size, smeared);
            let initial = random_values(&mut rng, case.cells());
            let mut expected = initial.clone();
            vanilla(&case, &mut expected);
            let actual = run_engine(Engine::Scalar, &case, &initial);
            assert_same_bits(&expected, &actual, &format!("scalar vs vanilla, size {size:?}"));
        }
    }
}

#[test]
fn perlin_scalar_matches_the_vanilla_loops() {
    check_against_vanilla(false, 0x5EED_0001, 6);
}

#[test]
fn smeared_scalar_matches_the_vanilla_loops() {
    check_against_vanilla(true, 0x5EED_0002, 6);
}

// ---------------------------------------------------------------------------
// SIMD against scalar
// ---------------------------------------------------------------------------

fn check_simd_equals_scalar(smeared: bool, seed: u64, rounds: usize) {
    let mut rng = Rng(seed);
    for size in test_sizes(&mut rng) {
        for _ in 0..rounds {
            let case = random_case(&mut rng, size, smeared);
            let initial = random_values(&mut rng, case.cells());
            let scalar = run_engine(Engine::Scalar, &case, &initial);
            let simd = run_engine(Engine::Simd, &case, &initial);
            assert_same_bits(&scalar, &simd, &format!("simd vs scalar, size {size:?}"));
        }
    }
}

#[test]
fn perlin_simd_equals_scalar_bit_for_bit() {
    check_simd_equals_scalar(false, 0x5EED_0003, 40);
}

#[test]
fn smeared_simd_equals_scalar_bit_for_bit() {
    check_simd_equals_scalar(true, 0x5EED_0004, 40);
}

#[test]
fn simd_equals_scalar_for_every_y_size_up_to_the_cap_neighbourhood() {
    let mut rng = Rng(0x5EED_0005);
    for size_y in (1..=40).chain([63, 64, 65, 127, 128, 129, 1023, 1024]) {
        for smeared in [false, true] {
            let case = random_case(&mut rng, Triple { x: 2, y: size_y, z: 3 }, smeared);
            let initial = random_values(&mut rng, case.cells());
            let scalar = run_engine(Engine::Scalar, &case, &initial);
            let simd = run_engine(Engine::Simd, &case, &initial);
            assert_same_bits(&scalar, &simd, &format!("size_y {size_y} smeared {smeared}"));
        }
    }
}

#[test]
fn the_noise_actually_changes_the_buffer() {
    let mut rng = Rng(0x5EED_0006);
    let case = random_case(&mut rng, Triple { x: 5, y: 17, z: 5 }, false);
    let zeros = vec![0.0; case.cells()];
    let result = run_engine(Engine::Scalar, &case, &zeros);
    assert!(result.iter().any(|&value| value != 0.0));
    assert!(result.iter().all(|value| value.is_finite() && value.abs() < 4.0));
}

// ---------------------------------------------------------------------------
// The C ABI
// ---------------------------------------------------------------------------

struct Call {
    values: Vec<f32>,
    p_adj: [u16; 256],
    size: [i32; 3],
    values_len: i32,
}

impl Call {
    fn new(size: [i32; 3]) -> Self {
        let mut rng = Rng(7);
        let cells = size.iter().map(|&axis| axis.max(0) as usize).product::<usize>();
        Self { values: vec![0.0; cells], p_adj: random_p_adj(&mut rng), size, values_len: cells as i32 }
    }

    fn perlin(&mut self, kernel: unsafe extern "C" fn(
        *mut f32, i32, *const u16, f64, f64, f64, i32, i32, i32, i32, i32, i32, i32, i32, i32, f64, f64, f32,
    ) -> i32) -> i32 {
        let [size_x, size_y, size_z] = self.size;
        // SAFETY: the buffers outlive the call and match the stated lengths.
        unsafe {
            kernel(
                self.values.as_mut_ptr(), self.values_len, self.p_adj.as_ptr(),
                1.5, 2.5, 3.5, size_x, size_y, size_z, 10, 20, 30, 4, 4, 4, 0.01, 0.02, 1.0,
            )
        }
    }
}

#[test]
fn done_returns_zero_and_writes_values() {
    for kernel in [kn_perlin_add_to_volume_scalar, kn_perlin_add_to_volume_simd] {
        let mut call = Call::new([5, 9, 5]);
        assert_eq!(call.perlin(kernel), 0);
        assert!(call.values.iter().any(|&value| value != 0.0));
    }
}

#[test]
fn smeared_entry_points_return_zero() {
    for kernel in [kn_smeared_add_to_volume_scalar, kn_smeared_add_to_volume_simd] {
        let mut call = Call::new([5, 9, 5]);
        let [size_x, size_y, size_z] = call.size;
        // SAFETY: the buffers outlive the call and match the stated lengths.
        let status = unsafe {
            kernel(
                call.values.as_mut_ptr(), call.values_len, call.p_adj.as_ptr(),
                1.5, 2.5, 3.5, size_x, size_y, size_z, 10, 20, 30, 4, 4, 4, 0.01, 0.02, 1.0, 0.25,
            )
        };
        assert_eq!(status, 0);
        assert!(call.values.iter().any(|&value| value != 0.0));
    }
}

#[test]
fn a_size_above_the_cap_returns_minus_one_and_leaves_the_buffer_alone() {
    let mut call = Call::new([1, 1, 1]);
    call.size = [1025, 1, 1];
    call.values_len = 1025;
    call.values = vec![0.0; 1025];
    assert_eq!(call.perlin(kn_perlin_add_to_volume_scalar), -1);
    assert!(call.values.iter().all(|&value| value == 0.0));
    call.size = [1, 1025, 1];
    assert_eq!(call.perlin(kn_perlin_add_to_volume_simd), -1);
    call.size = [1, 1, 1025];
    assert_eq!(call.perlin(kn_perlin_add_to_volume_simd), -1);
}

#[test]
fn the_cap_itself_is_accepted() {
    let mut call = Call::new([1024, 1, 1]);
    assert_eq!(call.perlin(kn_perlin_add_to_volume_simd), 0);
}

#[test]
fn bad_arguments_return_minus_two() {
    let mut call = Call::new([4, 4, 4]);
    call.values_len = 63;
    assert_eq!(call.perlin(kn_perlin_add_to_volume_scalar), -2, "values_len too small");
    call.values_len = -1;
    assert_eq!(call.perlin(kn_perlin_add_to_volume_scalar), -2, "negative values_len");
    call.values_len = 64;
    call.size = [4, -1, 4];
    assert_eq!(call.perlin(kn_perlin_add_to_volume_simd), -2, "negative size");

    let mut call = Call::new([4, 4, 4]);
    // SAFETY: the null pointers are rejected before any access.
    let status = unsafe {
        kn_perlin_add_to_volume_scalar(
            std::ptr::null_mut(), 64, call.p_adj.as_ptr(),
            0.0, 0.0, 0.0, 4, 4, 4, 0, 0, 0, 1, 1, 1, 1.0, 1.0, 1.0,
        )
    };
    assert_eq!(status, -2, "null values");
    // SAFETY: the null pointers are rejected before any access.
    let status = unsafe {
        kn_perlin_add_to_volume_scalar(
            call.values.as_mut_ptr(), 64, std::ptr::null(),
            0.0, 0.0, 0.0, 4, 4, 4, 0, 0, 0, 1, 1, 1, 1.0, 1.0, 1.0,
        )
    };
    assert_eq!(status, -2, "null table");
}

#[test]
fn an_empty_volume_is_done() {
    let mut call = Call::new([0, 4, 4]);
    assert_eq!(call.perlin(kn_perlin_add_to_volume_scalar), 0);
}

#[test]
fn noop_returns_zero_at_once() {
    let status = kn_noop(
        std::ptr::null_mut(), 0, std::ptr::null(),
        0.0, 0.0, 0.0, 99_999, 99_999, 99_999, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0,
    );
    assert_eq!(status, 0);
}

#[test]
fn simd_level_is_zero_or_two_and_matches_the_cpu() {
    let level = kn_simd_level();
    assert!(level == SIMD_LEVEL_SCALAR || level == SIMD_LEVEL_AVX2);
    assert_eq!(level, simd_level());
    #[cfg(target_arch = "x86_64")]
    assert_eq!(level == SIMD_LEVEL_AVX2, is_x86_feature_detected!("avx2"));
}
