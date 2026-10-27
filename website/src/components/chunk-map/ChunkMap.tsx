import { useEffect, useRef } from "react";
import { useMediaQuery } from "@/hooks/use-media-query";
import { ChunkSimulation, type LoadingMode } from "./chunk-simulation";
import { drawSimulation } from "./draw-chunk-map";

const MAX_PIXEL_RATIO = 2;
const MAX_STEP_SECONDS = 1 / 20;
const REPORT_EVERY_SECONDS = 0.25;
/** Without motion the map shows one still moment, taken after this much simulated time. */
const STILL_AFTER_SECONDS = 2.5;

const DESCRIPTIONS: Record<LoadingMode, string> = {
  distance:
    "A player flies east; chunks load nearest first, so the terrain ahead arrives late and the player meets missing chunks.",
  directional:
    "A player flies east; chunks ahead load first, so terrain is there before the player arrives.",
  tiles:
    "The pregenerator fills the map tile by tile around a standing player, whose own chunks come first.",
};

function fitToElement(canvas: HTMLCanvasElement) {
  const ratio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO);
  canvas.width = Math.round(canvas.clientWidth * ratio);
  canvas.height = Math.round(canvas.clientHeight * ratio);
}

function stillMoment(mode: LoadingMode): ChunkSimulation {
  const simulation = new ChunkSimulation(mode);
  for (let elapsed = 0; elapsed < STILL_AFTER_SECONDS; elapsed += MAX_STEP_SECONDS) {
    simulation.step(MAX_STEP_SECONDS);
  }
  return simulation;
}

/**
 * Runs the chunk loading model while the map is on screen and reports, a few times a second, the share of chunks
 * still missing ahead of the player.
 */
export function ChunkMap({
  mode,
  onMissingAhead,
}: {
  mode: LoadingMode;
  onMissingAhead?: (share: number) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const reportRef = useRef(onMissingAhead);
  reportRef.current = onMissingAhead;
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const simulation = reducedMotion ? stillMoment(mode) : new ChunkSimulation(mode);
    let frame = 0;
    let visible = false;
    let last = performance.now();
    let sinceReport = REPORT_EVERY_SECONDS;
    const report = (seconds: number) => {
      sinceReport += seconds;
      if (sinceReport < REPORT_EVERY_SECONDS) return;
      sinceReport = 0;
      reportRef.current?.(simulation.missingAhead());
    };
    const draw = (now: number) => {
      frame = 0;
      const seconds = Math.min(MAX_STEP_SECONDS, Math.max(0, now - last) / 1000);
      last = now;
      if (!reducedMotion) simulation.step(seconds);
      drawSimulation(canvas, simulation);
      report(seconds);
      if (visible && !reducedMotion) frame = requestAnimationFrame(draw);
    };
    const schedule = () => {
      if (frame) return;
      last = performance.now();
      frame = requestAnimationFrame(draw);
    };
    const resize = new ResizeObserver(() => {
      fitToElement(canvas);
      schedule();
    });
    const visibility = new IntersectionObserver((entries) => {
      visible = entries.at(-1)?.isIntersecting ?? false;
      schedule();
    });
    resize.observe(canvas);
    visibility.observe(canvas);
    return () => {
      cancelAnimationFrame(frame);
      resize.disconnect();
      visibility.disconnect();
    };
  }, [mode, reducedMotion]);

  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={DESCRIPTIONS[mode]}
      className="block size-full"
    />
  );
}
