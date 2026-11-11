#!/usr/bin/env python3
"""Create one editorial DRAFT from actual founder notes with a local Ollama model.

Requires: an Ollama-compatible server on localhost, e.g. running Gemma locally.
Runs without cloud API credits. Never approves or publishes the generated draft.

    python3 marketing/local_writer.py --notes marketing/source_notes/2026-11-10.md --model gemma3:4b
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

from pipeline import POSTS, LA, SLUG, load_posts, now_la, validate_posts

HERE = Path(__file__).resolve().parent
MAX_NOTES = 24000


def local_endpoint(base: str) -> str:
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
        raise ValueError("local writer only permits http://localhost or loopback to avoid leaking founder notes")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("invalid local Ollama endpoint")
    return base.rstrip("/") + "/api/generate"


def draft_from_notes(notes: str, model: str, *, url="http://127.0.0.1:11434", sender=None):
    if not notes.strip() or len(notes) > MAX_NOTES:
        raise ValueError(f"founder notes must contain 1–{MAX_NOTES} characters")
    if not model.strip():
        raise ValueError("model must be specified")
    known_features = json.loads((HERE / "FEATURE_REGISTER.json").read_text(encoding="utf-8"))
    feature_context = "\n".join(f'- {x["name"]}: {x["editorial_status"]}' for x in known_features["features"])
    system = (
        "You are editing the authentic product journal for Spresso, a solo-built AI personal-shopping assistant. "
        "Act as a thoughtful editor, not an invented team member or fake customer. "
        "Use ONLY factual information in SOURCE NOTES below. Do not invent founders, collaborators, "
        "revenue, customers, launched features, metrics, retailer integrations, prices, or timelines. "
        "Features marked in development are not generally available. If status isn't established, frame it as a design goal or current experiment. "
        "Write in warm, specific, non-salesy, human editorial prose with concrete product examples, "
        "without fabricated direct quotes or first-hand experiences not present in the notes. "
        "Return ONLY JSON object with keys title (short, vivid), summary (one sentence), category, and "
        "body (array of 3–5 distinct short paragraphs). End naturally; avoid generic corporate slogans in every paragraph.\n"
        "FEATURE STATUS REFERENCE:\n" + feature_context + "\nSOURCE NOTES:\n" + notes
    )
    payload = json.dumps({"model":model,"prompt":system,"stream":False,"format":"json","options":{"temperature":0.55}}).encode("utf-8")
    request = urllib.request.Request(local_endpoint(url), data=payload, headers={"Content-Type":"application/json"}, method="POST")
    transport = sender or urllib.request.urlopen
    with transport(request, timeout=180) as res:
        response = json.loads(res.read().decode("utf-8"))
    if response.get("error"):
        raise RuntimeError(str(response["error"]))
    try:
        obj = json.loads(response["response"])
    except (KeyError, TypeError, json.JSONDecodeError) as ex:
        raise ValueError("local model returned invalid JSON; no draft saved") from ex
    if not isinstance(obj, dict):
        raise ValueError("expected article object from local model")
    title, summary, body = obj.get("title"), obj.get("summary"), obj.get("body")
    if not isinstance(title, str) or not 8 <= len(title.strip()) <= 115:
        raise ValueError("invalid draft title length")
    if not isinstance(summary, str) or not 15 <= len(summary.strip()) <= 320:
        raise ValueError("invalid draft summary length")
    if not isinstance(body, list) or not 2 <= len(body) <= 6 or not all(isinstance(p, str) and 20 <= len(p) <= 1200 for p in body):
        raise ValueError("invalid generated paragraphs")
    return {"title":title.strip(),"summary":summary.strip(),"category":str(obj.get("category") or "Behind the build")[:45],"body":body}


def save_draft(article: dict, notes_path: Path, slug: str, model: str, *, now=None, dest=POSTS):
    if not SLUG.fullmatch(slug):
        raise ValueError("slug must contain lowercase letters, numbers and hyphens only")
    target = dest / (slug + ".json")
    if target.exists():
        raise FileExistsError("draft already exists; refusing to overwrite")
    instant = now or now_la()
    scheduled = (instant.astimezone(LA) + dt.timedelta(days=3)).replace(hour=10,minute=0,second=0,microsecond=0)
    post = {"slug":slug,**article,"status":"draft","approved_by":"","publish_at":scheduled.isoformat(),"body":article["body"],"channels":{},"source_notes":str(notes_path),"drafted_with_local_model":model}
    validate_posts([post])
    dest.mkdir(parents=True, exist_ok=True)
    # Exclusive create means an accidentally repeated run cannot overwrite an existing draft.
    with target.open("x", encoding="utf-8") as f:
        json.dump(post,f,indent=2,ensure_ascii=False)
        f.write("\n")
    return target


def main(argv=None):
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--notes", type=Path, required=True, help="Real dated notes authored by Spresso's builder")
    cli.add_argument("--slug", help="Unique lowercase story slug; defaults to notes stem")
    cli.add_argument("--model", default=os.environ.get("SPRESSO_LOCAL_MODEL", "gemma3:4b"))
    cli.add_argument("--ollama-url", default=os.environ.get("OLLAMA_HOST", "http://127.0.0.1:11434"))
    args = cli.parse_args(argv)
    if not args.notes.is_file():
        cli.error("notes file does not exist")
    slug = args.slug or args.notes.stem
    if not SLUG.fullmatch(slug):
        cli.error("--slug must contain lowercase letters, numbers and hyphens")
    if (POSTS / (slug + '.json')).exists():
        cli.error("slug already exists; choose another")
    notes = args.notes.read_text(encoding="utf-8")
    article = draft_from_notes(notes,args.model,url=args.ollama_url)
    path = save_draft(article,args.notes,slug,args.model)
    print(f"Created DRAFT (not approved / not published): {path}")

if __name__ == '__main__':
    try: main()
    except (OSError, ValueError, RuntimeError) as e:
        print(f"ERROR: {e}",file=sys.stderr)
        sys.exit(1)
