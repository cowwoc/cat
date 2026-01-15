# Plan: version-sort-fix

## Objective
sort minors by version before determining current_minor

## Details
Glob order puts v0.10 before v0.2 alphabetically, causing the script
to incorrectly identify v0.10 as current when v0.5 has pending tasks.

Fix: iterate through version-sorted keys after collecting all stats.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
