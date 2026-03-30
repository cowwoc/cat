# Plan

## Goal

Fix cat-update-client Step 3: it incorrectly branches on HOOK_STATUS, offering cat:safe-rm-agent when HOOKS_ACTIVE.
This is wrong because the plugin reinstall in Step 2 wipes `installPath/client`, leaving the `pre-bash` binary
inaccessible via the dangling symlink. The `BlockUnsafeRemoval` hook cannot fire, and `cat:safe-rm-agent` cannot load
(no `get-skill` binary). Step 3 must always use `rm -rf` directly — it is both safe (hook can't fire) and typically
a no-op (directory already gone).

Additionally, the HOOK_STATUS check block in Step 2 exists only to drive the now-removed Step 3 branching, so it
must also be removed.

## Pre-conditions

(none)

## Post-conditions

- [ ] Step 3 always uses `rm -rf` directly with no HOOK_STATUS branching
- [ ] Step 3 prose explains why `rm -rf` is safe (plugin reinstall wipes `installPath/client`, hook binary inaccessible)
- [ ] HOOK_STATUS check removed from Step 2 (it was only used to drive the now-removed Step 3 branching)
- [ ] Running cat-update-client produces no Bash hook errors
- [ ] E2E: Execute cat-update-client end-to-end and confirm all steps complete without hook errors

## Jobs

### Job 1

Edit `.claude/skills/cat-update-client/SKILL.md` in the worktree at
`/workspace/.cat/work/worktrees/2.1-fix-update-client-step3-rm-rf/.claude/skills/cat-update-client/SKILL.md`:

**Change 1 — Remove HOOK_STATUS check from Step 2:**

Remove the following block from Step 2 (between the step heading and the "perform the symlink removal" paragraph):

```
Check whether the plugin was previously installed by testing if the `pre-bash` binary exists.
**This check must happen before reinstalling** — the reinstall wipes the `client/` directory, making the
binary unavailable afterward.

```bash
test -f /home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/pre-bash && echo "HOOKS_ACTIVE" || echo "NO_HOOKS"
```

Store the result as HOOK_STATUS for use in Step 3.

Then perform
```

Replace with just:

```
Perform
```

(i.e., change "Then perform" to "Perform" since the introductory sentence is gone)

**Change 2 — Update Step 2 heading:**

Change heading from:

```
### 2. Check Hook Status and Reinstall the Plugin
```

To:

```
### 2. Reinstall the Plugin
```

**Change 3 — Replace Step 3 removal logic:**

Replace the current Step 3 opening prose and dual-branch conditional:

```
Remove the old client directory using the HOOK_STATUS captured in Step 2. The symlink was recreated at
the end of Step 2, so `cat:safe-rm-agent` is usable again:

- If HOOK_STATUS was `HOOKS_ACTIVE`: use the `cat:safe-rm-agent` skill to remove the directory:

  ```
  /cat:safe-rm-agent /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
  ```

- If HOOK_STATUS was `NO_HOOKS`: use `rm -rf` directly:

  ```bash
  rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
  ```
```

With:

```
Remove the old client directory. After the plugin reinstall in Step 2, `installPath/client` no longer exists
(the reinstall wipes it), so this is typically a no-op. Use `rm -rf` directly — the hook binary is inaccessible
at this point because the symlink target (`installPath/client`) was wiped by the reinstall:

```bash
rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
```
```

After making all three edits:

- Verify the final SKILL.md looks correct: Step 2 heading is "### 2. Reinstall the Plugin", no HOOK_STATUS check block, Step 3 uses `rm -rf` directly with explanation
- Update `index.json` at `/workspace/.cat/work/worktrees/2.1-fix-update-client-step3-rm-rf/.cat/issues/v2/v2.1/fix-update-client-step3-rm-rf/index.json` — set `"status": "closed"` (this belongs in the same commit as the implementation per CLAUDE.md)
- Commit in the worktree: `cd /workspace/.cat/work/worktrees/2.1-fix-update-client-step3-rm-rf && git add .claude/skills/cat-update-client/SKILL.md .cat/issues/v2/v2.1/fix-update-client-step3-rm-rf/index.json && git commit -m "bugfix: fix cat-update-client Step 3 to always use rm -rf directly"`

Return the commit hash when done.
