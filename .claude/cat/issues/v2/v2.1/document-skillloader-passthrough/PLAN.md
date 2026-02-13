# Plan: document-skillloader-passthrough

## Problem

The `plugin/hooks/README.md` does not document the SkillLoader's variable pass-through behavior. When the SkillLoader
encounters an unknown `${...}` variable, it passes it through as a literal string (matching Claude Code's native
behavior). This behavior should be documented.

## Satisfies

None - documentation

## Execution Steps

1. **Edit `plugin/hooks/README.md`** — Add a section documenting SkillLoader's variable resolution behavior:
   - Known built-in variables (`CLAUDE_PLUGIN_ROOT`, `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`) are resolved
   - Variables defined in `bindings.json` are resolved
   - Unknown `${...}` variables are passed through as literal text, matching Claude Code's native behavior

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `plugin/hooks/README.md` | Modify | Document SkillLoader variable pass-through behavior |

## Success Criteria

- [ ] README documents variable resolution behavior (built-ins, bindings, pass-through for unknowns)
