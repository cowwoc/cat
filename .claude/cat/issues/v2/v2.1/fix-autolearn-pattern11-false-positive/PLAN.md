# Bugfix: fix-autolearn-pattern11-false-positive

## Problem

`AutoLearnMistakes.java` Pattern 11 uses CASE_INSENSITIVE regex `CRITICAL
(DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM|ISSUE)` which matches severity-table vocabulary like "critical issues" or
"Must fix critical issues" in stakeholder-review/SKILL.md documentation. When agents read skill documentation files via
Bash (`cat`), the output triggers spurious `critical_self_acknowledgment` detections.

## Root Cause

Pattern 11 matches any occurrence of "CRITICAL ISSUE" (case-insensitive) without requiring first-person or
self-referential framing. `filterGitNoise` does not strip skill-documentation lines, so severity table content reaches
Pattern 11 unchanged.

**Evidence:** M459 — Session `ff07e9b6`, agent read `bzpr65m53.txt` (persisted stakeholder-review skill content
containing severity table) via `cat` Bash command. Line "Must fix critical issues" triggered false positive.

## Satisfies

None - infrastructure/reliability improvement

## Post-conditions

- [ ] Pattern 11 does NOT trigger on "Must fix critical issues" or "critical error" in severity tables
- [ ] Pattern 11 DOES trigger on "I made a critical error" or "I caused a critical mistake"
- [ ] Pattern 11 DOES trigger on "this was a catastrophic mistake"
- [ ] Tests in `AutoLearnMistakesTest` verify all false-positive and true-positive cases
- [ ] No regression in other AutoLearnMistakes patterns

## Implementation

Narrow Pattern 11's regex to require first-person/self-referential framing:

```java
// Before: matches any severity-table vocabulary
Pattern.compile("CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM|ISSUE)", Pattern.CASE_INSENSITIVE)

// After: requires first-person framing
Pattern.compile(
    "(I (made|caused|introduced|created|committed) a CRITICAL" +
    "|this (is|was) a CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM)" +
    "|catastrophic|devastating) (mistake|error|failure|bug)",
    Pattern.CASE_INSENSITIVE)
```

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` — narrow Pattern 11 regex
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java` — add false-positive and
  true-positive test cases for Pattern 11
