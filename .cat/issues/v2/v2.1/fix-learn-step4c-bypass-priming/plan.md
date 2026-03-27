# Plan

## Goal

Fix the priming defect in `plugin/skills/learn/first-use.md` Step 4c that causes agents to either:
1. Call `record-learning` directly with the full combined subagent JSON (nested structure) instead of the flat
   Phase 3 `.prevent` output, resulting in all fields silently defaulting to empty strings; or
2. Bypass the Phase 3 subagent entirely and invoke `record-learning` themselves when the subagent appears to fail.

Also correct the invalid M590 entry in `.cat/retrospectives/mistakes-2026-03.json` created by the original defect.

## Pre-conditions

(none)

## Post-conditions

- [ ] Step 4c explicitly states that `PHASE3_JSON` must be the `.prevent` key extracted from the subagent's combined
  JSON output, not the full combined JSON structure
- [ ] Step 4c includes a pre-check verifying that `category`, `description`, `root_cause`, and `prevention_type` are
  non-empty before invoking `record-learning`; if any are empty, the step fails with a clear error rather than
  silently recording empty fields
- [ ] Step 4c states that if the Phase 3 subagent returns an error or empty/invalid output, the correct action is to
  retry the subagent — not to call `record-learning` directly
- [ ] M590 entry in `.cat/retrospectives/mistakes-2026-03.json` is corrected with all fields non-empty
- [ ] The code block in Step 4c still contains the temp-file write (`printf '%s' "$PHASE3_JSON" > "$PHASE3_TMP"`),
  the `record-learning` invocation, and the error-handling `if` block -- only the comment/assignment and pre-check
  were changed; no other lines in the code block were added, removed, or modified

## Research Findings

The `record-learning` Java CLI tool (`client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`)
reads fields directly from the top-level JSON input via `getStringField(phase3Input, "category", "")` (lines 394-402).
When the full combined subagent JSON is passed (which nests these fields inside a `.prevent` object), the tool reads
from the top level and gets empty strings — silently producing an entry like M590 with all fields blank.

The current Step 4c instruction at line 427 says:
```
PHASE3_JSON="{prevent phase JSON output from subagent}"
```
This is ambiguous — "prevent phase JSON output" could be interpreted as either the `.prevent` key's value (correct)
or the full combined JSON that includes the prevent phase (incorrect). The fix must make extraction explicit with a
concrete code example showing how to extract the `.prevent` key from the combined subagent output.

The M590 entry in `.cat/retrospectives/mistakes-2026-03.json` has all empty fields (`category: ""`, `description: ""`,
`root_cause: ""`, `prevention_type: ""`, etc.) — a direct consequence of this priming defect.

## Approach

**Commit type:** `bugfix:` (for plugin skill fix) and `planning:` (for M590 correction in `.cat/retrospectives/`)

Fix the ambiguous instruction in Step 4c to make `.prevent` key extraction explicit, add a field-presence pre-check
before calling `record-learning`, add retry guidance for failed Phase 3 subagents, and correct the M590 entry with
actual values derived from the defect itself.

## Jobs

### Job 1

