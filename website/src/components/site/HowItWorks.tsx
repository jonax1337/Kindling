import { useEffect, useRef, useState } from "react";
import type { LoadingMode } from "@/components/chunk-map/chunk-simulation";
import { useMediaQuery } from "@/hooks/use-media-query";
import { gsap, motion, useGSAP } from "@/lib/gsap";
import { links } from "@/lib/links";
import { cn } from "@/lib/utils";
import { TextLink } from "./links";
import { MapFrame } from "./MapFrame";

interface Fact {
  value: string;
  label: string;
}

/** Measured figures of a phase, with the baseline and the runs they come from. */
interface Facts {
  items: Fact[];
  note: string;
}

interface Step {
  mode: LoadingMode;
  label: string;
  title: string;
  body: string;
  facts?: Facts;
}

interface StepGroup {
  name: string;
  link: { href: string; text: string };
  steps: Step[];
}

const GROUPS: StepGroup[] = [
  {
    name: "Loading order",
    link: { href: links.elytraTest, text: "Read the elytra test" },
    steps: [
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
        body: "Directional loading weights each chunk by its angle to the flight path, each player's generation queue stays short enough to keep re-sorting toward it, and on eight threads five chunk workers do the work where Paper starts two. Eight fliers on a four-core i7-7700 spent 82 % of their flight over the void on Leaf, Paper and Purpur and none on Kindling, which generated 2.5 times the chunks per second of Leaf, with a tick time p95 of 49 instead of 127 ms. It costs CPU time: about 44 % more than Leaf, for 2.5 times the chunks.",
      },
      {
        mode: "tiles",
        label: "Pregenerator",
        title: "Or have it ready.",
        body: "The built-in pregenerator fills the map in 16 by 16 chunk tiles at the lowest priority, keeps half-built neighbours in memory and steps back whenever ticks get slow.",
      },
    ],
  },
  {
    name: "Preview chunks",
    link: { href: links.previewChunks, text: "Read the preview measurements" },
    steps: [
      {
        mode: "previews",
        label: "Previews",
        title: "See 32 chunks, load 10.",
        body: "Beyond the view distance the server sends cheap previews computed from the world seed: terrain around the surface, trees and plants, lakes, rocks and ice spikes. They are never generated as real chunks and never saved, and a real chunk replaces its preview as the player comes within the view distance. Vanilla clients, no mod.",
        facts: {
          items: [
            { value: "52 %", label: "less CPU" },
            { value: "69 %", label: "less heap" },
            { value: "39.4 ms", label: "steady tick time, not 82.4" },
            { value: "33 s", label: "to the full view after joining, not 87" },
          ],
          note: "Previews to 32 with a view distance of 10, against a real view distance of 32: eight fliers with elytra, client render distance 32, three runs each. Against a plain view distance of 10 the tick time is not measurably different (39.4 against 39.9 ms) and CPU is about 19 % higher, on background threads.",
        },
      },
      {
        mode: "saved",
        label: "Saved land",
        title: "Explored land, exactly.",
        body: "Where the world is already explored, previews come from the save files and are exact, as saved. Everywhere else a preview comes from the seed and matches the real chunk's top block in about 88 % of the columns; trees are the main difference.",
        facts: {
          items: [
            { value: "88 %", label: "of seed preview columns match the real top block" },
            { value: "2.7×", label: "less generation work than a real chunk's terrain" },
            { value: "6×", label: "smaller packet than a saved chunk's" },
          ],
          note: "Measured on the bench host.",
        },
      },
      {
        mode: "gap-fill",
        label: "Fill the gaps",
        title: "No void while it loads.",
        body: "Inside the view distance, a chunk the server has not finished yet shows as a preview until the real chunk arrives. The only exception is the two chunks right around the player. Where plain Leaf leaves the terrain ahead empty, the player sees land.",
      },
      {
        mode: "travel",
        label: "Travel",
        title: "Fewer real chunks at speed.",
        body: "An opt-in server setting: while a player flies fast, the real view distance shrinks and previews show the rest; when the player slows down, it grows back. The server loads fewer real chunks for a fast flier. This phase is not measured on its own.",
      },
    ],
  },
];

const STEPS = GROUPS.flatMap((group) => group.steps);
/** Scroll length of one phase, in viewport heights; the stage adds a tenth for the pin itself. */
const PHASE_SCROLL_SVH = 100;
const PIN_SCROLL_SVH = 10;

