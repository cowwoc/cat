# Plan: add-skill-context-router

## Current State

Every skill invocation loads the full SKILL.md content into the conversation context via the Skill tool's
`<command-name>` expansion. When a skill is invoked multiple times in one session (common for `/cat:add`,
`/cat:work`, `/cat:status`), the full body is re-loaded each time.

**Evidence:** 44 skills averaging 384 lines each (~16,900 total lines). The `/cat:add` skill alone is 1,358
lines. Two invocations in one session consume ~76KB of duplicated context. With a 200K context window, this
represents ~38% consumed just for skill instructions.

This problem affects ALL agents and subagents. Subagents that invoke skills (via the Skill tool) also pay
the full context cost on every invocation.

## Target State

Each skill gets a thin wrapper that:
1. On **first invocation**: delegates to the original skill (full body loads into context as normal)
2. On **subsequent invocations**: delegates to a shared "context-router" skill that instructs the agent
   to follow the original skill definition already present in the conversation context, using the new
   arguments

This creates:
- **One wrapper per skill** (44 wrappers, each ~15-20 lines)
- **One shared router skill** (~20-30 lines) used by all wrappers on repeat invocations

## Satisfies

None - infrastructure/optimization task

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill behavior must remain identical. The wrapper is transparent.
- **Mitigation:** All wrappers delegate to the real skill on first call, so first-time behavior is
  identical. The router on subsequent calls references the already-loaded definition, which is already
  in context. Run full test suite to verify no regressions.

## Design

### Wrapper Skill Structure

Each wrapper replaces the original skill's registration in `plugin.json`. The original skill is renamed
(e.g., `add/SKILL.md` -> `add/FULL.md` or moved to a subdirectory).

Wrapper SKILL.md (example for `/cat:add`):
```markdown
---
description: "[same as original]"
argument-hint: "[same as original]"
allowed-tools: [Skill]
user-invocable: true
---

Check if the skill `cat:_add` has already been loaded in this conversation context.

**If NOT loaded yet** (no `<command-name>cat:_add</command-name>` tag visible in prior context):
- Invoke: `/cat:_add $ARGUMENTS`

**If already loaded** (the tag IS visible in prior context):
- Invoke: `/cat:_context-router _add $ARGUMENTS`
```

### Router Skill Structure

```markdown
---
description: "Route to already-loaded skill definition in context"
allowed-tools: []
user-invocable: false
---

The skill `cat:_$1` was previously loaded into this conversation. Its full definition is already
present in the context above.

Execute that skill's process using these arguments: $2..N

Follow the original skill's steps exactly as defined in the earlier context. Do NOT request the
skill be loaded again.
```

### Naming Convention

| Component | Name Pattern | Example |
|-----------|-------------|--------|
| Wrapper (user-facing) | `cat:{name}` | `cat:add` |
| Full skill (internal) | `cat:_{name}` | `cat:_add` |
| Router (shared) | `cat:_context-router` | `cat:_context-router` |

The underscore prefix distinguishes internal skills from user-facing wrappers.

## Files to Modify

### New Files
- `plugin/skills/context-router/SKILL.md` - Shared router skill (~20-30 lines)
- 44x wrapper SKILL.md files (one per existing skill, replacing current SKILL.md)

### Modified Files
- `plugin/.claude-plugin/plugin.json` - Update command paths (wrappers replace originals)
- Each existing `plugin/skills/{name}/SKILL.md` - Rename to `plugin/skills/{name}/FULL.md`

## Acceptance Criteria

- [ ] Behavior unchanged - all skill invocations produce identical results
- [ ] Tests passing - `python3 /workspace/run_tests.py` exits 0
- [ ] Code quality improved - repeat skill invocations use significantly less context
- [ ] Each skill has a thin wrapper (~15-20 lines) that checks for prior loading
- [ ] One shared context-router skill exists for all subsequent invocations
- [ ] Original skill content preserved (renamed, not deleted)

## Execution Steps

1. **Step 1:** Create the shared context-router skill
   - Files: `plugin/skills/context-router/SKILL.md`
   - Content: Instructions to follow an already-loaded skill definition from context

2. **Step 2:** For each of the 44 skills, rename `SKILL.md` to `FULL.md`
   - Preserve all content, frontmatter, execution_context references
   - Any supporting files (phase files, etc.) remain unchanged

3. **Step 3:** For each of the 44 skills, create a new wrapper `SKILL.md`
   - Copy `description` and `argument-hint` from the original frontmatter
   - Set `allowed-tools: [Skill]`
   - Add logic to check if internal skill was already loaded
   - If not loaded: invoke `cat:_{name} $ARGUMENTS`
   - If loaded: invoke `cat:_context-router _{name} $ARGUMENTS`

4. **Step 4:** Register all internal skills in `plugin.json`
   - Add internal skill paths (the `_`-prefixed ones) so they can be invoked
   - Keep existing user-invocable command paths (wrappers)

5. **Step 5:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria

- [ ] All 44 skills have wrapper + full body structure
- [ ] First invocation of any skill loads the full definition (identical to current behavior)
- [ ] Second+ invocation of the same skill routes through context-router (no re-expansion of full body)
- [ ] All tests pass
- [ ] No user-visible behavior changes
