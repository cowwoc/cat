# Mistake Categories

Reference for categorizing mistakes in `/cat:learn`.

## Category Reference

| Category | Description |
|----------|-------------|
| protocol_violation | Violated documented workflow, skill steps, or mandatory instructions |
| prompt_engineering | Subagent prompt lacked necessary instructions or constraints |
| context_degradation | Quality degraded due to context window pressure or compaction |
| tool_misuse | Used wrong tool, wrong flags, or misunderstood tool behavior |
| assumption_without_verification | Claimed state without measurement or verification |
| bash_error | Shell script error (syntax, reserved variables, compatibility) |
| git_operation_failure | Git command failed or produced unexpected results |
| build_failure | Compilation, checkstyle, PMD, or other build tool failure |
| test_failure | Test assertion failure or incorrect test construction |
| logical_error | Incorrect reasoning or misapplied rule |
| detection_gap | Monitoring/hook failed to catch a problem |
| architecture_issue | Structural or design-level mistake |
| documentation_violation | Violated documented standard (not workflow protocol) |
| giving_up | Presented options instead of implementing, or stopped prematurely |
| misleading_documentation | Documentation primed agent for wrong approach |

## Common Root Cause Patterns

| Pattern | Indicators | Typical Prevention |
|---------|------------|-------------------|
| Assumption without verification | "I assumed...", claimed state without measurement | Add verification step (hook/validation) |
| Completion bias | Rationalized ignoring protocol to finish task | Strengthen enforcement (hook/code_fix) |
| Memory reliance | Used memory instead of get-history/re-reading | Add verification requirement (process) |
| Environment state mismatch | Wrong directory, stale data, wrong branch | Add state verification (hook/validation) |
| Documentation ignored | Rule existed but wasn't followed | Escalate to hook/code_fix |
| Ordering/timing | Operations in wrong sequence | Add explicit ordering (skill/process) |
| Documentation priming | Skill doc taught algorithm agent then bypassed | Encapsulate internal algorithms (skill) |

## JSON Entry Format

```json
{
  "id": "{NEXT_ID}",
  "timestamp": "{ISO-8601 timestamp}",
  "category": "{see categories above}",
  "description": "{One-line description}",
  "root_cause": "{Root cause from analysis}",
  "rca_method": "{A|B|C}",
  "rca_method_name": "{5-whys|taxonomy|causal-barrier}",
  "prevention_type": "{code_fix|hook|validation|config|skill|process|documentation}",
  "prevention_path": "{path/to/file/changed}",
  "pattern_keywords": ["{keyword1}", "{keyword2}"],
  "prevention_implemented": true,
  "prevention_verified": true,
  "recurrence_of": "{null or ID of original mistake}",
  "prevention_quality": {
    "verification_type": "{positive|negative}",
    "fragility": "{low|medium|high}",
    "catches_variations": true
  },
  "correct_behavior": "{What should be done instead}"
}
```
