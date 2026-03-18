# Plan: Move Dependencies to PLAN.md

## Current State

Issue dependencies (blocking relationships) are stored as a `- **Dependencies:** [...]` bullet in
`STATE.md`. This couples runtime state (status, progress) with structural planning data (what must
complete first). `WorkPrepare.java`, `IssueDiscovery.java`, and `StateSchemaValidator.java` all read
dependencies from `STATE.md`.

## Target State

Dependencies move to a `## Dependencies` section in `PLAN.md` with `- issue-id` list entries.
`STATE.md` no longer carries a `Dependencies` field. All Java parsers read dependencies from
`PLAN.md`. A migration script moves existing `Dependencies` values from all `STATE.md` files to
their corresponding `PLAN.md` files.

## Parent Requirements

None (tech-debt refactor).

## Rejected Alternatives

- **Mirror in both files:** Creates dual source of truth — one would inevitably diverge.
- **Dedicated `DEPS.md`:** Unnecessary proliferation of per-issue files for structured data that
  belongs alongside the plan.
- **Move into `## Pre-conditions`:** Dependencies are machine-parsed structured data; `Pre-conditions`
  is freeform prose. Mixing them would require fragile parsing heuristics.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Any STATE.md files with non-empty `Dependencies` must be migrated before the
  new parsers run, or blocking will be silently lost. Migration script must run before first use of
  updated code.
- **Mitigation:** Idempotent migration script; extensive existing tests for `IssueDiscovery` and
  `WorkPrepare`; E2E smoke test via `/cat:work` with a blocked issue.

## Impact Notes

`GetStatusOutput.java` already has a dual parser: it tries `## Dependencies` section first, then
falls back to the inline STATE.md bullet. After this change, remove the STATE.md fallback — only the
PLAN.md section parser remains. The open issue `fix-state-field-validation` (v2.1) includes
`Dependencies` in the allowed-fields set — its `PLAN.md` post-conditions or documentation may need
updating if it has not yet been merged.

## PLAN.md Dependencies Section Format

```markdown
## Dependencies
- 2.1-some-prerequisite
- 2.1-another-prerequisite
```

For issues with no dependencies, omit the section entirely (parsers return empty when section is
absent). Issue templates include the section for discoverability, with `- None` as a placeholder.

## Files to Modify

### Java
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Remove `DEPS_PATTERN` field (reads STATE.md inline bullet)
  - Add `PLAN_DEPS_SECTION_PATTERN` reading `## Dependencies\n- item` from PLAN.md content
  - Update `resolveActiveDependencies(String content, ...)` to accept `Path planPath` instead of/in
    addition to STATE.md content; read PLAN.md with the new pattern
  - Update all callers of `resolveActiveDependencies` to pass `planPath` (already available as
    `issuePath.resolve("PLAN.md")`)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  - Update `getDependencies(List<String> lines)` to instead accept `Path issueDir`; read
    `issueDir.resolve("PLAN.md")` and parse the `## Dependencies` section
  - Update all callers of `getDependencies` to pass `issueDir` (already iterate issue dirs)
  - Remove `getDependencies(Path statePath)` overload that reads STATE.md
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
  - In `getIssueDependencies(Path stateFile)`: rename parameter to `issueDir`, change to read
    `issueDir.resolve("PLAN.md")`, keep only the `## Dependencies` section parser, remove the
    STATE.md inline-bullet fallback
  - Update callers: pass `issueDir` instead of `stateFile`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
  - Remove `"Dependencies"` from `ALLOWED_FIELDS` set
  - Remove the `Dependencies` validation block (format check for list syntax)

### Plugin / Templates
- `plugin/templates/issue-state.md`
  - Remove the line `- **Dependencies:** []`
- `plugin/templates/issue-plan.md`
  - Add `## Dependencies` section (with `- None` placeholder) to each template (Feature, Bugfix,
    Refactor, Performance) after the `## Parent Requirements` section

### Plugin / Concepts
- `plugin/concepts/state-schema.md`
  - Remove `Dependencies` row from the field table
  - Remove `Dependencies` from all examples
  - Remove from the "malformed fields" bullet list

