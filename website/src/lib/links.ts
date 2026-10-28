const REPOSITORY = "https://github.com/jonax1337/Kindling";
const DOCS = `${REPOSITORY}/blob/ver/26.3/docs`;

export const links = {
  repository: REPOSITORY,
  releases: `${REPOSITORY}/releases`,
  measurements: `${DOCS}/MEASUREMENTS.md`,
  elytraTest: `${DOCS}/MEASUREMENTS.md#elytra-test-new-chunks-ahead-of-fast-players`,
  leafComparison: `${DOCS}/MEASUREMENTS.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf`,
  roadmap: `${DOCS}/ROADMAP.md`,
  license: `${REPOSITORY}/blob/ver/26.3/LICENSE.md`,
  leaf: "https://github.com/Winds-Studio/Leaf",
} as const;
