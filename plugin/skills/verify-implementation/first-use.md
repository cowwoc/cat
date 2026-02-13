# Skill: verify-implementation

Post-execution verification that systematically checks all planned changes from PLAN.md were actually implemented in the codebase.

## Purpose

Verify that the implementation matches what was planned in the issue's PLAN.md file. This is a read-only audit that reports findings without making changes. The skill is invoked automatically by `/cat:work` between the execute and review phases.

## When to Use

- Invoked by `/cat:work` between execute and review phases to verify PLAN.md acceptance criteria before stakeholder quality review

## Arguments Format

This skill receives JSON arguments with execution context from `/cat:work`:

```json
{
  "issue_id": "2.1-issue-name",
  "issue_path": "/path/to/issue",
  "worktree_path": "/path/to/worktree",
  "execution_result": {
    "commits": [{"hash": "abc123", "message": "...", "type": "feature"}],
    "files_changed": 5
  }
}
```

## Output

Structured report showing per-criterion verification status:
- **Done**: Criterion fully satisfied with evidence
- **Partial**: Criterion partially satisfied (details provided)
- **Missing**: Criterion not satisfied

Each status includes evidence (file paths, line content, command output).

## Process

<step name="locate_issue">

**Locate the issue being audited:**

Parse JSON arguments provided by `/cat:work` to extract issue context.

```bash
# Parse JSON arguments from /cat:work
ISSUE_ID=$(echo "${ARGUMENTS}" | python3 -c "import sys, json; print(json.load(sys.stdin).get('issue_id', ''))")
ISSUE_PATH=$(echo "${ARGUMENTS}" | python3 -c "import sys, json; print(json.load(sys.stdin).get('issue_path', ''))")
WORKTREE_PATH=$(echo "${ARGUMENTS}" | python3 -c "import sys, json; print(json.load(sys.stdin).get('worktree_path', ''))")

if [[ -z "$ISSUE_ID" ]] || [[ -z "$ISSUE_PATH" ]]; then
  echo "FAIL: Missing required JSON arguments (issue_id, issue_path)"
  exit 1
fi

if [[ ! -f "${ISSUE_PATH}/PLAN.md" ]]; then
  echo "FAIL: PLAN.md not found at ${ISSUE_PATH}"
  exit 1
fi
```

Output:
```
◆ Auditing issue: {ISSUE_ID}
  Path: {ISSUE_PATH}
```

</step>

<step name="extract_criteria">

**Extract acceptance criteria and file specifications from PLAN.md:**

Parse PLAN.md to identify what needs verification:

1. **Acceptance Criteria**: Lines under `## Acceptance Criteria` that start with `- [ ]`
2. **File Changes**: Lines under `## Files to Modify` or mentioned in Execution Steps

