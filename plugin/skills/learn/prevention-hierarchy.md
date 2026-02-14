<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Prevention Hierarchy

Reference for choosing prevention levels in `/cat:learn`.

## Prevention Levels

| Level | Type | Description | Examples |
|-------|------|-------------|----------|
| 1 | code_fix | Make incorrect behavior impossible in code | Compile-time check, type system, API design |
| 2 | hook | Automated enforcement via PreToolUse/PostToolUse | Block dangerous commands, require confirmation |
| 3 | validation | Automated checks that catch mistakes early | Build verification, lint rules, test assertions |
| 4 | config | Configuration or threshold changes | Lower context threshold, adjust timeouts |
| 5 | skill | Update skill documentation with explicit guidance | Add anti-pattern section, add checklist item |
| 6 | process | Change workflow steps or ordering | Add mandatory checkpoint, reorder operations |
| 7 | documentation | Document to prevent future occurrence | Add to CLAUDE.md, update style guide |

**Key principle:** Lower level = stronger prevention. Always prefer level 1-3 over level 5-7.

## Escalation Rules

When current level failed, escalate:

| Failed Level | Escalate To | Example |
|--------------|-------------|---------|
| Documentation | Hook/Validation | Add pre-commit hook that blocks incorrect behavior |
| Process | Code fix | Make incorrect path impossible in code |
| Skill | Hook | Add enforcement that blocks wrong approach |
| Validation | Code fix | Compile-time or runtime enforcement |

## Documentation Prevention Blocked When (A002)

| Condition | Why Blocked | Required Action |
|-----------|-------------|-----------------|
| Similar documentation already exists | Documentation already failed | Escalate to hook or code_fix |
| Mistake category is `protocol_violation` | Protocol was documented but violated | Escalate to hook enforcement |
| This is a recurrence (`recurrence_of` is set) | Previous prevention failed | Escalate to stronger level |
| prevention_type would be `documentation` (level 7) | Weakest level, often ineffective | Consider hook (level 2) or validation (level 3) |

## Prevention Quality Checklist

Before implementing, verify:

```yaml
prevention_quality_check:
  verification_type:
    positive: "Check for PRESENCE of correct behavior"  # ✅ Preferred
    negative: "Check for ABSENCE of specific failure"   # ❌ Fragile

  generality:
    question: "If the failure mode varies slightly, will this still catch it?"

  inversion:
    question: "Can I invert this check to verify correctness instead?"
    pattern: |
      Instead of: "Fail if BAD_PATTERN exists"
      Try:        "Fail if GOOD_PATTERN is missing"

  fragility_assessment:
    low:    "Checks for correct format/behavior (positive verification)"
    medium: "Checks for category of errors (e.g., any TODO-like text)"
    high:   "Checks for exact observed failure (specific string match)"
```

**Decision gate:** If fragility is HIGH, redesign before implementing.
