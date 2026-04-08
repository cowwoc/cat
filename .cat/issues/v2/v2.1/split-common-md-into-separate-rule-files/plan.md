# Plan

## Goal

Split `.claude/rules/common.md` into focused, separately path-filtered rule files so each topic is only
loaded when relevant. Extract every section into its own file; if all content is extracted, delete
`common.md`. Add `paths:` frontmatter where the content is scoped to specific file types or directories.

## Extraction Map

| Section | New file | `paths:` filter |
|---------|----------|-----------------|
| Terminology: CAT Issues vs Claude TaskList | `.claude/rules/cat-issues-vs-tasklist.md` | (none — cross-cutting) |
| Language Requirements + Code Organization | `.claude/rules/language-requirements.md` | `["plugin/**", "client/**"]` |
| Multi-Instance Safety | `.claude/rules/multi-instance-safety.md` | `["plugin/**", "client/**"]` |
| Error Handling | `.claude/rules/error-handling.md` | (none — cross-cutting) |
| Configuration Reads in Worktrees | `.claude/rules/configuration-reads.md` | (none — cross-cutting) |
| No Backwards Compatibility | `.claude/rules/backwards-compatibility.md` | (none — cross-cutting) |
| MEMORY.md vs Project Conventions | `.claude/rules/memory-conventions.md` | (none — cross-cutting) |
| Documentation Style | `.claude/rules/documentation-style.md` | `["*.md"]` |
| Pre-existing Problems + Recurring Problems | `.claude/rules/pre-existing-problems.md` | (none — cross-cutting) |
| Shell Efficiency | `.claude/rules/shell-efficiency.md` | `["*.sh"]` |
| Testing | `.claude/rules/testing-conventions.md` | `["client/**"]` |
| Naming Conventions | `.claude/rules/naming-conventions.md` | (none — cross-cutting) |
| Enforcement (cat-rules patterns) | distribute into the file that owns each enforced rule | varies |

The three `cat-rules` enforcement patterns in common.md belong with the rules they enforce:
- `jq` in `*.sh` → `language-requirements.md`
- `/workspace/` in `*.sh,*.md` → `multi-instance-safety.md`
- `FIXME|TODO|fallback|workaround` → `backwards-compatibility.md`

## Pre-conditions

(none)

## Post-conditions

- [ ] Each section listed in the extraction map exists as its own file with the specified `paths:` frontmatter
- [ ] Each extracted file contains only its own section's content (no duplication across files)
- [ ] Enforcement cat-rules patterns are moved into the files that own the rule they enforce
- [ ] `common.md` is deleted (all content extracted)
- [ ] No references to `common.md` remain in CLAUDE.md or any other file

## Commit Type

`config:` — all changes are to `.claude/rules/` configuration files.

## Research Findings

### Existing references to `common.md`

- `.claude/rules/java.md` line 1912: "Tests run in parallel. In addition to the cross-language test isolation
  rules in `common.md`, Java tests must follow" — update reference to `testing-conventions.md`.
- `.claude/rules/common.md` enforcement messages — update to reference new file names (see Jobs below).
- `.claude/rules/plugin-file-references.md` — contains `common.md` only inside a "Non-Compliant Example"
  code block (illustrative text), not as a real reference. No update required.

### `paths:` frontmatter format

Use YAML frontmatter with an inline array on the `paths:` key. Files with no path filter need no frontmatter.

```yaml
---
paths: ["plugin/**", "client/**"]
---
```

### License headers for `.claude/rules/` files

Existing `.claude/rules/` files contain no license headers (consistent with `plugin/rules/` exemption
rationale — injected into agent context verbatim, headers waste tokens). New files must follow the same
convention: **no license header**.

## Jobs

### Job 1

Create seven cross-cutting rule files (no `paths:` filter). Read the full content of
`.claude/rules/common.md` to extract the exact section text for each file.

**Note:** The preamble at the top of `common.md` (the `# Common Conventions` heading and the
"Cross-cutting rules that apply to all CAT development work." line) is NOT extracted into any new file.
It is discarded when `common.md` is deleted in Job 3.

**File 1:** `.claude/rules/cat-issues-vs-tasklist.md`

