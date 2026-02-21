# Plan: require-reviewer-file-evidence

## Goal
Require stakeholder reviewers to produce evidence that they analyzed the diff against the base branch and read all
modified files before reviewing. This addresses PATTERN-018 (stakeholder review quality) where reviewers miss cross-file
duplication, validation gaps, and code quality issues because they skip files or read from wrong paths.

## Satisfies
None - retrospective action item A021

## Background

The current stakeholder review system (plugin/skills/stakeholder-review/SKILL.md) uses a pre-fetch model: file content
is included directly in the reviewer subagent prompt. However, PATTERN-018 shows that reviewers still miss issues
because:
- They do not systematically analyze the diff to understand what changed
- They skip pre-fetched file content without analyzing it
- Review output has no evidence of what was actually examined

The fix adds a mandatory "Evidence" section to each reviewer's JSON output and adds instructions requiring diff
analysis as the first review step.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Reviewers may still fabricate evidence. Increased prompt size.
- **Mitigation:** Evidence format is machine-parseable for future validation hooks.

## Files to Modify

### Stakeholder Agent Definitions (10 files)
All files in `plugin/agents/stakeholder-*.md`:
- `stakeholder-architecture.md`
- `stakeholder-business.md`
- `stakeholder-deployment.md`
- `stakeholder-design.md`
- `stakeholder-legal.md`
- `stakeholder-performance.md`
- `stakeholder-requirements.md`
- `stakeholder-security.md`
- `stakeholder-testing.md`
- `stakeholder-ux.md`

For each file, add two sections:

1. **Mandatory First Step** - Add before the review criteria section:
   ```
   ## Mandatory Pre-Review Steps

   Before analyzing any code, you MUST complete these steps in order:

   1. **Analyze the diff**: Review the git diff summary provided in "What Changed" section. List every file that was
      modified, added, or deleted.
   2. **Read all modified files**: For each modified file listed in the diff, read the full file content provided in
      the "Files to Review" section. Do not skip any file.
   3. **Note cross-file relationships**: Identify any patterns, interfaces, or dependencies that span multiple
      modified files.

   These steps must be completed before forming any review opinions.
   ```

2. **Evidence in Output Format** - Add `files_reviewed` field to the JSON output schema:
   ```json
   {
     "stakeholder": "...",
     "approval": "...",
     "files_reviewed": [
       {
         "path": "relative/path/to/file.ext",
         "action": "modified|added|deleted",
         "analyzed": true
       }
     ],
     "diff_summary": "Brief description of what changed across all files",
     "concerns": [...],
     "summary": "..."
   }
   ```

### Stakeholder Review Orchestrator
- `plugin/skills/stakeholder-review/SKILL.md`

In Step 4 (collect_reviews), add validation that each reviewer's JSON output includes:
- A non-empty `files_reviewed` array
- A `files_reviewed` count that matches the number of changed files from the diff
- A non-empty `diff_summary` field

If validation fails, flag the review as incomplete (treat as CONCERNS with a note about missing evidence).

## Acceptance Criteria
- [ ] All 10 stakeholder agent files include "Mandatory Pre-Review Steps" section
- [ ] All 10 stakeholder agent files include `files_reviewed` and `diff_summary` in JSON output schema
- [ ] Stakeholder review SKILL.md validates evidence fields in collected reviews
- [ ] Missing evidence triggers CONCERNS status with explanatory note
- [ ] All existing tests pass
- [ ] No regressions in existing review workflow
- [ ] E2E: Run stakeholder review on a real change and verify output includes files_reviewed with correct count

## Execution Steps
1. **Step 1:** Add "Mandatory Pre-Review Steps" section to all 10 stakeholder agent files
   - Files: plugin/agents/stakeholder-*.md
   - Insert section before the existing review criteria / concerns section
   - Use identical wording across all agents for consistency
2. **Step 2:** Update JSON output schema in all 10 stakeholder agent files
   - Add `files_reviewed` array field and `diff_summary` string field
   - Place after `approval` field and before `concerns` field
3. **Step 3:** Update stakeholder-review SKILL.md Step 4 (collect_reviews)
   - Add validation logic for `files_reviewed` and `diff_summary` fields
   - If fields missing or empty: set review status to CONCERNS with note
   - If file count mismatch: add warning to review summary
4. **Step 4:** Run tests to verify no regressions
   - Command: mvn -f client/pom.xml test

## Success Criteria
- [ ] All 10 agent files contain the pre-review steps section
- [ ] All 10 agent files define files_reviewed in their output schema
- [ ] SKILL.md validates evidence and flags missing evidence as CONCERNS
- [ ] All tests pass (exit code 0)