---
category: unit
---
## Turn 1
Please rename the constant `MAX_RETRY_COUNT` to `MAX_RETRIES` across these 5 source files:

1. src/main/java/RetryPolicy.java — replace all occurrences of MAX_RETRY_COUNT
2. src/main/java/HttpClient.java — replace all occurrences of MAX_RETRY_COUNT
3. src/main/java/DatabasePool.java — replace all occurrences of MAX_RETRY_COUNT
4. src/test/java/RetryPolicyTest.java — replace all occurrences of MAX_RETRY_COUNT
5. src/test/java/HttpClientTest.java — replace all occurrences of MAX_RETRY_COUNT

Each file uses MAX_RETRY_COUNT independently; no file's updated content depends on seeing the
result of editing another.

## Assertions
1. The Skill tool was invoked
2. All 5 Edit tool calls were issued in a single LLM response
3. No Edit call for any of the 5 files was issued in a separate response after waiting for
   another file's edit result
