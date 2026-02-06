# Plan: optimize-git-squash

## Goal
Optimize git-squash skill for common patterns: parallel initial investigation, auto-detect planning commit pattern
(feature + STATE.md updates), and preserve final STATE.md automatically.

## Satisfies
None - infrastructure/performance improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Edge cases in pattern detection may miss some commit patterns
- **Mitigation:** Pattern detection is additive optimization; fallback to manual workflow

## Files to Modify
- `plugin/skills/git-squash/SKILL.md` - Add parallel commands and pattern detection

## Acceptance Criteria
- [ ] All initial git investigation commands run in parallel
- [ ] Auto-detect "feature + planning STATE.md update" commit pattern
- [ ] Automatically preserve final STATE.md when squashing planning commits
- [ ] Pre-compute potential content conflicts for non-adjacent commits

## Execution Steps
1. **Step 1:** Add parallel initial investigation
   - Files: `plugin/skills/git-squash/SKILL.md`
   - Verify: Skill documents parallel git commands at start

2. **Step 2:** Add planning commit pattern detection
   - Files: `plugin/skills/git-squash/SKILL.md`
   - Verify: Skill includes logic to detect feature + planning commits

3. **Step 3:** Add automatic STATE.md preservation
   - Files: `plugin/skills/git-squash/SKILL.md`
   - Verify: Skill preserves final STATE.md content when pattern detected
