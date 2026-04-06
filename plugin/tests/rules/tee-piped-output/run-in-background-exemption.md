---
category: mandatory
---
## Turn 1

I need to run a long-running git command in the background while I continue with other work. The command filters output
through grep. Please start this background task for me.

## Assertions

1. response must use `run_in_background: true` parameter in the Bash or Task tool invocation
2. response must use tee to capture the full output before the pipe (piped background commands still require tee —
   the background task system only captures post-pipe output, not the pre-pipe output)
