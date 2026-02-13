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
- plugin/skills/audit-plan/SKILL.md - New skill definition for /cat:audit-plan
- plugin/hooks/hooks.json - Register skill in plugin hooks if needed

## Acceptance Criteria
- [ ] /cat:audit-plan skill exists and is invocable
- [ ] Reads PLAN.md acceptance criteria and file change specifications
- [ ] Spawns subagents to verify each criterion against actual codebase state
- [ ] Checks: file existence, content matches plan, deletions performed, verification commands pass
- [ ] Produces structured report with status (Done/Partial/Missing) per criterion with evidence
- [ ] Does NOT fix issues - only reports findings
- [ ] Functionality works end-to-end
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Create plugin/skills/audit-plan/SKILL.md with the audit skill definition
   - Files: plugin/skills/audit-plan/SKILL.md
   - Skill reads the current issue PLAN.md (from worktree context)
   - Extracts acceptance criteria and file change specifications
   - Spawns verification subagents (using Task tool) to check each criterion
   - Subagents use Read, Glob, Grep, Bash tools to verify changes
   - Collects results into structured report
   - Outputs report with per-criterion status: Done, Partial, Missing
   - Each status includes evidence (file path, line content, command output)
2. **Step 2:** Update plugin hooks registration if needed
   - Files: plugin/hooks/hooks.json (only if skill registration is required)
3. **Step 3:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] Skill file exists at plugin/skills/audit-plan/SKILL.md
- [ ] Skill is listed in /cat:help output
- [ ] All existing tests pass with no regressions