# Plan: fix-session-analyzer-session-id

## Problem
The optimize-execution skill and session-analyzer tool use `$CLAUDE_SESSION_ID` (the CAT hook-provided
session ID, e.g., `cbfa6fa7-...`) to locate subagent JSONL transcripts. However, Claude Code stores
subagent transcripts under its own session ID (the Claude Code session ID, e.g., `d5a0edb3-...`), which
is the filename of the parent session JSONL and the name of the subagents directory.

Result: session-analyzer finds 0 subagent files even when 8+ subagents ran, making all delegation
analysis invisible and the "Delegation Analysis" section of optimize-execution empty.

Empirically confirmed: the `d5a0edb3-...` session had 8 subagent JSONLs (512KB impl, 324KB verify,
etc.) while `cbfa6fa7-...` had no subagents dir at all.

## Satisfies
None

## Reproduction Code
```bash
# This returns 0 subagents even when subagents ran:
/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/session-analyzer cbfa6fa7-...

# But the subagents are here, under the Claude Code session ID:
ls /home/node/.config/claude/projects/-workspace/d5a0edb3-.../subagents/
# agent-a97ecc32f14034aec.jsonl  (512KB - implementation)
# agent-a64b961d06957a9fb.jsonl  (324KB - verify)
# ...
```

## Expected vs Actual
- **Expected:** session-analyzer finds all subagent JSONL files and includes them in delegation analysis
- **Actual:** subagents section is always empty `{}` when using CLAUDE_SESSION_ID

## Root Cause
Claude Code uses two session ID concepts:
1. The CAT hook session ID (`CLAUDE_SESSION_ID` env var) - used for CAT issue locks, worktrees
2. The Claude Code session ID - the actual JSONL filename, also used as the parent directory for subagents

The session-analyzer only looks up `{CLAUDE_SESSION_ID}/subagents/agent-{id}.jsonl` but subagents
live at `{CLAUDE_CODE_SESSION_ID}/subagents/agent-{id}.jsonl`.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - purely additive, existing analysis still works
- **Mitigation:** Fall back to CLAUDE_SESSION_ID if Claude Code session ID not found

## Files to Modify
- `client/src/main/java/.../SessionAnalyzer.java` - resolve Claude Code session ID from JSONL directory listing
- `plugin/skills/optimize-execution/first-use.md` - document correct session ID to pass

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Investigate how Claude Code session ID differs from CLAUDE_SESSION_ID and how to resolve it
  - Check: Does the main session JSONL filename equal the Claude Code session ID?
  - Check: Is the Claude Code session ID available from any env var (e.g., CLAUDE_CODE_SESSION_ID)?
  - Files: `client/src/main/java/.../SessionAnalyzer.java`
- Fix session-analyzer to resolve the correct session ID for subagent discovery
  - Strategy: Try CLAUDE_SESSION_ID first, then scan the JSONL directory for a matching session file
    whose basename (without .jsonl) has a `subagents/` subdirectory
  - Files: `client/src/main/java/.../SessionAnalyzer.java`
- Update optimize-execution skill to pass the correct session ID or document the resolution
  - Files: `plugin/skills/optimize-execution/first-use.md`

## Post-conditions
- [ ] Running session-analyzer with CLAUDE_SESSION_ID finds all subagent JSONLs from the session
- [ ] The Delegation Analysis section of optimize-execution output is populated when subagents ran
- [ ] Regression: existing analysis (tool frequency, cache candidates) is unaffected
- [ ] All tests pass
- [ ] E2E: Run optimize-execution after a session with delegations and verify subagents appear in output
