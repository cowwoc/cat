# Plan: pass-json-args-via-file

## Current State

Two skill handoffs in the CAT work orchestration pipeline pass JSON arrays as inline positional
arguments: `EXECUTION_COMMITS_JSON` (work-with-issue → work-confirm) and `COMMITS_JSON`
(work-with-issue → work-merge). JSON contains `{`, `}`, `"`, `:`, `[`, `]` which Bash `read`
and the Skill tool's arg preprocessing split on whitespace, corrupting the data and causing
"bad pattern" or parse errors.

Additionally, `plugin/concepts/skill-validation.md` documents `<test-prompts-json>` as a
positional arg for `cat:skill-validator-agent`, which has the same exposure.

## Target State

All inline JSON positional arguments in the plugin are replaced by file paths. The caller
writes JSON to a temporary file (path: `/tmp/cat-${ISSUE_ID}-<phase>.json`), passes the file
path as the argument, and the receiver reads JSON from the file.

## Parent Requirements
None

## Rejected Approaches

- **Base64 encode JSON**: Adds encoding/decoding complexity in both caller and receiver.
- **Environment variable**: Env vars are process-global and can leak across subagent boundaries.
- **Restructure to pass only IDs**: Requires downstream consumers to re-fetch data.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Skill argument interfaces change (callers pass file path, not inline
  JSON). No user-visible behavior change.
- **Mitigation:** All callers and receivers are updated atomically in the same wave.

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — caller side
- `plugin/skills/work-confirm-agent/first-use.md` — receiver side (reads JSON from file)
- `plugin/skills/work-confirm-agent/SKILL.md` — update argument-hint frontmatter field
- `plugin/skills/work-merge-agent/first-use.md` — receiver side (reads JSON from file)
- `plugin/skills/work-merge-agent/SKILL.md` — update argument-hint frontmatter field
- `plugin/concepts/skill-validation.md` — update `<test-prompts-json>` placeholder text

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

#### plugin/skills/work-with-issue-agent/first-use.md

**Change 1 — confirm phase JSON file write:**
Find the block (around line 119-126):
```
Invoke the confirm phase skill:

```
Skill tool:
  skill: "cat:work-confirm-agent"
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${EXECUTION_COMMITS_JSON} ${FILES_CHANGED} ${TRUST} ${VERIFY}"
```
```

Insert a new Bash code block immediately BEFORE the `Skill tool:` block:
```bash
EXECUTION_COMMITS_JSON_PATH="/tmp/cat-${ISSUE_ID}-confirm-commits.json"
printf '%s' "${EXECUTION_COMMITS_JSON}" > "${EXECUTION_COMMITS_JSON_PATH}"
```

Then change the `args:` line to:
```
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${EXECUTION_COMMITS_JSON_PATH} ${FILES_CHANGED} ${TRUST} ${VERIFY}"
```

Then change the prose line that reads:
```
Where `EXECUTION_COMMITS_JSON` is the JSON array of commits from the implement phase result,
```
to:
```
Where `EXECUTION_COMMITS_JSON_PATH` is the path to the JSON file containing commits from the implement phase result,
```

**Change 2 — merge phase JSON file write:**
Find the block for the merge phase invocation containing:
```
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${COMMITS_JSON} ${TRUST} ${VERIFY}"
```

Insert a new Bash code block immediately BEFORE the `Skill tool:` block:
```bash
COMMITS_JSON_PATH="/tmp/cat-${ISSUE_ID}-merge-commits.json"
printf '%s' "${COMMITS_JSON}" > "${COMMITS_JSON_PATH}"
```

Then change the `args:` line to use `${COMMITS_JSON_PATH}` in place of `${COMMITS_JSON}`.

