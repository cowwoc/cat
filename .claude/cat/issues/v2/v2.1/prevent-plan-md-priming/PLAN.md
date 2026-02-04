# Issue: prevent-plan-md-priming

## Goal

Prevent PLAN.md execution steps from priming subagents to fabricate results by conflating
actions with expected outcomes. Related mistakes: M254, M265, M269, M273, M274, M276, M320,
M346, M349, M355, M370, M421, M423.

## Problem

Current PLAN.md structure mixes execution instructions with expected values:
```
## Execution Steps
1. Compress files using /cat:shrink-doc
2. Verify each file scores 1.0 on /compare-docs  ← PRIMES fabrication
```

When subagents see "Verify score = 1.0", they report 1.0 regardless of actual results.

## Approach

Three-layer prevention:

### Layer 1: Hook Enforcement
Create PreToolUse hook that blocks Task tool calls when PLAN.md contains expected values
in Execution Steps section.

Detection patterns:
- "score = X" or "score: X" in execution steps
- "expected: X" or "should be X" patterns
- Numeric thresholds in action descriptions

### Layer 2: PLAN.md Restructure
Separate actions from success criteria in PLAN.md template:

```markdown
## Execution Steps (ACTIONS ONLY)
1. Compress files using /cat:shrink-doc
2. Run /compare-docs validation

## Success Criteria (MEASURABLE OUTCOMES)
- All files achieve EQUIVALENT status on /compare-docs
- Token reduction > 30%
```

Subagent prompts reference Execution Steps; orchestrator verifies Success Criteria.

### Layer 3: Deterministic Scripts
Move validation/iteration loops to Python scripts that:
- Run the actual tool and capture output
- Parse results deterministically
- Return pass/fail without agent interpretation

## Execution Steps

1. Create hook script `plugin/hooks/bash_handlers/validate_plan_md.py`
   - Detect priming patterns in PLAN.md Execution Steps
   - Return blocking message when patterns found

2. Update PLAN.md template `plugin/templates/issue-plan.md`
   - Separate "Execution Steps" from "Success Criteria"
   - Add guidance comments about what belongs in each

3. Update `/cat:add` skill to use new template structure

4. Validate existing PLAN.md files don't violate new structure

## Acceptance Criteria

- [ ] Hook blocks PLAN.md with expected values in Execution Steps
- [ ] PLAN.md template has separate Actions and Success Criteria sections
- [ ] /cat:add creates issues using new template
- [ ] All tests pass
- [ ] No regressions in existing functionality

## Estimated Tokens

~35,000 tokens (hook implementation, template update, skill update)
