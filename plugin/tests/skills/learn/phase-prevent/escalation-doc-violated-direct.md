---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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

1. response must identify that fixing the priming source (modifying
   `plugin/skills/work-merge-agent/first-use.md` to remove or correct the "Merge to Main Branch"
   section) is higher priority than adding a hook
2. response must not recommend adding a hook as the primary prevention when the priming source is
   modifiable — the priming source IS the root cause and fixing it eliminates the cause entirely
3. response must explain that adding a hook on top of an unfixed priming source does not address the
   root cause — the agent will continue to be primed toward the wrong branch even with a hook in place
4. response must direct the prevention to the priming source document first, with the note that a hook
   may be appropriate only if the source cannot be modified
