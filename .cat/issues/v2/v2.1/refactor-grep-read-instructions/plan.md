# Plan

## Goal

Run instruction-builder on cat:grep-and-read-agent skill to redesign and improve its instructions; add
e2e post-condition verifying subagents invoke this skill when they need to search for patterns and read
the matching files in a single operation.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/grep-and-read-agent/first-use.md` updated with improved trigger conditions and usage guidance (output of instruction-builder workflow)
- [ ] All tests pass after changes (`mvn -f client/pom.xml verify -e` exits 0)
- [ ] `plugin/tests/skills/grep-and-read-agent/first-use/e2e_subagent_invocation.md` exists and is registered (if index.json tracks test files in that directory)
- [ ] No regressions: all existing test cases in `plugin/tests/skills/grep-and-read-agent/first-use/` remain unchanged

## Main Agent Jobs

- /cat:instruction-builder-agent goal="create or update skill"

## Jobs

### Job 1

Create `plugin/tests/skills/grep-and-read-agent/first-use/e2e_subagent_invocation.md` with the following exact
content (modeled after `plugin/tests/skills/grep-and-read-agent/first-use/positive_find_implementations.md`):

```markdown
---
category: REQUIREMENT
---
## Turn 1

I need you to find all Java files that contain the word 'interface' and then read each one so I can
understand the codebase's interface landscape. You don't know the file paths ahead of time.

## Assertions

1. The Skill tool was invoked
2. The agent invoked grep-and-read-agent to search and read multiple files in a single operation
3. The agent did NOT make a raw Grep call followed by separate individual Read calls across separate messages
```

After creating the file:
- Check whether `plugin/tests/skills/grep-and-read-agent/first-use/index.json` exists. If it exists, add
  `"e2e_subagent_invocation"` to the list of test case names in that file. If the file does not exist, no
  action is needed (the test runner discovers files automatically).
- Run `mvn -f client/pom.xml verify -e` to confirm all tests still pass after adding the new test file.