```python
import re

def extract_acceptance_criteria(plan_content):
    """Extract checklist items from Acceptance Criteria section."""
    criteria = []
    in_section = False

    for line in plan_content.split('\n'):
        if line.startswith('## Acceptance Criteria'):
            in_section = True
            continue
        if in_section and line.startswith('##'):
            break
        if in_section:
            # Handle indentation variations and both checked/unchecked items
            stripped = line.strip()
            if stripped.startswith('- [ ]'):
                criterion = stripped[6:].strip()  # Remove "- [ ] "
                criteria.append(criterion)
            elif stripped.startswith('- [x]'):
                criterion = stripped[6:].strip()  # Remove "- [x] "
                criteria.append(criterion)

    return criteria

def extract_file_specs(plan_content):
    """Extract file change specifications from PLAN.md."""
    files_to_modify = []
    files_to_delete = []

    # Look in "Files to Modify" section
    in_section = False
    for line in plan_content.split('\n'):
        if line.startswith('## Files to Modify'):
            in_section = True
            continue
        if in_section and line.startswith('##'):
            break
        if in_section and line.strip().startswith('-'):
            # Extract file path (everything before first " - ")
            match = re.match(r'^\s*-\s+([^\s]+)', line)
            if match:
                item = match.group(1)
                # Filter out non-file items (must contain '/' or have file extension)
                if '/' in item or '.' in item:
                    files_to_modify.append(item)

    # Look in "Files to Delete" section
    in_section = False
    for line in plan_content.split('\n'):
        if line.startswith('## Files to Delete'):
            in_section = True
            continue
        if in_section and line.startswith('##'):
            break
        if in_section and line.strip().startswith('-'):
            # Extract file path
            match = re.match(r'^\s*-\s+([^\s]+)', line)
            if match:
                item = match.group(1)
                # Filter out non-file items (must contain '/' or have file extension)
                if '/' in item or '.' in item:
                    files_to_delete.append(item)

    # Also scan Execution Steps for file mentions and deletion keywords
    # Best-effort heuristic scanning - may not catch all file references
    in_steps = False
    for line in plan_content.split('\n'):
        if line.startswith('## Execution Steps'):
            in_steps = True
            continue
        if in_steps and line.startswith('##'):
            break
        if in_steps:
            # Primary: Look for "Files: path/to/file.ext" prefix
            match = re.search(r'Files?:\s+([^\s,]+(?:\.[a-z]+)?)', line, re.IGNORECASE)
            if match:
                file_path = match.group(1)
                if file_path not in files_to_modify:
                    files_to_modify.append(file_path)

            # Secondary: Scan for file-path-like patterns (word/word/file.ext)
            # Matches paths with at least one '/' and a file extension
            path_matches = re.findall(r'\b([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+\.[a-z]+)\b', line)
            for file_path in path_matches:
                if file_path not in files_to_modify:
                    files_to_modify.append(file_path)

            # Look for deletion keywords with file paths
            if re.search(r'\b(delete|remove|rm)\b', line, re.IGNORECASE):
                # Try to extract file path mentioned in deletion context
                match = re.search(r'([a-zA-Z0-9_/.-]+\.[a-z]+)', line)
                if match:
                    file_path = match.group(1)
                    if file_path not in files_to_delete:
                        files_to_delete.append(file_path)

    return {'modify': files_to_modify, 'delete': files_to_delete}
```

Output:
```
◆ Found {N} acceptance criteria and {M} file specifications
```

</step>

<step name="spawn_verifiers">

**Spawn verification subagents for each criterion:**

For each acceptance criterion, spawn a verification subagent using the Task tool.

**Subagent prompt structure:**

```
You are a verification agent auditing implementation compliance with a planned acceptance criterion.

## Acceptance Criterion
{criterion_text}

## Task
Verify whether this criterion is satisfied in the codebase.

Use these tools to investigate:
- Read: Check file contents
- Glob: Find files by pattern
- Grep: Search for specific content
- Bash: Run verification commands (git log, test commands, etc.)

## Evidence Requirements
For each criterion, provide:
1. Status: Done|Partial|Missing
2. Evidence: File paths, line numbers, command outputs
3. Notes: Any discrepancies or concerns

If a criterion cannot be definitively verified, set status to Missing with an explanation of why
verification was not possible. Do not guess or assume.

## File Context
These files were specified in PLAN.md:
{file_list}

## Response Format
Return JSON only:
{
  "criterion": "criterion text",
  "status": "Done|Partial|Missing",
  "evidence": [
    {"type": "file_exists|content_match|command_output", "detail": "..."}
  ],
  "notes": "Any additional observations"
}

Be thorough but efficient. Check actual implementation, not just file existence.
```

**Parallel spawning:**
Spawn all verifiers in parallel to reduce total execution time. Collect results as they complete.

```bash
# Spawn each verifier subagent
for criterion in "${CRITERIA[@]}"; do
  # Use Task tool with subagent_type and model specified
  # Pass criterion and file context
  # Store task ID for result collection
done
```

**Example Task tool invocation:**

