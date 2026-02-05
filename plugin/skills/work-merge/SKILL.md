---
description: Merge phase for /cat:work - squashes commits, merges to main, cleans up
user-invocable: false
---

# Work Phase: Merge

Run the merge script. Handle complex conflicts if the script returns CONFLICT status.

## Merge Result (Preprocessed)

!`python3 "${CLAUDE_PLUGIN_ROOT}/scripts/work-merge.py" --session-id "${CLAUDE_SESSION_ID}" --project-dir "${CLAUDE_PROJECT_DIR}" --plugin-root "${CLAUDE_PLUGIN_ROOT}" $ARGUMENTS`

**FAIL-FAST:** If no JSON output appears above, preprocessing failed. STOP execution.

## Handle Result

Parse the JSON output above:

- **MERGED**: Return the JSON result directly to the calling skill.
- **CONFLICT**: Read the conflicting files listed in the JSON, apply conflict resolution strategy below, then re-run the merge script via Bash.
- **ERROR**: Return the JSON result directly.

## Conflict Resolution Strategy

When handling conflicts manually:
1. **Code files**: Prefer task branch version (--theirs)
2. **Config files**: Manual merge required
3. **State files**: Merge metadata, keep both contributions
4. **Tests**: Include all tests from both branches

After resolving conflicts:
```bash
git add <resolved-files>
git rebase --continue
```

Then re-run the merge script to complete the remaining steps (merge to base, cleanup, etc.).
