# Plan: delegate-stakeholder-review-to-subagent

## Goal
Minimize parent agent context consumed by stakeholder review (Phase 3c) by trimming reviewer output, using file-based
concern storage, and delegating fix planning/implementation to subagents.

## Satisfies
None - performance optimization

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Reviewer subagents must be spawned by the main agent (no nesting). Their results unavoidably land in
  the main agent's context. The design must minimize what reviewers return inline while preserving enough detail for
  fix subagents to act on.
- **Mitigation:** Each reviewer writes comprehensive concern details to a file and returns only a brief summary inline.
  The main agent never reads the detail files — it passes file references to planning and implementation subagents.

## Design Principles
1. **Trust subagents.** If a reviewer claims it reviewed files, trust it. No evidence validation fields.
2. **Return only what the parent needs.** Severity, location, brief explanation, brief recommendation, detail file path.
3. **File-based handoff.** Comprehensive concern analysis goes to files. The main agent delegates file references to
   fix subagents without reading the files itself.
4. **Verify configuration controls re-review scope:**
   - `verify: "all"` → re-run all stakeholders after fixes
   - `verify: "changed"` → re-run only stakeholders that had concerns
   - `verify: "none"` → skip review entirely

## Files to Modify
- `plugin/skills/work-with-issue/SKILL.md` - Restructure Phase 3c for file-based concern handoff and fix delegation
- `plugin/agents/stakeholder-*.md` - Trim reviewer output; write detail files instead of returning inline
- `plugin/skills/stakeholder-review/SKILL.md` - Update orchestration to use new compact return format; remove evidence
  validation logic
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStakeholderConcernBox.java` - Update to render from compact
  concern format (severity, location, explanation, recommendation); remove handling of removed fields
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStakeholderReviewBox.java` - Update to work with compact
  reviewer results
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStakeholderSelectionBox.java` - Review for compatibility
  with new format
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetStakeholderOutputTest.java` - Update tests for new compact
  schema

## Post-conditions
- [ ] Each reviewer subagent writes comprehensive concern details to a file in the worktree
- [ ] Each reviewer returns only: approval status, and per-concern: severity, location, brief explanation, brief
  recommendation, detail file path
- [ ] No evidence validation fields in reviewer return values (files_reviewed, diff_summary removed)
- [ ] No reviewer-specific metadata in return values (test_coverage_assessment, requirements_checked removed)
- [ ] Main agent never reads concern detail files
- [ ] For issues the user agrees to fix (or auto-fix configured): main agent delegates to a planning subagent to revise
  PLAN.md, then delegates the revised plan to an implementation subagent
- [ ] Fix subagents receive concern detail file paths and read them directly
- [ ] Re-review scope respects verify configuration (all/changed/none)
- [ ] Parent agent context consumed by Phase 3c is reduced by at least 50% compared to current baseline

## Execution Steps
1. **Measure current baseline**
   - Files: session transcripts
   - Record average parent-agent input tokens consumed by Phase 3c across 3+ sessions

2. **Update reviewer agent definitions to write detail files**
   - Files: `plugin/agents/stakeholder-*.md`
   - Each reviewer writes comprehensive analysis to `<worktree>/.claude/cat/review/<stakeholder>-concerns.json`
   - File contains: full explanation, code snippets, attack vectors, recommendations with examples
   - Reviewer returns compact JSON inline:
     ```json
     {
       "stakeholder": "security",
       "approval": "CONCERNS",
       "concerns": [
         {
           "severity": "HIGH",
           "location": "src/UserDao.java:45",
           "explanation": "Unsanitized user input passed directly to SQL query",
           "recommendation": "Use parameterized queries instead of string concatenation",
           "detail_file": ".claude/cat/review/security-concerns.json"
         }
       ]
     }
     ```
   - Remove: `files_reviewed`, `diff_summary`, `test_coverage_assessment`, `requirements_checked`,
     `coverage_summary`, `action_required`

3. **Update stakeholder-review skill orchestration**
   - Files: `plugin/skills/stakeholder-review/SKILL.md`
   - Remove evidence validation logic (trust subagents)
   - Return compact aggregated result to parent:
     ```json
     {
       "review_status": "CONCERNS",
       "concerns": [
         {
           "severity": "HIGH",
           "stakeholder": "security",
           "location": "src/UserDao.java:45",
           "explanation": "Unsanitized user input passed directly to SQL query",
           "recommendation": "Use parameterized queries",
           "detail_file": ".claude/cat/review/security-concerns.json"
         }
       ]
     }
     ```

4. **Update Java display handlers for compact concern format**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStakeholderConcernBox.java`,
     `GetStakeholderReviewBox.java`, `GetStakeholderSelectionBox.java`
   - Update `GetStakeholderConcernBox` to render from compact fields: severity, location, explanation, recommendation
   - Remove handling of fields that no longer exist (attack_vector, current_complexity, userImpact, customerImpact,
     legal_risk, files_reviewed, diff_summary)
   - Update `GetStakeholderReviewBox` to work with compact per-reviewer status (no evidence fields)
   - Update `GetStakeholderOutputTest` to validate rendering with new compact schema

5. **Restructure Phase 3c fix loop in work-with-issue**
   - Files: `plugin/skills/work-with-issue/SKILL.md`
   - Main agent spawns reviewer subagents in parallel, receives compact results
   - For concerns at/above auto-fix threshold:
     a. Spawn **planning subagent** with concern detail file paths → revises PLAN.md with fix steps
     b. Spawn **implementation subagent** with revised PLAN.md → implements fixes, reads detail files directly
   - Re-review after fixes based on verify config:
     - `all`: re-spawn all reviewer subagents
     - `changed`: re-spawn only stakeholders that had concerns
     - `none`: skip (should not reach this point since review is skipped entirely)
   - Loop up to 3 iterations
   - At approval gate: show brief concern summaries (severity + explanation + location), not full details

6. **Run tests**
   - Verify stakeholder review tests pass with new compact format
   - Verify fix loop still functions with file-based handoff

7. **Measure post-change context usage**
   - Compare parent-agent token consumption for Phase 3c against baseline

## Post-conditions
- [ ] Phase 3c parent-agent context usage reduced by >= 50%
- [ ] All stakeholder perspectives still consulted (no reviewer dropped)
- [ ] Auto-fix loop functions via planning subagent → implementation subagent delegation
- [ ] Concern detail files exist in worktree after review
- [ ] E2E: A complete /cat:work run succeeds with the new review delegation