Content: The entire "Terminology: CAT Issues vs Claude TaskList" section from `common.md` (the `## Terminology`
heading through the end of the section before `## Language Requirements`). No frontmatter.

**File 2:** `.claude/rules/error-handling.md`

Content: The entire "Error Handling" section from `common.md` (the `## Error Handling` heading and all
sub-sections including "Error Message Content" and "Fail-Fast Principle", through the end of the section
before `## Configuration Reads`). No frontmatter.

**File 3:** `.claude/rules/configuration-reads.md`

No frontmatter (cross-cutting: applies whenever agents work in a worktree, regardless of which file types
they are editing).

Content: The entire "Configuration Reads in Worktrees" section from `common.md` (from
`## Configuration Reads in Worktrees` through the end before `## No Backwards Compatibility`).

**File 4:** `.claude/rules/backwards-compatibility.md`

Content: The entire "No Backwards Compatibility" section from `common.md` (the `## No Backwards Compatibility`
heading through the end of the section before `## MEMORY.md`). Append the `FIXME|TODO` enforcement pattern
from `common.md`'s `## Enforcement` section at the bottom. Update the enforcement message to reference
`.claude/rules/backwards-compatibility.md` instead of `.claude/rules/common.md`. No frontmatter.

Enforcement block to append (updated message):
```cat-rules
- pattern: "\\b(?:FIXME|TODO:[[:space:]]*fix|fallback|workaround)\\b"
  files: "*"
  severity: low
  message: "Comment flag indicates known issue or workaround. Resolve or track as a separate issue.\
 See .claude/rules/backwards-compatibility.md."
```

**File 5:** `.claude/rules/memory-conventions.md`

Content: The entire "MEMORY.md vs Project Conventions" section from `common.md` (the `## MEMORY.md`
heading through the end of the section before `## Documentation Style`). No frontmatter.

**File 6:** `.claude/rules/pre-existing-problems.md`

Content: The "Pre-existing Problems" section AND the "Recurring Problems After Closed Issues" section from
`common.md` (from `## Pre-existing Problems` through the end of "Recurring Problems" before `## Shell
Efficiency`). No frontmatter.

**File 7:** `.claude/rules/naming-conventions.md`

Content: The entire "Naming Conventions" section from `common.md` (the `## Naming Conventions` heading
through the end of the section before `## Enforcement`). No frontmatter.

**Commit:** Stage all 7 new files explicitly by path and commit with:
`config: extract cross-cutting sections from common.md`

**Do NOT commit index.json in Job 1.**

### Job 2

Create five path-filtered rule files. Read the full content of `.claude/rules/common.md` to extract the
exact section text for each file.

**File 1:** `.claude/rules/language-requirements.md`

Frontmatter:
```yaml
---
paths: ["plugin/**", "client/**"]
---
```

Content: The "Language Requirements" section AND the "Code Organization" section from `common.md`
(from `## Language Requirements` through the end of "Code Organization" before `## Multi-Instance Safety`).
Append the `jq` enforcement pattern from `common.md`'s `## Enforcement` section at the bottom. Update the
enforcement message to reference `.claude/rules/language-requirements.md` instead of `.claude/rules/common.md`.

Enforcement block to append (updated message):
```cat-rules
- pattern: "\\bjq\\b"
  files: "*.sh"
  severity: high
  message: "jq is not available in the plugin runtime. Use Java (via jlink tools) or Bash pattern\
 matching. See .claude/rules/language-requirements.md § Tool Availability."
```

**File 2:** `.claude/rules/multi-instance-safety.md`

Frontmatter:
```yaml
---
paths: ["plugin/**", "client/**"]
---
```

Content: The entire "Multi-Instance Safety" section from `common.md` (from `## Multi-Instance Safety`
through the end before `## Error Handling`). Append the `/workspace/` enforcement pattern from
`common.md`'s `## Enforcement` section at the bottom. Update the enforcement message to reference
`.claude/rules/multi-instance-safety.md` instead of `.claude/rules/common.md`.

