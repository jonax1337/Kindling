import { useRef } from "react";
import kindlingHome from "@/assets/kindling-home.webp";
import { useMediaQuery } from "@/hooks/use-media-query";
import { gsap, motion, useGSAP } from "@/lib/gsap";
import { cn } from "@/lib/utils";

/** The illustration opens from a small window to the whole screen while its stage sticks on wide screens. */
export function Vanilla() {
  const root = useRef<HTMLElement>(null);
  const wide = useMediaQuery(motion.wide);
  const narrow = useMediaQuery(motion.narrow);

  // Keyed on the same media state as the sticky classes and run after React applied them, so a rotation or a
  // reduced-motion toggle measures the new layout.
  useGSAP(
    () => {
      if (wide) {
        openWindow({ trigger: root.current, start: "top top", end: "bottom bottom", scrub: 1 });
      } else if (narrow) {
        const stage = root.current?.querySelector("[data-vanilla-stage]");
        openWindow({ trigger: stage, start: "top 85%", end: "center center", scrub: 1 });
      }
    },
    { scope: root, dependencies: [wide, narrow], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      aria-labelledby="vanilla-title"
      className={cn("relative isolate overflow-clip", wide && "h-[260svh]")}
    >
      <div
        data-vanilla-stage
        className={cn("relative flex min-h-svh items-end", wide && "sticky top-0 h-svh")}
      >
        <div
          data-vanilla-window
          className="absolute inset-0 -z-10 overflow-hidden [mask-image:linear-gradient(to_bottom,transparent,#000_14%,#000_80%,transparent)]"
        >
          <img
            data-vanilla-image
            src={kindlingHome}
            width={1122}
            height={1402}
            loading="lazy"
            decoding="async"
            alt="An illustrated voxel woodland, with a stone bridge leading to a warmly lit survival cabin."
            className="absolute -top-[8%] h-[116%] w-full max-w-none object-cover object-[50%_40%]"
          />
          <div className="absolute inset-0 bg-[linear-gradient(0deg,rgb(11_18_14/.95),rgb(11_18_14/.4)_50%,rgb(11_18_14/.15)),linear-gradient(90deg,rgb(11_18_14/.75),transparent_60%)]" />
        </div>
        <div className="wrap py-24 md:py-32">
          <h2
            id="vanilla-title"
            data-vanilla-copy
            className="text-[clamp(40px,5.2vw,72px)] leading-[1.04]"
          >
            The game stays
            <br />
            exactly the game.
          </h2>
          <p
            data-vanilla-copy
            className="mt-8 max-w-[34em] text-base leading-7 text-[#d6ddd3] md:text-[19px] md:leading-8"
          >
            Kindling's defaults change speed, not gameplay: no throttled mob spawning, no skipped
            entity ticks, no smaller world. The survival-bases test counts every item moved,
            smelted, brewed and crafted, and the counts match. One documented difference: where a
            plugin holds hoppers back and items pile up, the order they are picked up in can vary;
            two runs differed by 10 of 12 140 sorted items.
          </p>
        </div>
      </div>
    </section>
  );
}

function openWindow(scrollTrigger: ScrollTrigger.Vars) {
  gsap
    .timeline({ defaults: { ease: "power2.inOut" }, scrollTrigger })
    .fromTo(
      "[data-vanilla-window]",
      { clipPath: "inset(16% 20% 16% 20% round 16px)" },
      { clipPath: "inset(0% 0% 0% 0% round 0px)", duration: 1 },
    )
    .fromTo("[data-vanilla-image]", { scale: 1.3 }, { scale: 1.06, duration: 1 }, 0)
    .from(
      "[data-vanilla-copy]",
      { y: 48, opacity: 0, stagger: 0.15, duration: 0.5, ease: "power2.out" },
      0.75,
    )
    // The open scene settles slowly before the page moves on.
    .to("[data-vanilla-image]", { scale: 1, duration: 0.6, ease: "none" });
}
