#!/usr/bin/env python3
"""Print the cited source passage(s) for a card, so the checker compares against
the real text instead of memory.

Usage:
  python3 pipeline/passage.py <cardId>          # passages for one card
  python3 pipeline/passage.py --section "Cache"  # one section by heading

A section is the Markdown heading whose text equals `section`, through to the
next heading of the same or higher level. Non-Markdown sources (HTML pages) are
stripped to text and searched by the heading text.
"""
import html
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content"
WORK = HERE / "work" / "sources"

HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")


def load_cards():
    cards = {}
    for f in sorted((CONTENT / "cards").glob("*.json")):
        for c in json.loads(f.read_text())["cards"]:
            cards[c["id"]] = c
    return cards


def source_files_for(url: str):
    sources = json.loads((HERE / "sources.json").read_text())["sources"]
    for s in sources:
        if url.startswith(s["baseUrl"]):
            base = WORK / s["id"]
            m = re.search(r"/blob/[^/]+/([^#]+)", url)
            if m:  # URL names a specific file in the repo: search only that file
                f = base / m.group(1)
                return [f] if f.exists() else []
            files = [base / f for f in s.get("files", [])]
            files += [base / p["file"] for p in s.get("pages", []) if url.split("#")[0] == p["url"].split("#")[0]]
            return [f for f in files if f.exists()]
    return []


def md_section(text: str, section: str):
    lines = text.splitlines()
    for i, line in enumerate(lines):
        m = HEADING.match(line)
        if m and m.group(2).strip() == section:
            level = len(m.group(1))
            out = [line]
            for nxt in lines[i + 1:]:
                n = HEADING.match(nxt)
                if n and len(n.group(1)) <= level:
                    break
                out.append(nxt)
            return "\n".join(out)
    return None


def html_to_text(raw: str) -> str:
    raw = re.sub(r"(?is)<(script|style|nav|header|footer)\b.*?</\1>", "", raw)
    raw = re.sub(r"(?i)<(h[1-6])[^>]*>", lambda m: "\n" + "#" * int(m.group(1)[1]) + " ", raw)
    raw = re.sub(r"(?i)</h[1-6]>", "\n", raw)
    raw = re.sub(r"(?i)<(br|p|li|div|tr)[^>]*>", "\n", raw)
    raw = re.sub(r"<[^>]+>", "", raw)
    text = html.unescape(raw)
    return re.sub(r"\n\s*\n+", "\n\n", text)


def find_section(files, section):
    for f in files:
        text = f.read_text(errors="replace")
        if f.suffix in (".html", ".htm"):
            text = html_to_text(text)
        found = md_section(text, section)
        if found:
            return f, found
    return None, None


def main(argv):
    if len(argv) >= 3 and argv[1] == "--section":
        files = sorted(WORK.rglob("*.md")) + sorted(WORK.rglob("*.html"))
        f, found = find_section(files, argv[2])
        print(found or f"NOT FOUND: {argv[2]}")
        return 0 if found else 1
    if len(argv) != 2:
        print(__doc__)
        return 2
    card = load_cards().get(argv[1])
    if not card:
        print(f"unknown card {argv[1]}")
        return 1
    ok = True
    for s in card["sources"]:
        f, found = find_section(source_files_for(s["url"]), s["section"])
        print(f"===== {s['title']} :: {s['section']} ({f.relative_to(WORK) if f else 'missing'}) =====")
        if found:
            print(found)
        else:
            ok = False
            print("NOT FOUND. Run pipeline/fetch_sources.py, or fix the card's `section`.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
