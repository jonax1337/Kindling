import { ArrowRight, ArrowUpRight } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { links } from "@/lib/links";
import { cn } from "@/lib/utils";

/** A bold inline link with an arrow that moves on hover; external targets get the diagonal arrow. */
export function TextLink({
  href,
  children,
  className,
}: {
  href: string;
  children: ReactNode;
  className?: string;
}) {
  const external = href.startsWith("http");
  const Arrow = external ? ArrowUpRight : ArrowRight;
  return (
    <a
      href={href}
      className={cn(
        "group inline-flex min-h-11 items-center gap-2 text-sm font-bold text-foreground no-underline transition-colors hover:text-primary",
        className,
      )}
    >
      {children}
      <Arrow
        aria-hidden
        className={cn(
          "size-4 transition-transform duration-300 ease-out-expo",
          external
            ? "group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
            : "group-hover:translate-x-1",
        )}
      />
    </a>
  );
}

export function DownloadButton({ className }: { className?: string }) {
  return (
    <Button
      asChild
      size="lg"
      className={cn(
        "h-14 rounded-lg px-8 text-[15px] font-bold glow-ember duration-300 ease-out-expo hover:-translate-y-0.5 hover:bg-[#ff9b60] active:translate-y-px active:scale-[.98]",
        className,
      )}
    >
      <a href={links.releases}>
        Download Kindling
        <ArrowUpRight aria-hidden />
      </a>
    </Button>
  );
}
