# Plan: rewrite-test-turn1-organic

## Goal
Rewrite Turn 1 sections of test files for status-agent and git-squash-agent to use organic user prompts
that do not name or hint at which skill to invoke, so the agent must arrive at the skill through natural
problem-solving.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changing Turn 1 phrasing could affect what behavior the test is actually testing if not
  done carefully; assertions must remain unchanged.
- **Mitigation:** Only the ## Turn 1 section is modified; all frontmatter and ## Assertions sections
  are left intact.

## Files to Modify
- plugin/tests/skills/status-agent/first-use/1-no-issues.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/status-agent/first-use/2-latest-output.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/status-agent/first-use/3-box-format.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/status-agent/first-use/4-no-intro-phrase.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/status-agent/first-use/5-no-fabrication.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/status-agent/first-use/6-no-followup.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/git-squash-agent/first-use/no-learn-correct-resolution.md - rewrite Turn 1 to organic user request
- plugin/tests/skills/git-squash-agent/first-use/step3-seq-read-comment.md - rewrite Turn 1 to organic user request

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Rewrite Turn 1 in each status-agent test file to a natural user request for project status, preserving
  the `<output>` tag content verbatim after the request
  - Files: plugin/tests/skills/status-agent/first-use/1-no-issues.md,
    plugin/tests/skills/status-agent/first-use/2-latest-output.md,
    plugin/tests/skills/status-agent/first-use/3-box-format.md,
    plugin/tests/skills/status-agent/first-use/4-no-intro-phrase.md,
    plugin/tests/skills/status-agent/first-use/5-no-fabrication.md,
    plugin/tests/skills/status-agent/first-use/6-no-followup.md
- Rewrite Turn 1 in each git-squash-agent test file to a first-person user scenario without naming the
  skill, following the exact patterns specified in the issue request
  - Files: plugin/tests/skills/git-squash-agent/first-use/no-learn-correct-resolution.md,
    plugin/tests/skills/git-squash-agent/first-use/step3-seq-read-comment.md

## Post-conditions
- [ ] All 8 test files have Turn 1 rewritten to organic user prompts with no skill names or "The skill
  has been invoked" phrasing
- [ ] All ## Assertions sections are unchanged in all 8 files
- [ ] All frontmatter is unchanged in all 8 files
- [ ] The `<output>` tags in status-agent tests remain verbatim and in the same position
