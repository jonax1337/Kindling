import type { ChunkSimulation, Preview } from "./chunk-simulation";

/** The camera looks this share of the width ahead of a flying player. */
const LOOK_AHEAD = 0.14;
/** How long a freshly loaded chunk glows, in seconds. */
const FRESH_SECONDS = 0.45;
/** How long a new preview takes to fade in, in seconds. */
const PREVIEW_FADE_SECONDS = 0.3;

const GRASS = ["#3f6b3a", "#47753f", "#386134", "#4d7b44", "#3b6637"];
const WATER = ["#2c5d6e", "#2f6577"];
const SAND = "#a99a64";
const VOID = "#060a08";
const OUTSIDE = "rgba(250,247,239,.035)";
/** A seed preview is the terrain, dimmed and hatched in ember; a preview from the save files keeps its colours. */
const SEED_VEIL = "rgba(6,10,8,.5)";
const HATCH = "rgba(255,138,70,.55)";
const SAVED_OUTLINE = "rgba(250,247,239,.75)";
const VIEW_DISTANCE_RING = "rgba(250,247,239,.28)";
/** Over the hatching of the previews the view distance needs a brighter line. */
const PREVIEWED_VIEW_DISTANCE_RING = "rgba(250,247,239,.7)";
const PREVIEW_REACH_RING = "rgba(255,130,63,.5)";

interface View {
  cell: number;
  originX: number;
  originY: number;
}

