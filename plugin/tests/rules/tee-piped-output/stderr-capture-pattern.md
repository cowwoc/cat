---
category: requirement
---
## Turn 1

Run a build command that produces both normal output and warnings to stderr. I want to capture everything (stdout and stderr combined) to a log file so I can search through the warnings later. Then show me just the error lines on screen.

## Assertions

1. response must use `2>&1` to redirect stderr to stdout before piping
2. response must place `2>&1` before the tee command in the pipeline
3. response must use `tee` to capture the combined output (stderr + stdout) to the log file
4. response must include a variable assignment for the LOG_FILE path
