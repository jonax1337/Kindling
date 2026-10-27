import { useRef } from "react";
import { GetStarted } from "@/components/site/GetStarted";
import { Hero } from "@/components/site/Hero";
import { HowItWorks } from "@/components/site/HowItWorks";
import { SiteFooter } from "@/components/site/SiteFooter";
import { SiteHeader } from "@/components/site/SiteHeader";
import { Vanilla } from "@/components/site/Vanilla";
import { useScrolledPast } from "@/hooks/use-scrolled-past";
import { gsap, motion, ScrollTrigger, useGSAP } from "@/lib/gsap";

export function App() {
  const topMarker = useRef<HTMLDivElement>(null);
  const headerSolid = useScrolledPast(topMarker);

  // Runs after every section has set up its own triggers, so these see the final layout.
  useGSAP(() => {
    gsap.matchMedia().add(motion.allowed, () => {
      trackReadingProgress();
      return revealOnScroll();
    });
    // Late fonts change line heights, so every trigger is measured again once they are in.
    void document.fonts.ready.then(() => ScrollTrigger.refresh());
  });

  return (
    <div className="relative">
      <div ref={topMarker} aria-hidden className="pointer-events-none absolute top-0 h-12 w-px" />
      <a
        href="#main"
        className="fixed -top-20 left-6 z-50 rounded-lg bg-background px-6 py-4 focus:top-4"
      >
        Skip to content
      </a>
      <SiteHeader solid={headerSolid} />
      <main id="main" tabIndex={-1} className="outline-none">
        <Hero />
        <HowItWorks />
        <Vanilla />
        <GetStarted />
      </main>
      <SiteFooter />
    </div>
  );
}

function revealOnScroll() {
  const show = (elements: Element[]) =>
    gsap.to(elements, {
      y: 0,
      opacity: 1,
      duration: 0.9,
      ease: "power3.out",
      stagger: 0.08,
      overwrite: true,
    });
  gsap.set("[data-reveal]", { y: 32, opacity: 0 });
  ScrollTrigger.batch("[data-reveal]", { start: "top 92%", once: true, onEnter: show });
  // A visit that starts below a section (a link with a hash) must not leave it hidden.
  const showPassed = () => {
    const passed = gsap.utils
      .toArray<Element>("[data-reveal]")
      .filter((element) => element.getBoundingClientRect().bottom < 0);
    if (passed.length) show(passed);
  };
  ScrollTrigger.addEventListener("refresh", showPassed);
  return () => ScrollTrigger.removeEventListener("refresh", showPassed);
}

function trackReadingProgress() {
  gsap.to("[data-reading-progress]", {
    scaleX: 1,
    ease: "none",
    scrollTrigger: { start: 0, end: "max", scrub: true },
  });
}
