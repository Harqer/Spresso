# Spresso Editorial — credit-free Python fallback

This folder is a **working Python static-blog publisher and social-draft generator**, plus an optional local MCP server. The Python publisher has **no LLM requirement, subscription or AI generation credits**. It runs locally or through GitHub Actions.

## Install and test

```bash
python3 marketing/pipeline.py validate
python3 -m unittest discover -s marketing/tests -v
python3 marketing/pipeline.py build
python3 marketing/pipeline.py queue
python3 -m http.server 8000 --directory marketing/dist
# Preview at http://localhost:8000
```

## Scheduling and publication

- The `.github/workflows/spresso-editorial.yml` file runs **Mondays, Wednesdays, and Fridays at 10:17 AM America/Los_Angeles**, after the feature branch is merged into `main`.
- It generates HTML, RSS, sitemap and social drafts, and deploys **approved and due** posts to GitHub Pages (if the repository's Pages source is configured for GitHub Actions).
- Only posts in `marketing/posts/` with `status: "approved"`, a named `approved_by` reviewer and a past `publish_at` timestamp become public. All starter content is `draft` and **will not publish** until explicitly reviewed and changed.
- Example approval: update `status` and `approved_by` (your actual editorial reviewer), then set a timestamp. Do not invent testimonials, developer quotes, shipped features, product screenshots, usage metrics, price claims or delivery guarantees.
- Site URL defaults to `https://harqer.github.io/Spresso/` **only if GitHub Pages is configured and deployed**. Override `SPRESSO_BLOG_BASE_URL` as a GitHub Actions repository variable to match the actual deployed base URL. This is **not automatically mounted to** `spresso.getqubits.com/blog` (requires website or proxy integration).
- Use GitHub Settings → Pages → Build and deployment → GitHub Actions. A separate production website integration is optional.

## Safe publication fallback

The scheduled job does **not** use any external social API. It writes platform-specific draft posts (including unapproved posts, explicitly marked pending) to `marketing/outbox/social_drafts.md` / `.json`, and uploads them as a short-lived Actions artifact. Instagram, TikTok and YouTube need accompanying media before posting. X API creation is billed and is **disabled** under the zero-spend policy. Reddit requires authenticated API access and compliance with subreddit rules; use genuine Spresso community posts rather than mass posting. TikTok unaudited Direct Post clients cannot publish public videos. Meta/Instagram require approved account types/scopes.

Optional **Facebook Page** publishing is supported via Graph API with an authenticated *Page* access token that has required permissions; this requires an explicit command and local durable SQLite state:

```bash
export SPRESSO_SOCIAL_POSTING_ENABLED=yes
export SPRESSO_FB_PAGE_ID='YOUR_PAGE_ID'
export SPRESSO_FB_PAGE_TOKEN='YOUR_PAGE_ACCESS_TOKEN'
# Check Facebook account/page and approved post before running:
python3 marketing/pipeline.py publish-facebook --slug welcome-to-spresso --confirm
```

This code deliberately does **not** auto-send social posts from ephemeral GitHub runners. SQLite state is stored under `marketing/.local/`; keep it persistent for manual operations. Unknown network outcomes are marked as attempted; check the Page before considering any retry. **Never commit tokens, user data or private secrets into your public repo.** If a platform denies publication, it stays in the draft queue. The script never bypasses platform-specific approval, rate limits or paid API access.

## Optional self-owned local MCP plugin

```bash
python3 -m pip install 'mcp>=1.20,<2'
python3 marketing/mcp_server.py
```

Configure your local MCP-compatible client to run `python3 /absolute/path/to/marketing/mcp_server.py` as a **stdio** process. It exposes `review_calendar`, `build_blog_preview`, `prepare_social_queue`. It does not post on your behalf or require any AI subscription. ChatGPT itself cannot reach this local stdio MCP server unless you connect it through a supported mechanism; installing these files alone does not create a ChatGPT connector.

## Editorial routine

1. **Product truth:** consult `FEATURE_REGISTER.json`, current app/build screenshots, and developer notes; confirm which features actually work, which are beta, and which are planned.
2. **People, not invented identities:** founder updates must come from real founder notes, facts and dates. Never imply a team, investor, testimonial or partnership that does not exist.
3. **Review first:** write authentic posts in `marketing/posts/` using the existing JSON schema; leave `status: draft` until approved.
4. **Repurpose thoughtfully:** each blog article becomes a different platform-ready caption, but media is required where appropriate. Post to relevant communities deliberately, not as broad spam.
5. **Measure:** track first/2-second hold, watch time, saves, article clicks and comments. Prioritize content readers request and update posts when product behavior changes.

### Publishing-channel truth table

| Platform | Python fallback | Prerequisites |
|---|---|---|
| Website / GitHub Pages | Automatic when approved/due | Merge branch, configure Pages |
| Facebook Page | Optional real POST via CLI | Page ID, Page token, permissions, explicit opt-in |
| Instagram | Draft/export | Instagram professional account, Meta publish permission and media |
| TikTok | Draft/export | Authorized audited Content Posting API client for public direct posting, eligible media |
| YouTube | Draft/export | Video file and authorized YouTube Data API upload |
| X | Draft/export only | Posting API currently charged per request; no spending authorized |
| Reddit | Draft/export only | OAuth, community rules, and nonspam content |

Use `python3 marketing/pipeline.py queue` to create drafts even if every third-party account is disconnected.