- **Fix Step 4c in `plugin/skills/learn/first-use.md`:**

  All edits target the file `plugin/skills/learn/first-use.md` inside the worktree. Apply edits in the order listed
  (substep 3 inserts prose before the code block, substep 1 replaces lines inside the code block, substep 2 inserts
  new lines inside the code block).

  1. **Replace the ambiguous `PHASE3_JSON` comment and placeholder (lines 425-427).** Find the exact old text:
     ```
     # Store Phase 3 output in a shell variable (JSON from subagent result)
     # MUST use double-quoted assignment: PHASE3_JSON="..." — unquoted or eval'd assignment risks injection
     PHASE3_JSON="{prevent phase JSON output from subagent}"
     ```
     Replace it with:
     ```
     # Extract ONLY the .prevent object from the combined subagent JSON output.
     # The subagent returns a combined JSON with top-level keys: phases_executed, phase_summaries,
     # investigate, analyze, prevent. The record-learning CLI expects the FLAT .prevent object
     # (containing category, description, root_cause, prevention_type at the top level),
     # NOT the full combined JSON (which nests these fields inside the "prevent" key).
     #
     # WRONG: PHASE3_JSON="$FULL_SUBAGENT_JSON"  — fields are nested, record-learning reads empty strings
     # RIGHT: PHASE3_JSON="<the value of the 'prevent' key from the subagent JSON>"
     # MUST use double-quoted assignment: PHASE3_JSON="..." — unquoted or eval'd assignment risks injection
     PHASE3_JSON="{value of the 'prevent' key extracted from the combined subagent JSON}"
     ```
     Keep all other lines in the code block (lines 428-444) unchanged.

  2. **Insert a field-presence pre-check inside the code block.** Find the exact old text (line 428-433):
     ```

     # Write Phase 3 output to temp file using a variable to avoid injection
     # mktemp generates a unique path per invocation via random suffix (XXXXXX), ensuring
     # multi-instance safety even though /tmp is a shared directory.
     PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)
     ```
     Replace it with (inserting the validation loop before the temp-file write):
     ```

     # Pre-check: verify record-learning input fields are non-empty before invocation.
     # These are the fields record-learning reads from the top level of PHASE3_JSON.
     # If any are empty, the subagent output was likely passed as the full combined JSON
     # instead of just the .prevent key, or the prevent phase produced incomplete output.
     for FIELD in category description root_cause prevention_type; do
       FIELD_VALUE=$(printf '%s' "$PHASE3_JSON" | grep -o "\"${FIELD}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed "s/.*\"${FIELD}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/")
       if [[ -z "$FIELD_VALUE" ]]; then
         echo "ERROR: PHASE3_JSON is missing or has empty '${FIELD}' field. This usually means the full combined subagent JSON was passed instead of just the .prevent key. Learning NOT recorded."
         echo "Expected: PHASE3_JSON should contain the value of the 'prevent' key from the subagent output."
         echo "Got PHASE3_JSON (first 500 chars): $(printf '%s' "$PHASE3_JSON" | head -c 500)"
         exit 1
       fi
     done

     # Write Phase 3 output to temp file using a variable to avoid injection
     # mktemp generates a unique path per invocation via random suffix (XXXXXX), ensuring
     # multi-instance safety even though /tmp is a shared directory.
     PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)
     ```

  3. **Insert a "Phase 3 subagent failure handling" paragraph.** Find the exact old text (lines 421-423):
     ```
     **IMPORTANT:** The `PHASE3_JSON` variable MUST be assigned using double-quoted syntax (`PHASE3_JSON="..."`) to prevent
     word splitting and glob expansion. Never use `eval`, backtick substitution, or unquoted assignment with this value.
     ```
     Replace it with (appending the new paragraph after the existing one):
     ```
     **IMPORTANT:** The `PHASE3_JSON` variable MUST be assigned using double-quoted syntax (`PHASE3_JSON="..."`) to prevent
     word splitting and glob expansion. Never use `eval`, backtick substitution, or unquoted assignment with this value.

     **Phase 3 subagent failure handling:** If the Phase 3 subagent returns an error, empty output, or output
     that cannot be parsed as JSON, the correct action is to RETRY the Phase 3 subagent (go back to Step 3).
     Do NOT attempt to call `record-learning` directly with manually constructed or partial data — this bypasses
     the three-phase analysis and produces incomplete learning records.
     ```

- **Commit message:** `bugfix: fix learn Step 4c ambiguous PHASE3_JSON extraction and add field pre-check`

### Job 2

- **Fix M590 entry in `.cat/retrospectives/mistakes-2026-03.json`:**
  1. Read the current file content
  2. Find the M590 entry (all fields are empty strings)
  3. Replace the empty fields with actual values describing the defect that M590 was supposed to record.
     The M590 learning was recorded when the bypass priming defect itself occurred — the agent passed the full
     combined JSON to `record-learning` instead of just the `.prevent` key, resulting in empty fields. Use these
     corrected values:
     - `"category"`: `"documentation_priming"`
     - `"description"`: `"Agent passed full combined subagent JSON to record-learning instead of extracting the .prevent key, causing all fields (category, description, root_cause, prevention_type) to silently default to empty strings."`
     - `"root_cause"`: `"Step 4c instruction said PHASE3_JSON should contain 'prevent phase JSON output from subagent' which is ambiguous — agents interpreted this as the full combined JSON containing the prevent phase rather than just the .prevent key's value."`
     - `"cause_signature"`: `"documentation_priming:ambiguous_instruction:learn_skill"`
     - `"prevention_type"`: `"skill_instruction_fix"`
     - `"prevention_path"`: `"plugin/skills/learn/first-use.md"`
     - `"pattern_keywords"`: `["record-learning", "PHASE3_JSON", "prevent", "empty-fields", "learn-skill"]`
     - `"prevention_implemented"`: `true`
     - `"prevention_verified"`: `false`
     - `"correct_behavior"`: `"Extract only the .prevent key value from the combined subagent JSON before passing to record-learning. Validate that category, description, root_cause, and prevention_type are non-empty before invocation."`

- **Commit message:** `planning: correct M590 entry with actual defect details`
