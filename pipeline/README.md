# Content pipeline

Runs on a laptop through Claude Code. Nothing here ships in the app except the JSON it writes to `app/src/main/assets/content/`.

## Steps

1. **Register sources** in `sources.json` (id, URL or repo, license, attribution, which files or pages). Only vetted, free sources. Check the license before reusing text, and paraphrase rather than copy. Never use paid apps as a source.
2. **Fetch** them into the local cache: `python3 pipeline/fetch_sources.py` (writes `pipeline/work/`, which is gitignored).
3. **Generate** cards with Claude Code, following [GENERATOR.md](GENERATOR.md). Every card cites the exact source section it came from, and new cards start as `"verified": {"status": "unchecked"}`.
4. **Check** each card in a fresh Claude Code session or subagent that did not write it, following [CHECKER.md](CHECKER.md). It reads the passage with `python3 pipeline/passage.py <cardId>` and writes a report to `pipeline/checks/<batch>.json`.
5. **Apply** the report: `python3 pipeline/apply_checks.py pipeline/checks/<batch>.json`. Unsupported key points are dropped, failed cards are removed, and `verified` is filled in.
6. **Validate**: `python3 pipeline/validate.py` (schema, unique IDs, prereqs exist, no cycles, at least one source per card).

## Fixing flagged cards

The app exports flags (Settings → Export flags) as JSON. Re-run the checker on those card IDs with the flag notes as extra context, apply the report, and validate. If the fix changes the key points, `apply_checks.py` bumps `version`, and the app resets that card's scheduling; wording-only changes keep progress.

## Versioning rule

Bump `version` whenever a card changes. The app compares a hash of the key point IDs and texts: if it changed, scheduling resets for that card; otherwise history is kept.
