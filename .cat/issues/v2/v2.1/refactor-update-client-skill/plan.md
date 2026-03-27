# Plan

## Goal

Update cat-update-client skill: use cat:safe-rm-agent for cache removal instead of raw rm -rf, and chain steps 2-5
into minimal Bash calls.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged (cat-update-client still builds, reinstalls plugin, deploys jlink runtime, and
  verifies)
- [ ] Tests passing
- [ ] Code quality improved (fewer Bash calls, uses safe-rm pattern instead of raw rm -rf)
- [ ] E2E verification: Run the updated cat-update-client skill and verify it completes successfully without rm -rf
  hook blocks
