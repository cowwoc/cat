# Plan: Replace hook.sh with jlink launcher

## Problem
The optimize-execution skill references a non-existent `hook.sh run_handler` pattern to invoke
`SessionAnalyzer`. This pattern was superseded by jlink-generated launcher scripts. The skill fails on
first invocation because `hook.sh` doesn't exist.

## Satisfies
None (infrastructure fix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - straightforward reference update plus handler registration
- **Mitigation:** Existing test suite validates build

## Files to Modify
- `hooks/build-jlink.sh` - Add `session-analyzer` to HANDLERS array
- `plugin/skills/optimize-execution/first-use.md` - Replace `hook.sh run_handler` with jlink launcher path

## Acceptance Criteria
- [ ] `session-analyzer` entry added to HANDLERS array in `hooks/build-jlink.sh`
- [ ] `plugin/skills/optimize-execution/first-use.md` uses `${CLAUDE_PLUGIN_ROOT}/hooks/bin/session-analyzer`
  instead of `hook.sh run_handler`
- [ ] No remaining references to `hook.sh` as an executable invocation in plugin source files
- [ ] `mvn -f hooks/pom.xml test` passes

## Execution Steps
1. **Add SessionAnalyzer to HANDLERS array:** In `hooks/build-jlink.sh`, add
   `"session-analyzer:util.SessionAnalyzer"` to the HANDLERS array
2. **Update optimize-execution skill:** In `plugin/skills/optimize-execution/first-use.md` line 39, replace
   `"${CLAUDE_PLUGIN_ROOT}/hooks/hook.sh" run_handler io.github.cowwoc.cat.hooks.util.SessionAnalyzer "$SESSION_FILE"`
   with `"${CLAUDE_PLUGIN_ROOT}/hooks/bin/session-analyzer" "$SESSION_FILE"`
3. **Run tests:** Execute `mvn -f hooks/pom.xml test` to verify no regressions