### Migration
- `plugin/migrations/2.3.sh` — new migration script (see Wave 3 for full algorithm)
- `plugin/migrations/registry.json` — add `2.3` entry

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1 — Update Java parsers

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`:
  - Remove `DEPS_PATTERN` static field
  - Add `private static final Pattern PLAN_DEPS_LINE_PATTERN = Pattern.compile("^- ([a-zA-Z0-9][a-zA-Z0-9._-]*)\\s*$");`
  - Change `resolveActiveDependencies` signature from `(String content, ...)` to
    `(Path planPath, String stateContent, ...)` — stateContent still needed for status check
  - Inside `resolveActiveDependencies`: read PLAN.md, find `## Dependencies` section, extract
    each `- item` line using `PLAN_DEPS_LINE_PATTERN`; skip `None` (case-insensitive)
  - Update all 2 call sites to pass `issuePath.resolve("PLAN.md")` as `planPath`
  - Update Javadoc on the method and `PLAN_DEPS_LINE_PATTERN`

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`:
  - Remove `getDependencies(Path statePath)` overload that reads STATE.md
  - Rename `getDependencies(List<String> lines)` to `getDependencies(Path issueDir)` — reads
    `issueDir.resolve("PLAN.md")`
  - Parse `## Dependencies` section: same algorithm as `GetStatusOutput.getIssueDependencies`
    (section header, then `- item` lines until next `##`; skip `None`)
  - Update all callers to pass `issueDir` (the directory already in scope at each call site)
  - Remove any now-unused imports

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`:
  - Rename `getIssueDependencies(Path stateFile)` to `getIssueDependencies(Path issueDir)`
  - Change body to read `issueDir.resolve("PLAN.md")` with the existing `## Dependencies` section
    parser; remove the `inlinePattern` fallback block entirely
  - Update callers to pass `issueDir` instead of `stateFile`

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`:
  - Remove `"Dependencies"` from the `ALLOWED_FIELDS` `Set.of(...)` call
  - Remove the validation block that calls `validateListFormat(dependencies, "Dependencies")`
  - Remove the `String dependencies = fields.get("Dependencies");` line and its conditional
  - Remove any now-unused local variables

### Wave 2 — Update templates and documentation

- Update `plugin/templates/issue-state.md`:
  - Delete the line `- **Dependencies:** []`

- Update `plugin/templates/issue-plan.md`:
  - In Feature, Bugfix, Refactor, and Performance templates, insert the following block after the
    `## Parent Requirements` section and before the next `##` section:
    ```markdown
    ## Dependencies
    - None
    ```

- Update `plugin/concepts/state-schema.md`:
  - Remove the `| Dependencies | Always | ...` row from the field table
  - Remove `- **Dependencies:** [...]` from all examples in the file
  - Remove `Dependencies` from the bullet list of fields that can be malformed

### Wave 3 — Migration script and registry

