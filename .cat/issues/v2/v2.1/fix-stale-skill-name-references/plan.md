# Plan: fix-stale-skill-name-references

## Problem

Several skills were renamed to include an `-agent` suffix (e.g., `work-merge` → `work-merge-agent`,
`git-squash` → `git-squash-agent`), but references to the old non-agent names were not fully updated.
This causes Java hooks to check for the wrong skill name at runtime (functional bug), tests to test
the wrong skill name, and documentation to reference non-existent skills.

## Parent Requirements

None

## Root Cause

The renames were applied to `plugin/skills/` directories but not propagated to all Java hook guards,
Java tests, or Markdown documentation files that reference these skills by name.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Hook guards that check the wrong name currently pass everything through; fixing
  them restores the intended blocking behavior. Tests that use the wrong name will begin validating the
  correct behavior.
- **Mitigation:** Run `mvn -f client/pom.xml verify -e` after changes to confirm no regressions.

## Files to Modify

### Java hooks (functional bugs — hooks check wrong skill name)
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java` — line 98: `cat:work-merge` → `cat:work-merge-agent` (both subagent_type and skill checks), update comments on lines 30, 38, 83
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceWorktreeSafetyBeforeMerge.java` — line 49: `cat:work-merge` → `cat:work-merge-agent`, update comments on lines 17, 23
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java` — lines 132–133: `cat:work-merge` → `cat:work-merge-agent`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` — line 59: `"work-merge"` → `"work-merge-agent"` in `CAT_PHASE_SKILLS` set

### Java tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java` — lines 65, 74 (createMergeToolInput), 580–582, 601, 614, 635, 650: `cat:work-merge` → `cat:work-merge-agent`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` — lines 681, 727, 745, 753, 766, 774, 783, 791, 808: `cat:work-merge` → `cat:work-merge-agent`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCommitBeforeSubagentSpawnTest.java` — lines 46, 55: `cat:work-merge` → `cat:work-merge-agent`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnauthorizedMergeCleanupTest.java` — line 260: `cat:work-merge` → `cat:work-merge-agent`

### Plugin skills
- `plugin/skills/tdd-implementation-agent/tdd.md` — lines 293, 306, 362: `cat:git-squash` → `cat:git-squash-agent`
- `plugin/skills/tdd-implementation-agent/first-use.md` — lines 460, 464: `cat:git-squash` → `cat:git-squash-agent`
- `plugin/skills/instruction-builder-agent/testing.md` — lines 17, 26, 30, 80, 128: `cat:git-squash` → `cat:git-squash-agent`; line 81: `cat:git-commit` → `cat:git-commit-agent`
- `plugin/skills/instruction-builder-agent/skill-conventions.md` — line 486: `/cat:safe-rm` → `/cat:safe-rm-agent`

### Rules files
- `.claude/rules/skill-loading.md` — line 9: `cat:git-squash` → `cat:git-squash-agent`
- `.claude/rules/shell-efficiency.md` — line 17: `/cat:safe-rm` → `/cat:safe-rm-agent`
- `plugin/rules/subagent-skill-instructions.md` — line 9: `cat:git-commit` → `cat:git-commit-agent`

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Fix Java hooks (functional bugs)

- Update `EnforceApprovalBeforeMerge.java`: change `cat:work-merge` → `cat:work-merge-agent` on line 98,
  update matching comments on lines 30, 38, 83
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`
- Update `EnforceWorktreeSafetyBeforeMerge.java`: change `cat:work-merge` → `cat:work-merge-agent` on line 49,
  update matching comments on lines 17, 23
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceWorktreeSafetyBeforeMerge.java`
- Update `BlockUnauthorizedMergeCleanup.java`: change `cat:work-merge` → `cat:work-merge-agent` on lines 132–133
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
- Update `SessionAnalyzer.java`: change `"work-merge"` → `"work-merge-agent"` in `CAT_PHASE_SKILLS`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`

### Job 2: Fix Java tests

- Update `EnforceApprovalBeforeMergeTest.java`: change all `cat:work-merge` → `cat:work-merge-agent`
  in `createMergeToolInput`, Javadoc, and skill tool input JSON strings
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java`
- Update `HookEntryPointTest.java`: change all `cat:work-merge` → `cat:work-merge-agent`
  in tool input JSON strings and Javadoc
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java`
- Update `EnforceCommitBeforeSubagentSpawnTest.java`: change `cat:work-merge` → `cat:work-merge-agent`
  in Javadoc and tool input JSON
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCommitBeforeSubagentSpawnTest.java`
- Update `BlockUnauthorizedMergeCleanupTest.java`: change `cat:work-merge` → `cat:work-merge-agent`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnauthorizedMergeCleanupTest.java`

### Job 3: Fix plugin skill files

- Update `tdd-implementation-agent/tdd.md`: change `cat:git-squash` → `cat:git-squash-agent`
  - Files: `plugin/skills/tdd-implementation-agent/tdd.md`
- Update `tdd-implementation-agent/first-use.md`: change `cat:git-squash` → `cat:git-squash-agent`
  - Files: `plugin/skills/tdd-implementation-agent/first-use.md`
- Update `instruction-builder-agent/testing.md`: change `cat:git-squash` → `cat:git-squash-agent`
  and `cat:git-commit` → `cat:git-commit-agent`
  - Files: `plugin/skills/instruction-builder-agent/testing.md`
- Update `instruction-builder-agent/skill-conventions.md`: change `/cat:safe-rm` → `/cat:safe-rm-agent`
  - Files: `plugin/skills/instruction-builder-agent/skill-conventions.md`

### Job 4: Fix rules files

- Update `.claude/rules/skill-loading.md`: change `cat:git-squash` → `cat:git-squash-agent`
  - Files: `.claude/rules/skill-loading.md`
- Update `.claude/rules/shell-efficiency.md`: change `/cat:safe-rm` → `/cat:safe-rm-agent`
  - Files: `.claude/rules/shell-efficiency.md`
- Update `plugin/rules/subagent-skill-instructions.md`: change `cat:git-commit` → `cat:git-commit-agent`
  - Files: `plugin/rules/subagent-skill-instructions.md`

### Job 5: Run tests

- Run `mvn -f client/pom.xml verify -e` and confirm all tests pass

## Post-conditions

- [ ] `grep -r 'cat:work-merge[^-]' client/src plugin/ .claude/rules/` returns no matches
- [ ] `grep -r 'cat:git-squash[^-]' plugin/ .claude/rules/` returns no matches (excluding retrospectives/issues)
- [ ] `grep -r 'cat:git-commit[^-]' plugin/ .claude/rules/` returns no matches
- [ ] `grep -r 'cat:safe-rm[^-]' plugin/ .claude/rules/` returns no matches
- [ ] `mvn -f client/pom.xml verify -e` exits 0
