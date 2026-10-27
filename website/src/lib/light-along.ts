import { gsap } from "./gsap";

/** Scrubs the list's --draw custom property from 0 to 1, the length of its drawn line, while it crosses the screen. */
export function drawAlong(list: string) {
  gsap.fromTo(
    list,
    { "--draw": 0 },
    {
      "--draw": 1,
      ease: "none",
      scrollTrigger: { trigger: list, start: "top 70%", end: "bottom 55%", scrub: true },
    },
  );
}
