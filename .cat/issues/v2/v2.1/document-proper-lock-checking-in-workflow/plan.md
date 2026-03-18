# Bugfix: document-proper-lock-checking-in-workflow

## Problem

Agents are primed to check lock status manually via bash commands (e.g., `ls /workspace/.cat/locks/`) instead
of using the designated `issue-lock` CLI tool. When asked "Is issue X locked?", agents probe the filesystem with
`ls`, `find`, and similar commands, finding no locks directory and drawing incorrect conclusions before eventually
discovering the correct CLI.

**Evidence:** M467 — Agent responded to "Is the 2.1-add-regex-to-session-analyzer branch locked?" by first running
`ls -la /workspace/.cat/locks/` (directory does not exist), then `find` commands, before finally using
`issue-lock check`.

## Root Cause

No document in the agent's session context explains where locks are managed or how to query them. Agents default to
filesystem exploration, which is incorrect because locks are managed by the Java `issue-lock` CLI tool, not stored in
a discoverable directory layout.

## Satisfies

None - infrastructure/reliability improvement

## Post-conditions

- [ ] At least one document injected into agent session context explains that lock status must be checked via
  `issue-lock check <issue-id>`, not by inspecting filesystem paths
- [ ] The document clarifies that there is no user-accessible lock directory to browse
- [ ] Agents responding to "Is X locked?" use `issue-lock check` as the first and only step
- [ ] No bash filesystem probing (`ls`, `find`) precedes the `issue-lock check` call

## Implementation

Add a note to `InjectSessionInstructions.java` (or an appropriate concept file loaded early in sessions) documenting
the lock-checking procedure:

- Lock status is managed by the `issue-lock` CLI tool
- To check if an issue is locked: `issue-lock check <issue-id>`
- There is no filesystem directory to browse for locks
- Example correct usage

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` — add lock-checking
  procedure note, OR
- A concept file loaded at session start — document the lock-checking procedure
