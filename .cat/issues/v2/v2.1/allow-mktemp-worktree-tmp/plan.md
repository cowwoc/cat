# Plan: allow-mktemp-worktree-tmp

## Goal

Allow `BlockWorktreeIsolationViolation` to recognize variables assigned via
`$(mktemp -p <worktree-relative-path>)` as safe write targets, and update all `mktemp` usages in
plugin skill files, migration scripts, and hooks to create files inside `.cat/work/tmp` within the
issue worktree instead of the system temp directory.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** `ShellParser.parseScriptAssignments()` only handles literal string assignments; the
  new mktemp detection requires a separate parse pass. If the path matching logic is wrong, the hook
  could allow violations or continue blocking legitimate writes.
- **Mitigation:** Add test cases covering both the allowed and blocked paths. All tests must pass
  before the approval gate.

## Files to Modify

### Java (hook + parser + tests)

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`
  — add a new helper that parses `VARNAME=$(mktemp -p <path>)` patterns from the script; during
  variable expansion, if a variable matches one of these patterns and the resolved path is inside the
  worktree, treat the variable as a known-safe path so the write target is allowed instead of blocked.
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`
  — add `parseMktempAssignments(String script)` that extracts `{varName → mktemp -p path}` mappings
  from lines of the form `VARNAME=$(mktemp [-p <path>] ...)` or `VARNAME=\`mktemp ...\``.
- `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`
  — add test cases:
    1. `tmp=$(mktemp -p .cat/work/tmp) && echo foo > "$tmp"` — allowed (mktemp path inside worktree)
    2. `tmp=$(mktemp -p .cat/work/tmp) && echo foo > "$tmp"` with absolute worktree prefix — allowed
    3. `tmp=$(mktemp) && echo foo > "$tmp"` — blocked (mktemp with no `-p`; path unresolvable)
    4. `tmp=$(mktemp -p /tmp) && echo foo > "$tmp"` — allowed (`/tmp` is outside project dir)
    5. `tmp=$(mktemp -p /workspace/plugin) && echo foo > "$tmp"` — blocked (path inside project but
       outside worktree)

### Plugin skill files

- `plugin/skills/add-agent/first-use.md`
  — lines 553, 579: change `mktemp --suffix=.md` and `mktemp /tmp/cat-index-XXXXXX.json` to
  `mktemp -p .cat/work/tmp --suffix=.md` and `mktemp -p .cat/work/tmp --suffix=.json`; ensure
  `.cat/work/tmp` is created before the call.
- `plugin/skills/work-implement-agent/first-use.md`
  — lines 75, 143: change `BANNER_STDERR_FILE=$(mktemp)` to
  `BANNER_STDERR_FILE=$(mktemp -p .cat/work/tmp)` in both locations; ensure `.cat/work/tmp` is
  created before the call.
- `plugin/skills/learn/first-use.md`
  — line 456: change `PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)` to
  `PHASE3_TMP=$(mktemp -p .cat/work/tmp --suffix=.json)`.
- `plugin/skills/learn/phase-record.md`
  — line 20: change `PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)` to
  `PHASE3_TMP=$(mktemp -p .cat/work/tmp --suffix=.json)`.
- `plugin/skills/git-squash-agent/first-use.md`
  — line 234: change `SQUASH_TMPDIR=$(mktemp -d)` to `SQUASH_TMPDIR=$(mktemp -d -p .cat/work/tmp)`.

### Plugin migration and hook scripts

- `plugin/migrations/2.1.sh`
  — lines 954, 961: change both `tmp=$(mktemp)` calls to `tmp=$(mktemp -p .cat/work/tmp)` and
  ensure `.cat/work/tmp` is created before each call.
- `plugin/hooks/session-start.sh`
  — lines 164–165: change `temp=$(mktemp --suffix=.tar.gz)` and `temp_sha256=$(mktemp --suffix=.sha256)`
  to use `mktemp -p "${CLAUDE_PROJECT_DIR}/.cat/work/tmp"` (session-start runs in the main workspace,
  not a worktree, so `.cat/work/tmp` must be anchored to `CLAUDE_PROJECT_DIR`); ensure the directory
  exists before the call.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1 — Update `ShellParser` to detect `$(mktemp -p <path>)` assignments

- Add `parseMktempAssignments(String script)` to `ShellParser`:
  - Regex: lines matching `^\s*([A-Za-z_][A-Za-z0-9_]*)=\$\(mktemp\b([^)]*)\)` or
    backtick form `` `mktemp ...` ``
  - Extract the `-p <path>` argument from the mktemp options, if present
  - Return a `Map<String, String>` mapping variable name → resolved directory path (or `null` if no
    `-p` was given)
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`

### Job 2 — Wire mktemp detection into `BlockWorktreeIsolationViolation`

- In `check()`, after calling `ShellParser.parseScriptAssignments()`, also call
  `ShellParser.parseMktempAssignments()` and merge the result into `mergedLookup`:
  - For each entry `varName → mktemp -p <dir>`, resolve `<dir>` relative to `workingDirectory`
  - If the resolved directory is inside the worktree, add an entry to the merged lookup mapping
    `varName → <resolved-dir>/<some-placeholder>` (any path inside the worktree suffices for the
    subsequent worktree check; the actual file name is unknown at hook evaluation time, but the
    directory guarantees the file will be inside the worktree)
  - If no `-p` was given, do NOT add an entry (the variable remains unresolved → conservative block)
  - Files:
    `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`

### Job 3 — Add test cases for mktemp-aware isolation check

- Add 5 test cases to `BlockWorktreeIsolationViolationTest` as described in **Files to Modify**.
  - Files:
    `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`

### Job 4 — Update plugin skill files

- Update the 7 mktemp call sites in skill `first-use.md` and `phase-record.md` files listed in
  **Files to Modify**, prepending `mkdir -p .cat/work/tmp` before each block that uses mktemp.
  - Files: `plugin/skills/add-agent/first-use.md`, `plugin/skills/work-implement-agent/first-use.md`,
    `plugin/skills/learn/first-use.md`, `plugin/skills/learn/phase-record.md`,
    `plugin/skills/git-squash-agent/first-use.md`

### Job 5 — Update plugin migration and hook scripts

- Update `plugin/migrations/2.1.sh` (both mktemp calls) and `plugin/hooks/session-start.sh`
  (both mktemp calls) as described in **Files to Modify**.
  - Files: `plugin/migrations/2.1.sh`, `plugin/hooks/session-start.sh`

### Job 6 — Run full build and verify

- Run `mvn -f client/pom.xml verify -e` and confirm all tests pass (exit code 0).

## Post-conditions

- [ ] `ShellParser.parseMktempAssignments()` method exists and is covered by unit tests
- [ ] `BlockWorktreeIsolationViolationTest` includes all 5 new mktemp-aware test cases and all pass
- [ ] All 7 mktemp call sites in plugin skill files use `mktemp -p .cat/work/tmp`
- [ ] Both mktemp calls in `plugin/migrations/2.1.sh` use `mktemp -p .cat/work/tmp`
- [ ] Both mktemp calls in `plugin/hooks/session-start.sh` use `mktemp -p "${CLAUDE_PROJECT_DIR}/.cat/work/tmp"`
- [ ] `mkdir -p .cat/work/tmp` (or equivalent) precedes every updated mktemp call
- [ ] `mvn -f client/pom.xml verify -e` exits 0 with no test or lint failures
- [ ] E2E: A Bash command of the form `tmp=$(mktemp -p .cat/work/tmp) && echo foo > "$tmp"` issued
  from within an active issue worktree is allowed by the hook without producing a block message