```json
{
  "subagent_type": "general-purpose",
  "model": "haiku",
  "prompt": "You are a verification agent auditing implementation compliance with a planned acceptance criterion.\n\n## Acceptance Criterion\nAll modified files follow Python 3.10+ syntax\n\n## Task\nVerify whether this criterion is satisfied in the codebase.\n\nUse these tools to investigate:\n- Read: Check file contents\n- Glob: Find files by pattern\n- Grep: Search for specific content\n- Bash: Run verification commands (git log, test commands, etc.)\n\n## Evidence Requirements\nFor each criterion, provide:\n1. Status: Done|Partial|Missing\n2. Evidence: File paths, line numbers, command outputs\n3. Notes: Any discrepancies or concerns\n\nIf a criterion cannot be definitively verified, set status to Missing with an explanation of why verification was not possible. Do not guess or assume.\n\n## File Context\nThese files were specified in PLAN.md:\n- plugin/hooks/verify_plan.py\n- plugin/skills/verify-implementation/first-use.md\n\n## Response Format\nReturn JSON only:\n{\n  \"criterion\": \"criterion text\",\n  \"status\": \"Done|Partial|Missing\",\n  \"evidence\": [\n    {\"type\": \"file_exists|content_match|command_output\", \"detail\": \"...\"}\n  ],\n  \"notes\": \"Any additional observations\"\n}\n\nBe thorough but efficient. Check actual implementation, not just file existence."
}
```

</step>

<step name="verify_files">

**Verify file specifications independently:**

For each file in the file specifications list, verify:

**Files to modify:**
1. File exists
2. File was modified in relevant commits
3. File content aligns with plan description

**Files to delete:**
1. File no longer exists in the codebase
2. File was deleted in relevant commits

This is done separately from criterion verification for complete coverage.

```bash
# Verify files that should be modified
for file in "${FILE_SPECS_MODIFY[@]}"; do
  if [[ -f "$file" ]]; then
    # Check git history for this file
    if git log --oneline --follow -- "$file" | head -5; then
      FILE_STATUS="exists_and_modified"
    else
      FILE_STATUS="exists_not_modified"
    fi
  else
    FILE_STATUS="missing"
  fi

  # Record file verification result
  echo "File to modify: $file -> $FILE_STATUS"
done

# Verify files that should be deleted
for file in "${FILE_SPECS_DELETE[@]}"; do
  if [[ ! -e "$file" ]]; then
    # Verify it was actually deleted (not just never existed)
    if git log --all --oneline -- "$file" | head -5; then
      FILE_STATUS="deleted_confirmed"
    else
      FILE_STATUS="never_existed"
    fi
  else
    FILE_STATUS="still_exists"
  fi

  # Record deletion verification result
  echo "File to delete: $file -> $FILE_STATUS"
done
```

</step>

<step name="collect_results">

**Collect verification results from subagents:**

Wait for all verification subagents to complete and collect their JSON responses.

Parse each response and build aggregated status.

```python
def aggregate_results(verifier_results, file_modify_results, file_delete_results):
    """Aggregate all verification results."""
    done_count = 0
    partial_count = 0
    missing_count = 0
    file_done_count = 0
    file_missing_count = 0

    all_results = []

    # Process criterion verifications
    for result in verifier_results:
        if result['status'] == 'Done':
            done_count += 1
        elif result['status'] == 'Partial':
            partial_count += 1
        elif result['status'] == 'Missing':
            missing_count += 1

        all_results.append({
            'type': 'criterion',
            'criterion': result['criterion'],
            'status': result['status'],
            'evidence': result['evidence'],
            'notes': result.get('notes', '')
        })

    # Process file modification verifications
    for file_path, status in file_modify_results.items():
        if status == 'exists_and_modified':
            file_status = 'Done'
            file_done_count += 1
        else:
            file_status = 'Missing'
            file_missing_count += 1

        all_results.append({
            'type': 'file_modify',
            'file': file_path,
            'status': file_status,
            'evidence': [{'type': 'file_check', 'detail': status}]
        })

    # Process file deletion verifications
    for file_path, status in file_delete_results.items():
        if status == 'deleted_confirmed':
            file_status = 'Done'
            file_done_count += 1
        elif status == 'never_existed':
            file_status = 'Missing'
            file_missing_count += 1
        else:  # still_exists
            file_status = 'Missing'
            file_missing_count += 1

        all_results.append({
            'type': 'file_delete',
            'file': file_path,
            'status': file_status,
            'evidence': [{'type': 'deletion_check', 'detail': status}]
        })

    return {
        'total_criteria': len(verifier_results),
        'done': done_count,
        'partial': partial_count,
        'missing': missing_count,
        'file_done': file_done_count,
        'file_missing': file_missing_count,
        'results': all_results
    }
```

