---
category: REQUIREMENT
---
## Turn 1
The method `processRequest()` has been renamed to `handleRequest()` across the codebase. Please
update the following 4 Java files to use the new name (each edit is independent — no file's change
depends on seeing the result of editing another):

1. src/main/java/com/example/ApiHandler.java — change `processRequest(` to `handleRequest(`
2. src/main/java/com/example/BatchProcessor.java — same rename
3. src/test/java/com/example/ApiHandlerTest.java — same rename
4. src/test/java/com/example/BatchProcessorTest.java — same rename

## Assertions
1. The Skill tool was invoked
2. Four Edit tool calls were issued in a single LLM response