Enforcement block to append (updated message):
```cat-rules
- pattern: "/workspace/"
  files: "*.sh,*.md"
  severity: medium
  message: "Hardcoded /workspace/ path violates worktree isolation. Use ${CLAUDE_PROJECT_DIR} or\
 ${WORKTREE_PATH}. See .claude/rules/multi-instance-safety.md § Multi-Instance Safety."
```

**File 3:** `.claude/rules/documentation-style.md`

Frontmatter:
```yaml
---
paths: ["*.md"]
---
```

Content: The entire "Documentation Style" section from `common.md` (from `## Documentation Style`
through the end before `## Pre-existing Problems`).

**File 4:** `.claude/rules/shell-efficiency.md`

Frontmatter:
```yaml
---
paths: ["*.sh"]
---
```

Content: The entire "Shell Efficiency" section from `common.md` (from `## Shell Efficiency`
through the end before `## Testing`).

**File 5:** `.claude/rules/testing-conventions.md`

Frontmatter:
```yaml
---
paths: ["client/**"]
---
```

Content: The entire "Testing" section from `common.md` (from `## Testing` through the end before
`## Naming Conventions`), including all sub-sections ("No Redundant Builds" and "Test Isolation").

**Commit:** Stage all 5 new files explicitly by path and commit with:
`config: extract path-filtered sections from common.md`

**Do NOT commit index.json in Job 2.**

### Job 3

Cleanup: delete `common.md`, update references, close the issue.

**Step 1:** Delete `.claude/rules/common.md`.

**Step 2:** Update `.claude/rules/java.md`. Find and replace the reference to `common.md` on the
line containing:
```
Tests run in parallel. In addition to the cross-language test isolation rules in `common.md`, Java tests must follow
```
Change `common.md` to `testing-conventions.md` on that line.

**Step 3:** Commit the deletion and java.md update. Stage `.claude/rules/common.md` (deleted) and
`.claude/rules/java.md` explicitly. Commit with:
`config: delete common.md and update references`

**Step 4:** Update index.json: set `"status": "closed"` and `"progress": 100`.

**Step 5:** Commit index.json together with this final step. Stage:
- `.cat/issues/v2/v2.1/split-common-md-into-separate-rule-files/index.json`
Commit with:
`config: split common.md into focused rule files`

### Job 4

Fix post-implementation concerns raised in stakeholder review.

**Concern 1 (Mandatory):** `index.json` is missing the required `progress` field per index schema.
The Job 3 plan references setting `"progress": 100` but the implementation did not include this field in the JSON structure.

**Fix 1:** Update `index.json` to add the missing mandatory field. After Step 4 in Job 3, before the commit in Step 5,
ensure the JSON file includes all mandatory keys for closed issues: `status`, `progress`, `resolution`, `dependencies`,
`blocks`, and `target_branch`.

**Concern 2 (HIGH):** Two plugin files contain broken references to `common.md`:
- `plugin/skills/test-runner-isolation-validator/first-use.md` lines 294-295 reference conventions from `common.md`
  about temp file handling and multi-instance safety
- `plugin/concepts/rules-audience.md` line 115 contains an example using `common.md` to illustrate convention storage

These are deployed files shipped to end users. The references point to non-existent files after deletion.

**Fix 2:** Update `plugin/skills/test-runner-isolation-validator/first-use.md` lines 294-295 to reference the new
split rule files instead of `common.md`:
- Temp file handling context → reference `plugin/rules/multi-instance-safety.md`
- Multi-instance safety rules → reference `plugin/rules/multi-instance-safety.md`
- Shell efficiency rules → reference `plugin/rules/shell-efficiency.md`

**Fix 3:** Update `plugin/concepts/rules-audience.md` line 115. Remove or update the example that references `common.md`
(a source-only file). Replace with an example referencing deployed plugin files (e.g., `plugin/rules/multi-instance-safety.md`)
or delete the example if it is no longer illustrative.

**Commit:** After completing Fixes 1, 2, and 3, stage all modified files:
- `index.json` (updated with `progress` field)
- `plugin/skills/test-runner-isolation-validator/first-use.md`
- `plugin/concepts/rules-audience.md`
Commit with:
`config: fix missing progress field and update plugin file references after common.md deletion`
