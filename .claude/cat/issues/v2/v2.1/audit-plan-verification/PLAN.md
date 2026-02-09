# Plan: audit-plan-verification

## Goal
Clean-room implementation of a post-execution audit skill that systematically verifies all planned changes from PLAN.md
were actually implemented in the codebase after /cat:work execution completes.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Skill must not modify files, only report. Must handle varied PLAN.md formats.
- **Mitigation:** Read-only verification with structured reporting. Test against multiple PLAN.md formats.

## Files to Modify
- plugin/skills/audit-plan/SKILL.md - Skill definition with frontmatter and load-skill.sh invocation
- plugin/skills/audit-plan/content.md - Skill content (6-step verification process)
- plugin/skills/audit-plan/handler.sh - Handler script that generates SCRIPT OUTPUT via Java
- hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAuditPlanOutput.java - Report renderer
- hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/RunGetAuditPlanOutput.java - CLI runner
- hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetAuditPlanOutputTest.java - Unit tests

## Acceptance Criteria
- [ ] /cat:audit-plan skill exists and is invocable
- [ ] Reads PLAN.md acceptance criteria and file change specifications
- [ ] Spawns subagents to verify each criterion against actual codebase state
- [ ] Checks: file existence, content matches plan, deletions performed, verification commands pass
- [ ] Produces structured report with status (Done/Partial/Missing) per criterion with evidence
- [ ] Does NOT fix issues - only reports findings
- [ ] Report rendering implemented in Java (GetAuditPlanOutput), invoked via handler.sh
- [ ] Java class accepts JSON from stdin, outputs formatted markdown report
- [ ] Unit tests for report rendering (various status combinations, edge cases)
- [ ] Functionality works end-to-end
- [ ] Tests passing (mvn -f hooks/pom.xml verify)
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Create GetAuditPlanOutput.java - Java report renderer
   - Files: hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAuditPlanOutput.java
   - Accepts JSON input with issue_id, plan_path, criteria_results, file_results
   - Each result has: criterion/file, status (DONE/PARTIAL/MISSING), evidence, issues
   - Renders formatted markdown report with summary, per-criterion results, file changes, actions required
   - Follows existing Get*Output.java patterns (JvmScope constructor, getOutput method)
   - Uses JsonMapper from scope for JSON parsing
2. **Step 2:** Create RunGetAuditPlanOutput.java - CLI entry point
   - Files: hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/RunGetAuditPlanOutput.java
   - Reads JSON from stdin, passes to GetAuditPlanOutput, prints result to stdout
   - Follows RunGetStatusOutput pattern
3. **Step 3:** Create unit tests for GetAuditPlanOutput
   - Files: hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetAuditPlanOutputTest.java
   - Test cases: all DONE (COMPLETE), mixed statuses (PARTIAL), all MISSING (INCOMPLETE), empty input
   - Verify report contains correct status icons, counts, evidence, actions required section
4. **Step 4:** Create handler.sh for skill preprocessing
   - Files: plugin/skills/audit-plan/handler.sh
   - Outputs "SCRIPT OUTPUT AUDIT REPORT:" marker
   - Invokes the Java binary via hooks/bin/ to render the report
   - Note: handler.sh runs on skill load, but audit-plan collects data at runtime
   - Handler provides the SCRIPT OUTPUT marker; the skill collects JSON and pipes to the Java binary
5. **Step 5:** Create plugin/skills/audit-plan/SKILL.md and content.md
   - Files: plugin/skills/audit-plan/SKILL.md, plugin/skills/audit-plan/content.md
   - SKILL.md: frontmatter + load-skill.sh invocation
   - content.md: 6-step process (detect issue, parse plan, verify files, verify criteria, collect, report)
   - Step 6 (report) pipes collected JSON to the Java binary for rendering
   - Main-agent-only invocation restriction (spawns subagents)
   - Read-only operation guarantee
6. **Step 6:** Build and verify
   - Run: mvn -f hooks/pom.xml verify
   - All tests must pass including new GetAuditPlanOutputTest

## Success Criteria
- [ ] Skill file exists at plugin/skills/audit-plan/SKILL.md
- [ ] Java renderer exists at hooks/src/main/java/.../GetAuditPlanOutput.java
- [ ] Unit tests pass for report rendering
- [ ] mvn -f hooks/pom.xml verify passes
- [ ] No regressions to existing skills