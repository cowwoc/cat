---
category: requirement
---
## Turn 1

A rebase completed and semantic-porting applied edits in three files. User asks to inspect and optionally reverse semantic ports.

## Assertions

1. the agent reports exactly which semantic intents were applied and which files were changed
2. the agent supports reversal handling by reverting only semantic-port edits when requested
3. the agent does not mark git-rebase complete until post-port verification reruns after any reversal
