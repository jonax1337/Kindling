#!/usr/bin/env node
// Checks Kindling's Markdown docs against docs/STYLE.md:
// no emoji, no dates outside the changelog's release headings, no broken relative links or anchors.
//
//   node .claude/hooks/docs-lint.mjs            check every doc
//   node .claude/hooks/docs-lint.mjs FILE...    check these files
//   node .claude/hooks/docs-lint.mjs --hook     Claude Code PostToolUse hook: check the file the tool wrote,
//                                               exit 2 so Claude sees the findings

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const DOC_DIRS = ['docs', 'kindling-bench'];
const ROOT_DOCS = ['README.md', 'CHANGELOG.md', 'CONTRIBUTING.md', 'AGENTS.md'];
const SKIPPED_DIRS = new Set(['node_modules', 'runs', 'build', '.gradle']);

// Pictographs from U+2300 on; below that are text symbols such as (c), (tm) and arrows.
const EMOJI = /[\p{Extended_Pictographic}\u{1F1E6}-\u{1F1FF}\u{FE0F}]/gu;
const FIRST_EMOJI_CODE_POINT = 0x2300;
const MONTH = '(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?';
const DATE = new RegExp(`\\b20\\d\\d[-/][01]\\d[-/][0-3]\\d\\b|\\b[0-3]?\\d\\.? ${MONTH} 20\\d\\d\\b|\\b${MONTH} [0-3]?\\d,? 20\\d\\d\\b`);
const CHANGELOG_RELEASE_HEADING = /^## \S+ - 20\d\d-[01]\d-[0-3]\d\s*$/;
const LINK = /\]\(\s*(?:<([^>]+)>|([^)\s]+))(?:\s+(?:"[^"]*"|'[^']*'))?\s*\)|(?:src|srcset|href)=(?:"([^"]+)"|'([^']+)')|^\s{0,3}\[[^\]]+\]:\s*<?([^\s>]+)>?/g;
const FENCE = /^\s{0,3}(`{3,}|~{3,})/;
const SETEXT_UNDERLINE = /^\s{0,3}(=+|-+)\s*$/;

function isDoc(path) {
  const rel = relative(ROOT, path).split(sep).join('/');
  if (!rel.endsWith('.md') || rel.startsWith('..')) return false;
  if (ROOT_DOCS.includes(rel)) return true;
  return DOC_DIRS.some((dir) => rel.startsWith(dir + '/')) && !rel.split('/').some((part) => SKIPPED_DIRS.has(part));
}

function allDocs() {
  const found = ROOT_DOCS.map((name) => join(ROOT, name)).filter(existsSync);
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      if (SKIPPED_DIRS.has(name)) continue;
      const path = join(dir, name);
      if (statSync(path).isDirectory()) walk(path);
      else if (isDoc(path)) found.push(path);
    }
  };
  DOC_DIRS.map((dir) => join(ROOT, dir)).filter(existsSync).forEach(walk);
  return found;
}

// Lines outside fenced code blocks, numbered from 1; `text` has inline code removed, `raw` keeps it.
function proseLines(text) {
  const lines = [];
  let fence = null;
  text.split(/\r?\n/).forEach((line, index) => {
    const marker = FENCE.exec(line)?.[1];
    if (marker && !fence) {
      fence = marker;
      return;
    }
    if (fence) {
      if (marker && marker[0] === fence[0] && marker.length >= fence.length && line.trim() === marker) fence = null;
      return;
    }
    lines.push({ number: index + 1, raw: line, text: line.replace(/`[^`]*`/g, '') });
  });
  return lines;
}

function slugOf(heading) {
  return heading
    .replace(/<[^>]+>/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}\s_-]/gu, '')
    .replace(/\s/g, '-');
}

// GitHub's heading slugs, including the -1, -2 suffixes of repeated headings.
function anchorsOf(path) {
  const anchors = new Set();
  const counts = new Map();
  let previous = null;
  for (const line of proseLines(readFileSync(path, 'utf8'))) {
    const { raw } = line;
    for (const match of raw.matchAll(/<a\s+(?:id|name)="([^"]+)"/g)) anchors.add(match[1].toLowerCase());
    const atx = /^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$/.exec(raw)?.[1];
    const setext = SETEXT_UNDERLINE.test(raw) && previous?.number === line.number - 1 && previous.raw.trim()
      && !/^\s*([-*+>|]|\d+\.|#)/.test(previous.raw) ? previous.raw.trim() : null;
    previous = line;
    const heading = atx ?? setext;
    if (heading == null) continue;
    const slug = slugOf(heading);
    const seen = counts.get(slug) ?? 0;
    anchors.add(seen === 0 ? slug : `${slug}-${seen}`);
    counts.set(slug, seen + 1);
  }
  return anchors;
}

function checkLink(path, target) {
  if (/^([a-z]+:|\/\/|mailto:)/i.test(target)) return null;
  const [location, anchor] = target.split('#');
  const file = location.split('?')[0];
  let decoded;
  try {
    decoded = decodeURIComponent(file);
  } catch {
    return `link with a malformed escape: ${target}`;
  }
  const base = decoded.startsWith('/') ? ROOT : dirname(path);
  const destination = decoded ? resolve(base, decoded.replace(/^\/+/, '')) : path;
  if (!existsSync(destination)) return `link to a missing file: ${target}`;
  if (anchor && destination.endsWith('.md') && !anchorsOf(destination).has(anchor.toLowerCase())) {
    return `link to a missing heading: ${target}`;
  }
  return null;
}

function lint(path) {
  const findings = [];
  const isChangelog = relative(ROOT, path) === 'CHANGELOG.md';
  for (const { number, text } of proseLines(readFileSync(path, 'utf8'))) {
    const report = (message) => findings.push(`${relative(ROOT, path)}:${number}: ${message}`);
    if ([...text.matchAll(EMOJI)].some(([symbol]) => symbol.codePointAt(0) >= FIRST_EMOJI_CODE_POINT)) {
      report('emoji (write the word instead)');
    }
    if (DATE.test(text) && !(isChangelog && CHANGELOG_RELEASE_HEADING.test(text))) {
      report('date (living docs describe the current state; dates belong to CHANGELOG.md release headings)');
    }
    for (const match of text.matchAll(LINK)) {
      const targets = match[1] ?? match[2] ?? match[5] ?? (match[3] ?? match[4]).split(',');
      for (const target of [targets].flat().map((part) => part.trim().split(/\s+/)[0])) {
        const problem = target && checkLink(path, target);
        if (problem) report(problem);
      }
    }
  }
  return findings;
}

function hookTarget() {
  try {
    const input = JSON.parse(readFileSync(0, 'utf8') || '{}');
    return input.tool_input?.file_path ?? null;
  } catch {
    return null;
  }
}

const runAsHook = process.argv[2] === '--hook';
const args = runAsHook ? [] : process.argv.slice(2);
const target = runAsHook ? hookTarget() : null;
if (runAsHook && !(target && isDoc(resolve(target)))) process.exit(0);

const files = target ? [resolve(target)] : args.length ? args.map((arg) => resolve(arg)) : allDocs();
const findings = files.filter(existsSync).flatMap(lint);
if (findings.length === 0) process.exit(0);

console.error(`${findings.join('\n')}\n\n${findings.length} docs finding(s); rules: docs/STYLE.md`);
process.exit(runAsHook ? 2 : 1);
