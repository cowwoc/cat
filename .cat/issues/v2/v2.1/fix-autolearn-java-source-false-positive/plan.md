# Plan: fix-autolearn-java-source-false-positive

## Problem

AutoLearnMistakes.filterJsonContent() only strips JSON-structured content (lines starting with `{` or `[{`)
but not Java source code output. When the agent reads hook source files via Bash (e.g., `sed`, `grep`, `cat`
on `.java` files), the stdout contains Java string literals like `'PROTOCOL VIOLATION'`, `'BUILD FAILURE'`,
and test-related text from the hook's own pattern definitions and Javadoc. These literals pass through
filterJsonContent() unfiltered and match AutoLearnMistakes Patterns 1, 2, and 3, producing spurious
mistake detections that interrupt workflow and pollute the mistake record.

This is the **fourth recurrence** of M461/M466/M555 (insufficient output filtering in AutoLearnMistakes).
Previous recurrences: M461, M466, M555. Current: M563.

## Parent Requirements

None

## Reproduction Code

```bash
# Trigger 1: Read a hook source file containing pattern keywords in string literals
sed -n '65,90p' client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java
# → stdout contains "PROTOCOL VIOLATION" from hook's Javadoc/string literals
# → AutoLearnMistakes Pattern 3 fires: false positive protocol_violation

# Trigger 2: Run git diff that includes deleted file content containing pattern keywords
git diff v2.1..HEAD
# → stdout includes deleted PLAN.md content containing "violation types"
# → AutoLearnMistakes Pattern 3 fires: false positive protocol_violation (M563)
```

## Expected vs Actual

- **Expected:** Reading Java source files or git diffs via Bash does not trigger AutoLearnMistakes patterns
- **Actual:** AutoLearnMistakes Pattern 3 (`PROTOCOL VIOLATION|VIOLATION`) fires when stdout contains
  `'VIOLATION'` from Java source code string literals or from deleted file content in git diffs

## Root Cause

The hook scans all tool output for mistake patterns regardless of exit code or tool type. Commands that
succeed (exit_code 0) cannot represent real failures; any matching keywords in their output are false
positives from displayed content (source files, git history, etc.). Additionally, several patterns
(protocol_violation, missing_cleanup, restore_from_backup, and critical_self_acknowledgment/
self_acknowledged_mistake from tool output) presuppose agent reasoning/dialogue, which can only appear
in agent responses, not in tool output.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Could over-strip content and miss real mistakes if filter is too broad
- **Mitigation:** TDD approach — write failing tests for true positives (real Maven BUILD FAILURE,
  real protocol violations) AND for false positives (Java source code reads), verify all pass after fix

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`
  - Gate Bash-tool failure patterns on `toolName.equals("Bash") && exitCode != 0`
  - Remove patterns that scan for agent-dialogue/agent-reasoning concepts in tool output
    (Patterns 3, 8, 9 from tool output, 10, 11 from tool output)
  - Keep patterns 9 and 11 in the assistant message check at bottom of `detectMistake()`
  - Remove `JAVA_SOURCE_LINE_PATTERN` and Java source detection from `filterJsonContent()`
  - Simplify `filterJsonContent()`: remove git diff line filtering, keep JSONL filtering
  - Remove unused `MAX_PATTERN_GAP` constant
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java`
  - Add `toolResult(mapper, stdout, exitCode)` overload
  - Remove Java source detection tests and git diff test
  - Update true-positive tests to use exitCode=1
  - Add exit-code-based gating tests

## Test Cases

Exit-code-based gating coverage:
- [ ] `Bash with exit_code 0 does NOT trigger Pattern 1 even with 'BUILD FAILURE' in output`
- [ ] `Bash with exit_code 0 does NOT trigger Pattern 2 even with 'Tests run: 1, Failures: 1' in output`
- [ ] `Bash with exit_code != 0 DOES trigger Pattern 1 with 'BUILD FAILURE' in output`
- [ ] `Bash with exit_code != 0 DOES trigger Pattern 2 with 'Tests run: 1, Failures: 1' in output`
- [ ] `Tool output keywords from Pattern 3/8/10 removed never trigger regardless of exit_code`
- [ ] `Pattern 11/9 from tool output never trigger (removed from tool-output check)`
- [ ] `Pattern 11/9 from assistant messages still trigger (preserved in assistant check)`
- [ ] `Real Maven BUILD FAILURE with exit_code != 0 still triggers Pattern 1`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1+2+3 (Unified Implementation + Testing)

- Rewrite `detectMistake()` to gate Bash failure patterns on `toolName.equals("Bash") && exitCode != 0`
- Remove patterns that only apply to agent output (3, 8, 9 from tool-output, 10, 11 from tool-output)
- Simplify `filterJsonContent()`: remove Java source detection and git diff filtering (exit-code gating makes them unnecessary)
- Update test suite:
  - Add `toolResult(mapper, stdout, exitCode)` overload
  - Add tests verifying exit-code-based gating works
  - Remove Java source detection and git diff tests (no longer needed)
  - Update true-positive tests to use exitCode=1
- Run `mvn -f client/pom.xml test` — verify all 2528 tests PASS
- Update PLAN.md and STATE.md with final design
- Commit with type `bugfix:`

## Post-conditions

- [ ] Bash commands with exit_code 0 do NOT trigger any failure patterns (even if output contains keywords)
- [ ] Pattern 1 (build_failure) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 2 (test_failure) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 4 (merge_conflict) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 7 (git_operation_failure) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 12 (wrong_working_directory - git repo) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 13 (wrong_working_directory - pom.xml) only triggers when toolName == "Bash" and exitCode != 0
- [ ] Pattern 3 (protocol_violation) completely removed from tool-output scanning
- [ ] Pattern 8 (missing_cleanup) completely removed
- [ ] Pattern 10 (restore_from_backup) completely removed
- [ ] Pattern 9 (self_acknowledged_mistake) removed from tool-output scanning (kept in assistant message check)
- [ ] Pattern 11 (critical_self_acknowledgment) removed from tool-output scanning (kept in assistant message check)
- [ ] JAVA_SOURCE_LINE_PATTERN and Java source detection logic removed
- [ ] filterJsonContent() only filters JSONL conversation logs (no git diff or Java source filtering)
- [ ] Edit tool failures still work (not gated by Bash/exitCode)
- [ ] Skill step failures still work (already checks toolName == "Skill")
- [ ] Assistant message patterns still work (protocol_violation and critical_self_acknowledgment checks preserved)
- [ ] All 2528 tests pass
- [ ] No regression in other AutoLearnMistakes patterns