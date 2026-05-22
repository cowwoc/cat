# Plan

## Goal

Align CAT's Codex plugin support with Codex CLI 0.133.0 by updating documentation and local install/update behavior
for the new plugin commands, lifecycle/event surfaces, and goal behavior.

## Parent Requirements

None

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex parity documentation accurately describes the Codex hook/lifecycle events that CAT actually registers and
      any 0.133.0 event surfaces that remain unimplemented or require investigation.
- [ ] The local CAT update workflow uses the public `codex plugin marketplace` and `codex plugin add` commands for
      Codex plugin installation.
- [ ] If the new public Codex plugin commands fail, CAT fails fast with a clear error instead of manually copying a
      plugin cache fallback.
- [ ] Codex agent registration behavior is documented and preserved unless verified Codex plugin install now registers
      bundled custom agents automatically.
- [ ] CAT does not automatically create or mutate native Codex goals from CAT issue workflows; any future native-goal
      integration is explicitly opt-in.
- [ ] Tests or verification cover the updated hook documentation expectations and the fail-fast Codex update flow.
