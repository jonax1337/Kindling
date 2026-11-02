# Writing Kindling's docs

How the documentation is organised and written. The structure follows [Diátaxis](https://diataxis.fr/): every page answers one kind of question.

## Where a page goes

| Reader's question | Type | Where |
| --- | --- | --- |
| "Show me how to start." | Tutorial | [getting-started.md](getting-started.md) |
| "How do I do X?" | How-to guide | [guides/](guides/) |
| "What does setting or command X do?" | Reference | [reference/](reference/) |
| "Why is it built this way, and what did it measure?" | Explanation | [MEASUREMENTS.md](MEASUREMENTS.md), [measurements/](measurements/) |
| "What changed in release X?" | Changelog | [CHANGELOG.md](../CHANGELOG.md) |
| "What is planned or was dropped?" | Roadmap | [ROADMAP.md](ROADMAP.md) |
| "How do I work on Kindling?" | Contributor guide | [CONTRIBUTING.md](../CONTRIBUTING.md), [UPSTREAM.md](UPSTREAM.md), [kindling-bench](../kindling-bench/README.md) |

A page does one job. A guide does not explain why; it links to the explanation. Reference does not argue; it states defaults and effects. A fact lives on one page and the others link to it.

## Rules

1. **Current state only.** Living pages describe the build as it is now. When a change is measured again, the new result replaces the old one. History lives in `CHANGELOG.md` and in Git.
2. **No dates** outside the changelog's release headings. Name a baseline by what it is: "plain Leaf `66f99f8e`", "the same build with the setting off", "release 26.3-12".
3. **No emoji.** Results use the words below. GitHub alerts (`> [!NOTE]`) are fine, at most one per page.
4. **Every number names its baseline and its number of runs**, once per table or section, not in every cell.
5. **Result first.** A measurement section opens with the result line; method and raw tables follow, folded in `<details>`.
6. **Short sentences, plain words, active voice.** Write for a server admin who knows Paper but not Kindling's internals. Use English.
7. **Links must resolve.** Relative links and heading anchors are checked by the linter.

## Result words

| Word | Meaning |
| --- | --- |
| **Faster** | better (less time, CPU or memory), and `compare.py` marks the difference `*` (p < 0.05 and a gap of more than twice the larger half range, three runs or more) |
| **Same** | no difference that `compare.py` marks in the figures the change is about |
| **Slower** | costs measurably more time, CPU or memory |
| **Trade-off** | better in one marked figure and worse in another; name both |
| **Correct** | a correctness or vanilla-parity check; no speed claim |
| **Indicative** | fewer than three runs, or a single profile; not a finding |
| **Not measured** | built, no numbers yet |
| **Dropped** | measured or built and not kept |

Opt-in settings say "opt-in" in the text; it is not a result.

## Measurement sections

```markdown
## Worker priority

**Faster.** Elytra flight tick time p95 43.9 → 37.9 ms, main-thread wait −59 %, same chunk rate.
Against the same build with the setting off, 3 runs each.

What the change does, in one to three sentences, and what it means for a server.

<details>
<summary>Method and all numbers</summary>

...

</details>
```

## Checking

```bash
node .claude/hooks/docs-lint.mjs            # every doc
node .claude/hooks/docs-lint.mjs README.md  # one file
```

The linter reports emoji, dates and broken links or anchors. In Claude Code it runs as a hook after every edit of a doc ([`.claude/settings.json`](../.claude/settings.json)).
