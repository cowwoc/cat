<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: fix-skill-output-framing

## Problem
The status skill first-use SKILL.md uses negative instructional framing ("Do NOT summarize...") which fails ~10% of
the time on haiku with production-size Unicode box content. The agent either summarizes the output, re-invokes the
Skill tool, or leaks instruction text to the user. The SkillLoader subsequent invocation reference text also lacks
explicit instructions for re-executing the original skill instructions and does not generalize to other skills.

## Architectural Goal
Establish a pattern where:
1. **Instructions loaded once** — first-use SKILL.md carries full instructions, loaded into context on first call
2. **Dynamic elements isolated in tags** — `<output>` tags contain data that changes between invocations
3. **Subsequent calls reuse instructions** — reference text tells the agent to re-execute the original skill
   instructions with the updated output tag

Currently 3 skills use `<output>` tags (status, statusline, run-retrospective). As more skills migrate to this
pattern, the SkillLoader infrastructure must handle it generically.

## Satisfies
None

## Root Cause
Empirical testing (configs A-S, 100+ trials) identified:
1. Unicode box-drawing characters trigger haiku to recognize content as a CAT status display, causing Skill
   re-invocation or interpretation mode
2. Negative framing ("Do NOT...") fails to prevent this with production-size content (~10% failure rate)
3. User-centric framing ("The user wants you to respond with...") achieves 100% compliance
4. The subsequent invocation reference text must come before the output tag and instruct the agent to re-execute the
   original skill instructions

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Skills using output as data to parse (statusline, run-retrospective) are unaffected by the
  SkillLoader reference text change since their first-use instructions tell the agent to parse, not echo
- **Mitigation:** Empirical testing validates the fix at 100% across single and multi-invocation scenarios

## Files to Modify
- `plugin/skills/status-first-use/SKILL.md` - Replace negative framing with user-centric framing
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Update reference text for subsequent
  invocations
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` - Update test expectations
- `plugin/concepts/silent-execution.md` - Rename from handler-chain-pattern.md and rewrite with correct
  argument substitution syntax
- `plugin/skills/work-first-use/SKILL.md` - Convert Next Task section to use silent execution pattern via
  work-complete skill invocation

## Acceptance Criteria
- [ ] Bug fixed: status skill outputs only verbatim content + NEXT STEPS table, no instruction leakage
- [ ] SkillLoader reference text is generic and works for any skill with output tags
- [ ] Subsequent invocation ordering: reference instruction BEFORE output tag (instructions first, content second)
- [ ] Pattern generalizes: any skill can adopt `<output>` tags and get correct first-use + subsequent behavior
- [ ] Regression test: empirical test confirms 100% compliance with production-size content
- [ ] No new issues introduced in other skills using output tags (statusline, run-retrospective)
- [ ] silent-execution.md documents the correct argument substitution pipeline
- [ ] Work skill Next Task section uses silent execution via skill invocation instead of inline Bash

## Execution Steps
1. **Update status-first-use/SKILL.md:**
   - Replace the entire instruction block with user-centric framing
   - Files: `plugin/skills/status-first-use/SKILL.md`

2. **Update SkillLoader.java reference text for subsequent invocations:**
   - Change the reference text to instruct re-execution of original skill instructions
   - Ensure the reference text appears BEFORE the output tag
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`

3. **Update SkillLoader tests:**
   - Update test expectations to match the new reference text
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`

4. **Rename and rewrite handler-chain-pattern.md:**
   - Rename to `silent-execution.md`
   - Document the correct argument substitution pipeline: Claude Code resolves `$ARGUMENTS`, `$name`, `$1`,
     `$ARGUMENTS[0]` BEFORE preprocessor directives execute
   - Warn that `${ARGUMENTS}` (curly braces) does NOT work — only `$ARGUMENTS` (bare dollar sign)
   - Files: `plugin/concepts/silent-execution.md`

5. **Convert work skill Next Task section to silent execution:**
   - Replace the inline `get-next-task-box` Bash command with a `/cat:work-complete` skill invocation
   - The work skill passes `issue_id` and `base_branch` (and optionally `exclude_pattern`) as args
   - The work-complete skill uses `$ARGUMENTS` in a preprocessor directive to run the command invisibly
   - Keep the error handling `issue-lock.sh` as inline Bash (error paths need agent intervention)
   - Files: `plugin/skills/work-first-use/SKILL.md`, new `plugin/skills/work-complete/SKILL.md` and
     `plugin/skills/work-complete-first-use/SKILL.md`

6. **Run tests:**
   - Run `mvn -f client/pom.xml test` to verify all tests pass

## Success Criteria
- [ ] status-first-use/SKILL.md uses user-centric framing with no negative instructions
- [ ] SkillLoader subsequent invocation text is generic and appears before the output tag
- [ ] All SkillLoader tests pass
- [ ] Empirical test with production content achieves >= 90% compliance on haiku
- [ ] silent-execution.md correctly documents `$ARGUMENTS` (no braces) as the working syntax
- [ ] Work skill Next Task section invokes work-complete skill instead of inline Bash
