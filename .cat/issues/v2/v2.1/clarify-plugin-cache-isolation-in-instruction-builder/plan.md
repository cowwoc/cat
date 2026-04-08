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

## Jobs

### Job 1

- Edit `plugin/skills/instruction-builder-agent/first-use.md`:
  Replace the "Plugin cache refresh" paragraph (lines 915–917) with an expanded "Plugin cache
  isolation" section that:
  1. Explicitly names `CLAUDE_PLUGIN_ROOT` as the shared main cache — off-limits for writing during SPRT
  2. States the prohibition: **Never write plugin files to `CLAUDE_PLUGIN_ROOT`** during SPRT
  3. Explains why: it is shared across all active Claude sessions; writing to it affects every
     concurrent live session, not just the current test run
  4. Describes how `--plugin-source` achieves isolation: `ClaudeRunner` copies the specified directory
     into a per-test isolated config dir, then sets both `CLAUDE_CONFIG_DIR` and `CLAUDE_PLUGIN_ROOT`
     in the child process environment to point at that isolated copy
  5. Clarifies that no manual cache sync or `/reload-plugins` is needed

  **Replacement text (exact):**
  Replace:
  ```
  **Plugin cache refresh:** `ClaudeRunner` automatically syncs the current plugin source from the worktree
  into the isolated plugin cache before each test-run process starts. Skills loaded via the Skill tool within
  a test-run process always use the current worktree version.
  ```
  With:
  ```
  **Plugin cache isolation:** `CLAUDE_PLUGIN_ROOT` is the main plugin cache shared across all active Claude
  sessions. **Never write plugin files to `CLAUDE_PLUGIN_ROOT`** during SPRT — doing so modifies the live
  shared cache and affects every concurrent Claude session, not just the test run.

  The correct mechanism is `claude-runner --plugin-source <worktree>/plugin`. Before launching the nested
  Claude process, `ClaudeRunner` copies the directory specified by `--plugin-source` into an isolated per-test
  config directory, then sets both `CLAUDE_CONFIG_DIR` and `CLAUDE_PLUGIN_ROOT` in the child process environment
  to point at that isolated copy. The nested process reads plugin files exclusively from this isolated copy —
  never from the main cache.

  Skills invoked within a test-run process automatically use the current worktree version. No manual cache
  sync or `/reload-plugins` is needed.
  ```

- Update `.cat/issues/v2/v2.1/clarify-plugin-cache-isolation-in-instruction-builder/index.json` in the same commit:
  set `status` to `closed` and `progress` to `100`
- Commit message: `docs: clarify plugin cache isolation in instruction-builder-agent`
