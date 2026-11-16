#!/usr/bin/env python3
"""Validate bundled content: schema, unique stable IDs, prereqs exist, no prereq
cycles, at least one source per card. Exits non-zero on any error.

Usage: python3 pipeline/validate.py
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content"
SLUG = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
KINDS = {"concept", "design"}
STATUSES = {"unchecked", "verified", "partial", "failed"}

errors: list[str] = []


def err(where: str, msg: str) -> None:
    errors.append(f"{where}: {msg}")


def need(obj: dict, where: str, field: str, typ, nonempty=True):
    if field not in obj:
        err(where, f"missing `{field}`")
        return None
    v = obj[field]
    if not isinstance(v, typ) or isinstance(v, bool) and typ is int:
        err(where, f"`{field}` should be {typ.__name__ if isinstance(typ, type) else typ}")
        return None
    if nonempty and isinstance(v, (str, list)) and not v:
        err(where, f"`{field}` is empty")
    return v


def main() -> int:
    meta = json.loads((CONTENT / "tracks.json").read_text())
    tracks = {t["id"]: t for t in meta["tracks"]}
    topics = {}
    for t in meta["tracks"]:
        need(t, f"track {t.get('id')}", "name", str)
    for t in meta["topics"]:
        where = f"topic {t.get('id')}"
        if t["id"] in topics:
            err(where, "duplicate topic id")
        topics[t["id"]] = t
        if t.get("trackId") not in tracks:
            err(where, f"unknown trackId {t.get('trackId')}")
        need(t, where, "order", int)

    cards = {}
    for f in sorted((CONTENT / "cards").glob("*.json")):
        for c in json.loads(f.read_text())["cards"]:
            where = f"{f.name}:{c.get('id')}"
            cid = need(c, where, "id", str)
            if cid is None:
                continue
            if not SLUG.match(cid):
                err(where, "id must be a lowercase slug")
            if cid in cards:
                err(where, "duplicate card id")
            cards[cid] = c
            tr = need(c, where, "trackId", str)
            tp = need(c, where, "topicId", str)
            if tr and tr not in tracks:
                err(where, f"unknown trackId {tr}")
            if tp and tp not in topics:
                err(where, f"unknown topicId {tp}")
            elif tp and topics[tp]["trackId"] != tr:
                err(where, "topic belongs to a different track")
            if c.get("kind") not in KINDS:
                err(where, f"kind must be one of {sorted(KINDS)}")
            if c.get("difficulty") not in (1, 2, 3):
                err(where, "difficulty must be 1, 2, or 3")
            need(c, where, "prompt", str)
            need(c, where, "explanation", str)
            kps = need(c, where, "keyPoints", list) or []
            kp_ids = set()
            for kp in kps:
                kpid = kp.get("id")
                if not kpid or kpid in kp_ids:
                    err(where, f"key point id missing or duplicate: {kpid}")
                kp_ids.add(kpid)
                if not isinstance(kp.get("text"), str) or not kp["text"].strip():
                    err(where, f"key point {kpid} has no text")
                if c.get("kind") == "design" and not kp.get("group"):
                    err(where, f"design card key point {kpid} needs a group")
            srcs = need(c, where, "sources", list) or []
            for s in srcs:
                for k in ("title", "url", "section"):
                    if not isinstance(s.get(k), str) or not s[k]:
                        err(where, f"source missing `{k}`")
                if isinstance(s.get("url"), str) and not s["url"].startswith("https://"):
                    err(where, f"source url should be https: {s['url']}")
            qs = need(c, where, "questions", list) or []
            q_ids = set()
            for q in qs:
                qw = f"{where}:{q.get('id')}"
                if not q.get("id") or q["id"] in q_ids:
                    err(qw, "question id missing or duplicate")
                q_ids.add(q.get("id"))
                for k in ("stem", "why"):
                    if not isinstance(q.get(k), str) or not q[k].strip():
                        err(qw, f"question needs `{k}`")
                opts = q.get("options") or []
                opt_ids = [o.get("id") for o in opts]
                if not 3 <= len(opts) <= 5:
                    err(qw, "needs 3 to 5 options")
                if len(set(opt_ids)) != len(opt_ids) or any(not isinstance(o.get("text"), str) or not o["text"].strip() for o in opts):
                    err(qw, "options need unique ids and text")
                if len({o.get("text", "").strip().lower() for o in opts}) != len(opts):
                    err(qw, "duplicate option text")
                ans = q.get("answers") or []
                if not ans or any(a not in opt_ids for a in ans) or len(set(ans)) != len(ans):
                    err(qw, "answers must be a non-empty subset of option ids")
                if len(ans) >= len(opts):
                    err(qw, "at least one option must be wrong")
            need(c, where, "prereqs", list, nonempty=False)
            v = need(c, where, "version", int)
            if v is not None and v < 1:
                err(where, "version must be >= 1")
            ver = c.get("verified")
            if not isinstance(ver, dict) or ver.get("status") not in STATUSES:
                err(where, f"verified.status must be one of {sorted(STATUSES)}")

    for cid, c in cards.items():
        for p in c.get("prereqs", []):
            if p not in cards:
                err(cid, f"prereq {p} does not exist")
            elif p == cid:
                err(cid, "card lists itself as a prereq")

    # Cycle detection (DFS with colors).
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {cid: WHITE for cid in cards}

    def visit(cid, path):
        color[cid] = GRAY
        for p in cards[cid].get("prereqs", []):
            if p not in cards:
                continue
            if color[p] == GRAY:
                err(cid, "prereq cycle: " + " -> ".join(path + [p]))
            elif color[p] == WHITE:
                visit(p, path + [p])
        color[cid] = BLACK

    for cid in cards:
        if color[cid] == WHITE:
            visit(cid, [cid])

    by_status = {}
    for c in cards.values():
        s = c.get("verified", {}).get("status")
        by_status[s] = by_status.get(s, 0) + 1
    print(f"{len(cards)} cards, {len(topics)} topics, {len(tracks)} tracks; verification: {by_status}")
    if errors:
        print(f"\n{len(errors)} error(s):")
        for e in errors:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
