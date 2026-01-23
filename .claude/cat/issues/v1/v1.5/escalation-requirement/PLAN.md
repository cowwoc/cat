# Plan: escalation-requirement

## Objective
add escalation requirement when prevention already exists

## Details
When documentation/process already covers a mistake but was ignored,
recording that same documentation as "prevention" is invalid. The mistake
WILL recur. Must escalate to higher prevention level (hook, code fix, etc.).

Added:
- Step 6b: Check If Prevention Already Exists (MANDATORY)
- Escalation hierarchy table
- Anti-pattern M084: Recording existing documentation as prevention

Key insight: If pointing to a file that already contained the violated
instruction, you have NOT implemented prevention - just documented failure
to read. Must automate enforcement.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
