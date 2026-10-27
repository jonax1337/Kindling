import { useEffect, useRef, useState } from "react";
import { ChunkMap } from "@/components/chunk-map/ChunkMap";
import type { LoadingMode } from "@/components/chunk-map/chunk-simulation";
import { useMediaQuery } from "@/hooks/use-media-query";
import { gsap, motion, useGSAP } from "@/lib/gsap";
import { links } from "@/lib/links";
import { cn } from "@/lib/utils";
import { TextLink } from "./links";

interface Step {
  mode: LoadingMode;
  label: string;
  title: string;
  body: string;
}

const STEPS: Step[] = [
  {
    mode: "distance",
    label: "Leaf",
    title: "Nearest first.",
    body: "On Leaf and Paper, every chunk around a player queues by distance alone. At elytra speed the server keeps filling in the sides and the back, while the terrain ahead arrives too late.",
  },
  {
    mode: "directional",
    label: "Kindling",
    title: "Ahead first.",
    body: "Directional loading weights each chunk by its angle to the flight path, and each player's generation queue stays short enough to keep re-sorting toward it. The same chunks load, in a better order: eight fliers on a four-core i7-7700 went from 38 % to under 1 % of their time in the void, at the same CPU time. Flight ticks rise from 15 to 24 ms, because the main thread now handles terrain the players actually reach.",
  },
  {
    mode: "tiles",
    label: "Pregenerator",
    title: "Or have it ready.",
    body: "The built-in pregenerator fills the map in 16 by 16 chunk tiles at the lowest priority, keeps half-built neighbours in memory and steps back whenever ticks get slow.",
  },
];

/** On wide screens the section is a pinned stage whose three phases follow the scroll; elsewhere the steps stack. */
export function HowItWorks() {
  const pinned = useMediaQuery(motion.wide);
  return (
    <section id="how" aria-labelledby="how-title" className="relative">
      {pinned ? <PinnedStage /> : <StackedSteps />}
    </section>
  );
}

function Heading({ className }: { className?: string }) {
  return (
    <h2 id="how-title" className={cn("text-[clamp(40px,4.6vw,64px)] leading-[1.04]", className)}>
      How the world
      <br />
      arrives sooner.
    </h2>
  );
}

function StepExtra({ mode }: { mode: LoadingMode }) {
  if (mode === "directional")
    return <TextLink href={links.generationLimit}>Read the measurements</TextLink>;
  if (mode === "tiles") {
    return (
      <code className="w-fit rounded-lg border bg-muted px-4 py-2 font-mono text-[13px]">
        /pregen start world 5000
      </code>
    );
  }
  return null;
}

function PinnedStage() {
  const stage = useRef<HTMLDivElement>(null);
  const [phase, setPhase] = useState(0);
  const step = STEPS[phase];

  useGSAP(
    () => {
      const bars = gsap.utils.toArray<HTMLElement>("[data-phase-bar]");
      const timeline = gsap.timeline({
        defaults: { ease: "none" },
        scrollTrigger: {
          trigger: stage.current,
          start: "top top",
          end: "bottom bottom",
          scrub: true,
          onUpdate: (trigger) =>
            setPhase(Math.min(STEPS.length - 1, Math.floor(trigger.progress * STEPS.length))),
        },
      });
      for (const bar of bars) timeline.fromTo(bar, { scaleX: 0 }, { scaleX: 1 });
    },
    { scope: stage },
  );

  return (
    <div ref={stage} className="relative h-[310svh]">
      <div className="sticky top-0 flex h-svh items-center pt-(--header-height)">
        <div className="wrap grid grid-cols-[minmax(0,5fr)_minmax(0,7fr)] items-center gap-16">
          <div className="flex flex-col gap-10">
            <Heading />
            <ol className="sr-only" aria-label="Three ways to load chunks">
              {STEPS.map((item) => (
                <li key={item.mode}>
                  {item.label}: {item.title} {item.body}
                </li>
              ))}
            </ol>
            <div aria-hidden className="relative min-h-[280px]">
              {STEPS.map((item, index) => (
                <article
                  key={item.mode}
                  inert={index !== phase}
                  className={cn(
                    "absolute inset-0 flex flex-col gap-5 transition-all duration-700 ease-out-expo",
                    index === phase
                      ? "translate-y-0 opacity-100"
                      : "pointer-events-none translate-y-6 opacity-0",
                  )}
                >
                  <h3 className="text-[32px] leading-tight tracking-[-.01em]">
                    <span className="mr-3 text-primary">{item.label}:</span>
                    {item.title}
                  </h3>
                  <p className="max-w-[34em] text-muted-foreground">{item.body}</p>
                  {item.mode === "tiles" && <StepExtra mode={item.mode} />}
                </article>
              ))}
            </div>
            <ol aria-hidden className="grid list-none grid-cols-3 gap-3 p-0">
              {STEPS.map((item, index) => (
                <li key={item.mode} className="flex flex-col gap-2">
                  <span className="relative h-0.5 overflow-hidden rounded-full bg-border">
                    <span
                      data-phase-bar
                      className="absolute inset-0 origin-left bg-linear-to-r from-ember-deep to-primary"
                    />
                  </span>
                  <span
                    className={cn(
                      "text-xs font-bold transition-colors",
                      index === phase ? "text-foreground" : "text-muted-foreground",
                    )}
                  >
                    {item.label}
                  </span>
                </li>
              ))}
            </ol>
            <TextLink href={links.elytraTest} className="-mt-4 self-start">
              Read the elytra test
            </TextLink>
          </div>
          <MapFrame
            mode={step?.mode ?? "distance"}
            label={step?.label ?? "Leaf"}
            className="aspect-[4/3] max-h-[calc(100svh-var(--header-height)-6rem)]"
          />
        </div>
      </div>
    </div>
  );
}

