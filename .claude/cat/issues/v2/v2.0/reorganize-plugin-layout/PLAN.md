# Plan: reorganize-plugin-layout

## Current State
Plugin has nested directory structure with CAT resources under `plugin/.claude/cat/` containing references, templates, and workflows subdirectories. The stakeholders language files are deeply nested at `plugin/.claude/cat/references/stakeholders/lang/`.

## Target State
Flatten the plugin structure by moving CAT resources directly to `plugin/`:
- `plugin/.claude/cat/references/` → `plugin/concepts/` (rename for clarity)
- `plugin/.claude/cat/templates/` → `plugin/templates/`
- `plugin/.claude/cat/workflows/` → `plugin/workflows/`
- `plugin/.claude/cat/references/stakeholders/lang/` → `plugin/lang/`

Remove the now-empty `plugin/.claude/cat/` directory.

## Satisfies
None - infrastructure/organization task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Path references in skills and hooks need updating
- **Mitigation:** Update all file path references, run tests to verify

## Files to Modify
- Move+rename: `plugin/.claude/cat/references/` → `plugin/concepts/`
- Move: `plugin/.claude/cat/templates/` → `plugin/templates/`
- Move: `plugin/.claude/cat/workflows/` → `plugin/workflows/`
- Move: `plugin/.claude/cat/references/stakeholders/lang/` → `plugin/lang/`
- Update: Any files referencing old paths (including `references/` → `concepts/`)

## Acceptance Criteria
- [ ] Behavior unchanged - plugin functions identically
- [ ] All tests still pass
- [ ] Code quality improved - cleaner directory structure
- [ ] Old `.claude/cat/` directory removed from plugin

## Execution Steps
1. **Step 1:** Move lang directory first (before moving references)
   - Files: `plugin/.claude/cat/references/stakeholders/lang/` → `plugin/lang/`
   - Verify: `ls plugin/lang/`

2. **Step 2:** Move and rename references to concepts
   - Files: `plugin/.claude/cat/references/` → `plugin/concepts/`
   - Verify: `ls plugin/concepts/`

3. **Step 3:** Move templates directory
   - Files: `plugin/.claude/cat/templates/` → `plugin/templates/`
   - Verify: `ls plugin/templates/`

4. **Step 4:** Move workflows directory
   - Files: `plugin/.claude/cat/workflows/` → `plugin/workflows/`
   - Verify: `ls plugin/workflows/`

5. **Step 5:** Update path references in skills/hooks
   - Files: Search for `.claude/cat/` and `references/` occurrences
   - Update: `.claude/cat/references/` → `concepts/`
   - Update: `.claude/cat/templates/` → `templates/`
   - Update: `.claude/cat/workflows/` → `workflows/`
   - Verify: `grep -r ".claude/cat" plugin/` returns nothing
   - Verify: `grep -r "references/" plugin/` returns nothing (except stakeholders/)

6. **Step 6:** Remove empty .claude directory
   - Files: `plugin/.claude/`
   - Verify: `ls plugin/.claude/` should fail

7. **Step 7:** Run tests
   - Verify: `python3 /workspace/run_tests.py`