function describe(step: Step): string {
  const facts = step.facts?.items.map((fact) => `${fact.value} ${fact.label}.`).join(" ");
  return [`${step.label}: ${step.title}`, step.body, facts, step.facts?.note]
    .filter(Boolean)
    .join(" ");
}

/** On wide screens the section is a pinned stage whose phases follow the scroll; elsewhere the steps stack. */
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
    <h2
      id="how-title"
      className={cn("text-[clamp(36px,min(4.6vw,7svh),64px)] leading-[1.04]", className)}
    >
      How the world
      <br />
      arrives sooner.
    </h2>
  );
}

function StepFacts({ facts, className }: { facts: Facts; className?: string }) {
  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <dl className="m-0 grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
        {facts.items.map((fact) => (
          <div key={fact.value} className="flex flex-col gap-0.5">
            <dt className="font-display text-[clamp(22px,3.2svh,26px)] leading-none tabular-nums text-primary">
              {fact.value}
            </dt>
            <dd className="m-0 text-xs leading-snug text-muted-foreground">{fact.label}</dd>
          </div>
        ))}
      </dl>
      <p className="m-0 max-w-[40em] text-xs leading-relaxed text-muted-foreground">{facts.note}</p>
    </div>
  );
}

function StepExtra({ mode }: { mode: LoadingMode }) {
  if (mode === "directional")
    return <TextLink href={links.leafComparison}>Read the measurements</TextLink>;
  if (mode === "previews" || mode === "saved")
    return <TextLink href={links.previewChunks}>Read the preview measurements</TextLink>;
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
    <div
      ref={stage}
      className="relative"
      style={{ height: `${STEPS.length * PHASE_SCROLL_SVH + PIN_SCROLL_SVH}svh` }}
    >
      <div className="sticky top-0 flex h-svh items-center pt-(--header-height)">
        <div className="wrap grid grid-cols-[minmax(0,5fr)_minmax(0,7fr)] items-center gap-16">
          <div className="flex flex-col gap-[clamp(1rem,3svh,2rem)]">
            <Heading />
            <ol className="sr-only" aria-label="Ways chunks reach the player">
              {STEPS.map((item) => (
                <li key={item.mode}>{describe(item)}</li>
              ))}
            </ol>
            <div aria-hidden className="grid">
              {STEPS.map((item, index) => (
                <article
                  key={item.mode}
                  inert={index !== phase}
                  className={cn(
                    "col-start-1 row-start-1 flex flex-col gap-5 transition-all duration-700 ease-out-expo",
                    index === phase
                      ? "translate-y-0 opacity-100"
                      : "pointer-events-none translate-y-6 opacity-0",
                  )}
                >
                  <h3 className="text-[clamp(26px,3.8svh,32px)] leading-tight tracking-[-.01em]">
                    <span className="mr-3 text-primary">{item.label}:</span>
                    {item.title}
                  </h3>
                  <p className="max-w-[34em] text-[clamp(14px,2svh,16px)] text-muted-foreground">
                    {item.body}
                  </p>
                  {item.facts && <StepFacts facts={item.facts} />}
                  {item.mode === "tiles" && <StepExtra mode={item.mode} />}
                </article>
              ))}
            </div>
            <PhaseBars phase={phase} />
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

/** One progress bar per phase, in a row per group, each group with the link to its measurements. */
function PhaseBars({ phase }: { phase: number }) {
  return (
    <div aria-hidden className="flex flex-col gap-4">
      {GROUPS.map((group) => (
        <div key={group.name} className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs text-muted-foreground">{group.name}</span>
            <TextLink href={group.link.href} className="min-h-7 text-xs">
              {group.link.text}
            </TextLink>
          </div>
          <ol
            className="m-0 grid list-none gap-3 p-0"
            style={{ gridTemplateColumns: `repeat(${group.steps.length}, minmax(0, 1fr))` }}
          >
            {group.steps.map((item) => (
              <li key={item.mode} className="flex flex-col gap-2">
                <span className="relative h-0.5 overflow-hidden rounded-full bg-border">
                  <span
                    data-phase-bar
                    className="absolute inset-0 origin-left bg-linear-to-r from-ember-deep to-primary"
                  />
                </span>
                <span
                  className={cn(
                    "truncate text-xs font-bold transition-colors",
                    STEPS[phase] === item ? "text-foreground" : "text-muted-foreground",
                  )}
                >
                  {item.label}
                </span>
              </li>
            ))}
          </ol>
        </div>
      ))}
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
            {item.facts && <StepFacts facts={item.facts} />}
            <StepExtra mode={item.mode} />
          </article>
        ))}
      </div>
    </div>
  );
}
