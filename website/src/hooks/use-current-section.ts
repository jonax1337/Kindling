import { useEffect, useState } from "react";

/** The id of the section crossing the middle of the viewport, among the given ids; undefined between them. */
export function useCurrentSection(ids: readonly string[]): string | undefined {
  const [current, setCurrent] = useState<string>();
  useEffect(() => {
    const crossing = new Set<string>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) crossing.add(entry.target.id);
          else crossing.delete(entry.target.id);
        }
        setCurrent(ids.find((id) => crossing.has(id)));
      },
      { rootMargin: "-45% 0px -50% 0px" },
    );
    for (const id of ids) {
      const section = document.getElementById(id);
      if (section) observer.observe(section);
    }
    return () => observer.disconnect();
  }, [ids]);
  return current;
}
