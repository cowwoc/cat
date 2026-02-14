# Plan: add-work-with-issue-banners

## Goal
Implement progress banner generation for the work-with-issue skill. Currently the skill expects pre-rendered
banners from a handler that was never implemented after the handler architecture migrated to Java. Additionally,
ProgressBanner.java only has 4 phases but the skill expects 5 (adding Verifying between Executing and Reviewing).

## Satisfies
None - infrastructure gap fix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Adding VERIFYING enum shifts ordinals, but phaseSymbol() uses ordinal comparison internally
  which remains correct with the new ordering
- **Mitigation:** Existing CLI callers pass phase as string, not ordinal; unit tests verify all phase combinations

## Background Research

### Architecture Constraint
SkillOutput.getOutput() has no access to skill ARGUMENTS — only JvmScope is passed to the constructor.
The work-with-issue skill receives issue_id as JSON arguments at invocation time, but the SkillLoader
resolves bindings BEFORE the agent sees the content. Therefore a SkillOutput-based approach cannot
generate issue-specific banners.

### Chosen Approach
Instead of a SkillOutput binding, modify the work-with-issue skill to call ProgressBanner CLI at each
phase transition. This is simpler and consistent with how the /cat:work skill handles its preparing banner.
The skill fail-fast requirements for missing pre-rendered banners will be replaced with direct CLI invocation
instructions.

### Existing Code
- `ProgressBanner.java` - Has 4 phases: PREPARING, EXECUTING, REVIEWING, MERGING
- `ProgressBanner.Phase` enum - Uses ordinal comparison in phaseSymbol()
- `buildBanner()` - Renders phase line with symbols for each phase
- `generateAllPhases()` - Generates all phase banners with markdown formatting
- CLI: `--phase <name>` and `--all-phases` arguments

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java` - Add VERIFYING phase, update
  buildBanner to render 5 phases, update generateAllPhases, update CLI main() to accept "verifying"
- `plugin/skills/work-with-issue/first-use.md` - Replace fail-fast pre-rendered banner requirements with
  direct CLI invocation instructions at each phase transition
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/ProgressBannerTest.java` - Add tests for VERIFYING phase

## Acceptance Criteria
- [ ] ProgressBanner.Phase enum includes VERIFYING between EXECUTING and REVIEWING (5 phases total)
- [ ] buildBanner() renders 5-phase progress line (Preparing, Executing, Verifying, Reviewing, Merging)
- [ ] generateAllPhases() generates banners for all 5 phases
- [ ] CLI `--phase verifying` argument accepted and generates correct banner
- [ ] phaseSymbol() correctly classifies VERIFYING as complete/active/pending relative to current phase
- [ ] work-with-issue skill invokes ProgressBanner CLI at each phase transition instead of requiring
  pre-rendered handler output
- [ ] Fail-fast on missing pre-rendered banners removed from work-with-issue skill
- [ ] ProgressBanner Javadoc updated to list 5 phases
- [ ] Existing 4-phase CLI callers continue to work (they just do not reference VERIFYING)
- [ ] All tests pass: `mvn -f hooks/pom.xml verify`

## Execution Steps

1. **Step 1:** Read Java conventions from `.claude/cat/conventions/java.md`
   - Files: `.claude/cat/conventions/java.md`

2. **Step 2:** Add VERIFYING to ProgressBanner.Phase enum between EXECUTING and REVIEWING
   - Add Javadoc comment: "Verifying phase - acceptance criteria validation."
   - Update class-level Javadoc: "Workflow phases: Preparing, Executing, Verifying, Reviewing, Merging"
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java`

3. **Step 3:** Update buildBanner() to render 5 phase symbols
   - Add `String p3 = phaseSymbol(Phase.VERIFYING, currentPhase);` (renumber existing p3->p4, p4->p5)
   - Update phaseContent string to include Verifying between Executing and Reviewing
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java`

4. **Step 4:** Update generateAllPhases() to include VERIFYING phase banner
   - Add Verifying phase banner block between Executing and Reviewing
   - Update pattern descriptions: Preparing (◉ ○ ○ ○ ○), Executing (● ◉ ○ ○ ○), Verifying (● ● ◉ ○ ○),
    Reviewing (● ● ● ◉ ○), Merging (● ● ● ● ◉)
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java`

5. **Step 5:** Update CLI main() method to accept "verifying" as a valid --phase argument
   - Add case "verifying" -> phase = Phase.VERIFYING to the switch statement
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java`

6. **Step 6:** Update work-with-issue skill to use CLI invocation instead of pre-rendered banners
   - Remove the "SCRIPT OUTPUT PROGRESS BANNERS" and "INDIVIDUAL PHASE BANNERS" fail-fast requirements
   - At each phase transition step, add a CLI invocation instruction:
     ```
     Display the phase banner by running:
     "${CLAUDE_PLUGIN_ROOT}/hooks/bin/java" -Xms16m -Xmx96m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
       -Dstdin.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
       -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.skills.ProgressBanner \
       ${ISSUE_ID} --phase <phase-name>
     ```
   - Replace the banner sections in Steps 1, 3, 4, 5, 8 with direct CLI calls
   - Files: `plugin/skills/work-with-issue/first-use.md`

7. **Step 7:** Add/update unit tests for VERIFYING phase
   - Test generateBanner with VERIFYING as current phase produces correct symbols
   - Test generateAllPhases includes 5 phase blocks
   - Test phaseSymbol returns COMPLETE for phases before VERIFYING, ACTIVE for VERIFYING, PENDING for after
   - Files: `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/ProgressBannerTest.java`

8. **Step 8:** Run `mvn -f hooks/pom.xml verify` to confirm all tests pass

9. **Step 9:** Update STATE.md to closed with progress 100%
   - Files: `.claude/cat/issues/v2/v2.1/add-work-with-issue-banners/STATE.md`

## Success Criteria
- [ ] All 5 phases render correct symbol patterns in unit tests
- [ ] CLI accepts all 5 phase names including "verifying"
- [ ] work-with-issue skill no longer references pre-rendered banner sections
- [ ] `mvn -f hooks/pom.xml verify` passes with exit code 0