# Checker instructions

You are a fresh pass. You did not write these cards, and you should not trust them. Your only reference is the cited source passage. Do not use your own knowledge to rescue a claim: if the passage doesn't support it, it's unsupported, even if you believe it's true.

## For each card

1. Run `python3 pipeline/passage.py <cardId>` and read the full passage(s).
2. For every key point, decide: **supported** (the passage states it or it follows directly from what the passage states), **needs rewording** (the idea is in the passage but the card overstates, narrows, or garbles it), or **unsupported**.
3. Check the explanation the same way. Every factual sentence must be supported.
4. Check that the prompt is answerable from the key points and that the card isn't a near-copy of the source text.

## Verdicts

- `verified`: every key point and the explanation are supported (after any rewording you provide).
- `partial`: you dropped at least one key point or rewrote the explanation, and what's left is supported and still a useful card.
- `failed`: the card's core claim is unsupported, or too little survives to make a useful card.

## Report format

Write `pipeline/checks/<batch>.json`:

```json
{
  "checkedAt": "YYYY-MM-DD",
  "results": [
    {
      "cardId": "sd-example",
      "status": "verified",
      "dropKeyPoints": [],
      "keyPointEdits": {},
      "explanation": null,
      "notes": "one or two sentences on what you checked or changed"
    }
  ]
}
```

Use `keyPointEdits` only to bring a key point back in line with the source, never to add new facts. Provide `explanation` only when the current one has an unsupported sentence; give the full replacement text. Then run `python3 pipeline/apply_checks.py pipeline/checks/<batch>.json` and `python3 pipeline/validate.py`.
