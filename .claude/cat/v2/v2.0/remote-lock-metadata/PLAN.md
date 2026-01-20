# Plan: remote-lock-metadata

## Goal
Display remote branch ownership information (last committer, commit date) when skipping tasks due to
remote locks in /cat:work, and enhance /cat:cleanup to report stale remote locks without removing them.

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | *Define requirement* | must-have | *How to verify* |


## Research

*Populated by stakeholder research. Run `/cat:research` to fill, or add manually.*

### Stack
| Library | Purpose | Version | Rationale |
|---------|---------|---------|-----------|
| *TBD* | *TBD* | *TBD* | *TBD* |

### Architecture
- **Pattern:** *TBD*
- **Integration:** *TBD*

### Pitfalls
- *Run /cat:research to populate*


## Satisfies
None (infrastructure task)

## Approach Outlines

### Conservative
Add ownership display to /cat:work only when skipping due to remote locks.
- **Risk:** LOW
- **Tradeoff:** No staleness reporting in cleanup

### Balanced
Add ownership display to /cat:work and staleness scanning to /cat:cleanup with the 1-7 day window.
- **Risk:** MEDIUM
- **Tradeoff:** Fixed staleness thresholds

### Aggressive
Full metadata system with configurable staleness thresholds and detailed ownership history.
- **Risk:** HIGH
- **Tradeoff:** Over-engineering for current needs

## Implementation Notes

### Git Commands for Branch Metadata
```bash
# Get last commit author, email, and date for a remote branch
git log -1 --format='Author: %an <%ae>%nDate: %ci%nRelative: %cr' origin/branch-name

# List remote branches with metadata
git for-each-ref --sort=-committerdate \
  --format='%(refname:short) | %(committerdate:relative) | %(committername) | %(committeremail)' \
  refs/remotes/
```

### Staleness Logic
- **1-7 days idle**: Report as "potentially stale" in /cat:cleanup
- **>7 days idle**: Do not warn (avoid noise for long-running or abandoned work)
- **Never auto-remove**: Remote locks are only reported, never deleted

## Acceptance Criteria
- [ ] /cat:work displays last committer name and email when skipping due to remote lock
- [ ] /cat:work displays relative time since last commit (e.g., "2 days ago")
- [ ] /cat:cleanup scans remote branches matching CAT lock pattern
- [ ] /cat:cleanup reports branches with 1-7 days of inactivity as "potentially stale"
- [ ] /cat:cleanup does NOT report branches idle >7 days
- [ ] /cat:cleanup never removes remote locks automatically
