const REPOSITORY = "https://github.com/jonax1337/Kindling";
const DOCS = `${REPOSITORY}/blob/ver/26.3/docs`;

export const links = {
  repository: REPOSITORY,
  releases: `${REPOSITORY}/releases`,
  measurements: `${DOCS}/MEASUREMENTS.md`,
  elytraTest: `${DOCS}/MEASUREMENTS.md#elytra-test-new-chunks-ahead-of-fast-players`,
  generationLimit: `${DOCS}/MEASUREMENTS.md#concurrent-generations-per-player`,
  roadmap: `${DOCS}/ROADMAP.md`,
  license: `${REPOSITORY}/blob/ver/26.3/LICENSE.md`,
  leaf: "https://github.com/Winds-Studio/Leaf",
} as const;
