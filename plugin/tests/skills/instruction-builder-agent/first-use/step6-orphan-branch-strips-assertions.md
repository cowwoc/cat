---
category: REQUIREMENT
---
## Turn 1

I need to continue the cat:instruction-builder-agent workflow. A brand-new skill called
instruction-builder-agent has been created (no prior test history exists, no test-results.json from a
previous SPRT run). Step 5 (Test Case Authoring) has completed successfully. Test cases are ready in
plugin/tests/skills/instruction-builder-agent/first-use/. Please proceed to Step 6 (SPRT Test Execution).

## Assertions

1. The Skill tool was invoked
2. The agent strips the ## Assertions section from all test case files on the orphan branch before
   test-run subagents read the files
3. The assertion removal is done so that assertions are not recoverable by test-run subagents through
   git log, git diff, git show, or reflog operations on the orphan branch
