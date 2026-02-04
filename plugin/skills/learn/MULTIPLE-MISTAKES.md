# Multiple Independent Mistakes (M378)

Lazy-loaded when investigation reveals multiple separate issues.

## When to Use

When investigating a problem reveals multiple independent mistakes - not just multiple causes
of one mistake, but genuinely separate mistakes that each warrant their own RCA and prevention.

## Identification Pattern

```yaml
multiple_mistakes_check:
  question: "Are these separate issues that could occur independently?"
  if_yes: "Invoke /cat:learn separately for EACH mistake"
  if_no: "Continue with single /cat:learn for the one mistake"
```

## Workflow

1. **Complete the current `/cat:learn`** for the first mistake you identified
   - Full RCA (Steps 1-4)
   - Implement prevention (Step 9) - **actually edit files**
   - Record learning (Step 11)
   - Commit changes (Step 12)
2. **Invoke `/cat:learn` again** for each additional independent mistake
   - Each invocation runs the FULL workflow: research → RCA → **implement fix** → record → commit
3. **Each gets its own M-number**, RCA, and prevention

**CRITICAL: Each /cat:learn invocation must IMPLEMENT prevention, not just record the mistake.**

## Example

```yaml
# Observed failure: Batch compression failed midway through
# Investigation reveals TWO independent mistakes:

mistake_1:
  description: "Handler didn't exist for /cat:delegate preprocessing"
  action: "Complete /cat:learn -> M377"

mistake_2:
  description: "Agent didn't acknowledge user message mid-operation"
  action: "Invoke /cat:learn again -> M378"
```

## Why Separate Invocations Matter

- Each mistake may have different root causes requiring different RCA methods
- Each prevention needs its own implementation and verification
- Retrospective tracking is more accurate with distinct M-numbers
- Patterns are easier to identify when mistakes are properly separated

**BLOCKING: Do not bundle unrelated mistakes.**
