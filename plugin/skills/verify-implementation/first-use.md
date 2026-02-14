<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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

Use the Java CLI tool to parse PLAN.md and extract acceptance criteria, file specs, and grouped criteria.

```bash
HOOKS_BIN="${WORKTREE_PATH}/hooks/target/jlink/bin"
if [[ ! -x "$HOOKS_BIN/verify-audit" ]]; then
  HOOKS_BIN="/workspace/hooks/target/jlink/bin"
fi

PARSED=$("$HOOKS_BIN/verify-audit" parse "${ISSUE_PATH}/PLAN.md")

# Extract data from JSON output
CRITERIA=$(echo "$PARSED" | python3 -c "import sys, json; data = json.load(sys.stdin); print('\n'.join(data['criteria']))")
FILE_SPECS_MODIFY=$(echo "$PARSED" | python3 -c "import sys, json; data = json.load(sys.stdin); print('\n'.join(data['file_specs']['modify']))")
FILE_SPECS_DELETE=$(echo "$PARSED" | python3 -c "import sys, json; data = json.load(sys.stdin); print('\n'.join(data['file_specs']['delete']))")
GROUPS=$(echo "$PARSED" | python3 -c "import sys, json; print(json.dumps(json.load(sys.stdin)['groups']))")

CRITERIA_COUNT=$(echo "$CRITERIA" | grep -c . || echo 0)
FILE_COUNT_MODIFY=$(echo "$FILE_SPECS_MODIFY" | grep -c . || echo 0)
FILE_COUNT_DELETE=$(echo "$FILE_SPECS_DELETE" | grep -c . || echo 0)
TOTAL_FILES=$((FILE_COUNT_MODIFY + FILE_COUNT_DELETE))

echo "◆ Found ${CRITERIA_COUNT} acceptance criteria and ${TOTAL_FILES} file specifications"
```

The Java tool extracts:
1. **Acceptance Criteria**: Lines under `## Acceptance Criteria` section that start with `- [ ]` or `- [x]`
2. **File Specifications**: Files listed under `## Files to Modify`, `## Files to Delete`, and mentioned in `## Execution Steps`
3. **Grouped Criteria**: Criteria grouped by their file dependencies to optimize verification

</step>

<step name="spawn_verifiers">

**Spawn verification subagents with optimized grouping:**

The Java tool has already grouped acceptance criteria by their file dependencies. Spawn one subagent per group to minimize redundant file reads.

```bash
# Iterate through groups and spawn subagents
SUBAGENT_DATA=$(echo "$GROUPS" | python3 -c "
import sys
import json

groups = json.load(sys.stdin)

for idx, group in enumerate(groups):
    files = group['files']
    criteria = group['criteria']

    # Build file list for prompt
    file_list = '\n'.join(f'- {f}' for f in files)

    # Build unified prompt (always returns JSON array for consistency)
    if len(criteria) == 1:
        criterion_section = f'## Acceptance Criterion\n{criteria[0]}'
    else:
        criterion_lines = '\n'.join(f'{i+1}. {c}' for i, c in enumerate(criteria))
        criterion_section = f'## Acceptance Criteria\n{criterion_lines}'

    format_section = '''## Response Format
Return JSON array with one entry per criterion (even if only one criterion):
[
  {
    \"criterion\": \"criterion text\",
    \"status\": \"Done|Partial|Missing\",
    \"evidence\": [
      {\"type\": \"file_exists|content_match|command_output\", \"detail\": \"...\"}
    ],
    \"notes\": \"Any additional observations\"
  }
]'''

    prompt = f'''You are a verification agent auditing implementation compliance with planned acceptance criteria.

{criterion_section}

## Task
Verify whether {'this criterion is' if len(criteria) == 1 else 'EACH criterion is'} satisfied in the codebase.

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

If a criterion cannot be definitively verified, set status to Missing with an explanation of why verification was not possible. Do not guess or assume.

## File Context
These files were specified in PLAN.md:
{file_list}

{format_section}

Be thorough but efficient. Check actual implementation, not just file existence.'''

    # Output Task tool invocation data
    print(f'SUBAGENT_GROUP_{idx}')
    print(json.dumps({'prompt': prompt, 'criteria_count': len(criteria)}))
")

# Store prompts for Task tool invocations
declare -a PROMPTS
while IFS= read -r line; do
  if [[ "$line" =~ ^SUBAGENT_GROUP_([0-9]+)$ ]]; then
    read -r data_line
    PROMPT=$(echo "$data_line" | python3 -c "import sys, json; print(json.load(sys.stdin)['prompt'])")
    PROMPTS+=("$PROMPT")

    echo "◆ Prepared verification subagent for group ${BASH_REMATCH[1]}"
  fi
done <<< "$SUBAGENT_DATA"

echo ""
echo "SPAWN_VERIFICATION_SUBAGENTS"
echo "GROUP_COUNT=${#PROMPTS[@]}"
for idx in "${!PROMPTS[@]}"; do
  echo "PROMPT_${idx}<<PROMPT_EOF"
  echo "${PROMPTS[$idx]}"
  echo "PROMPT_EOF"
done
```