All other occurrences of `COMMITS_JSON` in the file (e.g., "append them to `EXECUTION_COMMITS_JSON`
to build the complete `COMMITS_JSON` array") describe in-memory variable usage and must NOT be changed.

#### plugin/skills/work-confirm-agent/first-use.md

**Change 1 — argument table row 7:**
Find the argument table row for position 7. Current row:
```
| 7 | execution_commits_json | JSON array of commit objects from the implement phase |
```
Replace with:
```
| 7 | execution_commits_json_path | Path to JSON file containing commit objects from the implement phase |
```

**Change 2 — read command (line 47):**
Current line:
```
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH EXECUTION_COMMITS_JSON FILES_CHANGED TRUST VERIFY <<< "$ARGUMENTS" && \
```
Replace `EXECUTION_COMMITS_JSON` with `EXECUTION_COMMITS_JSON_PATH` (keep everything else identical,
including the `&& \` suffix if present).

**Change 3 — add file-read assignments after the read command:**
After the `read` command line (line 47 or the end of the chained `&& \` block that follows it),
add the following two lines as a new bash block:
```bash
EXECUTION_COMMITS_JSON=$(cat "$EXECUTION_COMMITS_JSON_PATH")
CURRENT_COMMITS_JSON="$EXECUTION_COMMITS_JSON"
```
There is no existing bash assignment for `CURRENT_COMMITS_JSON` immediately after the `read` line —
add these two lines as new code without removing any existing line.

#### plugin/skills/work-confirm-agent/SKILL.md

Find the `argument-hint:` field in the YAML frontmatter. Current value:
```
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json> <files_changed> <trust> <verify>"
```
Replace `<execution_commits_json>` with `<execution_commits_json_path>` (keep everything else identical).

#### plugin/skills/work-merge-agent/first-use.md

**Change 1 — argument table row 7:**
Find the argument table row for position 7. Current row:
```
| 7 | commits_json | JSON array of all commit objects accumulated across phases |
```
Replace with:
```
| 7 | commits_json_path | Path to JSON file containing all commit objects accumulated across phases |
```

**Change 2 — read command (line 25):**
Current line:
```
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON TRUST VERIFY <<< "$ARGUMENTS"
```
Replace `COMMITS_JSON` with `COMMITS_JSON_PATH`.

**Change 3 — add file-read assignment after the read command:**
After the `read` command (line 25), add:
```bash
COMMITS_JSON=$(cat "$COMMITS_JSON_PATH")
```
There is no existing bash assignment for `COMMITS_JSON` from a file — add this line as new code.

#### plugin/skills/work-merge-agent/SKILL.md

Find the `argument-hint:` field in the YAML frontmatter. Current value contains `<commits_json>`.
Replace `<commits_json>` with `<commits_json_path>`.

#### plugin/concepts/skill-validation.md

Replace all occurrences of `<test-prompts-json>` with `<test-prompts-json-path>` (both in code
examples and prose text).

#### .cat/issues/v2/v2.1/pass-json-args-via-file/STATE.md

Update:
- `**Status:**` from `open` to `closed`
- `**Progress:**` from `0%` to `100%`

### Wave 2

#### E2E runtime verification of implement→confirm→merge handoff

Write a Bats test script at `plugin/skills/work-with-issue-agent/test-json-handoff.bats` that
constructs a synthetic JSON commits array containing special characters (`{`, `}`, `"`, `:`, `[`, `]`),
writes it to a temp file via `printf '%s'`, reads it back with `cat`, and asserts the round-trip
value is byte-for-byte identical to the original. Run the test with:
```bash
bats plugin/skills/work-with-issue-agent/test-json-handoff.bats
```
The test must exit 0. This validates the file-write/file-read pattern used by the actual skill
handoffs (`EXECUTION_COMMITS_JSON_PATH` and `COMMITS_JSON_PATH`) without requiring a live CAT
work session.

## Post-conditions
- [ ] `plugin/skills/work-with-issue-agent/first-use.md`: no `${EXECUTION_COMMITS_JSON}` or
  `${COMMITS_JSON}` in any `args:` field (grep confirms only `_PATH` variants in args lines)
- [ ] `plugin/skills/work-confirm-agent/first-use.md`: contains
  `EXECUTION_COMMITS_JSON=$(cat "$EXECUTION_COMMITS_JSON_PATH")` after the read command
- [ ] `plugin/skills/work-merge-agent/first-use.md`: contains
  `COMMITS_JSON=$(cat "$COMMITS_JSON_PATH")` after the read command
- [ ] `plugin/skills/work-confirm-agent/SKILL.md`: argument-hint contains
  `<execution_commits_json_path>` (not `<execution_commits_json>`)
- [ ] `plugin/skills/work-merge-agent/SKILL.md`: argument-hint contains
  `<commits_json_path>` (not `<commits_json>`)
- [ ] `plugin/concepts/skill-validation.md`: no remaining `<test-prompts-json>` occurrences
- [ ] User-visible behavior unchanged (same commits flow through phases)
- [ ] E2E: invoke `/cat:work` on any available issue and confirm implement→confirm→merge
  handoff completes without "bad pattern" or JSON parse errors in the terminal output
