# STATE.md Schema

All issue STATE.md files must conform to this standardized schema.

## Mandatory Keys (All Issues)

### Status
**Format:** `Status: open | in-progress | closed`

**Description:** Current state of the issue.

**Values:**
- `open` - Issue is defined but work has not started
- `in-progress` - Work is actively being performed
- `closed` - Issue is completed or resolved

### Progress
**Format:** `Progress: 0-100%`

**Description:** Percentage completion estimate.

### Dependencies
**Format:** `Dependencies: []` or `Dependencies: [issue-id-1, issue-id-2]`

**Description:** List of issue IDs that must be completed before this issue can be closed.

### Blocks
**Format:** `Blocks: []` or `Blocks: [issue-id-1, issue-id-2]`

**Description:** List of issue IDs that cannot be completed until this issue is closed.

### Last Updated
**Format:** `Last Updated: YYYY-MM-DD`

**Description:** Date of last meaningful update to the issue.

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

## Removed Keys

The following keys are no longer part of the schema:

- **Completed** - Completion date is determined by Last Updated on closed issues
- **Completed At** - Variant of Completed
- **Version** - Determined by parent folder structure
- **Tokens Used** - Not part of STATE.md schema
- **Started** - Start date tracking removed
- **Reason** - Folded into Resolution parenthetical
- **Closed Reason** - Folded into Resolution parenthetical
- **Obsolete Reason** - Folded into Resolution parenthetical
- **Abandoned** - Folded into Resolution
- **Decomposed At** - Removed
- **Decomposed** - Removed
- **Duplicate Of** - Folded into Resolution as "duplicate (issue-id)"
- **Assignee** - Removed
- **Priority** - Removed
- **Worktree** - Removed
- **Merged** - Removed
- **Commit** - Removed
- **Note** - Removed
- **Scope Note** - Removed
- **Completion Notes** - Removed
- **Created From** - Renamed to Parent