After the bash script outputs the group prompts above, **immediately spawn verification subagents**:

1. For each PROMPT_N in the output, invoke the Task tool with:
   - `subagent_type: "general-purpose"`
   - `model: "haiku"` (verification is straightforward - check files against criteria)
   - `prompt: <the full prompt text between PROMPT_EOF markers>`

2. Store all returned task IDs for result collection in the next step.

3. Spawn all subagents in parallel (multiple Task tool calls in a single message). They will run concurrently and return results together.

Each subagent verifies all criteria in its group against the same file context, avoiding redundant reads.

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
declare -A FILE_MODIFY_RESULTS
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if [[ -f "$file" ]]; then
    if git log --oneline --follow -- "$file" | head -5 >/dev/null 2>&1; then
      FILE_MODIFY_RESULTS["$file"]="exists_and_modified"
    else
      FILE_MODIFY_RESULTS["$file"]="exists_not_modified"
    fi
  else
    FILE_MODIFY_RESULTS["$file"]="missing"
  fi
done <<< "$FILE_SPECS_MODIFY"

# Verify files that should be deleted
declare -A FILE_DELETE_RESULTS
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if [[ ! -e "$file" ]]; then
    if git log --all --oneline -- "$file" | head -5 >/dev/null 2>&1; then
      FILE_DELETE_RESULTS["$file"]="deleted_confirmed"
    else
      FILE_DELETE_RESULTS["$file"]="never_existed"
    fi
  else
    FILE_DELETE_RESULTS["$file"]="still_exists"
  fi
done <<< "$FILE_SPECS_DELETE"
```

</step>

<step name="collect_results">

**Collect verification results and generate report:**

Wait for all verification subagents to complete. Use TaskOutput to retrieve each subagent's JSON response.

Each subagent returns a JSON array of criterion objects (even for single-criterion groups):
```
[
  {"criterion": "...", "status": "Done|Partial|Missing", "evidence": [...], "notes": "..."}
]
```

Aggregate all criterion results into a single `criteria_results` array, then combine with file verification results and generate the report using the Java CLI tool.

```bash
# Prepare aggregated results JSON (criteria + file verification)
# The criteria_results array contains all criterion objects from all subagents
# The file_results contains FILE_MODIFY_RESULTS and FILE_DELETE_RESULTS from verify_files step

# Example structure:
# {
#   "criteria_results": [
#     {"criterion": "...", "status": "Done", "evidence": [...], "notes": "..."},
#     {"criterion": "...", "status": "Partial", "evidence": [...], "notes": "..."},
#     ...
#   ],
#   "file_results": {
#     "modify": {"file1": "exists_and_modified", "file2": "missing", ...},
#     "delete": {"file3": "deleted_confirmed", ...}
#   }
# }

# Generate formatted report using Java CLI tool
REPORT=$("$HOOKS_BIN/verify-audit" report --issue-id "$ISSUE_ID" <<< "$RESULTS_JSON")
```

The Java tool:
1. Parses the verification results JSON
2. Counts Done/Partial/Missing criteria and file verifications
3. Determines overall assessment (COMPLETE/PARTIAL/INCOMPLETE)
4. Renders a formatted box report with criteria details and file verification status

</step>

<step name="report">

**Output the audit report:**

Display the report generated by the Java CLI tool. The report includes:

1. **Summary Box**: Overall counts and assessment
2. **Criteria Verification Details**: Per-criterion status, evidence, and notes
3. **File Verification Details**: File existence and modification status
4. **JSON Summary Line**: Machine-readable assessment for downstream processing

```bash
echo "$REPORT"
```

**Status Icons:**
- `✓` - Done (fully satisfied)
- `◐` - Partial (partially satisfied)
- `✗` - Missing (not satisfied)

**Overall Assessment:**
- **COMPLETE**: All criteria Done, all files verified
- **PARTIAL**: Some criteria Partial, no Missing
- **INCOMPLETE**: Any criteria Missing or files not verified

</step>

## Notes

**Read-only operation:** This skill never modifies files. It only reads and reports.

**Subagent usage:** Verification subagents use Read, Glob, Grep, and Bash tools to investigate the codebase. Their internal tool calls are invisible to the user.

**Performance optimization:** Criteria are grouped by file dependencies before spawning subagents. When multiple criteria reference the same files, a single subagent verifies all of them together, reading each file only once. This reduces token usage from ~187K to ~30-40K for typical single-file issues. Criteria referencing different files are still verified in parallel.

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
