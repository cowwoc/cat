<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Workflow: Duplicate Issue Handling

## When to Load

Load this workflow when during exploration an issue is discovered to be a **duplicate** -
another issue already implemented the same functionality.

## Signs of a Duplicate

1. Investigation reveals the functionality already exists
2. Tests for this issue's scenarios already pass
3. Another issue addressed the same root cause

## Handling Process

### 1. Stop Execution

Skip worktree creation and implementation phases entirely.

### 2. Verify Duplicate

Test the specific scenarios from this issue's plan.md to confirm they work:

```bash
# Run tests related to this issue's scenarios
# If all pass, this is confirmed duplicate
```

### 3. Identify Original

Find which issue/commit implemented the fix:

```bash
# Search for when functionality was added
git log --oneline --grep="<related keywords>"

# Check other closed issues in same version
find .cat/issues/v*/v*.*/ -name "index.json" -exec grep -l '"status".*"closed"' {} \;
```

### 4. Update index.json

```json
{
  "status": "closed",
  "resolution": "duplicate (v{major}.{minor}-{original-issue-name})",
  "dependencies": [],
  "blocks": []
}
```

### 5. Commit index.json Only

Duplicate issues have no implementation commit - only the index.json update:

```bash
git add .cat/issues/v{major}/v{major}.{minor}/{issue-name}/index.json
git commit -m "$(cat <<'EOF'
config: close duplicate issue {issue-name}

Duplicate of {original-issue}.
Verification confirmed all scenarios from plan.md pass.
EOF
)"
```

### 6. Release Lock and Cleanup

Same as normal issue completion:

```bash
# Release issue lock
"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "$ISSUE_ID" "${CLAUDE_SESSION_ID}"

# Remove worktree if created
git worktree remove "$WORKTREE_PATH" --force 2>/dev/null || true
```

### 7. Offer Next Issue

Continue to next executable issue as normal.

---

## Resolution Values

| Resolution | Meaning |
|------------|---------|
| `implemented` | Issue closed with code changes |
| `duplicate` | Functionality already exists |
| `obsolete` | Issue no longer needed |

---

## When NOT to Load

- Normal issue execution
- Issue that modifies existing functionality
- Issue that extends existing implementation
