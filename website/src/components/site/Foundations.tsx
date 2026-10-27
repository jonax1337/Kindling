import { Box, Coffee, Leaf, type LucideIcon, Puzzle, Scale } from "lucide-react";
import { links } from "@/lib/links";

interface Fact {
  icon: LucideIcon;
  label: string;
  value: string;
  href?: string;
}

const FACTS: Fact[] = [
  { icon: Box, label: "Minecraft", value: "26.3" },
  { icon: Coffee, label: "Java", value: "25" },
  { icon: Leaf, label: "Built on", value: "Leaf", href: links.leaf },
  { icon: Puzzle, label: "Plugins", value: "Paper API" },
  { icon: Scale, label: "Open source", value: "MIT / GPL-3.0", href: links.license },
];

/** The facts an admin scans for first, as a glass bar along the bottom of the hero. */
export function Foundations() {
  return (
    <ul
      aria-label="Project foundations"
      className="wrap m-0 mb-6 grid list-none grid-cols-2 gap-1 rounded-xl border border-white/10 bg-background/55 p-1.5 backdrop-blur-md md:mb-8 md:grid-cols-5"
    >
      {FACTS.map((fact) => (
        <li
          key={fact.label}
          className="flex items-center gap-3 rounded-lg px-3 py-2.5 last:col-span-2 md:last:col-span-1"
        >
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/12 text-primary">
            <fact.icon aria-hidden className="size-4.5" />
          </span>
          <span className="flex min-w-0 flex-col leading-tight">
            <span className="text-[13px] text-[#d6ddd3]">{fact.label}</span>
            {fact.href ? (
              <a
                href={fact.href}
                className="-my-3 inline-flex min-h-11 items-center text-sm font-bold text-foreground no-underline hover:text-primary"
              >
                {fact.value}
              </a>
            ) : (
              <strong className="text-sm text-foreground">{fact.value}</strong>
            )}
          </span>
        </li>
      ))}
    </ul>
  );
}
