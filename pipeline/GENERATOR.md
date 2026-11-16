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
- `questions`: 2–4 multiple-choice questions per card. The app is quiz-first: these are what the learner answers, and the key points, explanation, and sources are shown as feedback afterward.
- No behavioral content, no timers, nothing about specific companies' interview processes.

## Question rules

- Each question has `id` (`q1`, `q2`...), `stem`, 3–5 `options` (`{id, text}`), `answers` (one or more option ids), and `why` (one or two sentences shown after answering).
- More than one answer makes it "select all that apply"; the app adds that label, so don't write it into the stem. At least one option must be wrong.
- The correct options must be stated by the cited passage.
- Wrong options (distractors) must be **contradicted** by the passage, not merely absent from it. The safest way is to misattribute facts from the same passage: swap push and pull CDN traits, give layer 7's behavior to layer 4, use the in-sequence availability number for the in-parallel case. A distractor that might be true in some reading is a bug.
- Keep options similar in length and style so the answer doesn't stand out. Avoid "all of the above" and "none of the above"; the app shuffles option order.
- Test understanding and application where the source allows (scenarios, "which would you choose"), not just definitions.
