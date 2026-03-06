# Plan: refactor-plugin-bundled-rules

## Current State

`InjectSessionInstructions.java` contains a ~312-line hardcoded Java string with all session behavioral rules
(user input handling, mistake tracking, commit workflow, skill compliance, worktree isolation, etc.). This string
is compiled into the jlink binary and injected on every SessionStart. Rules cannot be edited without recompilation,
cannot target subagents independently, and cannot use the same audience-filtering infrastructure as
`.claude/cat/rules/`.

## Target State

- New directory `plugin/rules/` ships with the plugin and is installed to `${CLAUDE_PLUGIN_ROOT}/rules/`
- Rule files in `plugin/rules/` use the same YAML frontmatter format as `.claude/cat/rules/` (audience filtering
  via `mainAgent:`, `subagent:`, `paths:` fields)
- `InjectRulesToMainAgent` and `InjectRulesToSubAgent` read from TWO sources:
  1. `${CLAUDE_PLUGIN_ROOT}/rules/` — plugin-bundled rules (end-user facing)
  2. `${projectDir}/.claude/cat/rules/` — project-local rules (developer facing)
- Plugin-bundled rules are invisible in the end-user's `.claude/cat/rules/` directory — they are read directly
  from the plugin cache
- `InjectSessionInstructions` hardcoded string is split into individual `.md` files in `plugin/rules/`, each with
  appropriate audience tags
- `InjectSessionInstructions` class is removed (or reduced to only inject the dynamic `Session ID:` line)
- The `Session ID: ${sessionId}` line currently appended by `InjectSessionInstructions.handle()` needs a solution:
  either a variable substitution mechanism in rule files or a minimal dedicated handler

## Satisfies

None — architectural refactor

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Rule content moves from compiled Java to runtime-read files; any parsing error would
  cause rules to not be injected
- **Mitigation:** Validate all rule files parse correctly in tests; keep InjectSessionInstructions as fallback
  until migration is verified end-to-end

## Files to Modify

- `plugin/rules/` — new directory with individual rule `.md` files (split from InjectSessionInstructions)
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectRulesToMainAgent.java` — add plugin rules
  source path
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectRulesToSubAgent.java` — add plugin rules
  source path
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RulesDiscovery.java` — extend to accept multiple
  source directories
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` — remove or reduce
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — remove InjectSessionInstructions
  from handler list (if fully replaced)

## Pre-conditions

- [ ] refactor-rules-injection-classes is closed (InjectRulesToMainAgent and InjectRulesToSubAgent exist)

## Execution Waves

### Wave 1: Create plugin rules directory and split InjectSessionInstructions

- Create `plugin/rules/` directory
- Split `InjectSessionInstructions.INSTRUCTIONS` string into individual `.md` files, each with YAML frontmatter:
  - `plugin/rules/user-input-handling.md` — mainAgent: true
  - `plugin/rules/mistake-handling.md` — mainAgent: true
  - `plugin/rules/commit-before-review.md` — mainAgent: true, subagent: true
  - `plugin/rules/skill-workflow-compliance.md` — mainAgent: true, subagent: true
  - `plugin/rules/persisted-skill-output.md` — mainAgent: true
  - `plugin/rules/work-request-handling.md` — mainAgent: true
  - `plugin/rules/implementation-delegation.md` — mainAgent: true
  - `plugin/rules/worktree-isolation.md` — mainAgent: true, subagent: true
  - `plugin/rules/fail-fast-protocol.md` — mainAgent: true, subagent: true
  - `plugin/rules/verbatim-output-skills.md` — mainAgent: true
  - `plugin/rules/qualified-issue-names.md` — mainAgent: true
  - `plugin/rules/issue-lock-checking.md` — mainAgent: true
  - `plugin/rules/tool-usage-efficiency.md` — mainAgent: true, subagent: true
  - `plugin/rules/background-task-retrieval.md` — mainAgent: true
  - `plugin/rules/git-identity-protection.md` — mainAgent: true, subagent: true
  - Files: `plugin/rules/*.md`
  - Each file gets a license header (per license-header.md rules for `.md` files)

### Wave 2: Extend RulesDiscovery and injection classes

- Extend `RulesDiscovery.getCatRulesForAudience()` to accept a list of source directories instead of one:
  - Files: `RulesDiscovery.java`
  - New signature: `getCatRulesForAudience(List<Path> rulesDirs, ...)` or overload that merges results
  - Discover rules from all directories, merge, then filter by audience
  - Deduplicate if same filename appears in both sources (project-local overrides plugin-bundled)

- Update `InjectRulesToMainAgent` to read from both sources:
  - Files: `InjectRulesToMainAgent.java`
  - Source 1: `${CLAUDE_PLUGIN_ROOT}/rules/` (requires `JvmScope` to provide plugin root path)
  - Source 2: `${projectDir}/.claude/cat/rules/`

- Update `InjectRulesToSubAgent` to read from both sources:
  - Files: `InjectRulesToSubAgent.java`
  - Same two sources as above

- Handle `Session ID:` injection:
  - If variable substitution is used: add `${SESSION_ID}` support to `RulesDiscovery`
  - If minimal handler: keep a small `InjectSessionId` handler that only injects `Session ID: ${sessionId}`
  - Remove or gut `InjectSessionInstructions.java`

- Update `SessionStartHook.java` to remove `InjectSessionInstructions` from handler list

- Add/update tests:
  - Files: test classes for `InjectRulesToMainAgent`, `InjectRulesToSubAgent`, `RulesDiscovery`
  - Test: plugin rules directory is read and content injected
  - Test: project rules and plugin rules are merged correctly
  - Test: audience filtering works across both sources
  - Test: missing plugin rules directory is handled gracefully
  - Test: session ID is still injected

- Run `mvn -f client/pom.xml verify`
- Update STATE.md: status closed, progress 100%
- Commit: `refactor: replace InjectSessionInstructions with plugin-bundled rule files`

## Post-conditions

- [ ] `plugin/rules/` directory exists with individual rule `.md` files
- [ ] All content from `InjectSessionInstructions.INSTRUCTIONS` is present in `plugin/rules/*.md` files
- [ ] `InjectRulesToMainAgent` reads from both `${CLAUDE_PLUGIN_ROOT}/rules/` and `${projectDir}/.claude/cat/rules/`
- [ ] `InjectRulesToSubAgent` reads from both sources
- [ ] Plugin-bundled rules are NOT visible in end-user's `.claude/cat/rules/` directory
- [ ] Rules with `subagent: true` are now injected into subagents (new capability vs hardcoded string)
- [ ] `Session ID:` line is still injected into every session
- [ ] `InjectSessionInstructions` class is removed or reduced to session-ID-only
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
- [ ] E2E: Start a session and confirm all behavioral rules appear in context from plugin rules source
