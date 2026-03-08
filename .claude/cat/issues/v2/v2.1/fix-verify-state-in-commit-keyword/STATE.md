# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Blocks:** []
- **Target Branch:** v2.1

## Implementation Summary

Fixed misleading test documentation in VerifyStateInCommitTest:
1. Renamed `allowsWhenStateMdHasMalformedFormat` to `warnsWhenStateMdHasMalformedFormat`
   - Updated Javadoc to clarify that a warning is issued when STATUS_PATTERN does not match
2. Added new test `warnsWhenStateMdHasEmptyStatusValue` to verify edge case with empty status values
3. Fixed Javadoc on `warnsWhenStateMdHasInProgressStatus` to accurately describe what it tests
   - Changed from "specifically 'in-progress' and 'blocked'" to "'in-progress' (not closed)"
   - Removed implication that method tests blocked status (separate test covers that)
4. Added new test `warnsWhenStateMdHasStatusKeyWithNoValue` to cover truly-empty STATUS edge case
   - Tests "- **Status:**\n" (no trailing space, no value after colon)
   - STATUS_PATTERN (.+) fails to match because no character after colon-space
   - Warns correctly that status is not closed
5. All 19 VerifyStateInCommitTest tests pass
