import { ArrowUpRight } from "lucide-react";
import { useEffect, useState } from "react";
import { useCurrentSection } from "@/hooks/use-current-section";
import { logoHorizontalDark } from "@/lib/brand";
import { links } from "@/lib/links";
import { cn } from "@/lib/utils";

const NAVIGATION = [
  { id: "how", label: "How it works" },
  { id: "start", label: "Get started" },
] as const;
const SECTION_IDS = NAVIGATION.map((item) => item.id);

export function SiteHeader({ solid }: { solid: boolean }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const current = useCurrentSection(SECTION_IDS);

  useEffect(() => {
    if (!menuOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [menuOpen]);

  return (
    <header
      data-site-header
      className={cn(
        "fixed inset-x-0 top-0 z-40 border-b transition-[background-color,border-color,backdrop-filter] duration-500",
        solid || menuOpen
          ? "border-white/8 bg-background/80 backdrop-blur-xl backdrop-saturate-150"
          : "border-transparent",
      )}
    >
      <div className="wrap flex min-h-(--header-height) flex-wrap items-center gap-x-8">
        <a
          href="#top"
          aria-label="Kindling home"
          className="mr-auto inline-flex min-h-11 shrink-0 items-center"
        >
          <img
            src={logoHorizontalDark}
            width={168}
            height={44}
            alt="Kindling"
            className="h-10 w-auto lg:h-11"
          />
        </a>
        <button
          type="button"
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          onClick={() => setMenuOpen((open) => !open)}
          className="min-h-11 min-w-18 rounded-lg border border-white/15 bg-background/30 px-3 text-[13px] font-bold backdrop-blur-md lg:hidden"
        >
          {menuOpen ? "Close" : "Menu"}
        </button>
        <nav
          id="main-navigation"
          aria-label="Main navigation"
          className={cn(
            "basis-full flex-col items-start pb-4 lg:flex lg:basis-auto lg:flex-row lg:items-center lg:gap-6 lg:pb-0 xl:gap-8",
            menuOpen ? "flex" : "hidden",
          )}
        >
          {NAVIGATION.map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              aria-current={current === item.id ? "true" : undefined}
              onClick={() => setMenuOpen(false)}
              className={cn(
                "relative inline-flex min-h-11 items-center text-sm font-semibold whitespace-nowrap no-underline transition-colors after:absolute after:inset-x-0 after:bottom-2 after:h-0.5 after:origin-left after:scale-x-0 after:bg-primary after:transition-transform after:duration-500 hover:text-foreground aria-[current=true]:text-foreground aria-[current=true]:after:scale-x-100",
                solid || menuOpen
                  ? "text-muted-foreground"
                  : "text-[#e3e9e0] [text-shadow:0_1px_8px_rgb(11_18_14/.8)]",
              )}
            >
              {item.label}
            </a>
          ))}
          <a
            href={links.repository}
            className={cn(
              "group inline-flex min-h-11 items-center gap-1 text-sm font-semibold whitespace-nowrap no-underline transition-colors hover:text-foreground",
              solid || menuOpen
                ? "text-muted-foreground"
                : "text-[#e3e9e0] [text-shadow:0_1px_8px_rgb(11_18_14/.8)]",
            )}
          >
            GitHub
            <ArrowUpRight
              aria-hidden
              className="size-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </a>
          <a
            href={links.releases}
            className="mt-2 inline-flex h-10 items-center rounded-lg bg-primary px-4 text-sm font-bold text-primary-foreground no-underline transition-colors hover:bg-[#ff9b60] lg:mt-0"
          >
            Download
          </a>
        </nav>
      </div>
      <div
        data-reading-progress
        aria-hidden
        className={cn(
          "absolute -bottom-px left-0 h-0.5 w-full origin-left scale-x-0 bg-linear-to-r from-ember-deep to-primary transition-opacity duration-500",
          solid ? "opacity-100" : "opacity-0",
        )}
      />
    </header>
  );
}