- Create `plugin/migrations/2.3.sh` with the following logic:

  ```bash
  #!/usr/bin/env bash
  # Copyright (c) 2026 Gili Tzabari. All rights reserved.
  #
  # Licensed under the CAT Commercial License.
  # See LICENSE.md in the project root for license terms.
  set -euo pipefail
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  source "${SCRIPT_DIR}/lib/utils.sh"

  log_migration "2.3" "Migrating Dependencies field from STATE.md to PLAN.md"

  ISSUES_DIR="${CLAUDE_PROJECT_DIR}/.cat/issues"

  # Idempotency: check if any STATE.md still has Dependencies field
  remaining=$(grep -rl "^\- \*\*Dependencies:\*\*" "${ISSUES_DIR}" 2>/dev/null || true)
  if [[ -z "$remaining" ]]; then
    log_migration "2.3" "Already migrated — no STATE.md files contain Dependencies field"
    set_last_migrated_version "2.3"
    exit 0
  fi

  while IFS= read -r state_file; do
    issue_dir="$(dirname "$state_file")"
    plan_file="${issue_dir}/PLAN.md"

    # Extract dependencies value
    deps_line=$(grep "^\- \*\*Dependencies:\*\*" "$state_file" || true)
    if [[ -z "$deps_line" ]]; then
      continue
    fi

    # Parse the value from: - **Dependencies:** [dep1, dep2] or []
    deps_value=$(echo "$deps_line" | sed 's/^- \*\*Dependencies:\*\* *//')

    # Remove Dependencies line from STATE.md
    grep -v "^\- \*\*Dependencies:\*\*" "$state_file" > "${state_file}.tmp"
    mv "${state_file}.tmp" "$state_file"

    # If empty, nothing to add to PLAN.md
    if [[ "$deps_value" == "[]" || -z "$deps_value" ]]; then
      continue
    fi

    # Skip if PLAN.md does not exist
    if [[ ! -f "$plan_file" ]]; then
      log_migration "2.3" "WARN: No PLAN.md for ${issue_dir} — skipping dependency migration"
      continue
    fi

    # Skip if PLAN.md already has ## Dependencies section (idempotent)
    if grep -q "^## Dependencies" "$plan_file"; then
      continue
    fi

    # Extract dep IDs from [dep1, dep2] format
    inner=$(echo "$deps_value" | sed 's/^\[//;s/\]$//')
    dep_lines=""
    IFS=',' read -ra deps <<< "$inner"
    for dep in "${deps[@]}"; do
      dep_trimmed=$(echo "$dep" | tr -d ' ')
      if [[ -n "$dep_trimmed" ]]; then
        dep_lines="${dep_lines}- ${dep_trimmed}\n"
      fi
    done

    if [[ -z "$dep_lines" ]]; then
      continue
    fi

    # Insert ## Dependencies section before ## Pre-conditions (or append before ## Sub-Agent)
    if grep -q "^## Pre-conditions" "$plan_file"; then
      sed -i "s/^## Pre-conditions/## Dependencies\n${dep_lines}\n## Pre-conditions/" "$plan_file"
    else
      printf "\n## Dependencies\n%b" "$dep_lines" >> "$plan_file"
    fi

    log_migration "2.3" "Migrated dependencies from ${state_file}"
  done < <(echo "$remaining")

  log_success "2.3" "Dependencies migration complete"
  set_last_migrated_version "2.3"
  ```

- Update `plugin/migrations/registry.json`:
  - Add after the `2.2` entry:
    ```json
    {
      "version": "2.3",
      "script": "2.3.sh",
      "description": "Move issue Dependencies field from STATE.md to PLAN.md"
    }
    ```

### Wave 4 — Tests and verification

- Run `mvn -f client/pom.xml test` and confirm all tests pass
- Fix any compilation errors or test failures from Waves 1–3
- Update any test files that construct STATE.md content with `Dependencies:` to use PLAN.md
  `## Dependencies` section format instead

## Post-conditions

- [ ] `plugin/templates/issue-state.md` does not contain a `Dependencies` field
- [ ] `plugin/templates/issue-plan.md` contains `## Dependencies` section in all four templates
- [ ] `plugin/concepts/state-schema.md` does not mention `Dependencies` as a STATE.md field
- [ ] `WorkPrepare.java` reads dependencies from PLAN.md `## Dependencies` section (no reference to
  STATE.md `- **Dependencies:**` pattern)
- [ ] `IssueDiscovery.java` reads dependencies from PLAN.md `## Dependencies` section
- [ ] `GetStatusOutput.java` reads dependencies from PLAN.md only (no STATE.md fallback)
- [ ] `StateSchemaValidator.java` does not include `Dependencies` in `ALLOWED_FIELDS`
- [ ] `plugin/migrations/2.3.sh` exists, is executable, and is idempotent
- [ ] `plugin/migrations/registry.json` has a `2.3` entry pointing to `2.3.sh`
- [ ] All Maven tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: create a test issue with a `## Dependencies` section in PLAN.md referencing an open
  issue; run `/cat:work`; confirm the new issue is blocked
