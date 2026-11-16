#!/usr/bin/env python3
"""Apply a checker report to the bundled cards.

Report format (pipeline/checks/<batch>.json):
{
  "checkedAt": "2026-11-15",
  "results": [
    {
      "cardId": "sd-cap-theorem",
      "status": "verified" | "partial" | "failed",
      "dropKeyPoints": ["kp3"],            // key points the passage does not support
      "keyPointEdits": {"kp2": "new text"},// optional rewording to match the source
      "explanation": "...",                // optional replacement explanation
      "dropQuestions": ["q2"],              // questions with an unsupported answer or a distractor that might be true
      "questionEdits": {"q1": {...}},       // full replacement question object (same id)
      "notes": "why"                        // free text, kept in the report only
    }
  ]
}

Rules:
- "failed" cards are removed from the bundle (the report keeps the record).
- Dropping or rewording key points bumps `version`, which resets that card's
  scheduling in the app (key points changed). Explanation-only edits also bump
  `version` but the app keeps progress when the key point set is unchanged.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content" / "cards"


def main(argv) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    report = json.loads(pathlib.Path(argv[1]).read_text())
    at = report["checkedAt"]
    results = {r["cardId"]: r for r in report["results"]}
    seen = set()
    for f in sorted(CONTENT.glob("*.json")):
        data = json.loads(f.read_text())
        kept = []
        for c in data["cards"]:
            r = results.get(c["id"])
            if not r:
                kept.append(c)
                continue
            seen.add(c["id"])
            if r["status"] == "failed":
                print(f"removed {c['id']}: {r.get('notes', '')}")
                continue
            changed = False
            drop = set(r.get("dropKeyPoints", []))
            if drop:
                c["keyPoints"] = [k for k in c["keyPoints"] if k["id"] not in drop]
                changed = True
            for kid, text in r.get("keyPointEdits", {}).items():
                for k in c["keyPoints"]:
                    if k["id"] == kid and k["text"] != text:
                        k["text"] = text
                        changed = True
            dq = set(r.get("dropQuestions", []))
            if dq:
                c["questions"] = [q for q in c.get("questions", []) if q["id"] not in dq]
                changed = True
            for qid, newq in r.get("questionEdits", {}).items():
                for i, q in enumerate(c.get("questions", [])):
                    if q["id"] == qid and q != newq:
                        c["questions"][i] = dict(newq, id=qid)
                        changed = True
            if r.get("explanation") and r["explanation"] != c["explanation"]:
                c["explanation"] = r["explanation"]
                changed = True
            if changed:
                c["version"] += 1
            status = r["status"]
            if not c.get("questions"):
                status = "failed"  # nothing left to quiz on; the app hides it
                print(f"{c['id']}: no questions left, marked failed")
            c["verified"] = {"status": status, "checkedAt": at}
            kept.append(c)
        data["cards"] = kept
        f.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    missing = set(results) - seen
    if missing:
        print(f"report mentions unknown cards: {sorted(missing)}")
        return 1
    print(f"applied {len(seen)} results")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
