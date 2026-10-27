import { type RefObject, useEffect, useState } from "react";

/** True once the element (a marker at the top of the page) has left the viewport. */
export function useScrolledPast(marker: RefObject<HTMLElement | null>): boolean {
  const [past, setPast] = useState(false);
  useEffect(() => {
    const element = marker.current;
    if (!element) return;
    const observer = new IntersectionObserver((entries) =>
      setPast(!(entries.at(-1)?.isIntersecting ?? true)),
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [marker]);
  return past;
}
