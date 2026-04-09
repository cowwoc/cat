# Plan: Suppress Known-Harmless Jackson AOT Warnings

## Goal
The AOT cache creation step in `build-jlink.sh` emits known-harmless warnings because Jackson's
SQL extension classes (`JavaSqlBlobSerializer`, `JavaSqlDateSerializer`, `JavaBeansAnnotationsImpl`)
reference `java.sql` types not included in the jlink image. These warnings are expected and safe to
suppress, but all other AOT warnings must still be visible.

Capture the AOT cache creation stderr, filter out the specific known-harmless lines by their full
message text, and forward the remainder to stderr. On failure, show the full unfiltered stderr so
nothing is hidden during diagnosis.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The grep filter becomes a maintained allowlist; new Jackson SQL serializers added in
  future upgrades would appear as new warnings until the pattern is updated
- **Mitigation:** Patterns match full message text (not just class names), so unrelated warnings
  mentioning the same class names are not accidentally suppressed

## Files to Modify
- `client/build-jlink.sh` — update `generate_startup_archives()` to capture and filter AOT create stderr

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
In `generate_startup_archives()`, replace the current AOT cache creation block:

```bash
if ! "$java_bin" \
  -XX:AOTMode=create \
  -XX:AOTConfiguration="$aot_config" \
  -XX:AOTCache="$aot_cache" \
  -XX:+AOTClassLinking \
  -m "$(handler_main PreToolUseHook)" \
  2>&1; then
  error "Failed to create AOT cache"
fi
```

With a version that:
1. Captures stderr to a temp file (using `mktemp`, cleaned up via `trap`)
2. On failure: prints full unfiltered stderr to stderr, then calls `error`
3. On success: filters the captured stderr through `grep -Ev` with a pattern matching the exact
   warning message text for the three known-harmless classes, forwards remaining lines to stderr
   (`|| true` prevents grep from failing when all lines are filtered)

Exact lines to suppress (match by full message text, not just class name):
```
[warning][aot] Preload Warning: Verification failed for tools.jackson.databind.ext.sql.JavaSqlBlobSerializer
[warning][aot] Preload Warning: Verification failed for tools.jackson.databind.ext.sql.JavaSqlDateSerializer
[warning][aot] Skipping tools/jackson/databind/ext/sql/JavaSqlBlobSerializer
[warning][aot] Skipping tools/jackson/databind/ext/sql/JavaSqlDateSerializer
[warning][aot] Skipping tools/jackson/databind/ext/beans/JavaBeansAnnotationsImpl
```

## Post-conditions
- [ ] AOT cache creation stderr is captured and filtered
- [ ] The five known-harmless warning lines no longer appear in `build-jlink.sh` output
- [ ] All other AOT warnings still appear
- [ ] On AOT cache creation failure, full unfiltered stderr is shown
- [ ] Build completes successfully (`mvn -f client/pom.xml verify -e` passes)
