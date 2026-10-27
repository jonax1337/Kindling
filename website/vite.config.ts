import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, type HtmlTagDescriptor, type Plugin } from "vite";

const brandDir = fileURLToPath(new URL("../docs/branding", import.meta.url));

/**
 * Link previews and browsers ask for these files under fixed names, so they are copied from the brand kit into the
 * build instead of getting hashed asset names.
 */
function brandFilesUnderFixedNames(): Plugin {
  const files = {
    "social-preview.png": "holzschnitt/social-preview-dark.png",
    "favicon.png": "holzschnitt/server-icon.png",
  };
  return {
    name: "brand-files-under-fixed-names",
    apply: "build",
    generateBundle() {
      for (const [fileName, source] of Object.entries(files)) {
        this.emitFile({ type: "asset", fileName, source: readFileSync(`${brandDir}/${source}`) });
      }
    },
  };
}

/** The hero image and the headline font are found only after the script runs; preloading them starts both at once. */
function preloadFirstPaint(): Plugin {
  const preloads: { name: string; attrs: Record<string, string | boolean> }[] = [
    { name: "kindling-world.webp", attrs: { as: "image", fetchpriority: "high" } },
    { name: "Fraunces.woff2", attrs: { as: "font", type: "font/woff2", crossorigin: true } },
  ];
  let base = "/";
  return {
    name: "preload-first-paint",
    apply: "build",
    configResolved(config) {
      base = config.base;
    },
    transformIndexHtml: {
      order: "post",
      handler(_html, context) {
        const assets = Object.values(context.bundle ?? {}).filter((file) => file.type === "asset");
        return preloads.flatMap(({ name, attrs }): HtmlTagDescriptor[] => {
          const asset = assets.find((file) => file.names.includes(name));
          if (!asset) throw new Error(`No built asset named ${name} to preload`);
          return [
            {
              tag: "link",
              attrs: { rel: "preload", href: `${base}${asset.fileName}`, ...attrs },
              injectTo: "head",
            },
          ];
        });
      },
    },
  };
}

export default defineConfig({
  // GitHub Pages serves the project site under the repository name.
  base: "/Kindling/",
  plugins: [react(), tailwindcss(), brandFilesUnderFixedNames(), preloadFirstPaint()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      "@brand": brandDir,
    },
  },
  // The brand kit lives in docs/branding next to this folder.
  server: { host: "127.0.0.1", port: 5174, strictPort: true, fs: { allow: [".."] } },
});
