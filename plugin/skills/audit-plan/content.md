# /cat:audit-plan

Post-execution audit skill that verifies all acceptance criteria and file changes from PLAN.md were implemented.

## Purpose

After /cat:work completes an issue, this skill systematically verifies that:
- All acceptance criteria from PLAN.md were satisfied
- All planned file changes were implemented
- No planned items were skipped or partially implemented

This is a read-only verification skill - it reports findings but does NOT fix issues.

## Usage

```
/cat:audit-plan [issue-id]
```

If no issue-id is provided, the skill detects the current issue from:
1. The worktree path (if in a worktree)
2. The current branch name (if branch matches issue pattern)

**RESTRICTION:** This skill can ONLY be invoked by the main agent, not by subagents.

## Execution Steps

### Step 1: Detect Issue Context

If issue-id argument is provided, use it. Otherwise detect from context:
- Check if current directory is in `.claude/cat/worktrees/[issue-id]/`
- If not, check if current branch name matches issue pattern (e.g., `2.1-issue-name`)
- If detection fails, report error and stop

### Step 2: Parse PLAN.md

Read the PLAN.md file from the issue directory:
- Extract all acceptance criteria (lines with `- [ ]` checkboxes)
- Extract "Files to Modify" section (list of files planned to change)
- Extract execution steps for context

Store parsed data for verification in subsequent steps.

### Step 3: Verify File Changes

For each file listed in "Files to Modify" section of PLAN.md, spawn a subagent to verify:
- Does the file exist? (for new files or modifications)
- Was the file deleted? (for planned deletions)
- Does the content match the planned changes described in execution steps?

Each subagent returns:
- `status`: DONE, PARTIAL, or MISSING
- `evidence`: Brief description of what was found (file path, key lines, etc.)
- `issues`: List of problems (e.g., "File not found", "Missing expected function")

### Step 4: Verify Acceptance Criteria

For each acceptance criterion from PLAN.md, spawn a subagent to verify:
- Read the criterion description
- Search the codebase for evidence the criterion was satisfied
- Run verification commands if the criterion specifies them (e.g., "tests pass")

Each subagent returns:
- `status`: DONE, PARTIAL, or MISSING
- `evidence`: What was found (test output, file content, command result)
- `issues`: List of problems if not fully satisfied

### Step 5: Collect Results

Aggregate all verification results into a JSON structure:

```json
{
  "issue_id": "2.1-example",
  "plan_path": ".claude/cat/issues/v2/v2.1/example/PLAN.md",
  "criteria_results": [
    {
      "criterion": "Skill exists and is invocable",
      "status": "DONE",
      "evidence": "Found at plugin/skills/example/SKILL.md",
      "issues": []
    }
  ],
  "file_results": [
    {
      "file": "plugin/skills/example/SKILL.md",
      "status": "DONE",
      "evidence": "File created with correct frontmatter",
      "issues": []
    }
  ]
}
```

### Step 6: Render Report

Pipe the collected JSON to the Java report renderer:

```bash
echo '<json>' | java -cp "${CLAUDE_PROJECT_DIR}/hooks/target/cat-hooks-2.1.jar" \
  io.github.cowwoc.cat.hooks.skills.GetAuditPlanOutput
```

Output the rendered report verbatim - do NOT summarize or interpret it.

## Status Values

- **DONE**: Criterion fully satisfied, no issues found
- **PARTIAL**: Criterion partially satisfied, some issues remain
- **MISSING**: Criterion not satisfied, no evidence found

## Report Format

The rendered report includes:
- Overall status (COMPLETE, PARTIAL, INCOMPLETE)
- Summary counts (done, partial, missing)
- Per-criterion details with evidence and issues
- Per-file details with evidence and issues
- Actions required section (if any checks failed)

## Notes

- This skill is read-only - it verifies but does not modify files
- Subagents should use Read, Glob, Grep, and Bash tools to gather evidence
- All verification happens against the CURRENT codebase state (after implementation)
- The skill assumes the worktree still exists and PLAN.md is accessible
