/**
 * A small top-down model of chunk loading: a player flies east, the server can load only so many chunks per second,
 * and the loading order decides whether the terrain ahead is there in time. The preview modes add a second, much
 * faster layer: cheap previews beyond the view distance that a real chunk always replaces. An illustration, not a
 * benchmark.
 */
export type LoadingMode =
  | "distance"
  | "directional"
  | "tiles"
  | "previews"
  | "saved"
  | "gap-fill"
  | "travel";

type LoadOrder = "distance" | "directional" | "tiles";

/** A stand-in for a chunk beyond the view distance; `exact` previews come from the save files, the rest from the seed. */
export interface Preview {
  x: number;
  y: number;
  at: number;
  exact: boolean;
}

/** What the readouts on the map show, refreshed a few times a second. */
export interface MapReadout {
  /** The share of required chunks in the flight path that show neither a real chunk nor a preview, from 0 to 1. */
  missingAhead: number;
  /** How many times farther the player sees than the server loads real chunks. */
  sightRatio: number;
  /** The real view distance as a share of its full size, from 0 to 1. */
  viewShare: number;
}

/** An earlier flight whose chunks are in the save files: a band that winds across the world. */
interface SavedLand {
  amplitude: number;
  frequency: number;
  shift: number;
  halfWidth: number;
}

/** Where the previews show up; each optional part is a feature the mode has. */
interface PreviewSettings {
  /** How far from the player previews reach, in chunks. */
  reach: number;
  /** Seed previews finished per second; previews from the save files need no generation. */
  capacity: number;
  savedLand?: SavedLand;
  /** Inside the view distance, chunks not finished yet show a preview, except within `skipRadius` of the player. */
  gapFill?: { skipRadius: number };
}

/** The real view distance shrinks while the player is fast and grows back when the player slows down. */
interface TravelSettings {
  slowSpeed: number;
  shrunkRadius: number;
  fastSeconds: number;
  slowSeconds: number;
}

interface ModeSettings {
  order: LoadOrder;
  /** Flight speed in chunks per second. */
  speed: number;
  /** Real chunks the server finishes per second. */
  capacity: number;
  /** View distance (flight) or half the side of the pregenerated square (tiles), in chunks. */
  radius: number;
  /** Chunks across the width of the map. */
  columns: number;
  preview?: PreviewSettings;
  travel?: TravelSettings;
}

const FLIGHT: ModeSettings = { order: "distance", speed: 7, capacity: 110, radius: 9, columns: 28 };
/** The preview modes zoom out: previews reach 16 over a view distance of 5, the ratio of 32 over 10. */
const PREVIEWED: ModeSettings = {
  ...FLIGHT,
  order: "directional",
  radius: 5,
  columns: 44,
  preview: { reach: 16, capacity: 900 },
};
const SAVED_LAND: SavedLand = { amplitude: 8, frequency: 0.09, shift: 2.2, halfWidth: 2.5 };

const SETTINGS: Record<LoadingMode, ModeSettings> = {
  distance: FLIGHT,
  directional: { ...FLIGHT, order: "directional" },
  tiles: { ...FLIGHT, order: "tiles", speed: 0, capacity: 80 },
  previews: PREVIEWED,
  saved: { ...PREVIEWED, preview: { reach: 16, capacity: 900, savedLand: SAVED_LAND } },
  "gap-fill": {
    ...PREVIEWED,
    capacity: 20,
    preview: { reach: 16, capacity: 900, gapFill: { skipRadius: 2 } },
  },
  travel: {
    ...PREVIEWED,
    travel: { slowSpeed: 1.5, shrunkRadius: 2.5, fastSeconds: 5, slowSeconds: 4 },
  },
};

const DIRECTIONAL_WEIGHT = 0.75;
const TILE = 4;
const PLAYER_RADIUS = 2;
const UNLOAD_MARGIN = 4;
const RESTART_AFTER_SECONDS = 1.6;
/** How quickly the player's speed follows the pace of the flight, per second. */
const PACE_EASING = 1.6;
/** tan 30°: the half-angle of the flight path. */
const FLIGHT_PATH_SLOPE = Math.tan(Math.PI / 6);

function key(x: number, y: number): string {
  return `${x},${y}`;
}

function coordinates(chunkKey: string): [number, number] {
  const [x, y] = chunkKey.split(",");
  return [Number(x), Number(y)];
}

