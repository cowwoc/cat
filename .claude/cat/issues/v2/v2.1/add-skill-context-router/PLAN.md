# Plan: add-skill-context-router

## Current State

Every skill invocation loads the full SKILL.md content into the conversation context via the Skill tool's
`<command-name>` expansion. When a skill is invoked multiple times in one session (common for `/cat:add`,
`/cat:work`, `/cat:status`), the full body is re-loaded each time.

**Evidence:** 44 skills averaging 384 lines each (~16,900 total lines). The `/cat:add` skill alone is 1,358
lines. Two invocations in one session consume ~76KB of duplicated context. With a 200K context window, this
represents ~38% consumed just for skill instructions.

## Target State

Each skill uses `!`command`` preprocessing in SKILL.md to conditionally load content:
1. On **first invocation**: preprocessor `cat`s `content.md` (full skill body) and creates a session marker file
2. On **subsequent invocations**: preprocessor detects marker and `cat`s `router.md` (tiny redirect to
   already-loaded content in context)

**Tested and verified:** `!`command`` with `${CLAUDE_SESSION_ID}` works in plugin skills. Marker file
`/tmp/cat-{name}-${CLAUDE_SESSION_ID}` enables session-scoped conditional loading with zero hooks and zero extra
tool calls.

## Satisfies

None - infrastructure/optimization task

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None. Skill behavior is identical - same frontmatter, same content, just loaded
  conditionally via preprocessing instead of statically.
- **Mitigation:** First invocation is byte-identical to current behavior (same content.md body). Router on
  subsequent calls is a simple redirect. Run full test suite + A/B testing to verify.

## Design

### Skill Directory Structure (per skill)

```
skills/{name}/
├── SKILL.md       # Thin wrapper: frontmatter + !`command` preprocessing line
├── content.md     # Full skill body (original SKILL.md content without frontmatter)
└── router.md      # Tiny "follow already-loaded instructions" redirect
```

### Wrapper SKILL.md Pattern

```markdown
---
description: [original description]
argument-hint: [original if any]
allowed-tools: [original]
model: [original if any]
[other original frontmatter]
---

!`M="/tmp/cat-{name}-${CLAUDE_SESSION_ID}"; if [ -f "$M" ]; then cat "${CLAUDE_PLUGIN_ROOT}/skills/{name}/router.md"; else cat "${CLAUDE_PLUGIN_ROOT}/skills/{name}/content.md"; touch "$M"; fi`
```

### Shared Router Content

All router.md files contain the same text:

```
The skill instructions were already loaded earlier in this conversation. Find the previously loaded skill
definition above and follow those instructions for the current invocation.
```

### Handler Integration

Skill handlers (UserPromptSubmit) remain registered on their original names (e.g., `help`, `add`, `work`).
They fire on every invocation and inject fresh `additionalContext` data. The `!`command`` preprocessing controls
which instructions are loaded — the handler provides the data those instructions reference.

No changes needed to handlers, PostToolUse handler, or handler registry.

### Plugin.json

No changes needed. Each skill remains a single directory with SKILL.md. No nested skills, no extra entries.

## Files to Create

- 44x `content.md` files (one per skill, body extracted from current SKILL.md)
- 44x `router.md` files (identical content, one per skill directory)

## Files to Modify

- 44x `SKILL.md` files (replace body with `!`command`` preprocessing, keep frontmatter)

## Files NOT Modified

- `plugin/.claude-plugin/plugin.json` - No changes
- `plugin/hooks/skill_handlers/*.py` - No handler changes
- `plugin/hooks/posttool_handlers/skill_preprocessor_output.py` - No changes

## Acceptance Criteria

- [ ] Behavior unchanged - all skill invocations produce identical results on first call
- [ ] Tests passing - `python3 /workspace/run_tests.py` exits 0
- [ ] Repeat invocations load router.md instead of full content
- [ ] A/B test confirms token reduction on second invocation
- [ ] Each skill directory has SKILL.md + content.md + router.md
- [ ] No handler or hook modifications

## Execution Steps

1. **Create content.md for each skill**: Extract the body (everything after the closing `---` frontmatter
   delimiter) from each current SKILL.md and write to content.md in the same directory.

2. **Create router.md for each skill**: Write the shared router text to router.md in each skill directory.

3. **Rewrite each SKILL.md**: Keep the original YAML frontmatter block. Replace the body with a single
   `!`command`` preprocessing line that conditionally loads content.md or router.md based on a session marker
   file at `/tmp/cat-{name}-${CLAUDE_SESSION_ID}`.

4. **Run tests**: `python3 /workspace/run_tests.py` to verify no regressions.

## Success Criteria

- [ ] All 44 skills have SKILL.md + content.md + router.md structure
- [ ] First invocation loads full content (identical to current behavior)
- [ ] Second+ invocation loads tiny router (~2 lines vs hundreds/thousands)
- [ ] All tests pass
- [ ] A/B test shows significant token reduction on repeat invocations
- [ ] No user-visible behavior changes
