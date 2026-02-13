# Plan: audit-plan-verification

## Goal
Clean-room implementation of a post-execution audit skill that systematically verifies all planned changes from PLAN.md were actually implemented in the codebase after /cat:work execution completes.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Skill must not modify files, only report. Must handle varied PLAN.md formats.
- **Mitigation:** Read-only verification with structured reporting. Test against multiple PLAN.md formats.

## Files to Modify
- plugin/skills/verify-implementation/SKILL.md - Renamed skill definition
- plugin/skills/verify-implementation/content.md - Updated skill content with /cat:work integration
- plugin/skills/work-with-issue/content.md - Add verify-implementation phase between execute and review
- plugin/hooks/hooks.json - Register skill in plugin hooks if needed

## Acceptance Criteria
- [ ] /cat:verify-implementation skill exists and is invocable (renamed from audit-plan)
- [ ] Skill can be invoked automatically by /cat:work with JSON arguments
- [ ] Skill can also be invoked manually from worktree or with issue ID
- [ ] Reads PLAN.md acceptance criteria and file change specifications
- [ ] Spawns subagents to verify each criterion against actual codebase state
- [ ] Checks: file existence, content matches plan, deletions performed, verification commands pass
- [ ] Produces structured report with status (Done/Partial/Missing) per criterion with evidence
- [ ] Does NOT fix issues - only reports findings
- [ ] work-with-issue skill includes new Step 4 (Verify Implementation) between execute and review
- [ ] Banner patterns updated to show 5 phases instead of 4
- [ ] Functionality works end-to-end
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Rename plugin/skills/audit-plan to plugin/skills/verify-implementation
   - Use: git mv plugin/skills/audit-plan plugin/skills/verify-implementation
   - Update SKILL.md to reference verify-implementation instead of audit-plan
   - Update content.md title and all skill name references
2. **Step 2:** Update verify-implementation/content.md for /cat:work integration
   - Add Arguments Format section showing JSON structure from /cat:work
   - Update locate_issue step to check for JSON arguments first, then worktree config, then plain issue ID
   - Update "When to Use" section to mention automatic invocation by /cat:work
   - Update "Purpose" to clarify it's a /cat:work phase but also manually invocable
   - Update example usage to show automatic invocation as primary use case
3. **Step 3:** Add verify-implementation phase to work-with-issue/content.md
   - Insert new Step 4 (Verify Implementation) after "Handle Execution Result" (line 260)
   - Renumber old Step 4 → Step 5, Step 5 → Step 6, Step 6 → Step 7, Step 7 → Step 8, Step 8 → Step 9
   - Update all step references throughout the file
   - Update banner patterns to show 5 phases (Preparing, Executing, Verifying, Reviewing, Merging)
   - Update architecture description to list 4 phases: Execute, Verify, Review, Merge
   - New Step 4 invokes verify-implementation skill with JSON arguments
   - Handle verification result: spawn fix subagent for Missing criteria (max 2 iterations)
   - Skip verification if VERIFY == "none"
4. **Step 4:** Update PLAN.md acceptance criteria and file list
   - Add plugin/skills/work-with-issue/content.md to Files to Modify
   - Update acceptance criteria to reflect rename and integration
5. **Step 5:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] Skill directory renamed to verify-implementation
- [ ] All references updated from audit-plan to verify-implementation
- [ ] work-with-issue includes new verification phase
- [ ] Step numbering correct throughout work-with-issue
- [ ] Banner patterns show 5 phases
- [ ] All existing tests pass with no regressions