# GitLab MCP Connection Guide (Vaultier Automation)

This document describes how to connect the Vaultier AI Agent (via Model Context Protocol) to GitLab for automated reviews, issue management, and merge operations.

## 1. Setup GitLab MCP Server

To allow Vaultier to interact with your repository, you must add the GitLab MCP server to your AI client (e.g., Claude Desktop or Android Studio Agent).

### Configuration (`claude_desktop_config.json` or equivalent)

```json
{
  "mcpServers": {
    "gitlab": {
      "command": "npx",
      "args": [
        "-y",
        "@mcp-server/gitlab"
      ],
      "env": {
        "GITLAB_PERSONAL_ACCESS_TOKEN": "YOUR_GITLAB_PAT",
        "GITLAB_API_URL": "https://gitlab.com/api/v4"
      }
    }
  }
}
```

## 2. Automated Review & Merge Workflow

The Vaultier pipeline is now configured with a "Zero-Mock" safety gate.

### Workflow Sequence:
1. **Push Feature**: You push a new feature to a branch.
2. **Open MR**: You (or the AI via MCP) open a Merge Request.
3. **Green Gate**: The GitLab Runner executes the `.gitlab-ci.yml` pipeline:
   - Starts a live Vaultier Brain.
   - Runs `LIVE_INTEGRATION_TEST=true`.
   - Sends telemetry to Sentry.
4. **Auto-Review**: The AI (via MCP) reviews the code diff and CI logs.
5. **Auto-Merge**: If tests pass and the review is positive, the `auto-merge-mr` job triggers the GitLab API to merge the branch into `main`.

## 3. Required GitLab Variables

Set these in **Settings > CI/CD > Variables**:

| Variable | Description |
| :--- | :--- |
| `INFISICAL_CLIENT_ID` | Access for secret hydration. |
| `INFISICAL_CLIENT_SECRET` | Access for secret hydration. |
| `VERCEL_API_TOKEN` | Production deployment key. |
| `CLOUDFLARE_API_TOKEN` | Edge service deployment key. |
| `GITLAB_AUTO_MERGE_TOKEN` | Personal Access Token with `api` scope for merging. |

---
**Vaultier is now fully integrated into the GitLab ecosystem.**