export class ChunkSimulation {
  readonly mode: LoadingMode;
  /** The full real view distance, in chunks. */
  readonly radius: number;
  /** How far previews reach; the real view distance when the mode has none. */
  readonly reach: number;
  /** Chunks across the width of the map. */
  readonly columns: number;
  /** The real view distance right now; only the travel mode changes it. */
  viewRadius: number;
  playerX = 0;
  time = 0;
  readonly #settings: ModeSettings;
  readonly #loadedAt = new Map<string, number>();
  readonly #previews = new Map<string, Preview>();
  #speed: number;
  #budget = 0;
  #previewBudget = 0;
  #completedAt: number | undefined;

  constructor(mode: LoadingMode) {
    this.mode = mode;
    this.#settings = SETTINGS[mode];
    this.radius = this.#settings.radius;
    this.reach = this.#settings.preview?.reach ?? this.radius;
    this.columns = this.#settings.columns;
    this.#speed = this.#targetSpeed();
    this.viewRadius = this.#radiusForSpeed();
  }

  step(seconds: number) {
    this.time += seconds;
    this.#advance(seconds);
    this.#budget += this.#settings.capacity * seconds;
    this.#loadInOrder();
    this.#unloadOutOfView();
    this.#updatePreviews(seconds);
    this.#restartFinishedPregeneration();
  }

  /** The time a chunk finished loading, if it has. */
  loadedAt(x: number, y: number): number | undefined {
    return this.#loadedAt.get(key(x, y));
  }

  /** The preview standing in for a chunk that is not loaded, if there is one. */
  preview(x: number, y: number): Preview | undefined {
    return this.#previews.get(key(x, y));
  }

  /** Whether the chunk lies inside the area the server has to load. */
  isRequired(x: number, y: number): boolean {
    const dx = x + 0.5 - this.playerX;
    const dy = y + 0.5;
    if (this.mode === "tiles") return Math.max(Math.abs(dx), Math.abs(dy)) <= this.viewRadius;
    return Math.hypot(dx, dy) <= this.viewRadius;
  }

  readout(): MapReadout {
    return {
      missingAhead: this.missingAhead(),
      sightRatio: this.reach / this.viewRadius,
      viewShare: this.viewRadius / this.radius,
    };
  }

