# Real founder notes for local drafting

Create a file named by date and topic, e.g. `2026-11-10-order-tracking.md`, with **actual** founder updates and evidence:

- What was implemented or tested, with date and current production status
- Which user problem motivated it, and what changed
- What remains incomplete or blocked
- Links or screenshot locations if available
- A real founder quote or anecdote, **only** if you actually wrote it

Then run `python3 marketing/local_writer.py --notes marketing/source_notes/2026-11-10-order-tracking.md --slug order-tracking-build-update`. An optional locally running Ollama/Gemma model writes a new JSON post with `status:draft`. Review all claims and set `approved_by` and `status:approved` yourself. The scheduled workflow does not call the model (GitHub runners cannot access your laptop's localhost).
