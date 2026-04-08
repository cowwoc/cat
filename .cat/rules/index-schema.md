---
paths: ["index.json", "**/index.json"]
---
# index.json Schema

All issue index.json files must conform to this standardized schema.

## Mandatory Keys (All Issues)

### Status
**Format:** `Status: open | in-progress | blocked | closed`

**Description:** Current state of the issue.

**Values:**
- `open` - Issue is defined but work has not started
- `in-progress` - Work is actively being performed
- `blocked` - Work cannot proceed until a dependency is resolved
- `closed` - Issue is completed or resolved

### Dependencies
**Format:** `Dependencies: []` or `Dependencies: [issue-id-1, issue-id-2]`

**Description:** List of issue IDs that must be completed before this issue can be closed.

### Blocks
**Format:** `Blocks: []` or `Blocks: [issue-id-1, issue-id-2]`

**Description:** List of issue IDs that cannot be completed until this issue is closed.

## Mandatory Keys (Closed Issues Only)

### Resolution
**Format:** `Resolution: <value>`

**Description:** How the issue was resolved.

**Values:**
- `implemented` - Issue was completed as planned
- `duplicate (<issue-id>)` - Issue duplicates another issue
- `obsolete (<explanation>)` - Issue is no longer relevant
- `won't-fix (<explanation>)` - Issue will not be addressed
- `not-applicable (<explanation>)` - Issue does not apply

## Optional Keys

### Parent
**Format:** `Parent: <issue-id>`

**Description:** Parent issue ID for decomposed sub-issues.

## Content After Keys

Any content following the key-value section is preserved as-is. Common patterns:

- Sub-issues tables
- Summary sections
- Implementation notes
