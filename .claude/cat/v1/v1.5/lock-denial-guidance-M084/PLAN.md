# Plan: lock-denial-guidance-M084

## Objective
add explicit guidance to lock denial output (M084 prevention)

## Details
When a task lock is denied, the output now includes:
- action: "FIND_ANOTHER_TASK" - explicit required action
- guidance: "Do NOT investigate, remove, or question this lock..."

This is the escalated prevention for M084 - documentation alone (which
said "MANDATORY: execute different task") was insufficient. Embedding
the guidance directly in the script output provides immediate context
at the point of failure.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
