# Plan: wire-remaining-java-equivalents

## Current State
Six utility scripts have Java equivalents in `client/src/main/java/.../hooks/util/` but lack `main()` methods,
jlink launcher entries, and skill reference updates. The old scripts remain in `plugin/scripts/` and are still
referenced by skill definitions.

## Target State
All six Java utility classes are wired as jlink launchers. All skill references updated to use Java launchers.
Old ported scripts removed.

## Satisfies
None - infrastructure/tech debt (completes parent issue `port-utility-scripts`)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill invocations change from Python/bash to Java launchers; must verify output parity
- **Mitigation:** Wire and test each launcher individually; verify output matches original

## Scripts to Wire

| Old Script | Java Class | Launcher Name | Entry Point |
|-----------|-----------|---------------|-------------|
| `work-prepare.py` | `util.WorkPrepare` | `work-prepare` | `main()` method |
| `wrap-markdown.py` | `util.MarkdownWrapper` | `wrap-markdown` | `main()` method |
| `batch-read.sh` | `util.BatchReader` | `batch-read` | `main()` method |
| `monitor-subagents.sh` | `util.SubagentMonitor` | `monitor-subagents` | `main()` method |
| `register-hook.sh` | `util.HookRegistrar` | `register-hook` | `main()` method |
| `validate-status-alignment.sh` | `util.StatusAlignmentValidator` | `validate-status-alignment` | `main()` method |

## Skill References to Update

| File | Line(s) | Old Reference | New Reference |
|------|---------|---------------|---------------|
| `plugin/skills/work/first-use.md` | 59 | `python3 "${CLAUDE_PLUGIN_ROOT}/scripts/work-prepare.py"` | `"${CLAUDE_PLUGIN_ROOT}/client/bin/work-prepare"` |
| `plugin/skills/work-prepare/first-use.md` | 8, 14 | `plugin/scripts/work-prepare.py` | Java class / launcher reference |
| `plugin/skills/register-hook/first-use.md` | 188, 213, 243 | `~/.claude/scripts/register-hook.sh` | `"${CLAUDE_PLUGIN_ROOT}/client/bin/register-hook"` |
| `plugin/skills/monitor-subagents/first-use.md` | 92 | `${CLAUDE_PLUGIN_ROOT}/scripts/monitor-subagents.sh` | Launcher reference |
| `plugin/skills/batch-read/first-use.md` | 63 | `batch-read.sh` | Launcher reference |
| `plugin/emoji-widths.json` | 3 | `Run scripts/measure-emoji-widths.sh` | Note script is dev-only contribution tool |

## Files to Modify
- `client/src/main/java/.../util/WorkPrepare.java` - Add main() method
- `client/src/main/java/.../util/MarkdownWrapper.java` - Add main() method
- `client/src/main/java/.../util/BatchReader.java` - Add main() method
- `client/src/main/java/.../util/SubagentMonitor.java` - Add main() method
- `client/src/main/java/.../util/HookRegistrar.java` - Add main() method
- `client/src/main/java/.../util/StatusAlignmentValidator.java` - Add main() method
- `client/build-jlink.sh` - Add 6 launcher entries to HANDLERS array
- `plugin/skills/work/first-use.md` - Update work-prepare.py reference
- `plugin/skills/work-prepare/first-use.md` - Update documentation references
- `plugin/skills/register-hook/first-use.md` - Update 3 script references
- `plugin/skills/monitor-subagents/first-use.md` - Update script reference
- `plugin/skills/batch-read/first-use.md` - Update diagram reference
- `plugin/emoji-widths.json` - Update comment
- `plugin/scripts/work-prepare.py` - Remove
- `plugin/scripts/wrap-markdown.py` - Remove
- `plugin/scripts/batch-read.sh` - Remove
- `plugin/scripts/monitor-subagents.sh` - Remove
- `plugin/scripts/register-hook.sh` - Remove
- `plugin/scripts/validate-status-alignment.sh` - Remove

## Pre-conditions
- [ ] All Java util classes exist with correct business logic
- [ ] Existing jlink build works (`client/build-jlink.sh` succeeds)

## Execution Steps
1. **Add main() methods to all 6 Java util classes:** Follow the existing pattern from classes like `GetRenderDiffOutput.java` or `SessionAnalyzer.java` that already have main() methods. Each main() should parse CLI arguments, invoke the class logic, and write output to stdout.
2. **Add launcher entries to build-jlink.sh:** Add 6 entries to the HANDLERS array: `work-prepare:util.WorkPrepare`, `wrap-markdown:util.MarkdownWrapper`, `batch-read:util.BatchReader`, `monitor-subagents:util.SubagentMonitor`, `register-hook:util.HookRegistrar`, `validate-status-alignment:util.StatusAlignmentValidator`.
3. **Build jlink image:** Run `client/build-jlink.sh` and verify all 6 new launchers appear in `client/bin/`.
4. **Update skill references:** Replace all old script references in skill first-use.md files with new Java launcher paths per the reference table above.
5. **Update emoji-widths.json comment:** Change `Run scripts/measure-emoji-widths.sh to contribute` to note the script is a developer contribution tool (keep the script itself).
6. **Remove old ported scripts:** Delete `work-prepare.py`, `wrap-markdown.py`, `batch-read.sh`, `monitor-subagents.sh`, `register-hook.sh`, `validate-status-alignment.sh` from `plugin/scripts/`.
7. **Run tests:** Execute `mvn -f client/pom.xml test` and verify all pass.
8. **Verify output parity:** Run `work-prepare` launcher with `--arguments ""` and verify it produces valid JSON matching the Python script output contract.

## Post-conditions
- [ ] All 6 Java util classes have `public static void main(String[])` methods
- [ ] `client/build-jlink.sh` HANDLERS array contains all 6 launcher entries
- [ ] All 6 launchers exist in `client/bin/` after build
- [ ] All skill first-use.md files reference Java launchers, not old scripts
- [ ] Old ported scripts (6 files) removed from `plugin/scripts/`
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: `work-prepare` launcher with `--arguments ""` produces valid JSON with a `status` field
- [ ] E2E: `wrap-markdown` launcher wraps input text at 120 characters