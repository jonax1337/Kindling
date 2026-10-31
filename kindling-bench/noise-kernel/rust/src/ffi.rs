//! The C ABI called through Panama FFM.
//!
//! Every function returns 0 when done, -1 when a size is above the native cap
//! (the caller then uses Java) and -2 for a bad argument. Pointers must be
//! non-null and aligned for their type; nothing is allocated per call.

use std::slice;

use crate::axes::MAX_AXIS;
use crate::engine::{run, simd_level};
use crate::job::{Engine, Job, Noise, Triple, Volume, YAxis};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
enum Status {
    Done = 0,
    TooLarge = -1,
    BadArgument = -2,
}

/// The arguments of one call exactly as they arrive over the C ABI.
struct RawCall {
    values: *mut f32,
    values_len: i32,
    p_adj: *const u16,
    offset: Triple<f64>,
    size: Triple<i32>,
    min: Triple<i32>,
    step: Triple<i32>,
    xz_scale: f64,
    y_scale: f64,
    amplitude: f32,
}

/// Declares one exported kernel entry point. The smeared kernels take the extra
/// trailing `fudge_y_scale`.
macro_rules! export_kernel {
    ($name:ident, $engine:expr) => {
        /// # Safety
        /// `values` must point to `values_len` floats and `p_adj` to 256 `u16`.
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn $name(
            values: *mut f32, values_len: i32, p_adj: *const u16,
            offset_x: f64, offset_y: f64, offset_z: f64,
            size_x: i32, size_y: i32, size_z: i32,
            min_x: i32, min_y: i32, min_z: i32,
            step_x: i32, step_y: i32, step_z: i32,
            xz_scale: f64, y_scale: f64, amplitude: f32,
        ) -> i32 {
            let call = RawCall {
                values, values_len, p_adj,
                offset: Triple { x: offset_x, y: offset_y, z: offset_z },
                size: Triple { x: size_x, y: size_y, z: size_z },
                min: Triple { x: min_x, y: min_y, z: min_z },
                step: Triple { x: step_x, y: step_y, z: step_z },
                xz_scale, y_scale, amplitude,
            };
            // SAFETY: forwarded from the contract of this function.
            unsafe { execute($engine, YAxis::Perlin, &call) }
        }
    };
    ($name:ident, $engine:expr, smeared) => {
        /// # Safety
        /// `values` must point to `values_len` floats and `p_adj` to 256 `u16`.
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn $name(
            values: *mut f32, values_len: i32, p_adj: *const u16,
            offset_x: f64, offset_y: f64, offset_z: f64,
            size_x: i32, size_y: i32, size_z: i32,
            min_x: i32, min_y: i32, min_z: i32,
            step_x: i32, step_y: i32, step_z: i32,
            xz_scale: f64, y_scale: f64, amplitude: f32,
            fudge_y_scale: f64,
        ) -> i32 {
            let call = RawCall {
                values, values_len, p_adj,
                offset: Triple { x: offset_x, y: offset_y, z: offset_z },
                size: Triple { x: size_x, y: size_y, z: size_z },
                min: Triple { x: min_x, y: min_y, z: min_z },
                step: Triple { x: step_x, y: step_y, z: step_z },
                xz_scale, y_scale, amplitude,
            };
            // SAFETY: forwarded from the contract of this function.
            unsafe { execute($engine, YAxis::Smeared { fudge_y_scale }, &call) }
        }
    };
}

export_kernel!(kn_perlin_add_to_volume_scalar, Engine::Scalar);
export_kernel!(kn_perlin_add_to_volume_simd, Engine::Simd);
export_kernel!(kn_smeared_add_to_volume_scalar, Engine::Scalar, smeared);
export_kernel!(kn_smeared_add_to_volume_simd, Engine::Simd, smeared);

/// Returns 0 at once: measures the FFM call overhead with the real signature.
#[unsafe(no_mangle)]
pub extern "C" fn kn_noop(
    _values: *mut f32, _values_len: i32, _p_adj: *const u16,
    _offset_x: f64, _offset_y: f64, _offset_z: f64,
    _size_x: i32, _size_y: i32, _size_z: i32,
    _min_x: i32, _min_y: i32, _min_z: i32,
    _step_x: i32, _step_y: i32, _step_z: i32,
    _xz_scale: f64, _y_scale: f64, _amplitude: f32,
) -> i32 {
    Status::Done as i32
}

/// 0 = scalar only, 2 = AVX2 detected at runtime.
#[unsafe(no_mangle)]
pub extern "C" fn kn_simd_level() -> i32 {
    simd_level()
}

/// # Safety
/// The pointers of `call` must be valid for the lengths the call states.
unsafe fn execute(engine: Engine, y_axis: YAxis, call: &RawCall) -> i32 {
    // SAFETY: forwarded from the contract of this function.
    let status = match unsafe { try_execute(engine, y_axis, call) } {
        Ok(()) => Status::Done,
        Err(status) => status,
    };
    status as i32
}

/// # Safety
/// The pointers of `call` must be valid for the lengths the call states.
unsafe fn try_execute(engine: Engine, y_axis: YAxis, call: &RawCall) -> Result<(), Status> {
    let size = checked_size(call.size)?;
    let cells = size.x * size.y * size.z;
    if cells == 0 {
        return Ok(());
    }
    // SAFETY: forwarded from the contract of this function.
    let values = unsafe { values_slice(call.values, call.values_len, cells)? };
    // SAFETY: forwarded from the contract of this function.
    let p_adj = unsafe { table_ref(call.p_adj)? };
    let job = Job {
        volume: Volume { size, min: call.min, step: call.step },
        noise: Noise {
            p_adj,
            offset: call.offset,
            xz_scale: call.xz_scale,
            y_scale: call.y_scale,
            amplitude: call.amplitude,
        },
        y_axis,
    };
    run(engine, &job, values);
    Ok(())
}

fn checked_size(size: Triple<i32>) -> Result<Triple<usize>, Status> {
    let axes = [size.x, size.y, size.z];
    if axes.iter().any(|&axis| axis < 0) {
        return Err(Status::BadArgument);
    }
    if axes.iter().any(|&axis| axis as usize > MAX_AXIS) {
        return Err(Status::TooLarge);
    }
    Ok(Triple { x: size.x as usize, y: size.y as usize, z: size.z as usize })
}

/// # Safety
/// `values` must be valid for `values_len` floats if it is non-null.
unsafe fn values_slice<'a>(values: *mut f32, values_len: i32, cells: usize) -> Result<&'a mut [f32], Status> {
    if values.is_null() || !values.is_aligned() || (values_len as i64) < cells as i64 {
        return Err(Status::BadArgument);
    }
    // SAFETY: non-null, aligned, and the caller guarantees `values_len` floats.
    Ok(unsafe { slice::from_raw_parts_mut(values, cells) })
}

/// # Safety
/// `p_adj` must be valid for 256 `u16` if it is non-null.
unsafe fn table_ref<'a>(p_adj: *const u16) -> Result<&'a [u16; 256], Status> {
    if p_adj.is_null() || !p_adj.is_aligned() {
        return Err(Status::BadArgument);
    }
    // SAFETY: non-null, aligned, and the caller guarantees 256 entries.
    Ok(unsafe { &*p_adj.cast::<[u16; 256]>() })
}
