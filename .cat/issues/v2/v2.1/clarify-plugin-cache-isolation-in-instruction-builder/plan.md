# Plan: clarify-plugin-cache-isolation-in-instruction-builder

## Goal

Update `cat:instruction-builder-agent`'s skill documentation to make two rules explicit:
1. **Never update the local plugin cache** (`CLAUDE_PLUGIN_ROOT`) — it is shared and modifying it
   affects live production sessions
2. **How plugin changes reach SPRT test agents** — via `claude-runner --plugin-source <worktree>/plugin`,
   which copies worktree plugin files to an isolated config dir and sets both `CLAUDE_CONFIG_DIR` and
   `CLAUDE_PLUGIN_ROOT` to that isolated dir

## Type

docs

## Background

During SPRT iteration in `cat:instruction-builder-agent`, an agent incorrectly attempted to sync
changed plugin files to the main plugin cache (`CLAUDE_PLUGIN_ROOT`) before running SPRT test trials.
This was intended to make worktree changes visible to test agents, but the correct mechanism is:

- `claude-runner --plugin-source <worktree>/plugin` copies worktree files to an isolated config dir
- `ClaudeRunner.java` sets `CLAUDE_CONFIG_DIR` AND `CLAUDE_PLUGIN_ROOT` in the child process to point
  at the isolated config dir (after issue `fix-claude-runner-plugin-root-isolation` is implemented)
- Test agents read plugin files from the isolated dir, not the main cache

The main plugin cache must NEVER be modified during testing because:
- It is shared across all active Claude sessions
- Modifying it mid-session affects live production sessions running concurrently
- Concurrent instruction-builder runs would overwrite each other's files

## Files to Modify

- `plugin/skills/instruction-builder-agent/first-use.md`
  - In the SPRT testing section (or wherever the agent is instructed to run SPRT trials), add an
    explicit note: **Do NOT copy plugin files to `CLAUDE_PLUGIN_ROOT`** — the `claude-runner
    --plugin-source` argument handles plugin isolation automatically
  - Explain that the `--plugin-source <worktree>/plugin` flag is the correct and only mechanism for
    making worktree changes visible to SPRT test agents

## Post-Conditions

- [ ] `first-use.md` explicitly prohibits copying plugin files to `CLAUDE_PLUGIN_ROOT` during SPRT
      testing, with a brief explanation of why (shared cache, affects production sessions)
- [ ] `first-use.md` explains that `claude-runner --plugin-source <worktree>/plugin` is the correct
      mechanism for isolating plugin changes during tests — no manual cache sync needed
- [ ] The documentation is located in the same section where `empirical-test-runner` or SPRT run
      commands are described, so agents encounter it at the point of use

## Dependencies

- `fix-claude-runner-plugin-root-isolation` — must be implemented first so the documented behavior
  (both `CLAUDE_CONFIG_DIR` and `CLAUDE_PLUGIN_ROOT` are set in isolated sessions) is accurate
