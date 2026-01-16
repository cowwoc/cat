# Plan: exit-gate-improvements

## Objective
add exit gate task dependency rule

## Details
Tasks listed in a version's `## Exit Gate Tasks` section now have an
implicit dependency on ALL other tasks in the same version. Exit gate
tasks can only execute when every non-gating task is completed.

This prevents running validation/gate tasks before all implementation
work is done.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
