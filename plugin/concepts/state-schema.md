<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# index.json Schema Reference

## Current Schema (v2.1+)

### Issue-Level index.json

All issue `index.json` files MUST be valid JSON containing only the following fields:

| Field | Required | Values |
|-------|----------|--------|
| `status` | Always | `open`, `in-progress`, `blocked`, or `closed` |
| `resolution` | Closed issues only | `implemented`, `duplicate (...)`, `obsolete (...)`, `won't-fix (...)`, or `not-applicable (...)` |
| `target_branch` | Optional | The branch this issue merges into (e.g., `v2.1`, `main`) |
| `dependencies` | Optional | `["issue-slug", ...]` — issues this issue depends on |
| `blocks` | Optional | `["issue-slug", ...]` — issues this issue blocks |
| `parent` | Optional (sub-issues only) | Parent issue slug string |
| `decomposedInto` | Optional (decomposed issues only) | Array of sub-issue slug strings |

**Example — open issue (minimal):**

```json
{
  "status": "open"
}
```

**Example — open issue with dependencies:**

```json
{
  "status": "in-progress",
  "dependencies": ["prerequisite-issue"]
}
```

**Example — closed issue:**

```json
{
  "status": "closed",
  "resolution": "implemented"
}
```

### Version-Level State

Version state files (major, minor, patch) use a different structure and are not subject to issue schema
validation. See `plugin/templates/major-state.md`, `minor-state.md`, and `patch-plan.md` for templates.

## Migration

Run `plugin/migrations/2.1.sh` to convert existing `STATE.md` files to `index.json` format. The script is
idempotent — running it multiple times produces the same result.

## Validation

`StateSchemaValidator` (a PreToolUse hook) enforces the schema on every Write or Edit tool call targeting
an issue-level `index.json` file. It blocks writes that:

- Contain invalid JSON
- Include unrecognized fields
- Have invalid `status` values
- Have malformed `dependencies` or `blocks` arrays
- Are missing `resolution` on closed issues
- Have invalid `resolution` values
