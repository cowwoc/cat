# Plan

## Goal

Fix hook errors during cat-update-client by merging steps 2, 3, and 4 into a single Bash call so the
client symlink is never dangling when hooks fire.

**Root cause:** Step 2 recreates the symlink pointing to `INSTALL_PATH/client` before that directory
exists (the plugin reinstall wipes it). When post-bash fires after step 2, and when pre-bash fires before
step 3, the symlink is dangling — `pre-bash`, `post-bash`, and `post-tool-use` binaries are not found.

**Fix:** Merge steps 2 (reinstall + symlink recreate), 3 (rm -rf + cp jlink), and 4 (write VERSION) into
one Bash call. By the time any hook fires, the client directory is fully populated.

Note: `rm -rf` must be used directly (not `cat:safe-rm-agent`) because during this window the hook binary
is inaccessible — this was established by the closed issue `fix-update-client-step3-rm-rf`.

## Pre-conditions

(none)

## Post-conditions

- [ ] Steps 2, 3, and 4 execute in a single Bash call in the SKILL.md
- [ ] The symlink recreate (`ln -sfn`) and the jlink copy (`cp -r`) happen in the same call
- [ ] Step prose updated to reflect the merged structure
- [ ] Running cat-update-client produces no pre-bash, post-bash, or post-tool-use hook errors
- [ ] E2E verification: Run cat-update-client end-to-end and confirm all steps complete without hook errors