interface Box {
  left: number;
  top: number;
  size: number;
  gap: number;
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

const hatches = new WeakMap<CanvasRenderingContext2D, { tile: number; pattern: CanvasPattern }>();

function hatchTile(size: number): HTMLCanvasElement {
  const tile = document.createElement("canvas");
  tile.width = size;
  tile.height = size;
  const context = tile.getContext("2d");
  if (!context) return tile;
  context.strokeStyle = HATCH;
  context.lineWidth = Math.max(1, size * 0.16);
  context.beginPath();
  for (const offset of [-size, 0, size]) {
    context.moveTo(-size, -size + offset);
    context.lineTo(size * 2, size * 2 + offset);
  }
  context.stroke();
  return tile;
}

/** Diagonal ember lines laid on the chunk grid, so they travel with the terrain instead of swimming over it. */
function hatchFor(context: CanvasRenderingContext2D, view: View): CanvasPattern | undefined {
  const tile = Math.max(4, Math.round(view.cell / 2));
  let cached = hatches.get(context);
  if (cached?.tile !== tile) {
    const pattern = context.createPattern(hatchTile(tile), "repeat");
    if (!pattern) return undefined;
    cached = { tile, pattern };
    hatches.set(context, cached);
  }
  const scale = view.cell / 2 / tile;
  cached.pattern.setTransform(new DOMMatrix().translate(view.originX, view.originY).scale(scale));
  return cached.pattern;
}

function fillTile(
  context: CanvasRenderingContext2D,
  fill: string | CanvasPattern,
  { left, top, size, gap }: Box,
) {
  context.fillStyle = fill;
  context.fillRect(left + gap / 2, top + gap / 2, size - gap, size - gap);
}

function drawChunk(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  x: number,
  y: number,
  view: View,
  hatch: CanvasPattern | undefined,
) {
  const box = {
    left: view.originX + x * view.cell,
    top: view.originY + y * view.cell,
    size: view.cell,
    gap: Math.max(1, view.cell * 0.08),
  };
  const loadedAt = simulation.loadedAt(x, y);
  const preview = loadedAt === undefined ? simulation.preview(x, y) : undefined;
  if (loadedAt !== undefined) {
    fillTile(context, terrainColour(x, y), box);
    drawLoadFlash(context, simulation.time - loadedAt, box);
  } else if (preview) {
    drawPreview(context, preview, { age: simulation.time - preview.at, hatch }, box);
  } else {
    drawUnloaded(context, simulation, x, y, box);
  }
}

/** A chunk that has just arrived flashes in ember light. */
function drawLoadFlash(context: CanvasRenderingContext2D, age: number, box: Box) {
  if (age > FRESH_SECONDS) return;
  context.fillStyle = `rgba(255,170,90,${0.85 * (1 - age / FRESH_SECONDS)})`;
  context.fillRect(box.left, box.top, box.size, box.size);
}

/** A preview fades in over the void: exact ones in full colour with an outline, seed ones dimmed and hatched. */
function drawPreview(
  context: CanvasRenderingContext2D,
  preview: Preview,
  look: { age: number; hatch: CanvasPattern | undefined },
  box: Box,
) {
  context.globalAlpha = Math.min(1, look.age / PREVIEW_FADE_SECONDS);
  fillTile(context, terrainColour(preview.x, preview.y), box);
  if (preview.exact) drawSavedOutline(context, box);
  else {
    fillTile(context, SEED_VEIL, box);
    if (look.hatch) fillTile(context, look.hatch, box);
  }
  context.globalAlpha = 1;
}

function drawSavedOutline(context: CanvasRenderingContext2D, box: Box) {
  const width = Math.max(1, box.size * 0.07);
  const inset = box.gap / 2 + width / 2;
  context.strokeStyle = SAVED_OUTLINE;
  context.lineWidth = width;
  context.strokeRect(box.left + inset, box.top + inset, box.size - inset * 2, box.size - inset * 2);
}

/** Required chunks still missing in front of the player glow red: there players fly into the void. */
function drawUnloaded(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  x: number,
  y: number,
  box: Box,
) {
  const required = simulation.isRequired(x, y);
  fillTile(context, required ? VOID : OUTSIDE, box);
  if (simulation.mode === "tiles" || !required || x + 0.5 <= simulation.playerX) return;
  context.strokeStyle = "rgba(255,90,40,.55)";
  context.lineWidth = Math.max(1, box.size * 0.06);
  const inset = box.gap;
  context.strokeRect(box.left + inset, box.top + inset, box.size - inset * 2, box.size - inset * 2);
}

function strokeDashedCircle(
  context: CanvasRenderingContext2D,
  view: View,
  shape: { centreX: number; radius: number; colour: string },
) {
  context.setLineDash([view.cell * 0.3, view.cell * 0.3]);
  context.strokeStyle = shape.colour;
  context.lineWidth = Math.max(1, view.cell * 0.05);
  context.beginPath();
  context.arc(shape.centreX, view.originY, shape.radius * view.cell, 0, Math.PI * 2);
  context.stroke();
}

function drawViewDistance(
  context: CanvasRenderingContext2D,
  simulation: ChunkSimulation,
  view: View,
) {
  const centreX = view.originX + simulation.playerX * view.cell;
  context.save();
  if (simulation.mode === "tiles") {
    const half = (simulation.radius + 0.5) * view.cell;
    context.setLineDash([view.cell * 0.3, view.cell * 0.3]);
    context.strokeStyle = VIEW_DISTANCE_RING;
    context.lineWidth = Math.max(1, view.cell * 0.05);
    context.beginPath();
    context.rect(centreX - half, view.originY - half, half * 2, half * 2);
    context.stroke();
  } else {
    const hasPreviews = simulation.reach > simulation.radius;
    strokeDashedCircle(context, view, {
      centreX,
      radius: simulation.viewRadius,
      colour: hasPreviews ? PREVIEWED_VIEW_DISTANCE_RING : VIEW_DISTANCE_RING,
    });
    if (hasPreviews) {
      strokeDashedCircle(context, view, {
        centreX,
        radius: simulation.reach,
        colour: PREVIEW_REACH_RING,
      });
    }
  }
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
  const columns = simulation.columns;
  const cell = canvas.width / columns;
  const lookAhead = simulation.mode === "tiles" ? 0 : columns * LOOK_AHEAD;
  const cameraX = simulation.playerX + lookAhead;
  const view = { cell, originX: canvas.width / 2 - cameraX * cell, originY: canvas.height / 2 };
  const rows = Math.ceil(canvas.height / cell / 2) + 1;
  const hatch = hatchFor(context, view);
  context.clearRect(0, 0, canvas.width, canvas.height);
  for (let x = Math.floor(cameraX - columns / 2) - 1; x <= Math.ceil(cameraX + columns / 2); x++) {
    for (let y = -rows; y <= rows; y++) drawChunk(context, simulation, x, y, view, hatch);
  }
  drawViewDistance(context, simulation, view);
  drawPlayer(context, simulation, view);
}
