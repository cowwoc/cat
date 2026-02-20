---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Skill: verify-implementation

Post-execution verification that systematically checks all planned changes from PLAN.md were actually implemented in
the codebase.

## Purpose

Verify that the implementation matches what was planned in the issue's PLAN.md file. This is a read-only audit that
reports findings without making changes. The skill is invoked automatically by `/cat:work` between the execute and
review phases.

## When to Use

- Invoked by `/cat:work` between execute and review phases to verify PLAN.md acceptance criteria before stakeholder
  quality review

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

<step name="prepare">

**Prepare all audit data in a single step:**

Parse JSON arguments, validate the issue, parse PLAN.md, generate subagent prompts, and verify file specs.

```bash
CLIENT_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
if [ ! -x "$CLIENT_BIN/verify-audit" ]; then
  CLIENT_BIN="/workspace/client/target/jlink/bin"
fi

PREPARED=$(echo "${ARGUMENTS}" | "$CLIENT_BIN/verify-audit" prepare)
if [ $? -ne 0 ]; then
  echo "FAIL: $PREPARED"
  exit 1
fi
```

After running, read the JSON output to extract values:
- Display: "◆ Auditing issue: {issue_id}" and "◆ Found {criteria_count} acceptance criteria and {file_count} file
  specifications"
- Extract `prompts` array — each entry has `group_index` and `prompt` fields
- Store `file_results` for the collect_results step

Then **immediately spawn verification subagents**:

For each entry in the `prompts` array, invoke the Task tool with:
- `subagent_type: "general-purpose"`
- `model: "haiku"` (verification is straightforward - check files against criteria)
- `prompt: <the full prompt text from the "prompt" field>`

Spawn all subagents in parallel (multiple Task tool calls in a single message). They will run concurrently and return
results together.

Store all returned task IDs for result collection in the next step.

Each subagent verifies all criteria in its group against the same file context, avoiding redundant reads.

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

Aggregate all criterion results into a single `criteria_results` array, then combine with file verification results.
Write the combined JSON to a temporary file using the Write tool, then pipe it to the report command.

The combined JSON must follow this structure:
```json
{
  "criteria_results": [...all criterion objects from all subagents...],
  "file_results": {"modify": {...}, "delete": {...}}
}
```

Where `file_results` comes from the `file_results` field in the `PREPARED` JSON produced in the prepare step.

Use the Write tool to write the combined JSON to `/tmp/verify-results-${ISSUE_ID}.json`, then generate the report:

Extract `issue_id` directly from the `PREPARED` JSON output (the agent can read JSON natively). Then generate the
report:

```bash
REPORT=$("$CLIENT_BIN/verify-audit" report "$ISSUE_ID" < /tmp/verify-results-${ISSUE_ID}.json)
```

The Java tool:
1. Parses the verification results JSON
2. Counts Done/Partial/Missing criteria and file verifications
3. Determines overall assessment (COMPLETE/PARTIAL/INCOMPLETE)
4. Renders a formatted box report with criteria details and file verification status

Display the report:

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

**Subagent usage:** Verification subagents use Read, Glob, Grep, and Bash tools to investigate the codebase. Their
internal tool calls are invisible to the user.

**Performance optimization:** Criteria are grouped by file dependencies before spawning subagents. When multiple
criteria reference the same files, a single subagent verifies all of them together, reading each file only once. This
reduces token usage from ~187K to ~30-40K for typical single-file issues. Criteria referencing different files are
still verified in parallel.

**No auto-fix:** This skill reports findings only. If issues are found, the user must decide whether to re-run
implementation or accept the current state.

## Related Skills

- `/cat:stakeholder-review` - Quality review by domain experts (design, testing, security, etc.)
- `/cat:status` - Progress tracking and issue status monitoring

## Limitations

- **Heuristic-based verification:** Automated checks parse PLAN.md structure and verify file operations, but cannot
  replace human review for semantic correctness.
- **Parse-dependent:** Only verifies what can be parsed from PLAN.md. If acceptance criteria or file specifications
  are not explicitly documented, they will not be verified.
- **No semantic understanding:** Verifies that code exists and was modified, but cannot judge whether implementation
  correctly satisfies the intent behind acceptance criteria.
- **Limited to documented plans:** Cannot verify undocumented requirements or implicit expectations.

## Example Usage

Invoked automatically by `/cat:work`:
```
# Invoked automatically between execute and review phases
# No manual invocation needed - /cat:work provides JSON arguments
```
