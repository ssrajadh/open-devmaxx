#!/usr/bin/env python3
"""Download the vetted sources listed in sources.json into pipeline/work/sources/.

Git sources are shallow-cloned. URL sources (HTML pages) are saved as raw files.
Everything under pipeline/work/ is gitignored: it's a local cache, not content.
"""
import json
import pathlib
import subprocess
import sys
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
WORK = HERE / "work" / "sources"


def main() -> int:
    sources = json.loads((HERE / "sources.json").read_text())["sources"]
    WORK.mkdir(parents=True, exist_ok=True)
    for src in sources:
        dest = WORK / src["id"]
        if "repo" in src:
            if dest.exists():
                subprocess.run(["git", "-C", str(dest), "pull", "-q", "--ff-only"], check=False)
            else:
                subprocess.run(["git", "clone", "-q", "--depth", "1", src["repo"], str(dest)], check=True)
            rev = subprocess.run(["git", "-C", str(dest), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
            print(f"{src['id']}: {rev}")
        for page in src.get("pages", []):
            dest.mkdir(parents=True, exist_ok=True)
            out = dest / page["file"]
            if not out.exists():
                req = urllib.request.Request(page["url"], headers={"User-Agent": "interview-prep-content-pipeline"})
                out.write_bytes(urllib.request.urlopen(req, timeout=60).read())
            print(f"{src['id']}: {page['file']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