function StackedSteps() {
  const [mode, setMode] = useState<LoadingMode>("distance");
  const steps = useRef<HTMLDivElement>(null);
  const label = STEPS.find((item) => item.mode === mode)?.label ?? "Leaf";

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setMode((entry.target as HTMLElement).dataset.mode as LoadingMode);
          }
        }
      },
      { rootMargin: "-40% 0px -40% 0px" },
    );
    for (const step of steps.current?.querySelectorAll("[data-mode]") ?? []) observer.observe(step);
    return () => observer.disconnect();
  }, []);

  return (
    <div className="wrap py-24">
      <Heading className="mb-10" />
      <div className="sticky top-(--header-height) z-10 -mx-2 bg-linear-to-b from-background from-80% to-transparent px-2 pt-4 pb-8">
        <MapFrame mode={mode} label={label} className="mx-auto aspect-[4/3] max-w-[340px]" />
      </div>
      <div ref={steps}>
        {STEPS.map((item) => (
          <article
            key={item.mode}
            data-mode={item.mode}
            className={cn(
              "flex flex-col items-start gap-4 border-t py-10 transition-opacity duration-500",
              mode === item.mode ? "opacity-100" : "opacity-70 motion-reduce:opacity-100",
            )}
          >
            <h3 className="text-[26px] leading-tight tracking-[-.01em]">
              <span className="mr-2 text-primary">{item.label}:</span>
              {item.title}
            </h3>
            <p className="text-muted-foreground">{item.body}</p>
            <StepExtra mode={item.mode} />
          </article>
        ))}
      </div>
    </div>
  );
}

/** The live map with its readout: which order is playing and how much of the terrain ahead is still missing. */
function MapFrame({
  mode,
  label,
  className,
}: {
  mode: LoadingMode;
  label: string;
  className?: string;
}) {
  const [missing, setMissing] = useState(0);
  const flying = mode !== "tiles";
  return (
    <figure
      className={cn(
        "relative m-0 w-full overflow-hidden rounded-xl border bg-[#060a08]",
        className,
      )}
    >
      <ChunkMap mode={mode} onMissingAhead={setMissing} />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_50%_50%,transparent_55%,rgb(6_10_8/.85))]"
      />
      <figcaption className="absolute inset-x-4 top-4 flex flex-wrap items-start justify-between gap-3 text-xs">
        <span className="rounded-lg border border-white/10 bg-background/70 px-3 py-2 font-bold backdrop-blur">
          {label}
          <span className="ml-2 hidden font-normal text-muted-foreground sm:inline">
            {flying ? "player flying east" : "pregenerating around a player"}
          </span>
        </span>
        {flying && (
          <span className="rounded-lg border border-white/10 bg-background/70 px-3 py-2 backdrop-blur">
            <span className="text-muted-foreground">Missing ahead </span>
            <strong
              className={cn(
                "font-display text-base tabular-nums transition-colors",
                missing > 0.05 ? "text-[#ff6a4a]" : "text-[#8fd18a]",
              )}
            >
              {Math.round(missing * 100)} %
            </strong>
          </span>
        )}
      </figcaption>
      <p className="absolute bottom-3 left-4 m-0 text-[11px] text-[#c3cdc1]">
        Illustration of the loading order, not a benchmark.
      </p>
    </figure>
  );
}
