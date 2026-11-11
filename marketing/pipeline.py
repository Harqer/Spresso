#!/usr/bin/env python3
"""Spresso editorial pipeline. Standard library only; no AI or social credits required.

    python marketing/pipeline.py validate
    python marketing/pipeline.py build
    python marketing/pipeline.py queue
    python marketing/pipeline.py publish-facebook --slug welcome-to-spresso --confirm

Only explicitly approved, due content can be published. Social drafts are exported by
'queue' and never posted automatically by the scheduled GitHub Action.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import re
import sqlite3
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
POSTS = HERE / "posts"
LA = ZoneInfo("America/Los_Angeles")
PLATFORMS = ("facebook", "instagram", "tiktok", "youtube", "x", "reddit")
SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
DEFAULT_BASE = "https://harqer.github.io/Spresso/"


def now_la():
    return dt.datetime.now(tz=LA)


def parse_datetime(s: str):
    t = dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
    if t.tzinfo is None:
        raise ValueError("publish_at requires timezone e.g. -08:00 or Z")
    return t


def load_posts():
    posts = []
    for p in sorted(POSTS.glob("*.json")):
        data = json.loads(p.read_text(encoding="utf-8"))
        data["_path"] = str(p)
        posts.append(data)
    validate_posts(posts)
    return posts


def validate_posts(posts):
    ids = set()
    for p in posts:
        if not isinstance(p, dict):
            raise ValueError("post must be a JSON object")
        slug = p.get("slug", "")
        if not isinstance(slug, str) or not SLUG.fullmatch(slug):
            raise ValueError(f"invalid slug: {slug!r}")
        if slug in ids:
            raise ValueError(f"duplicate slug: {slug}")
        ids.add(slug)
        if p.get("status") not in ("draft", "approved"):
            raise ValueError(f"{slug}: status must be draft or approved")
        if not p.get("title") or not isinstance(p.get("title"), str):
            raise ValueError(f"{slug}: missing title")
        if not isinstance(p.get("summary"), str) or not p["summary"].strip():
            raise ValueError(f"{slug}: missing summary")
        if not isinstance(p.get("body"), list) or not p["body"] or not all(isinstance(x, str) and x.strip() for x in p["body"]):
            raise ValueError(f"{slug}: body must be a non-empty list of paragraphs")
        if not isinstance(p.get("approved_by"), str):
            raise ValueError(f"{slug}: missing approved_by (empty for drafts)")
        if p["status"] == "approved" and not p["approved_by"].strip():
            raise ValueError(f"{slug}: approved content must name a real reviewer")
        parse_datetime(p["publish_at"])
        if not isinstance(p.get("channels", {}), dict):
            raise ValueError(f"{slug}: channels must be an object")
        bad = set(p.get("channels", {})) - set(PLATFORMS)
        if bad:
            raise ValueError(f"{slug}: unsupported channels {bad}")
        # Human-authored, independently verified facts must be approved, not invented by a generator.
        if any(x in str(p).lower() for x in ("<script", "javascript:")):
            raise ValueError(f"{slug}: prohibited script markup in content")


def due(p, instant):
    return p["status"] == "approved" and parse_datetime(p["publish_at"]) <= instant


def html_document(title, description, body, canonical, nav_base):
    title_e = html.escape(title, quote=True)
    descr_e = html.escape(description, quote=True)
    canonical_e = html.escape(canonical, quote=True)
    base_e = html.escape(nav_base, quote=True)
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title_e} | spresso</title><meta name="description" content="{descr_e}"><link rel="canonical" href="{canonical_e}">
<meta property="og:title" content="{title_e}"><meta property="og:description" content="{descr_e}"><meta property="og:type" content="article">
<meta property="og:url" content="{canonical_e}"><link rel="alternate" type="application/rss+xml" title="Spresso Journal" href="{base_e}feed.xml">
<style>
:root{{--ink:#1d2619;--green:#446732;--cream:#fbfaf6;--pale:#eef3e8}}*{{box-sizing:border-box}}
body{{background:var(--cream);color:var(--ink);font:17px/1.72 system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0}}
header,main,footer{{max-width:800px;margin:auto;padding:24px}}header{{display:flex;align-items:center;justify-content:space-between;gap:20px;border-bottom:1px solid #dae1d5}}
a{{color:var(--green)}}.brand{{font-size:31px;font-weight:750;letter-spacing:-1.7px;text-decoration:none}}
.tagline{{font-size:12px;letter-spacing:.03em;color:#5e7951}}h1{{line-height:1.14;font-size:clamp(34px,6vw,57px);letter-spacing:-1.5px;margin-bottom:10px}}
h2{{line-height:1.2}}article{{padding:30px 0;border-bottom:1px solid #d9e4d1}}article h2{{margin:0}}
.meta{{color:#5e705c;font-size:14px}}.hero{{background:var(--pale);padding:22px;border-radius:20px;margin:35px 0}}
footer{{font-size:13px;color:#5e705c}}.btn{{background:var(--green);color:white;padding:11px 18px;text-decoration:none;border-radius:22px;display:inline-block}}
</style></head><body><header><div><a class="brand" href="{base_e}">spresso</a><div class="tagline">Your shopping, on autopilot.</div></div><a href="https://spresso.getqubits.com/">Explore the app</a></header><main>{body}</main><footer>Spresso Journal · Product stories, build notes and feature explainers. Published updates are reviewed before release.</footer></body></html>'''


def e(s):
    return html.escape(str(s), quote=True)


def post_url(base, slug):
    return base.rstrip("/") + "/posts/" + urllib.parse.quote(slug) + "/"


def build_site(posts, destination: Path, base_url: str, instant):
    base = base_url.rstrip("/") + "/"
    if not base.startswith("https://"):
        raise ValueError("base URL must be an HTTPS URL")
    destination.mkdir(parents=True, exist_ok=True)
    due_posts = sorted((p for p in posts if due(p, instant)), key=lambda x: parse_datetime(x["publish_at"]), reverse=True)
    articles = []
    feed = ET.Element("rss", version="2.0")
    channel = ET.SubElement(feed, "channel")
    for key, value in (("title", "Spresso Journal"), ("link", base), ("description", "Human-reviewed product and founder notes from Spresso.")):
        ET.SubElement(channel, key).text = value
    sitemap = ET.Element("urlset", xmlns="http://www.sitemaps.org/schemas/sitemap/0.9")
    ET.SubElement(ET.SubElement(sitemap, "url"), "loc").text = base
    for p in due_posts:
        url = post_url(base, p["slug"])
        display_date = parse_datetime(p["publish_at"]).astimezone(LA).strftime("%B %-d, %Y")
        body = f'<p class="meta">{e(display_date)} · {e(p.get("category", "Product update"))}</p><h1>{e(p["title"])}</h1><div class="hero">{e(p["summary"])}</div>'
        body += "".join(f"<p>{e(par)}</p>" for par in p["body"])
        body += f'<p><a href="{e(base)}">← All updates</a></p>'
        dst = destination / "posts" / p["slug"] / "index.html"
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(html_document(p["title"], p["summary"], body, url, base), encoding="utf-8")
        articles.append(f'<article><p class="meta">{e(display_date)} · {e(p.get("category", "Product update"))}</p><h2><a href="{e(url)}">{e(p["title"])}</a></h2><p>{e(p["summary"])}</p><a href="{e(url)}">Read update</a></article>')
        item = ET.SubElement(channel, "item")
        for key, value in (("title", p["title"]), ("link", url), ("guid", url), ("description", p["summary"]), ("pubDate", parse_datetime(p["publish_at"]).strftime("%a, %d %b %Y %H:%M:%S %z"))):
            ET.SubElement(item, key).text = value
        ET.SubElement(ET.SubElement(sitemap, "url"), "loc").text = url
    intro = '<h1>Behind spresso.</h1><div class="hero">Stories about building a more personal approach to shopping, from feature ideas to the details of how the app works.</div>'
    if not articles:
        intro += '<p>Our first stories are being prepared. Follow along for updates.</p>'
    (destination / "index.html").write_text(html_document("Journal", "Product and founder notes from Spresso.", intro + "".join(articles), base, base), encoding="utf-8")
    (destination / "feed.xml").write_bytes(ET.tostring(feed, encoding="utf-8", xml_declaration=True))
    (destination / "sitemap.xml").write_bytes(ET.tostring(sitemap, encoding="utf-8", xml_declaration=True))
    (destination / "robots.txt").write_text("User-agent: *\nAllow: /\nSitemap: " + base + "sitemap.xml\n", encoding="utf-8")
    (destination / ".nojekyll").write_text("", encoding="utf-8")
    return due_posts


def social_text(p, platform, url):
    custom = p.get("channels", {}).get(platform)
    if isinstance(custom, str) and custom.strip():
        body = custom.strip()
    else:
        body = f'{p["title"]}\n\n{p["summary"]}'
    if platform == "x":
        suffix = "\n" + url
        if len(body) + len(suffix) > 280:
            body = body[: 279 - len(suffix)].rsplit(" ", 1)[0].rstrip(".,; ") + "…"
        return body + suffix
    if platform == "reddit":
        return f"{body}\n\nRead the full update: {url}\n\n— Official Spresso team"
    return body + "\n\n" + url


def queue_posts(posts, output: Path, base_url: str, instant):
    output.mkdir(parents=True, exist_ok=True)
    base = base_url.rstrip("/") + "/"
    records = []
    for p in sorted(posts, key=lambda x: (x["publish_at"], x["slug"])):
        url = post_url(base, p["slug"])
        for platform in PLATFORMS:
            rec = {"id": p["slug"] + ":" + platform, "platform": platform, "title": p["title"], "body": social_text(p, platform, url), "url": url, "status": ("ready_for_review" if due(p, instant) else "pending_editorial_approval" if p["status"] == "draft" else "scheduled_for_blog"), "requires_media": platform in ("instagram", "tiktok", "youtube"), "origin": p["slug"], "approved_by": p["approved_by"]}
            records.append(rec)
    (output / "social_drafts.json").write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    readable = "# Spresso social publishing review queue\n\nThese are drafts, including unapproved concepts for editorial review; the Python pipeline does not post them automatically.\n\n"
    for rec in records:
        readable += f'## {rec["platform"].title()} — {rec["title"]}\n\n{rec["body"]}\n\n---\n\n'
    (output / "social_drafts.md").write_text(readable, encoding="utf-8")
    return records


def publish_facebook(post, db_path: Path, *, confirm: bool, instant):
    if not confirm:
        raise ValueError("publishing requires explicit --confirm")
    if os.environ.get("SPRESSO_SOCIAL_POSTING_ENABLED") != "yes":
        raise ValueError("set SPRESSO_SOCIAL_POSTING_ENABLED=yes to authorize live writes")
    if not due(post, instant):
        raise ValueError("post not approved or not yet due")
    page_id = os.environ.get("SPRESSO_FB_PAGE_ID", "")
    page_token = os.environ.get("SPRESSO_FB_PAGE_TOKEN", "")
    api_version = os.environ.get("SPRESSO_GRAPH_VERSION", "v24.0")
    if not re.fullmatch(r"v\d+\.\d+", api_version):
        raise ValueError("invalid Graph API version")
    if not page_id or not page_token:
        raise ValueError("SPRESSO_FB_PAGE_ID and SPRESSO_FB_PAGE_TOKEN required")
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    try:
        conn.execute("CREATE TABLE IF NOT EXISTS sent (id TEXT PRIMARY KEY, state TEXT NOT NULL, remote_id TEXT, last_attempt TEXT NOT NULL)")
        conn.commit()
        key = "facebook:" + post["slug"]
        found = conn.execute("SELECT state,remote_id FROM sent WHERE id=?", (key,)).fetchone()
        if found:
            raise ValueError(f"duplicate blocked: {key} is {found[0]} (remote ID {found[1] or 'unknown'})")
        # State is durable before the remote call. Unknown network outcome never auto-retries.
        conn.execute("INSERT INTO sent(id,state,remote_id,last_attempt) VALUES (?,?,?,?)", (key, "unknown", None, instant.isoformat()))
        conn.commit()
        url = post_url(os.environ.get("SPRESSO_BLOG_BASE_URL", DEFAULT_BASE), post["slug"])
        body = social_text(post, "facebook", url)
        payload = urllib.parse.urlencode({"message": body, "access_token": page_token}).encode("utf-8")
        req = urllib.request.Request(f"https://graph.facebook.com/{api_version}/{urllib.parse.quote(page_id)}/feed", data=payload, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=25) as response:
                obj = json.loads(response.read().decode("utf-8"))
        except (urllib.error.HTTPError, urllib.error.URLError) as ex:
            # Leave unknown until checked manually; prevents potential double post.
            raise RuntimeError("Facebook call failed or outcome unknown; check Page before manual retry. " + str(ex)) from ex
        if not obj.get("id"):
            raise RuntimeError("Facebook response missing post ID; check Page before retry")
        conn.execute("UPDATE sent SET state='published', remote_id=? WHERE id=?", (obj["id"], key))
        conn.commit()
        return obj["id"]
    finally:
        conn.close()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    b = sub.add_parser("build")
    b.add_argument("--output", type=Path, default=HERE / "dist")
    q = sub.add_parser("queue")
    q.add_argument("--output", type=Path, default=HERE / "outbox")
    f = sub.add_parser("publish-facebook")
    f.add_argument("--slug", required=True)
    f.add_argument("--confirm", action="store_true")
    f.add_argument("--state", type=Path, default=HERE / ".local" / "state.sqlite3")
    args = ap.parse_args(argv)
    posts = load_posts()
    instant = now_la()
    base = os.environ.get("SPRESSO_BLOG_BASE_URL", DEFAULT_BASE)
    if args.command == "validate":
        print(f"OK: validated {len(posts)} posts; {sum(due(p, instant) for p in posts)} approved and due")
    elif args.command == "build":
        due_posts = build_site(posts, args.output, base, instant)
        print(f"Built site in {args.output}, with {len(due_posts)} approved and due posts")
    elif args.command == "queue":
        records = queue_posts(posts, args.output, base, instant)
        print(f"Wrote {len(records)} platform-specific drafts to {args.output}")
    elif args.command == "publish-facebook":
        selected = next((p for p in posts if p["slug"] == args.slug), None)
        if selected is None:
            raise ValueError("slug not found")
        print("Facebook post ID:", publish_facebook(selected, args.state, confirm=args.confirm, instant=instant))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, json.JSONDecodeError) as err:
        print(f"ERROR: {err}", file=sys.stderr)
        sys.exit(1)
