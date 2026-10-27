# Kindling brand kit

**Holzschnitt (concept 09) is Kindling's selected repository identity.** The root README uses its banner with automatic light/dark variants.

- **Holzschnitt (concept 09):** the primary identity: the carved K and central ember, paired with a warm, heavy serif wordmark.
- **Glutblatt (concept 01):** the retained alternative: the leaf/flame mark, paired with a bold geometric wordmark.

Both share the same palette and layouts. Use one direction consistently within a surface; do not place both full logos side by side in the repository header.

![Brand overview](brand-overview.png)

## Files

Every row below exists in **both** `glutblatt/` and `holzschnitt/`. All SVG masters have a matching PNG export. `light` means intended for light backgrounds; `dark` means intended for dark backgrounds. Transparent files retain genuine alpha in the PNGs.

| Asset | SVG / PNG names | PNG dimensions | Background |
| --- | --- | --- | --- |
| Symbol only | `icon-color`, `icon-inverse` | 1024 x 1024 | Transparent |
| One-color symbol | `icon-mono-black`, `icon-mono-white` | 1024 x 1024 | Transparent |
| Horizontal logo | `logo-horizontal-light`, `logo-horizontal-dark` | 1600 x 420 | Transparent |
| Stacked logo | `logo-stacked-light`, `logo-stacked-dark` | 1000 x 1000 | Transparent |
| Text only | `wordmark-light`, `wordmark-dark` | 1600 x 480 | Transparent |
| Avatar | `avatar-light`, `avatar-dark` | 1024 x 1024 | Solid |
| README banner | `banner-light`, `banner-dark` | 1600 x 500 | Solid |
| Social preview | `social-preview-light`, `social-preview-dark` | 1280 x 640 | Solid |

Additional PNGs: `server-icon.png` (64 x 64, dark background), `icon-32-light.png`, and `icon-32-dark.png` (32 x 32, transparent).

The masters are **real vector paths**, including all typography: no embedded raster images, external font dependencies, scripts, remote assets, or SVG filters. Open them directly in Inkscape, Figma, Illustrator, or a browser. Use the SVG as the source for any new export size, rather than enlarging a PNG.

## Repository README header

The **root** README uses the following banner from `docs/branding/`. These paths are relative to the repository root. The theme-aware markup follows [GitHub's documented picture pattern](https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/quickstart-for-writing-on-github#adding-an-image-to-suit-your-visitors).

```html
<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/holzschnitt/banner-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="docs/branding/holzschnitt/banner-light.svg">
    <img alt="Kindling" src="docs/branding/holzschnitt/banner-light.svg" width="1280">
  </picture>
</h1>
```

For the Glutblatt alternative, replace `holzschnitt` with `glutblatt`. PNGs with the same basename are available if the destination does not support SVG.

For a compact transparent header, use:

```html
<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/holzschnitt/logo-horizontal-dark.svg">
    <img alt="Kindling" src="docs/branding/holzschnitt/logo-horizontal-light.svg" width="560">
  </picture>
</div>
```

Use either the banner or the compact full logo as the main header to avoid repeating the wordmark.

## GitHub social preview and avatar

Use the [Holzschnitt dark social preview](holzschnitt/social-preview-dark.png) for repository link cards. Upload this **PNG** in repository **Settings > General > Social preview > Edit**; adding it to Git alone does not change that setting. GitHub recommends 1280 x 640 pixels and a file below 1 MB; all supplied social previews meet those limits. See [GitHub's social preview documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview).

For an organization or profile avatar, use the corresponding 1024-pixel `avatar-*.png`. A repository has no separate general-purpose avatar setting. The avatar exports keep the mark inside a generous safe area for circular crops. The 64-pixel `server-icon.png` is ready to copy to a Minecraft server directory separately; no running server was changed.

## Color and typography

| Role | Light surfaces | Dark surfaces |
| --- | --- | --- |
| Ember | `#F56B27` | `#FF823F` |
| Leaf | `#4C7E35` | `#82B65F` |
| Wordmark / foreground | `#19291F` | `#FAF7EF` |
| Surface | `#FAF7EF` | `#101A14` |
| Secondary copy | `#556457` | `#ACB9AA` |

Machine-readable values are in `palette.json`. Orange and green are identity accents; body copy uses the foreground colors.

- **Glutblatt wordmark:** Manrope ExtraBold, weight 800.
- **Holzschnitt wordmark:** Fraunces, weight 900, optical size 9, softness 100, wonky 0.
- **Supporting text:** Manrope Medium / Bold, weights 500 / 700.

Original variable fonts and their SIL Open Font License notices are included in `fonts/`. Font sources: [Manrope](https://github.com/google/fonts/tree/main/ofl/manrope), [Fraunces](https://github.com/google/fonts/tree/main/ofl/fraunces). The included font licenses cover those font files; this kit does not change the repository's software license.

The SVG wordmarks use outlines with small optical spacing adjustments. Editing ordinary live text will not reproduce the outlines exactly; use the supplied wordmark paths for the identity.

## Usage

- Keep the original proportions and built-in clear space. Do not stretch, rotate, or add outlines or shadows to the mark.
- Use the light asset on pale surfaces and the dark asset on dark surfaces. The one-color symbols are true black and white.
- Prefer at least 32 pixels for the standalone symbol. At smaller sizes, inspect the leaf vein and the K's central spark before use.
- The banner copy comes from the repository's existing positioning; it introduces no new measured performance claim.
- Visual checks covered the exported images, transparency, dimensions, and SVG structure. GitHub uploads, the live README presentation, avatar crops, and the Minecraft server-list appearance remain manual integration checks.

## Package overview

`brand-overview.svg` / `.png` is the presentation board. `index.html` is an offline contact sheet with direct links to the assets. `exports.json` lists the SVG masters, output sizes, and transparency. The kit contains 33 SVG/PNG pairs and 6 additional small PNGs.
