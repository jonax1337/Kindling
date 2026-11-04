import { type ComponentType, type CSSProperties, useState } from "react";
import { ChunkMap } from "@/components/chunk-map/ChunkMap";
import type { LoadingMode, MapReadout } from "@/components/chunk-map/chunk-simulation";
import { cn } from "@/lib/utils";

const NO_READOUT: MapReadout = { missingAhead: 0, sightRatio: 1, viewShare: 1 };
/** Below this share of missing chunks ahead, the count reads as healthy. */
const MISSING_TOLERANCE = 0.05;
/** The real view distance counts as reduced or full beyond these shares of its full size. */
const REDUCED_VIEW_SHARE = 0.7;
const FULL_VIEW_SHARE = 0.95;

type Tone = "good" | "bad" | "accent";

const TONES: Record<Tone, string> = {
  good: "text-[#8fd18a]",
  bad: "text-[#ff6a4a]",
  accent: "text-primary",
};

function ReadoutPill({ label, value, tone }: { label: string; value: string; tone: Tone }) {
  return (
    <span className="rounded-lg border border-white/10 bg-background/70 px-3 py-2 backdrop-blur">
      <span className="text-muted-foreground">{label} </span>
      <strong className={cn("font-display text-base tabular-nums transition-colors", TONES[tone])}>
        {value}
      </strong>
    </span>
  );
}

function MissingAhead({ readout }: { readout: MapReadout }) {
  return (
    <ReadoutPill
      label="Missing ahead"
      value={`${Math.round(readout.missingAhead * 100)} %`}
      tone={readout.missingAhead > MISSING_TOLERANCE ? "bad" : "good"}
    />
  );
}

function Sight({ readout }: { readout: MapReadout }) {
  return (
    <ReadoutPill label="Sees" value={`${readout.sightRatio.toFixed(1)}× as far`} tone="accent" />
  );
}

function RealViewDistance({ readout }: { readout: MapReadout }) {
  let state = "changing";
  if (readout.viewShare <= REDUCED_VIEW_SHARE) state = "reduced";
  else if (readout.viewShare >= FULL_VIEW_SHARE) state = "full";
  return <ReadoutPill label="Real view distance" value={state} tone="accent" />;
}

/** The readout a mode shows, if any: what matters in that phase. */
const READOUTS: Record<LoadingMode, ComponentType<{ readout: MapReadout }> | undefined> = {
  distance: MissingAhead,
  directional: MissingAhead,
  tiles: undefined,
  previews: Sight,
  saved: Sight,
  "gap-fill": MissingAhead,
  travel: RealViewDistance,
};

type Swatch = "real" | "preview" | "saved";

const SWATCHES: Record<Swatch, { label: string; style: CSSProperties }> = {
  real: { label: "Real", style: { background: "#47753f" } },
  preview: {
    label: "Preview",
    style: {
      background:
        "repeating-linear-gradient(135deg, rgba(255,138,70,.75) 0 1.5px, transparent 1.5px 4px), linear-gradient(rgba(6,10,8,.5), rgba(6,10,8,.5)), #47753f",
    },
  },
  saved: {
    label: "Saved",
    style: { background: "#47753f", boxShadow: "inset 0 0 0 1.5px rgba(250,247,239,.75)" },
  },
};

/** The chunk kinds a preview phase draws; the loading-order phases only draw real chunks and void. */
const LEGENDS: Partial<Record<LoadingMode, Swatch[]>> = {
  previews: ["real", "preview"],
  saved: ["real", "preview", "saved"],
  "gap-fill": ["real", "preview"],
  travel: ["real", "preview"],
};

function Legend({ swatches }: { swatches: Swatch[] }) {
  return (
    <ul className="m-0 flex list-none items-center gap-3 p-0">
      {swatches.map((swatch) => (
        <li key={swatch} className="flex items-center gap-1.5">
          <span
            aria-hidden
            className="size-3 rounded-[3px] border border-white/10"
            style={SWATCHES[swatch].style}
          />
          {SWATCHES[swatch].label}
        </li>
      ))}
    </ul>
  );
}

function playerCaption(mode: LoadingMode): string {
  if (mode === "tiles") return "pregenerating around a player";
  if (mode === "travel") return "player flying east, fast then slow";
  return "player flying east";
}

/** The live map with its readout: which order is playing and what the phase is about. */
export function MapFrame({
  mode,
  label,
  className,
}: {
  mode: LoadingMode;
  label: string;
  className?: string;
}) {
  const [readout, setReadout] = useState(NO_READOUT);
  const Readout = READOUTS[mode];
  const legend = LEGENDS[mode];
  return (
    <figure
      className={cn(
        "relative m-0 w-full overflow-hidden rounded-xl border bg-[#060a08]",
        className,
      )}
    >
      <ChunkMap mode={mode} onReadout={setReadout} />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_50%_50%,transparent_55%,rgb(6_10_8/.85))]"
      />
      <figcaption className="absolute inset-x-4 top-4 flex flex-wrap items-start justify-between gap-3 text-xs">
        <span className="rounded-lg border border-white/10 bg-background/70 px-3 py-2 font-bold backdrop-blur">
          {label}
          <span className="ml-2 hidden font-normal text-muted-foreground sm:inline">
            {playerCaption(mode)}
          </span>
        </span>
        {Readout && <Readout readout={readout} />}
      </figcaption>
      <div className="absolute inset-x-4 bottom-3 flex flex-wrap items-end justify-between gap-x-4 gap-y-1 text-[11px] text-[#c3cdc1]">
        <p className="m-0">Illustration, not a benchmark.</p>
        {legend && <Legend swatches={legend} />}
      </div>
    </figure>
  );
}
