import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(useGSAP, ScrollTrigger);

/** Media queries shared by every animation; motion only runs when the visitor has not asked for less. */
export const motion = {
  allowed: "(prefers-reduced-motion: no-preference)",
  wide: "(prefers-reduced-motion: no-preference) and (min-width: 900px)",
  narrow: "(prefers-reduced-motion: no-preference) and (max-width: 899px)",
  finePointer: "(prefers-reduced-motion: no-preference) and (pointer: fine)",
} as const;

export { gsap, ScrollTrigger, useGSAP };
