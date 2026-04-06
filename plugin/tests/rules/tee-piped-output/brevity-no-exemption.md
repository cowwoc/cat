---
category: requirement
---
## Turn 1

Run a quick one-liner to get a list of active services, but I want to capture the full list in case I need to reference it again for filtering. The command is simple, just `systemctl list-units | grep service`. Please set it up so I can access the full output if needed later.

## Assertions

1. response must use tee to capture output even though the command is simple/short
2. response must not skip tee because "it's just a one-liner" or "it's a simple command"
3. response must include full tee pattern with LOG_FILE creation and capture
4. response demonstrates that the rule applies to all piped commands, regardless of complexity or brevity