</step>

<step name="report">

**Generate structured audit report:**

Output a comprehensive report showing verification status for all criteria and files.

```
╔══════════════════════════════════════════════════════════════════════════════╗
║ AUDIT REPORT: {ISSUE_ID}                                                     ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Summary                                                                      ║
║   Acceptance Criteria:                                                      ║
║     Total:        {total}                                                   ║
║     ✓ Done:       {done}                                                    ║
║     ◐ Partial:    {partial}                                                 ║
║     ✗ Missing:    {missing}                                                 ║
║   File Verifications:                                                       ║
║     ✓ Done:       {file_done}                                               ║
║     ✗ Missing:    {file_missing}                                            ║
╚══════════════════════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

ACCEPTANCE CRITERIA VERIFICATION

{for each criterion}
  [{status_icon}] {criterion_text}

      Evidence:
      {for each evidence item}
        • {evidence.type}: {evidence.detail}
      {end}

      {if notes}
      Notes: {notes}
      {end}
{end}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FILE SPECIFICATIONS VERIFICATION

Files to Modify:
{for each file in modify list}
  [{status_icon}] {file_path}
      {file_status_detail}
{end}

{if delete list not empty}
Files to Delete:
{for each file in delete list}
  [{status_icon}] {file_path}
      {deletion_status_detail}
{end}
{endif}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OVERALL ASSESSMENT

{if missing_count > 0}
⚠ INCOMPLETE: {missing_count} criteria not satisfied
  Review missing items and re-run implementation if needed.
{elif partial_count > 0}
⚠ PARTIAL: {partial_count} criteria partially satisfied
  Review partial items for completeness.
{else}
✓ COMPLETE: All acceptance criteria satisfied
{endif}
```

**Status Icons:**
- `✓` - Done (fully satisfied)
- `◐` - Partial (partially satisfied)
- `✗` - Missing (not satisfied)

</step>

## Notes

**Read-only operation:** This skill never modifies files. It only reads and reports.

**Subagent usage:** Verification subagents use Read, Glob, Grep, and Bash tools to investigate the codebase. Their internal tool calls are invisible to the user.

**Performance:** Spawning verifiers in parallel reduces total execution time. For N criteria, execution time is roughly constant (limited by longest verification).

**No auto-fix:** This skill reports findings only. If issues are found, the user must decide whether to re-run implementation or accept the current state.

## Related Skills

- `/cat:stakeholder-review` - Quality review by domain experts (design, testing, security, etc.)
- `/cat:status` - Progress tracking and issue status monitoring

## Limitations

- **Heuristic-based verification:** Automated checks parse PLAN.md structure and verify file operations, but cannot replace human review for semantic correctness.
- **Parse-dependent:** Only verifies what can be parsed from PLAN.md. If acceptance criteria or file specifications are not explicitly documented, they will not be verified.
- **No semantic understanding:** Verifies that code exists and was modified, but cannot judge whether implementation correctly satisfies the intent behind acceptance criteria.
- **Limited to documented plans:** Cannot verify undocumented requirements or implicit expectations.

## Example Usage

Invoked automatically by `/cat:work`:
```
# Invoked automatically between execute and review phases
# No manual invocation needed - /cat:work provides JSON arguments
```
