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
| Threshold | Lower threshold + hook | Add monitoring that forces action |
| Validation | Code fix | Compile-time or runtime enforcement |

## Priming Root Cause

When the investigate phase sets `priming_found: true`, the prevention hierarchy changes. Priming means a document
**caused** the mistake — not merely failed to prevent it. The document is the root cause.

**Mandatory evaluation order when `priming_found: true`:**

1. **Remove or correct the priming source first.** If the document can be modified (rewritten, reordered, or
   removed), that IS the prevention. Prevention type is `documentation` targeting the priming source itself.
2. **Hooks or enforcement only as a fallback.** Use hook/enforcement (level 2) only when the priming source
   cannot be modified — for example, it is an external document, a read-only reference, or required for
   legitimate reasons that outweigh the priming risk.

**Rationale:** Removing the cause is always cheaper and more durable than enforcing against the symptom.
A hook that blocks the wrong behavior still leaves the priming in place — agents will be primed toward the
wrong behavior and rely on the hook catching it. Fix the source.

| Priming Source | Can Modify? | Prevention Action |
|----------------|-------------|-------------------|
| Skill file in `plugin/skills/` | Yes | Rewrite or remove the priming section |
| Concept doc in `plugin/concepts/` | Yes | Rewrite or remove the priming section |
| External documentation (out of repo) | No | Add hook to intercept/enforce correct behavior |
| Read-only reference required for context | No | Add enforcement layer; mark as priming risk |

**Key distinction from escalation rules:**

| Situation | Meaning | Action |
|-----------|---------|--------|
| Documentation failed to prevent mistake | Doc was passive; agent didn't follow it | Escalate to hook |
| Documentation caused the mistake (priming) | Doc was active; agent followed it incorrectly | Fix the doc |

## Documentation Prevention Blocked When (A002)

| Condition | Why Blocked | Required Action |
|-----------|-------------|-----------------|
| Similar documentation already exists | Documentation already failed | Escalate to hook or code_fix |
| Mistake category is `protocol_violation` | Protocol was documented but violated | Escalate to hook enforcement |
| This is a recurrence (`recurrence_of` is set) | Previous prevention failed | Escalate to stronger level |
| prevention_type would be `documentation` (level 7) | Weakest level, often ineffective | Consider hook (level 2) or validation (level 3) (exception: when `priming_found: true` and source is modifiable — see learn-agent Step 5b) |

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