  /** The share of required chunks in the flight path ahead of the player that show nothing yet, from 0 to 1. */
  missingAhead(): number {
    let ahead = 0;
    let missing = 0;
    this.#forEachRequired((x, y) => {
      if (!this.isInFlightPath(x, y)) return;
      ahead++;
      if (!this.#isShown(x, y)) missing++;
    });
    return ahead === 0 ? 0 : missing / ahead;
  }

  /** Whether the chunk lies ahead of the player within the cone a flier looks into. */
  isInFlightPath(x: number, y: number): boolean {
    const dx = x + 0.5 - this.playerX;
    return dx > 0 && Math.abs(y + 0.5) <= dx * FLIGHT_PATH_SLOPE;
  }

  #isShown(x: number, y: number): boolean {
    const chunk = key(x, y);
    return this.#loadedAt.has(chunk) || this.#previews.has(chunk);
  }

  #allRequiredLoaded(): boolean {
    let complete = true;
    this.#forEachRequired((x, y) => {
      if (!this.#loadedAt.has(key(x, y))) complete = false;
    });
    return complete;
  }

  #forEachRequired(visit: (x: number, y: number) => void) {
    this.#forEachAround(this.radius, (x, y) => {
      if (this.isRequired(x, y)) visit(x, y);
    });
  }

  #forEachAround(distance: number, visit: (x: number, y: number) => void) {
    const reach = Math.ceil(distance);
    for (let x = Math.floor(this.playerX) - reach; x <= Math.ceil(this.playerX) + reach; x++) {
      for (let y = -reach - 1; y <= reach; y++) visit(x, y);
    }
  }

  #distanceTo(x: number, y: number): number {
    return Math.hypot(x + 0.5 - this.playerX, y + 0.5);
  }

  #advance(seconds: number) {
    this.#speed += (this.#targetSpeed() - this.#speed) * Math.min(1, seconds * PACE_EASING);
    this.viewRadius = this.#radiusForSpeed();
    this.playerX += this.#speed * seconds;
  }

  /** A travelling player alternates between the fast and the slow part of a cycle. */
  #targetSpeed(): number {
    const travel = this.#settings.travel;
    if (!travel) return this.#settings.speed;
    const cycle = this.time % (travel.fastSeconds + travel.slowSeconds);
    return cycle < travel.fastSeconds ? this.#settings.speed : travel.slowSpeed;
  }

  #radiusForSpeed(): number {
    const travel = this.#settings.travel;
    if (!travel) return this.radius;
    const fastShare = (this.#speed - travel.slowSpeed) / (this.#settings.speed - travel.slowSpeed);
    const share = Math.min(1, Math.max(0, fastShare));
    return this.radius - (this.radius - travel.shrunkRadius) * share;
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
      this.#previews.delete(key(chunk.x, chunk.y));
    }
    if (waiting.length === 0) this.#budget = Math.min(this.#budget, 1);
  }

  #cost(x: number, y: number): number {
    const dx = x + 0.5 - this.playerX;
    const dy = y + 0.5;
    if (this.#settings.order === "distance") return Math.hypot(dx, dy);
    if (this.#settings.order === "directional") return aheadFirstCost(dx, dy);
    return tileCost(dx, dy);
  }

  #unloadOutOfView() {
    for (const chunk of this.#loadedAt.keys()) {
      const [x, y] = coordinates(chunk);
      if (!this.#isRetained(x, y)) this.#loadedAt.delete(chunk);
    }
  }

  /** Where previews take over, a real chunk leaves with the view distance; elsewhere it lingers behind the player. */
  #isRetained(x: number, y: number): boolean {
    if (this.#settings.preview) return this.#distanceTo(x, y) <= this.viewRadius;
    return x >= this.playerX - this.radius - UNLOAD_MARGIN;
  }

  #updatePreviews(seconds: number) {
    const settings = this.#settings.preview;
    if (!settings) return;
    this.#previewBudget += settings.capacity * seconds;
    this.#dropPreviewsOutOfReach(settings);
    this.#createPreviews(settings);
  }

  #dropPreviewsOutOfReach(settings: PreviewSettings) {
    for (const [chunk, preview] of this.#previews) {
      if (this.#distanceTo(preview.x, preview.y) > settings.reach) this.#previews.delete(chunk);
    }
  }

  /** Saved land shows up at once; seed previews queue ahead of the player first and share the preview budget. */
  #createPreviews(settings: PreviewSettings) {
    const waiting: { x: number; y: number; cost: number }[] = [];
    this.#forEachAround(settings.reach, (x, y) => {
      if (!this.#wantsPreview(settings, x, y)) return;
      if (isSavedLand(settings.savedLand, x, y)) this.#addPreview(x, y, true);
      else waiting.push({ x, y, cost: aheadFirstCost(x + 0.5 - this.playerX, y + 0.5) });
    });
    waiting.sort((a, b) => a.cost - b.cost);
    for (const chunk of waiting) {
      if (this.#previewBudget < 1) break;
      this.#previewBudget -= 1;
      this.#addPreview(chunk.x, chunk.y, false);
    }
    if (waiting.length === 0) this.#previewBudget = Math.min(this.#previewBudget, 1);
  }

  #wantsPreview(settings: PreviewSettings, x: number, y: number): boolean {
    const chunk = key(x, y);
    if (this.#loadedAt.has(chunk) || this.#previews.has(chunk)) return false;
    const distance = this.#distanceTo(x, y);
    if (distance > settings.reach) return false;
    if (distance > this.viewRadius) return true;
    return settings.gapFill !== undefined && distance >= settings.gapFill.skipRadius;
  }

  #addPreview(x: number, y: number, exact: boolean) {
    this.#previews.set(key(x, y), { x, y, at: this.time, exact });
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

/** Nearest first, with chunks ahead of the player counting as closer. */
function aheadFirstCost(dx: number, dy: number): number {
  const distance = Math.hypot(dx, dy);
  return distance * (1 - DIRECTIONAL_WEIGHT * (dx / distance));
}

function isSavedLand(land: SavedLand | undefined, x: number, y: number): boolean {
  if (!land) return false;
  const centre = land.amplitude * Math.sin(x * land.frequency + land.shift);
  return Math.abs(y + 0.5 - centre) <= land.halfWidth;
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
