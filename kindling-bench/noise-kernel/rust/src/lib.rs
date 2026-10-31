//! Native volume noise kernels for the Kindling native-kernel experiment.
//!
//! This crate is a port of Paper's optimised noise kernels
//! (`PerlinNoise.addToVolume` and `SmearedPerlinNoise.addToVolume` from the
//! Moonrise chunk_gen patches, GPL-3.0). It is meant to be called from Java
//! through Panama FFM and must stay bit-identical to the Java code:
//! the same IEEE operations in the same order, and no fused multiply-add.
//!
//! The scalar engine is the faithful port. The AVX2 engine evaluates eight Y
//! values at a time with separate multiply and add instructions in the scalar
//! operation order.

mod axes;
#[cfg(target_arch = "x86_64")]
mod avx2;
mod engine;
mod ffi;
mod gradient;
mod job;
mod kernel;
mod math;

#[cfg(test)]
mod tests;
