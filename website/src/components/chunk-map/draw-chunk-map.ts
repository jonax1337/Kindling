import type { ChunkSimulation } from "./chunk-simulation";

/** Chunks across the width of the map. */
const COLUMNS = 28;
/** How long a freshly loaded chunk glows, in seconds. */
const FRESH_SECONDS = 0.45;
/** The camera looks this share of the width ahead of a flying player. */
const LOOK_AHEAD = 0.14;

const GRASS = ["#3f6b3a", "#47753f", "#386134", "#4d7b44", "#3b6637"];
const WATER = ["#2c5d6e", "#2f6577"];
const SAND = "#a99a64";
const VOID = "#060a08";
const OUTSIDE = "rgba(250,247,239,.035)";

interface View {
  cell: number;
  originX: number;
  originY: number;
}

function hash(x: number, y: number): number {
  let h = (x * 374761393 + y * 668265263) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

/** A top-down Minecraft-ish landscape: meadows with a river that winds along the flight path. */
function terrainColour(x: number, y: number): string {
  const river = Math.abs(Math.sin(x * 0.21) * 4.2 + Math.sin(x * 0.07) * 2 - y);
  const variation = hash(x, y);
  if (river < 1.1) return WATER[variation < 0.5 ? 0 : 1] ?? "#2c5d6e";
  if (river < 1.7) return SAND;
  return GRASS[Math.floor(variation * GRASS.length)] ?? "#3f6b3a";
}

function chunkColour(simulation: ChunkSimulation, x: number, y: number): string {
  if (simulation.loadedAt(x, y) !== undefined) return terrainColour(x, y);
  return simulation.isRequired(x, y) ? VOID : OUTSIDE;
}

function drawChunk(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  x: number,
  y: number,
  view: View,
) {
  const left = view.originX + x * view.cell;
  const top = view.originY + y * view.cell;
  const gap = Math.max(1, view.cell * 0.08);
  context.fillStyle = chunkColour(simulation, x, y);
  context.fillRect(left + gap / 2, top + gap / 2, view.cell - gap, view.cell - gap);
  drawLoadFlash(context, simulation, x, y, { left, top, size: view.cell });
  drawMissingEdge(context, simulation, x, y, { left, top, size: view.cell, gap });
}

/** A chunk that has just arrived flashes in ember light. */
function drawLoadFlash(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  x: number,
  y: number,
  box: { left: number; top: number; size: number },
) {
  const loadedAt = simulation.loadedAt(x, y);
  if (loadedAt === undefined) return;
  const age = simulation.time - loadedAt;
  if (age > FRESH_SECONDS) return;
  context.fillStyle = `rgba(255,170,90,${0.85 * (1 - age / FRESH_SECONDS)})`;
  context.fillRect(box.left, box.top, box.size, box.size);
}

/** Required chunks still missing in front of the player glow red: there players fly into the void. */
function drawMissingEdge(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  x: number,
  y: number,
  box: { left: number; top: number; size: number; gap: number },
) {
  if (simulation.mode === "tiles") return;
  if (simulation.loadedAt(x, y) !== undefined || !simulation.isRequired(x, y)) return;
  if (x + 0.5 <= simulation.playerX) return;
  context.strokeStyle = "rgba(255,90,40,.55)";
  context.lineWidth = Math.max(1, box.size * 0.06);
  const inset = box.gap;
  context.strokeRect(box.left + inset, box.top + inset, box.size - inset * 2, box.size - inset * 2);
}

function drawViewDistance(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  view: View,
) {
  const centreX = view.originX + simulation.playerX * view.cell;
  const centreY = view.originY;
  context.save();
  context.setLineDash([view.cell * 0.3, view.cell * 0.3]);
  context.strokeStyle = "rgba(250,247,239,.28)";
  context.lineWidth = Math.max(1, view.cell * 0.05);
  context.beginPath();
  if (simulation.mode === "tiles") {
    const half = (simulation.radius + 0.5) * view.cell;
    context.rect(centreX - half, centreY - half, half * 2, half * 2);
  } else {
    context.arc(centreX, centreY, simulation.radius * view.cell, 0, Math.PI * 2);
  }
  context.stroke();
  context.restore();
}

/** A white arrow heading east, trailing a streak while it flies. */
function drawPlayer(context: CanvasRenderingContext2D, simulation: ChunkSimulation, view: View) {
  const cell = view.cell;
  context.save();
  context.translate(view.originX + simulation.playerX * cell, view.originY);
  if (simulation.mode !== "tiles") {
    const streak = context.createLinearGradient(-cell * 6, 0, 0, 0);
    streak.addColorStop(0, "rgba(255,130,63,0)");
    streak.addColorStop(1, "rgba(255,200,150,.8)");
    context.fillStyle = streak;
    context.fillRect(-cell * 6, -cell * 0.14, cell * 6, cell * 0.28);
  }
  context.fillStyle = "#faf7ef";
  context.shadowColor = "rgba(255,130,63,1)";
  context.shadowBlur = cell * 1.2;
  context.beginPath();
  context.moveTo(cell * 0.85, 0);
  context.lineTo(-cell * 0.5, -cell * 0.6);
  context.lineTo(-cell * 0.2, 0);
  context.lineTo(-cell * 0.5, cell * 0.6);
  context.closePath();
  context.fill();
  context.restore();
}

/** Draws the area around the player, with the camera following a flying player slightly ahead. */
export function drawSimulation(canvas: HTMLCanvasElement, simulation: ChunkSimulation) {
  const context = canvas.getContext("2d");
  if (!context) return;
  const cell = canvas.width / COLUMNS;
  const lookAhead = simulation.mode === "tiles" ? 0 : COLUMNS * LOOK_AHEAD;
  const cameraX = simulation.playerX + lookAhead;
  const view = { cell, originX: canvas.width / 2 - cameraX * cell, originY: canvas.height / 2 };
  const rows = Math.ceil(canvas.height / cell / 2) + 1;
  context.clearRect(0, 0, canvas.width, canvas.height);
  for (let x = Math.floor(cameraX - COLUMNS / 2) - 1; x <= Math.ceil(cameraX + COLUMNS / 2); x++) {
    for (let y = -rows; y <= rows; y++) drawChunk(context, simulation, x, y, view);
  }
  drawViewDistance(context, simulation, view);
  drawPlayer(context, simulation, view);
}
