# Plan: improve-skill-discoverability

## Problem
Skills are being bypassed because they read like passive documentation rather than actions to invoke.

**Evidence from session 2026-01-21:**
- Agent ran `git rebase v2.0` directly instead of using `/cat:git-rebase`
- Hook blocked the command, but the skill wasn't considered
- Root cause: Skill description was passive ("Safely rebase with...") not directive ("ALWAYS use this instead of...")

**Pattern observed:**
- Skills with clear "When to Use" sections get invoked (e.g., `git-merge-linear`)
- Skills that read like reference docs get bypassed (e.g., `git-rebase`)

## Goal
Audit all skills and update them to follow a consistent, action-oriented pattern that increases compliance.

## Satisfies
- Reduces bypassed skills
- Improves agent compliance with safe practices
- Consistent skill documentation

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Over-documenting simple skills
- **Mitigation:** Only add structure where it improves discoverability

## Files to Modify

### Already Updated (in this session)
- plugin/skills/git-rebase/SKILL.md - DONE (needs commit)

### To Review and Update
- plugin/skills/git-amend/SKILL.md
- plugin/skills/git-commit/SKILL.md
- plugin/skills/git-squash/SKILL.md
- plugin/skills/validate-git-safety/SKILL.md
- plugin/skills/safe-rm/SKILL.md
- plugin/skills/safe-remove-code/SKILL.md
- Any other skills in plugin/skills/

## Pattern to Apply

Each skill should have:

1. **Frontmatter with `cat:` prefix**:
   ```yaml
   ---
   name: cat:skill-name
   description: ACTION-ORIENTED description starting with verb or "ALWAYS use..."
   allowed-tools: Bash, Read, etc.
   ---
   ```

2. **MANDATORY header** (if skill replaces a dangerous operation):
   ```markdown
   **MANDATORY**: Use this skill instead of running `<command>` directly.
   ```

3. **"When to Use" section**:
   - Clear scenarios when skill should be invoked
   - Specific triggers (e.g., "when fast-forward fails")

4. **"When NOT to Use" section** (if applicable):
   - Scenarios where skill is inappropriate

5. **Step-by-step Workflow**:
   - Not just reference snippets
   - Executable steps with verification

6. **Success Criteria checklist**

## Acceptance Criteria
- [ ] All skills have `cat:` prefix in name
- [ ] All skills have action-oriented descriptions
- [ ] Skills that replace dangerous commands have MANDATORY header
- [ ] All skills have "When to Use" section
- [ ] git-rebase changes committed

## Execution Steps

1. **Commit git-rebase update**
   - Already edited in session
   - Verify: git diff shows changes
   - Commit with task ID

2. **Audit all skills**
   - List all skills in plugin/skills/
   - Check each for: name prefix, description style, When to Use section
   - Document which need updates

3. **Update non-compliant skills**
   - Apply pattern to each skill
   - Prioritize: git-* skills (most likely to be bypassed)

4. **Verify skill registration**
   - Check that updated skills appear in Skill tool's available list
   - Verify descriptions are clear
