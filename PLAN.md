# Refactor Hook Exit Code and Output Handling

## Goal
Separate hook behavior from preprocessor command behavior:
- **Hooks** (HookHandler implementers): Always exit 0, communicate success/failure through JSON output
- **Preprocessor commands** (utility classes): Exit 0 on success, non-zero on failure, output to stdout/stderr appropriately

## Solution

### 1. Create HookRunner Utility Class

Centralize hook execution with Claude Code compliance:
- Create JvmScope, read HookInput from stdin, run the handler
- Write warnings to stderr, hook output JSON to stdout
- On exception inside the scope: log error, output `{"systemMessage": "Hook failed: ..."}` to stdout
- On scope creation failure: log to stderr (no JsonMapper available)
- Return normally (JVM exits with code 0 by default)

Hook implementations delegate their `main()` method:
```java
public static void main(String[] args) {
  HookRunner.execute(PostToolUseHook::new, args);
}
```

### 2. Update All Hook Classes (main() only)

Replace try-catch logic in all 13 HookHandler implementers with `HookRunner.execute()`:
- PostToolUseHook, PostBashHook, PostReadHook, PreReadHook, PreToolUseHook
- PreAskHook, PreWriteHook, PreIssueHook, SessionStartHook, SessionEndHook
- SubagentStartHook, UserPromptSubmitHook, PostToolUseFailureHook

Keep the `run()` methods unchanged.

### 3. Verify Preprocessor Commands (no changes needed)

GitSquash, GitMergeLinear, GitAmendSafe, GitRebaseSafe, and other util/* classes
already use the correct pattern: exit 0 on success, non-zero on failure.

### 4. Simplify build-jlink.sh

- Remove stdout capture workaround from launcher scripts, use `exec` directly
- Use AOT cache unconditionally (fail-fast, no fallback)
- Make AOT generation fail fast on error instead of silently returning 0

## Acceptance Criteria

- [ ] All HookHandler implementers use HookRunner.execute() in main()
- [ ] All hooks always exit with code 0
- [ ] Hook errors produce JSON with `systemMessage` field on stdout
- [ ] All hook warnings go to stderr
- [ ] Preprocessor commands exit 0/non-zero appropriately
- [ ] build-jlink.sh launchers simplified (no stdout capture, no AOT fallback)
- [ ] All tests pass: `mvn verify`
- [ ] No functional changes to hook behavior - only exit code/output handling
