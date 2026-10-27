# Kindling website

The public site is a static landing page at **https://jonax1337.github.io/Kindling/**. GitHub Pages publishes the `/docs` directory on `ver/26.3`, with `.nojekyll` disabling Jekyll processing. There is no Node build, Java build, backend or separate documentation framework.

## What belongs where

- `index.html`: landing page, measured results, quick start and FAQ.
- `site.css`: responsive light/dark themes using the existing Kindling palette and self-hosted Fraunces/Manrope fonts.
- `site.js`: navigation, theme choice, accessible command tabs, clipboard feedback and GSAP animation.
- `vendor/`: pinned GSAP 3.15.0 core and ScrollTrigger distribution files. See the license information there.
- `media/kindling-world.webp`: generated voxel-world illustration, optimized to WebP at its original 1916 x 821 dimensions.
- `media/kindling-home.webp`: portrait woodland illustration at 1122 x 1402, used beside the feature descriptions.
- `branding/`: the existing Kindling K logo, wordmark, fonts and social-preview assets.

The repository README remains the configuration reference; `MEASUREMENTS.md` remains the source for performance claims. The site links to both on GitHub. The pregeneration comparison is explicitly against Kindling 26.3-1, while the elytra result states its workload. No live release data or user data is fetched by the page. Download links go to the release list because builds are currently pre-releases.

## Motion

GSAP provides the hero parallax, entrance sequence, reading progress and section reveals. A second, gently moving world illustration accompanies the feature descriptions, with a sticky frame on desktop and normal document flow on mobile. Scrolling stays native. Transform and opacity animation is disabled for `prefers-reduced-motion`; navigation, downloads, tabs and clipboard remain functional. Content is readable if JavaScript or the animation runtime is unavailable.

The design follows the existing identity, with design variance 6, motion intensity 7 and visual density 3. The serif is deliberate: Fraunces is already Kindling's wordmark font. Both themes share the same layout and palette throughout.

## Spacing

`site.css` defines an 8-pixel spacing scale with `--space-1` through `--space-16`. Component gaps, padding, margins and section rhythms use these tokens. Desktop/tablet/mobile gutters are 48/32/24 pixels, with 16-pixel gutters below 360 pixels. Text line heights follow the same rhythm. Borders, font sizes and image crop percentages retain their own optical dimensions.

Keep these tokens when adjusting layout. Major sections use 64, 96 or 128 pixels; paragraph spacing uses 16, 24 or 32 pixels. Shared columns and controls align to the same spacing rules instead of introducing individual offsets.

## Preview and publication

For a local static preview:

```sh
python -m http.server 4173 --bind 127.0.0.1 --directory docs
```

On GitHub, **Settings > Pages > Build and deployment** should use **Deploy from a branch**, branch **ver/26.3**, folder **/docs**. This uses GitHub's Pages deployment and does not trigger a Kindling server build for changes confined to `docs/` and Markdown files.

The site uses relative asset paths, so it works under the `/Kindling/` project prefix. Update the canonical URL, Open Graph URLs, sitemap and root README website link together if the repository is renamed again.

## Hero illustration

The image was generated with the built-in image generation tool, then encoded as WebP without changing the composition. It is an illustration, not a gameplay screenshot or benchmark capture. The original is retained in the generating chat's image artifacts.

Generation prompt:

> Create a premium panoramic website hero illustration for Kindling, a Minecraft survival server software project. Ultra-wide landscape 21:9 composition. A peaceful expansive Minecraft-style voxel world at golden dawn: a winding river cuts through a lush spruce and oak valley, blocky cliffs and distant layered mountains dissolve into pale mist, one small warm timber-and-stone survival house nestled near the river on the right third, tiny amber lantern light. Rich forest greens, warm sunlit grass, pale cream sky, restrained warm orange highlights. Beautiful game-world cinematography, crisp block-built terrain and foliage, atmospheric distance, polished natural shader lighting, inviting and calm rather than epic fantasy. Camera from an elevated hill with a few dark blocky foliage shapes at the far bottom edge, valley open in the middle and right. The upper left quarter must be quiet, darker forest and soft open air suitable behind overlaid website text. Wide horizontal composition with generous scenic depth. No text, no letters, no logos, no UI, no characters, no enemies, no border, no frames, no watermark. This is an art-directed voxel landscape illustration, not a software screenshot.

Portrait illustration prompt:

> Create a premium portrait 4:5 editorial website illustration in a beautifully rendered Minecraft-style voxel world. A small, lived-in timber-and-stone survival cabin with warmly glowing windows beyond an old block-built stone footbridge crossing a clear woodland stream. View from a winding forest path at human eye level, with tall dark spruce trunks framing both sides, sunlit ferns and grass blocks close in the foreground, soft morning mist and layers of woodland receding behind the house. The path and bridge lead the eye upward into the scene. Warm early sunlight, deep pine green, soft stone grey, pale cream air, tiny ember-orange window lights. Quiet, inviting, detailed block geometry and convincing game-shader light. A place you would want to keep coming home to, not an epic fantasy panorama. Strong foreground, middle ground and distant layers for a gentle vertical parallax crop. High-end art direction, coherent with a nature-led software brand. No people, no enemies, no text, no letters, no logos, no badges, no UI, no frames, no watermark. This is a scenic voxel illustration, not a captured benchmark or application screenshot.
