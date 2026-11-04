import { logoHorizontalDark } from "@/lib/brand";
import { links } from "@/lib/links";

const FOOTER_LINKS = [
  { href: links.releases, label: "Releases" },
  { href: links.repository, label: "GitHub" },
  { href: `${links.repository}/issues`, label: "Issues" },
  { href: links.roadmap, label: "Roadmap" },
  { href: links.measurements, label: "Measurements" },
  { href: links.leaf, label: "Built on Leaf" },
  { href: links.brand, label: "Brand kit" },
  { href: links.license, label: "License and credits" },
];

export function SiteFooter() {
  return (
    <footer className="wrap flex flex-col items-start justify-between gap-4 border-t py-10 md:flex-row md:items-center">
      <a
        href="#top"
        aria-label="Back to top"
        className="flex min-h-11 items-center gap-4 font-display text-[22px] font-extrabold no-underline"
      >
        <img
          src={logoHorizontalDark}
          width={176}
          height={46}
          alt="Kindling"
          className="h-11 w-auto"
        />
      </a>
      <nav aria-label="Footer" className="flex flex-wrap gap-x-8 gap-y-1">
        {FOOTER_LINKS.map((link) => (
          <a
            key={link.label}
            href={link.href}
            className="text-[13px] leading-[44px] text-muted-foreground no-underline transition-colors hover:text-primary"
          >
            {link.label}
          </a>
        ))}
      </nav>
    </footer>
  );
}
