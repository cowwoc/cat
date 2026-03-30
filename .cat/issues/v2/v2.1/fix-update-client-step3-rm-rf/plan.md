# Plan

## Goal

Fix cat-update-client Step 3: it incorrectly branches on HOOK_STATUS, offering cat:safe-rm-agent when HOOKS_ACTIVE. This is wrong because the plugin reinstall in Step 2 wipes installPath/client, leaving the pre-bash binary inaccessible via the dangling symlink. The BlockUnsafeRemoval hook cannot fire, and cat:safe-rm-agent cannot load (no get-skill binary). Step 3 must always use rm -rf directly — it is both safe (hook can't fire) and typically a noop (directory already gone).

## Pre-conditions

(none)

## Post-conditions

- [ ] Step 3 always uses `rm -rf` directly with no HOOK_STATUS branching
- [ ] Step 3 prose explains why `rm -rf` is safe (plugin reinstall wipes `installPath/client`, hook binary inaccessible)
- [ ] HOOK_STATUS check removed from Step 2 (it was only used to drive the now-removed Step 3 branching)
- [ ] Running cat-update-client produces no Bash hook errors
- [ ] E2E: Execute cat-update-client end-to-end and confirm all steps complete without hook errors
