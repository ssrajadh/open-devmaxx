# Generator instructions

You turn sections of a vetted source into cards. You do not use your own knowledge to add facts: if the source section doesn't say it, it doesn't go on the card.

## Process

1. Pick a source section (a heading in a fetched file under `pipeline/work/sources/`). Read it in full with `python3 pipeline/passage.py --section "<heading>"`.
2. Write one card per idea worth recalling. Typically 3–7 key points per concept card, 8–15 per design card.
3. Paraphrase. Don't paste sentences from the source. Short technical terms and names (e.g. "write-through", "Base62") are fine.
4. Cite the exact heading text in `sources[].section` and a URL with the section anchor. If the card draws on two sections, list both.
5. Set `verified` to `{"status": "unchecked", "checkedAt": null}` and `version` to 1.
6. Run `python3 pipeline/validate.py`.

## Card rules

- `id`: stable lowercase slug with a track prefix (`sd-`, `cs-`, `ai-`). Never reuse or rename an ID once shipped; it's the key for the user's progress.
- `prompt`: a question the learner can answer out loud in under a minute (concept) or walk through in 10–20 minutes (design).
- `keyPoints`: each one is a single checkable claim. The learner ticks the ones they covered, so avoid vague points like "understands caching".
- `group`: required on design cards (e.g. Requirements, Estimates, High-level design, Data model, Core components, Scaling).
- `explanation`: short Markdown. Context, a mnemonic, or a nuance from the same source. Still no unsupported claims.
- `prereqs`: only cards the learner really needs first. Keep chains shallow.
- `difficulty`: 1 = definition, 2 = compare/contrast or tradeoff, 3 = multi-part or design.
- No behavioral content, no timers, nothing about specific companies' interview processes.
