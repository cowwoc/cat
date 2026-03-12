# Plan: enforce-subagent-validation-separation-hook

## Goal
Implement a PostToolUse hook that detects when an agent claims verification or validation results without
corresponding evidence from an actual skill invocation in the session transcript. Addresses A001/PATTERN-001
(subagent validation fabrication), escalated after documentation-level prevention proved ineffective (5
post-fix recurrences).

## Background

PATTERN-001 persists across 9 total occurrences (5 after fix). Documentation in `subagent-delegation.md`
instructs "subagents that PRODUCE output must NOT validate it", but agents continue fabricating verification
scores and validation results. The ESCALATE-A001 escalation proposes a structural enforcement mechanism:
a PostToolUse hook that detects validation claims lacking tool evidence.

The detection heuristic: when an agent's output contains validation/verification claim keywords (e.g.,
"score:", "verified:", "validated:", "PASS/FAIL", semantic equivalence assertions) AND no corresponding
skill invocation (e.g., `cat:compare-docs`, `cat:verify-implementation`) appears in the recent session
transcript, emit a warning.

## Satisfies

None — escalation A001 from retrospective (PATTERN-001: subagent_validation_fabrication)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** High false-positive rate if detection patterns are too broad; legitimate validation reports
  from actual skill invocations may trigger warnings
- **Mitigation:** Scope detection to specific keyword patterns AND absence of skill invocation evidence;
  start with warning-only (not blocking) to calibrate before considering blocking

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectValidationWithoutEvidence.java` —
  new PostToolUse handler; checks assistant output for validation claim patterns and warns if no
  corresponding skill invocation evidence found in session transcript
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectValidationWithoutEvidenceTest.java` —
  tests for pattern detection, false-positive scenarios, and evidence matching
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` — register the new handler

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- **Read PostToolUseHook.java and existing handlers:** Understand registration pattern and handler interface
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`
- **Design detection patterns:** Define keyword set for validation claims (score:, verified:, PASS, FAIL,
  semantic equivalence, validation complete) and skill evidence patterns (cat:compare-docs,
  cat:verify-implementation invocations in transcript)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- **Implement DetectValidationWithoutEvidence.java:** Warning-only handler that checks assistant output
  against session transcript evidence
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectValidationWithoutEvidence.java`
- **Write tests:** Cover true-positive detection, false-positive suppression when evidence present,
  and edge cases
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectValidationWithoutEvidenceTest.java`
- **Register handler:** Add to PostToolUseHook handler list
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java`
- **Run tests:** `mvn -f client/pom.xml test`
- **Commit**

## Post-conditions

- [ ] `DetectValidationWithoutEvidence.java` exists and is registered in `PostToolUseHook.java`
- [ ] Handler emits a warning when assistant output contains validation claim patterns without
  corresponding skill invocation evidence in the session transcript
- [ ] Handler does NOT warn when validation claims are accompanied by evidence of skill invocation
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] At least 5 test cases covering true-positive and false-positive scenarios
