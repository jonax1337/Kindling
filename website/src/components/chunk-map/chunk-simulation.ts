/**
 * A small top-down model of chunk loading: a player flies east, the server can load only so many chunks per second,
 * and the loading order decides whether the terrain ahead is there in time. An illustration, not a benchmark.
 */
export type LoadingMode = "distance" | "directional" | "tiles";

interface ModeSettings {
  /** Flight speed in chunks per second. */
  speed: number;
  /** Chunks the server finishes per second. */
  capacity: number;
  /** View distance (flight) or half the side of the pregenerated square (tiles), in chunks. */
  radius: number;
}

const SETTINGS: Record<LoadingMode, ModeSettings> = {
  distance: { speed: 7, capacity: 110, radius: 9 },
  directional: { speed: 7, capacity: 110, radius: 9 },
  tiles: { speed: 0, capacity: 80, radius: 9 },
};
const DIRECTIONAL_WEIGHT = 0.75;
const TILE = 4;
const PLAYER_RADIUS = 2;
const UNLOAD_MARGIN = 4;
const RESTART_AFTER_SECONDS = 1.6;
/** tan 30°: the half-angle of the flight path. */
const FLIGHT_PATH_SLOPE = Math.tan(Math.PI / 6);

function key(x: number, y: number): string {
  return `${x},${y}`;
}

export class ChunkSimulation {
  readonly mode: LoadingMode;
  readonly radius: number;
  playerX = 0;
  time = 0;
  readonly #settings: ModeSettings;
  readonly #loadedAt = new Map<string, number>();
  #budget = 0;
  #completedAt: number | undefined;

  constructor(mode: LoadingMode) {
    this.mode = mode;
    this.#settings = SETTINGS[mode];
    this.radius = this.#settings.radius;
  }

  step(seconds: number) {
    this.time += seconds;
    this.playerX += this.#settings.speed * seconds;
    this.#budget += this.#settings.capacity * seconds;
    this.#loadInOrder();
    this.#unloadBehind();
    this.#restartFinishedPregeneration();
  }

  /** The time a chunk finished loading, if it has. */
  loadedAt(x: number, y: number): number | undefined {
    return this.#loadedAt.get(key(x, y));
  }

  /** Whether the chunk lies inside the area the server has to load. */
  isRequired(x: number, y: number): boolean {
    const dx = x + 0.5 - this.playerX;
    const dy = y + 0.5;
    if (this.mode === "tiles") return Math.max(Math.abs(dx), Math.abs(dy)) <= this.radius;
    return Math.hypot(dx, dy) <= this.radius;
  }

  /** The share of required chunks in the flight path ahead of the player that are still missing, from 0 to 1. */
  missingAhead(): number {
    let ahead = 0;
    let missing = 0;
    this.#forEachRequired((x, y) => {
      if (!this.isInFlightPath(x, y)) return;
      ahead++;
      if (!this.#loadedAt.has(key(x, y))) missing++;
    });
    return ahead === 0 ? 0 : missing / ahead;
  }

  /** Whether the chunk lies ahead of the player within the cone a flier looks into. */
  isInFlightPath(x: number, y: number): boolean {
    const dx = x + 0.5 - this.playerX;
    return dx > 0 && Math.abs(y + 0.5) <= dx * FLIGHT_PATH_SLOPE;
  }

  #allRequiredLoaded(): boolean {
    let complete = true;
    this.#forEachRequired((x, y) => {
      if (!this.#loadedAt.has(key(x, y))) complete = false;
    });
    return complete;
  }

  #forEachRequired(visit: (x: number, y: number) => void) {
    const reach = Math.ceil(this.radius);
    for (let x = Math.floor(this.playerX) - reach; x <= Math.ceil(this.playerX) + reach; x++) {
      for (let y = -reach - 1; y <= reach; y++) {
        if (this.isRequired(x, y)) visit(x, y);
      }
    }
  }

  #loadInOrder() {
    const waiting: { x: number; y: number; cost: number }[] = [];
    this.#forEachRequired((x, y) => {
      if (!this.#loadedAt.has(key(x, y))) waiting.push({ x, y, cost: this.#cost(x, y) });
    });
    waiting.sort((a, b) => a.cost - b.cost);
    for (const chunk of waiting) {
      if (this.#budget < 1) break;
      this.#budget -= 1;
      this.#loadedAt.set(key(chunk.x, chunk.y), this.time);
    }
    if (waiting.length === 0) this.#budget = Math.min(this.#budget, 1);
  }

  #cost(x: number, y: number): number {
    const dx = x + 0.5 - this.playerX;
    const dy = y + 0.5;
    const distance = Math.hypot(dx, dy);
    if (this.mode === "distance") return distance;
    if (this.mode === "directional") return distance * (1 - DIRECTIONAL_WEIGHT * (dx / distance));
    return tileCost(dx, dy);
  }

  #unloadBehind() {
    const limit = this.playerX - this.radius - UNLOAD_MARGIN;
    for (const chunk of this.#loadedAt.keys()) {
      if (Number(chunk.split(",")[0]) < limit) this.#loadedAt.delete(chunk);
    }
  }

  #restartFinishedPregeneration() {
    if (this.mode !== "tiles") return;
    if (this.#completedAt === undefined) {
      if (this.#allRequiredLoaded()) this.#completedAt = this.time;
      return;
    }
    if (this.time - this.#completedAt < RESTART_AFTER_SECONDS) return;
    this.#loadedAt.clear();
    this.#completedAt = undefined;
  }
}

/** The player's own chunks first, then 4 by 4 tiles ring by ring from the centre, each tile row by row. */
function tileCost(dx: number, dy: number): number {
  if (Math.max(Math.abs(dx), Math.abs(dy)) < PLAYER_RADIUS) return Math.hypot(dx, dy) / 100;
  const column = Math.floor(dx);
  const row = Math.floor(dy);
  const tileX = Math.floor(column / TILE) + 0.5;
  const tileY = Math.floor(row / TILE) + 0.5;
  const ring = Math.max(Math.abs(tileX), Math.abs(tileY));
  const turn = (Math.atan2(tileY, tileX) + Math.PI) / (2 * Math.PI);
  const inTile = (((row % TILE) + TILE) % TILE) * TILE + (((column % TILE) + TILE) % TILE);
  return 1 + ring * 10 + turn + inTile / (TILE * TILE * 10);
}
