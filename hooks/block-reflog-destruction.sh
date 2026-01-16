#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in block-reflog-destruction.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# block-reflog-destruction.sh - Prevent premature destruction of git recovery safety net
#
# ADDED: 2026-01-05 after agent ran "git reflog expire --expire=now --all && git gc --prune=now"
# immediately after git filter-branch, permanently destroying recovery options.
#
# PREVENTS: Premature destruction of reflog which is the primary recovery mechanism
# for history-rewriting operations (filter-branch, rebase, reset).

# Source the CAT hook library for consistent messaging
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Initialize as Bash hook (reads stdin, parses JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

# Use HOOK_COMMAND from init_bash_hook
COMMAND="$HOOK_COMMAND"
if [[ -z "$COMMAND" ]]; then
    echo '{}'
    exit 0
fi

# Check for acknowledgment bypass
if echo "$COMMAND" | grep -qE '# ACKNOWLEDGED:.*([Rr]eflog|gc|prune)'; then
    # User acknowledged the risk - allow the command
    exit 0
fi

# Check for reflog expire with --expire=now (dangerous)
if echo "$COMMAND" | grep -qE 'git\s+reflog\s+expire.*--expire=(now|all|0)'; then
    output_hook_block "
**BLOCKED: Premature reflog destruction detected**

You're attempting to run:
  $COMMAND

This command PERMANENTLY DESTROYS the git reflog, which is your PRIMARY RECOVERY
MECHANISM after history-rewriting operations like:
- git filter-branch
- git rebase
- git reset --hard
- git commit --amend

**Why this is dangerous:**
The reflog keeps references to ALL previous HEAD positions for ~90 days by default.
If something went wrong with filter-branch or rebase, you can recover using:
  git reflog
  git reset --hard HEAD@{N}

Once you run 'git reflog expire --expire=now', this recovery option is GONE FOREVER.

**RECOMMENDED APPROACH:**
1. WAIT at least 24-48 hours after risky operations before cleanup
2. Verify the operation was successful (build works, tests pass, history correct)
3. Keep backup branches instead of relying on immediate gc cleanup
4. Let git's natural expiration handle reflog cleanup (90 days default)

**If you really need to proceed (DANGER):**
Only run this if you have:
- Verified the operation was 100% successful
- Have an external backup (another clone, pushed to remote)
- Are absolutely certain no recovery will be needed

To bypass this block, acknowledge the risk by running the command with a comment:
  # ACKNOWLEDGED: Reflog destruction is intentional, backup exists externally
  git reflog expire --expire=now --all
"
    exit 0
fi

# Check for gc --prune=now (also dangerous, often paired with reflog expire)
if echo "$COMMAND" | grep -qE 'git\s+gc.*--prune=(now|all|0)'; then
    output_hook_block "
**WARNING: Immediate garbage collection detected**

You're attempting to run:
  $COMMAND

This command with --prune=now immediately removes unreachable objects, which includes:
- Commits from aborted rebases
- Commits from reset operations
- Objects that the reflog was protecting

**Risk Level:**
- If reflog is intact: MEDIUM (reflog still protects recent objects)
- If reflog was just expired: CRITICAL (no recovery possible)

**RECOMMENDED APPROACH:**
1. Use 'git gc' without --prune=now (uses 2-week default)
2. Let git handle gc automatically
3. Only use --prune=now after verifying all operations are correct

**If this is paired with reflog expire:**
This combination is EXTREMELY DANGEROUS. See reflog warning above.

To bypass this block, acknowledge the risk by running the command with a comment:
  # ACKNOWLEDGED: Immediate gc prune is intentional, all operations verified
  git gc --prune=now
"
    exit 0
fi

# Check for deletion of .git/refs/original (filter-branch backup)
if echo "$COMMAND" | grep -qE 'rm\s+(-rf?|--recursive)?\s+.*\.git/refs/original'; then
    output_hook_block "
**BLOCKED: Deletion of filter-branch backup detected**

You're attempting to run:
  $COMMAND

The .git/refs/original/ directory is created by git filter-branch as a SAFETY BACKUP.
It contains references to all original commits before they were rewritten.

**Why this is dangerous:**
If the filter-branch operation introduced problems (wrong commits removed,
incorrect message rewriting, etc.), you can recover using:
  git reset --hard refs/original/refs/heads/main

Once you delete .git/refs/original/, this recovery option is GONE FOREVER.

**RECOMMENDED APPROACH:**
1. WAIT until you've verified filter-branch worked correctly
2. Check build passes, tests pass, history looks correct
3. Only delete after explicit user confirmation

To bypass this block, the user must EXPLICITLY request deletion of refs/original.
"
    exit 0
fi

# No dangerous cleanup commands detected
exit 0
