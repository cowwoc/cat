# Minor Version 2.1: Pluggable Issue Trackers

## Overview
Add support for multiple issue tracker backends (local files, GitHub Issues, and others) through a storage abstraction layer.

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | Design storage abstraction interface | must-have | Interface supports CRUD for versions/tasks |
| REQ-002 | Local file backend (current behavior) | must-have | Existing .claude/cat storage works via abstraction |
| REQ-003 | GitHub Issues backend | should-have | Can read/write planning metadata to GitHub Issues |
| REQ-004 | Backend selection via config | must-have | cat-config.json specifies active backend |
| REQ-005 | Extensible for future backends | nice-to-have | Clear pattern for adding Jira, Linear, etc. |

## Gates

### Entry
- v2.0 complete

### Exit
- All tasks complete
- At least local + GitHub backends working
