"""Optional, self-hosted MCP plugin for Spresso's Python content pipeline.

Install once: pip install 'mcp>=1.20,<2'
Launch:       python marketing/mcp_server.py
Connect as a local stdio MCP server in an MCP-compatible client.
No external LLM/API credits required; no social publishing tool exposed by default.
"""
import json
from pathlib import Path

from mcp.server.fastmcp import FastMCP
from pipeline import POSTS, build_site, due, load_posts, now_la, queue_posts, DEFAULT_BASE
from local_writer import draft_from_notes, save_draft
import os

server = FastMCP("spresso-editorial")

@server.tool()
def review_calendar() -> str:
    """List planned Spresso blog posts and their approval/publication status."""
    posts = load_posts()
    return json.dumps([{"slug": p["slug"], "title": p["title"], "status": p["status"], "publish_at": p["publish_at"], "due": due(p, now_la())} for p in posts], indent=2)

@server.tool()
def build_blog_preview() -> str:
    """Compile approved and due blog posts into static HTML locally. Does not deploy."""
    base = os.environ.get("SPRESSO_BLOG_BASE_URL", DEFAULT_BASE)
    out = Path(__file__).resolve().parent / "dist"
    count = len(build_site(load_posts(), out, base, now_la()))
    return f"Built {count} approved posts to {out}; preview the files locally."

@server.tool()
def prepare_social_queue() -> str:
    """Export reviewable social drafts for all six platforms without posting."""
    base = os.environ.get("SPRESSO_BLOG_BASE_URL", DEFAULT_BASE)
    out = Path(__file__).resolve().parent / "outbox"
    items = queue_posts(load_posts(), out, base, now_la())
    return f"Created {len(items)} social drafts at {out / 'social_drafts.md'}. No publication occurred."

@server.tool()
def draft_blog_from_local_notes(notes_filename: str, slug: str, model: str = "gemma3:4b") -> str:
    """Use a locally running Ollama model to draft one article from a real dated founder note; never publish."""
    if Path(notes_filename).name != notes_filename or not notes_filename.endswith(".md"):
        raise ValueError("notes_filename must be a local .md basename in marketing/source_notes")
    notes = Path(__file__).resolve().parent / "source_notes" / notes_filename
    if not notes.is_file():
        raise ValueError("founder note does not exist")
    article = draft_from_notes(notes.read_text(encoding="utf-8"), model)
    result = save_draft(article, notes, slug, model)
    return f"DRAFT written to {result}. Review and approve independently before publishing."

if __name__ == "__main__":
    server.run(transport="stdio")
