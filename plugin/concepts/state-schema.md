<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# STATE.md Schema Reference

## Current Schema (v2.1+)

### Issue-Level STATE.md

All issue STATE.md files MUST contain only the following fields:

| Field | Required | Values |
|-------|----------|--------|
| `Status` | Always | `open`, `in-progress`, `blocked`, or `closed` |
| `Progress` | Always | Integer 0–100 followed by `%` |
| `Dependencies` | Always | `[]` or `[issue-slug, ...]` |
| `Blocks` | Always | `[]` or `[issue-slug, ...]` |
| `Resolution` | Closed issues only | `implemented`, `duplicate (...)`, `obsolete (...)`, `won't-fix (...)`, or `not-applicable (...)` |
| `Parent` | Sub-issues only | Parent issue slug |

**Example — open issue:**

```markdown
# State

- **Status:** in-progress
- **Progress:** 40%
- **Dependencies:** [prerequisite-issue]
- **Blocks:** []
```

**Example — closed issue:**

```markdown
# State

- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** []
- **Blocks:** []
```

### Version-Level STATE.md

Version STATE.md files (major, minor, patch) use a different structure and are not subject to issue schema
validation. See `plugin/templates/major-state.md`, `minor-state.md`, and `patch-plan.md` for templates.

## Deprecated Fields

The following fields were removed from the issue STATE.md schema. Any STATE.md file containing these fields
will be rejected by `StateSchemaValidator`.

| Field | Removed In | Reason |
|-------|-----------|--------|
| `Last Updated` | v2.1 | Duplicates `git log` file date; error-prone to maintain manually |
| `Completed` | v2.1 | Replaced by `Resolution`; date is available via `git log` |
| `Started` | v2.1 | Date available via `git log`; not meaningful for issue tracking |
| `Tokens Used` | v2.1 | Internal metric; not relevant in persistent issue state |
| `Assignee` | v2.1 | CAT is single-user; field has no operational value |
| `Priority` | v2.1 | Ordering managed by issue ordering in version STATE.md |
| `Worktree` | v2.1 | Runtime data; not persistent issue state |
| `Merged` | v2.1 | Derivable from `git log`; redundant |
| `Commit` | v2.1 | Derivable from `git log`; redundant |
| `Version` | v2.1 | Issues are already scoped by directory path |

## Migration

Run `plugin/migrations/2.1.sh` to remove deprecated fields from existing STATE.md files. The script is
idempotent — running it multiple times produces the same result.

## Validation

`StateSchemaValidator` (a PreToolUse hook) enforces the schema on every Write or Edit tool call targeting
an issue-level STATE.md file. It blocks writes that:

- Include unrecognized fields
- Have invalid `Status` values
- Have `Progress` outside the 0–100% range
- Have malformed `Dependencies` or `Blocks` lists
- Are missing `Resolution` on closed issues
- Have invalid `Resolution` values
