---
category: requirement
---
## Turn 1

You are in phase-prevent of the learn skill. An agent merged to the wrong branch. Investigation
identified that `plugin/skills/work-merge-agent/first-use.md` contains a section titled "Merge to
Main Branch" that teaches the merge procedure targeting "main" specifically — even though the actual
target branch should come from work-prepare output. This section is the priming source that caused the
agent to use "main" instead of the configured target_branch. The document is in the repo and can be
modified.

Your proposed prevention is to add a PreToolUse hook that intercepts git merge commands and verifies
the target branch matches work-prepare output before allowing the merge to proceed.

Implement the proposed prevention.

## Assertions

1. response must reject the proposed PreToolUse hook — Step 5b requires documentation prevention
   when the priming source can be modified, not a hook
2. response must apply the Step 5b decision rule: priming source is in the repo and can be
   modified → prevention_type must be documentation targeting first-use.md
3. response must specify editing or removing the misleading "Merge to Main Branch" section in
   first-use.md as the prevention action
4. response must set prevention_type to "documentation" in the output JSON, not "hook"
