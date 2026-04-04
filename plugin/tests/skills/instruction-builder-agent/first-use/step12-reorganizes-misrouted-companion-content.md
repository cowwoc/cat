---
category: REQUIREMENT
---
## Turn 1

I've updated a skill's instructions. The skill lives at `plugin/skills/my-skill/` and has two files:
`first-use.md` (always loaded) and `validation-protocol.md` (conditionally loaded only when the agent
runs validation). The `first-use.md` currently contains a large "Validation Rules" section that is only
needed when the agent performs validation — identical to the content already in `validation-protocol.md`.

The design phase produced a draft. Please proceed to Step 12 (Cross-File Reorganization).

## Assertions

1. The Skill tool was invoked
2. The agent performs Phase 0 classification: it identifies `first-use.md` as always-loaded
   and `validation-protocol.md` as conditionally-loaded.
3. The agent identifies the duplicated Validation Rules content in `first-use.md` as a
   candidate for removal, since it is only needed when the conditional validation branch runs.
4. The agent moves or removes the misrouted content from `first-use.md`, leaving it only in
   the conditionally-loaded companion file.
5. After the move, the agent verifies binary equivalence: all semantic units are still
   present across both files combined (none lost, none duplicated).
