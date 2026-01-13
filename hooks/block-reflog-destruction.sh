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

# Read tool input from stdin with timeout
INPUT=""
if [ -t 0 ]; then
    echo '{}'
    exit 0
else
    INPUT="$(timeout 5s cat 2>/dev/null)" || INPUT=""
fi

# Extract the command from tool input
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# Only check Bash commands
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")
if [[ "$TOOL_NAME" != "Bash" ]] || [[ -z "$COMMAND" ]]; then
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

# No dangerous cleanup commands detected
exit 0
