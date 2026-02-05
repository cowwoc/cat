# Plan: refactor-delegate-skill-removal

## Goal
Remove the `--skill` capability from the delegate skill. Delegate should only handle
CAT issue delegation (`--issues`), not skill invocation delegation.

## Problem Statement
The `--skill` parameter in delegate attempts to spawn subagents that invoke skills.
But subagents cannot invoke skills (Skill tool unavailable). This creates a broken
workflow where delegated skill invocations silently fail or require manual workarounds.

The correct pattern is:
- Main agent invokes skills directly
- Delegate handles CAT issue orchestration only

## Satisfies
M429 - Technically impossible workflow correction

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:**
  - Existing code may reference --skill parameter
  - Need to provide migration guidance
- **Mitigation:**
  - Search for --skill usage before removal
  - Document alternative pattern clearly

## Scope

### Files to Modify
| File | Changes |
|------|---------|
| plugin/skills/delegate/SKILL.md | Remove --skill parameter and all related sections |

### Files to Search
| Pattern | Purpose |
|---------|---------|
| `--skill` in plugin/ | Find any references to removed capability |
| `delegate.*--skill` | Find invocation patterns to update |

## Design

### Sections to Remove from delegate.md
1. `--skill` parameter from Parameters table
2. `--skill` parsing from "Parse Arguments" section
3. Skill delegation examples
4. "For skill delegation" prompt templates
5. "For Skills" subsection under "When to Use"
6. Skill-related anti-patterns

### Sections to Update
1. Purpose - remove skill delegation mention
2. Parameters - only `--issues` and `--sequential`
3. Examples - only CAT issue examples
4. Execution flow - remove skill branches

### Alternative Pattern Documentation
Add section explaining how to achieve skill batch execution:

```markdown
## Skill Batch Execution

To execute a skill on multiple inputs, invoke the skill directly at main agent level:

❌ WRONG (removed capability):
/cat:delegate --skill shrink-doc file1.md file2.md

✅ CORRECT (main agent invokes):
For each file:
  /cat:shrink-doc file1.md
  /cat:shrink-doc file2.md

Or use parallel Task tool invocations with skill instructions embedded in prompts.
```

## Acceptance Criteria
- [ ] --skill parameter removed from delegate
- [ ] No references to skill delegation remain in delegate.md
- [ ] Alternative pattern documented
- [ ] Search confirms no other files reference delegate --skill
- [ ] Delegate still works correctly for --issues

## Execution Steps

### Step 1: Search for --skill references
```bash
grep -r "delegate.*--skill" plugin/
grep -r "\-\-skill" plugin/skills/delegate/
```
Document any files that need updating.

### Step 2: Remove --skill from delegate.md
- Remove parameter from table
- Remove parsing logic
- Remove skill-specific sections
- Remove skill examples

### Step 3: Add migration guidance
- Document the correct pattern for skill batch execution
- Explain why --skill was removed

### Step 4: Update any referencing files
- Fix any files found in Step 1
- Update to use correct pattern

### Step 5: Verify delegate still works
- Test /cat:delegate --issues with a test issue
- Confirm basic functionality preserved

### Step 6: Commit changes
- "config: remove --skill capability from delegate (M429)"
