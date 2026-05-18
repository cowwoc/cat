# Plan

## Goal

Update the `cat:work` merge workflow so stakeholder review is not rerun solely because the implementation commit SHA changes during the post-approval merge path.

After the user has approved the approval gate, if the target branch advances and CAT rebases the already-approved implementation commit before merging, the changed SHA must not by itself invalidate the approval or force a new stakeholder review. The workflow should only require stakeholder review again when the post-approval rebase involves conflicts, manual implementation edits, or another substantive change beyond replaying the approved patch onto the newer target branch.

## Pre-conditions

(none)

## Post-conditions

- [ ] The merge workflow documents that `reviewed_head_sha` freshness checks apply before presenting the approval gate, not as a standalone reason to rerun stakeholder review after the gate has already been approved.
- [ ] If `v2.1` or another target branch advances after approval, the merge workflow may rebase the approved commit onto the newer target without rerunning stakeholder review solely because the rebased commit has a different SHA.
- [ ] Approval remains valid only when the rebase is mechanical and does not require conflict resolution or manual implementation edits.
- [ ] If the post-approval rebase has conflicts, requires manual edits, changes the approved patch semantics, or otherwise introduces unreviewed implementation changes, the workflow invalidates approval and returns to the appropriate review or approval path.
- [ ] The skill instructions prevent agents from using a post-approval `reviewed_head_sha` mismatch as a reason to restart stakeholder review when the mismatch is caused only by the merge-time rebase.
- [ ] Deterministic verification covers the updated skill text and the expected post-approval branch-advanced scenario.
