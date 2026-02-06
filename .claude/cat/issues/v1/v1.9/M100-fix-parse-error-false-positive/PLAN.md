# Plan: M100-fix-parse-error-false-positive

## Problem
Pattern 13/13b triggered on jq parse errors in stderr even when the overall bash command succeeded (exit code 0). This
caused false positive mistake detection when jq subcommands failed but the script completed successfully.

## Solution
- Extract exit code from hook context
- Only trigger parse_error patterns when TOOL_EXIT_CODE != 0
- Benign stderr output no longer flags as mistakes

## Acceptance Criteria
- [x] Exit code checked before triggering parse_error
- [x] No false positives when command succeeds
- [x] Legitimate parse errors still detected
