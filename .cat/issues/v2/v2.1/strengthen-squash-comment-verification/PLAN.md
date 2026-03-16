# Plan: Strengthen CONCURRENT_MODIFICATION comment verification

## Type
bugfix

## Goal
Update the CONCURRENT_MODIFICATION section in `plugin/skills/git-squash-agent/first-use.md` to add an explicit
semantic verification step: when rebase auto-resolves comments or documentation, verify the merged text accurately
describes actual code behavior and doesn't contain contradictions or inaccuracies.

## Files to Modify
- `plugin/skills/git-squash-agent/first-use.md`

## Post-conditions
- [ ] The CONCURRENT_MODIFICATION verification section includes an explicit bullet requiring semantic verification
  of auto-resolved comments and documentation
- [ ] The guidance is clear that structural verification alone is insufficient for comments — the merged text must
  accurately describe what the code actually does
- [ ] The file compiles and passes any validation checks

## Sub-Agent Waves

### Wave 1
1. Read `plugin/skills/git-squash-agent/first-use.md` to understand the current CONCURRENT_MODIFICATION verification
   guidance
2. Locate the verification bullets in the CONCURRENT_MODIFICATION section
3. Add an explicit semantic verification bullet: "For comments, documentation, or test descriptions that were
   auto-resolved: verify the merged text accurately describes actual code behavior. Auto-resolution takes the
   target branch's text, which may not reflect changes made on the issue branch. Confirm the resulting comment
   is semantically correct, not just structurally present."
4. Commit the change with message: `bugfix: add semantic comment verification to CONCURRENT_MODIFICATION guidance`
