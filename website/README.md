# Kindling website

The public site at **https://jonax1337.github.io/Kindling/**: one page built with Vite, React and TypeScript, styled with Tailwind CSS 4 and shadcn/ui, animated with GSAP. The repository README stays the configuration reference and [MEASUREMENTS.md](../docs/MEASUREMENTS.md) the source of every performance figure; the site links to both.

## Working on it

Node.js 24 and pnpm 11 are required.

```sh
cd website
pnpm install
pnpm dev        # http://127.0.0.1:5174/Kindling/
pnpm check      # Biome, TypeScript and the production build
pnpm preview    # serves dist/ the way GitHub Pages will
```

`pnpm-workspace.yaml` holds the supply-chain rules (packages at least seven days old, install scripts only by allow-list); pins younger than that are listed there with the reason.

## Publication

`.github/workflows/website.yml` runs `pnpm check` on every change to `website/` or `docs/branding/` and deploys `website/dist` to GitHub Pages from `ver/26.3`. The repository setting **Settings > Pages > Build and deployment > Source** has to be **GitHub Actions**. Changes here never start a server build (`build.yml` ignores `website/**`).

The site is served under `/Kindling/` (`base` in `vite.config.ts`). The canonical URL, the Open Graph tags in `index.html`, `public/sitemap.xml` and the website link in the root README change together if the repository is renamed again.

## What is where

| Path | Content |
| --- | --- |
| `src/App.tsx` | page order, scroll reveals and reading progress |
| `src/components/site/` | one component per section |
| `src/components/chunk-map/` | the chunk loading model behind "How the world arrives sooner" and its canvas drawing |
| `src/components/ui/` | shadcn/ui primitives (button, tabs, accordion) |
| `src/lib/links.ts` | every outbound link in one place |
| `src/styles/app.css` | Tailwind theme: Kindling's dark brand colours mapped onto shadcn's tokens |

Logos and fonts are imported from the brand kit in [docs/branding](../docs/branding/README.md) (`@brand` alias), not copied. The build adds `favicon.png` and `social-preview.png` from the kit under fixed names.

## Motion and accessibility

The page has one dark theme. GSAP drives the hero entrance and parallax, two sticky scroll scenes (the chunk-map stage and the vanilla window), the drawn line through the start steps and the section reveals. Reveals fade opacity only, so content below the fold stays in the Tab order. Scrolling stays native.

With `prefers-reduced-motion: reduce`, no GSAP animation is created and the chunk map shows one still moment. Below 900 px the chunk-map stage and the vanilla window become ordinary stacked sections.

The chunk map is an illustration of the loading orders, not a benchmark: a player flies east at 7 chunks per second, the server finishes 110 chunks per second, and "Missing ahead" counts unloaded chunks within 30° of the flight path. The measured results are the ones in MEASUREMENTS.md.

## Illustrations

The image was generated with the built-in image generation tool, then encoded as WebP without changing the composition. It is an illustration, not a gameplay screenshot or benchmark capture. The original is retained in the generating chat's image artifacts.

Generation prompt:

> Create a premium panoramic website hero illustration for Kindling, a Minecraft survival server software project. Ultra-wide landscape 21:9 composition. A peaceful expansive Minecraft-style voxel world at golden dawn: a winding river cuts through a lush spruce and oak valley, blocky cliffs and distant layered mountains dissolve into pale mist, one small warm timber-and-stone survival house nestled near the river on the right third, tiny amber lantern light. Rich forest greens, warm sunlit grass, pale cream sky, restrained warm orange highlights. Beautiful game-world cinematography, crisp block-built terrain and foliage, atmospheric distance, polished natural shader lighting, inviting and calm rather than epic fantasy. Camera from an elevated hill with a few dark blocky foliage shapes at the far bottom edge, valley open in the middle and right. The upper left quarter must be quiet, darker forest and soft open air suitable behind overlaid website text. Wide horizontal composition with generous scenic depth. No text, no letters, no logos, no UI, no characters, no enemies, no border, no frames, no watermark. This is an art-directed voxel landscape illustration, not a software screenshot.

Portrait illustration prompt:

> Create a premium portrait 4:5 editorial website illustration in a beautifully rendered Minecraft-style voxel world. A small, lived-in timber-and-stone survival cabin with warmly glowing windows beyond an old block-built stone footbridge crossing a clear woodland stream. View from a winding forest path at human eye level, with tall dark spruce trunks framing both sides, sunlit ferns and grass blocks close in the foreground, soft morning mist and layers of woodland receding behind the house. The path and bridge lead the eye upward into the scene. Warm early sunlight, deep pine green, soft stone grey, pale cream air, tiny ember-orange window lights. Quiet, inviting, detailed block geometry and convincing game-shader light. A place you would want to keep coming home to, not an epic fantasy panorama. Strong foreground, middle ground and distant layers for a gentle vertical parallax crop. High-end art direction, coherent with a nature-led software brand. No people, no enemies, no text, no letters, no logos, no badges, no UI, no frames, no watermark. This is a scenic voxel illustration, not a captured benchmark or application screenshot.
