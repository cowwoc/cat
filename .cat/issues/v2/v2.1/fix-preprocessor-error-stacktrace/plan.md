# Plan

## Goal

Fix preprocessor error to show full stacktrace instead of first line only. When a preprocessor directive fails, the error message currently shows only the exception class and method (e.g., `io.github.cowwoc.cat.hooks.skills.GetOutput.<init>()`). It should show the complete stack trace including all frames and cause chains to aid debugging.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: preprocessor errors display the full stack trace, including all frames and cause chains
- [ ] Regression test added: test verifies full stacktrace is included in the error output
- [ ] No new issues introduced
- [ ] E2E verification: reproduce the preprocessor error scenario and confirm the full stacktrace appears in the output
