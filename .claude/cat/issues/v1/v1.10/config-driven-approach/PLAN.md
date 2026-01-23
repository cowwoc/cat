# Plan: config-driven-approach

## Current State
Task PLAN.md templates use Conservative/Balanced/Aggressive approach outlines that describe scope
and risk tradeoffs. This creates redundancy with the existing Trust/Verify/Curiosity/Patience
configuration dimensions in cat-config.json.

## Target State
Replace approach outlines with guidance on how the four config dimensions affect task execution.
The PLAN.md focuses on WHAT to do, while cat-config.json controls HOW it's done.

## Satisfies
- None (template refactoring)

## Refactor Approach

### Current Template Structure (to remove)
```markdown
## Approach Outlines

### Conservative
[minimal scope] - Risk: LOW - Tradeoff: [limited]

### Balanced
[moderate scope] - Risk: MEDIUM - Tradeoff: [some areas untouched]

### Aggressive
[comprehensive] - Risk: HIGH - Tradeoff: [large change surface]
```

### New Template Structure (to add)
```markdown
## Execution Behavior

Task execution is controlled by cat-config.json dimensions:

| Dimension | Low Setting | High Setting |
|-----------|-------------|--------------|
| **trust** | More verification steps, explicit approval | Auto-proceed, skip confirmations |
| **verify** | Skip tests/builds | Full test suite, stakeholder review |
| **curiosity** | Stick to plan strictly | Explore edge cases, suggest improvements |
| **patience** | Quick iterations, accept good-enough | Thorough analysis, handle discovered issues |

Current config: Read from cat-config.json at execution time.
```

## Files to Modify
- `plugin/.claude/cat/templates/task-plan.md` - Remove approach outlines, add config behavior section
- `plugin/commands/add.md` - Update task creation to use new template
- `plugin/commands/work.md` - Remove choose_approach step (config already determines behavior)

## Acceptance Criteria
- [ ] Task templates no longer have Conservative/Balanced/Aggressive sections
- [ ] Templates explain how Trust/Verify/Curiosity/Patience affect execution
- [ ] /cat:add creates tasks with new template structure
- [ ] /cat:work uses cat-config.json directly (no approach selection step)
- [ ] Existing tasks with old approach format still work (graceful handling)
