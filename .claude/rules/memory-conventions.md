# MEMORY.md vs Project Conventions

**MEMORY.md is for short-term fixes only** — technical discoveries, workarounds, and session-specific knowledge that
hasn't yet been formalized. When a rule should persist as a project or plugin convention, add it to the appropriate
convention file (see CLAUDE.md § "Convention File Locations"), not MEMORY.md.

| Content Type | Location |
|---|---|
| Short-term workarounds, discoveries | `MEMORY.md` |
| Project development conventions | `.claude/rules/` |
| End-user behavioral rules | `plugin/rules/`, `plugin/` files |
