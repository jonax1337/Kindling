import { useRef } from "react";
import kindlingWorld from "@/assets/kindling-world.webp";
import { gsap, motion, useGSAP } from "@/lib/gsap";
import { Foundations } from "./Foundations";
import { DownloadButton, TextLink } from "./links";

const POINTER_DRIFT = { x: 24, y: 16 };

export function Hero() {
  const root = useRef<HTMLElement>(null);

  useGSAP(
    () => {
      const media = gsap.matchMedia();
      media.add(motion.allowed, () => {
        playEntrance();
        driftOnScroll(root.current);
      });
      media.add(motion.finePointer, () => followPointer(root.current));
    },
    { scope: root },
  );

  return (
    <section
      ref={root}
      id="top"
      aria-labelledby="hero-title"
      className="relative isolate flex min-h-svh flex-col justify-end overflow-clip pt-(--header-height)"
    >
      <div
        data-hero-scene
        aria-hidden
        className="absolute inset-0 -z-10 [mask-image:linear-gradient(to_bottom,#000_65%,transparent)]"
      >
        <img
          data-hero-image
          src={kindlingWorld}
          width={1916}
          height={821}
          alt=""
          fetchPriority="high"
          className="absolute -top-[6%] -left-[4%] h-[112%] w-[108%] max-w-none object-cover object-[68%_50%] md:object-[62%_50%]"
        />
        <div className="absolute inset-0 bg-[linear-gradient(0deg,var(--background)_6%,rgb(11_18_14/.85)_40%,rgb(11_18_14/.6)_72%,rgb(11_18_14/.75))] md:bg-[radial-gradient(120%_90%_at_70%_40%,transparent_30%,rgb(11_18_14/.55)_75%),linear-gradient(90deg,rgb(11_18_14/.92),rgb(11_18_14/.55)_38%,rgb(11_18_14/.05)_70%),linear-gradient(0deg,var(--background)_0%,rgb(11_18_14/.6)_16%,transparent_42%,rgb(11_18_14/.45)_100%)]" />
      </div>
      <div data-hero-content className="wrap pt-16 pb-10 md:pt-24 md:pb-14">
        <p
          data-hero-enter
          className="mb-6 text-[13px] font-bold tracking-[.14em] text-foreground uppercase [text-shadow:0_1px_8px_rgb(11_18_14/.9)]"
        >
          Open-source Leaf fork for Minecraft 26.3
        </p>
        <h1
          id="hero-title"
          className="text-[clamp(46px,8.4vw,124px)] leading-[.98] font-[680] tracking-[-.045em] [word-spacing:.06em]"
        >
          <span className="-mb-[.1em] block overflow-hidden pb-[.1em]">
            <span data-hero-line className="block">
              The same Minecraft.
            </span>
          </span>
          <span className="-mb-[.1em] block overflow-hidden pb-[.1em]">
            <span data-hero-line className="block text-primary">
              Built to keep up.
            </span>
          </span>
        </h1>
        <p
          data-hero-enter
          className="mt-8 max-w-[31em] text-base leading-7 text-[#d6ddd3] md:text-lg md:leading-[30px]"
        >
          A Leaf fork that loads terrain ahead of fast players, ships a built-in pregenerator and
          lets idle hoppers sleep. Nothing that changes gameplay is on by default.
        </p>
        <div data-hero-enter className="mt-10 flex flex-wrap items-center gap-6 md:gap-8">
          <DownloadButton />
          <TextLink href="#how">See how it works</TextLink>
        </div>
        <p data-hero-enter className="mt-6 text-sm text-foreground/90">
          Pre-release · one production server so far · back up your worlds before switching.
        </p>
      </div>
      <div data-hero-enter>
        <Foundations />
      </div>
    </section>
  );
}

/** The world settles in at once (it is the largest paint, so it is never hidden), then the copy rises. */
function playEntrance() {
  gsap
    .timeline({ defaults: { ease: "expo.out", duration: 1.4 } })
    .from("[data-hero-image]", { scale: 1.12, duration: 2.4 })
    .from(
      document.querySelector("[data-site-header]"),
      { yPercent: -100, opacity: 0, duration: 1 },
      0.3,
    )
    .from("[data-hero-line]", { yPercent: 110, stagger: 0.14 }, 0.35)
    .from("[data-hero-enter]", { y: 24, opacity: 0, stagger: 0.1, duration: 1 }, 0.7);
}

/** Layers drift at different speeds as the hero leaves: the world slowly, the copy faster. */
function driftOnScroll(hero: HTMLElement | null) {
  const scrub = { trigger: hero, start: "top top", end: "bottom top", scrub: true };
  gsap.to("[data-hero-scene]", { yPercent: 14, scale: 1.08, ease: "none", scrollTrigger: scrub });
  gsap.to("[data-hero-content]", {
    y: -140,
    opacity: 0,
    ease: "none",
    scrollTrigger: { ...scrub, end: "75% top" },
  });
}

function followPointer(hero: HTMLElement | null) {
  if (!hero) return;
  const moveX = gsap.quickTo("[data-hero-image]", "x", { duration: 1.2, ease: "power3.out" });
  const moveY = gsap.quickTo("[data-hero-image]", "y", { duration: 1.2, ease: "power3.out" });
  const onMove = (event: PointerEvent) => {
    moveX((event.clientX / window.innerWidth - 0.5) * -POINTER_DRIFT.x);
    moveY((event.clientY / window.innerHeight - 0.5) * -POINTER_DRIFT.y);
  };
  hero.addEventListener("pointermove", onMove);
  return () => hero.removeEventListener("pointermove", onMove);
}
