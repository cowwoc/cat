# Plan: enforce-skill-loading-via-hook

## Goal

Add a `RequireSkillForCommand` BashHandler that blocks guarded Bash commands (e.g., `git rebase`,
`git commit --amend`, `rm -rf`) unless the required skill has already been loaded in the current
agent's session. A JSON registry file maps command patterns to required skills, achieving 100%
compliance without relying on instruction-based reminders.

## Parent Requirements

None

## Approaches

### A: JSON Registry File + BashHandler (chosen)

- **Risk:** LOW
- **Scope:** 4 files (minimal)
- **Description:** A JSON config file maps regex patterns to required skill names. A new `RequireSkillForCommand`
  BashHandler reads the registry at construction time, matches incoming commands, checks the session's
  `skills-loaded` marker file, and blocks if the skill is not loaded. The registry lives at
  `plugin/config/skill-command-registry.json` — extensible without code changes.

### B: Hardcoded Java Map (rejected)

- **Risk:** LOW
- **Scope:** 2 files
- **Description:** A static `Map<Pattern, String>` in Java. Adding new mappings requires a code change and
  rebuild. Rejected because the post-conditions require a JSON file extensible without code changes.

### C: Instruction-Only Approach (rejected)

- **Risk:** HIGH
- **Scope:** 0 files
- **Description:** CLAUDE.md rules and skill trigger words only. Rejected because LLM compliance is never
  100%, especially under context pressure or in subagents. This is the current broken state.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** False positives if patterns are too broad; circular dependency if a skill's own implementation
  runs guarded commands
- **Mitigation:** Patterns use `\b` word boundaries. Circular dependency is avoided automatically: by the time
  the skill's implementation runs the guarded command, the skill is already loaded (the skill marker is written
  when the Skill tool is invoked, before any Bash calls).

## Files to Modify

- `plugin/config/skill-command-registry.json` — NEW: command pattern → required skill mapping
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java` — NEW: BashHandler
  implementation
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java` — register `RequireSkillForCommand`
  in handler list
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RequireSkillForCommandTest.java` — NEW: unit tests

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `plugin/config/skill-command-registry.json`
  - Files: `plugin/config/skill-command-registry.json`
  - Content: JSON object with a `"guards"` array of objects, each with `"pattern"` (regex string) and
    `"skill"` (skill name) fields. Use patterns from the table below.
  - Initial guards (6 entries):

    | Pattern | Required Skill | Rationale |
    |---------|---------------|-----------|
    | `\bgit\s+rebase\b` | `cat:git-rebase-agent` | MANDATORY per CLAUDE.md rules |
    | `\bgit\s+commit\b.*--amend\b` | `cat:git-amend-agent` | MANDATORY per CLAUDE.md rules |
    | `\bgit\s+push\b.*--force\b` | `cat:validate-git-safety-agent` | MANDATORY per CLAUDE.md rules |
    | `\brm\s+-[a-zA-Z]*r[a-zA-Z]*` | `cat:safe-rm-agent` | MANDATORY per CLAUDE.md rules |
    | `\bgit\s+filter-branch\b` | `cat:git-rewrite-history-agent` | MANDATORY per CLAUDE.md rules |
    | `\bgit\s+reset\b.*--hard\b` | `cat:validate-git-safety-agent` | MANDATORY per CLAUDE.md rules |

  - Example JSON structure:
    ```json
    {
      "guards": [
        {
          "pattern": "\\bgit\\s+rebase\\b",
          "skill": "cat:git-rebase-agent"
        },
        {
          "pattern": "\\bgit\\s+commit\\b.*--amend\\b",
          "skill": "cat:git-amend-agent"
        }
      ]
    }
    ```

- Create `RequireSkillForCommand.java`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java`
  - Package: `io.github.cowwoc.cat.hooks.bash`
  - Implements: `BashHandler`
  - Constructor: `RequireSkillForCommand(JvmScope scope)` — reads `CLAUDE_PLUGIN_ROOT/config/skill-command-registry.json`
    at construction time; stores compiled `Pattern` list paired with skill name strings
  - `check(HookInput input)` logic:
    1. Get `command = input.getCommand()`; if blank, return `BashHandler.allow()`
    2. For each `(Pattern pattern, String skillName)` in the registry:
       a. If `pattern.matcher(command).find()` matches:
          - Get `sessionId = input.getSessionId()`
          - Get `catAgentId = input.getCatAgentId(sessionId)`
          - Compute `baseDir = scope.getSessionBasePath().toAbsolutePath().normalize()`
          - Resolve `agentDir = SkillLoader.resolveAndValidateContainment(baseDir, catAgentId, "catAgentId")`
          - Read `markerFile = agentDir.resolve("skills-loaded")`
          - If `markerFile` does not exist → **block**
          - If `markerFile` exists: read lines, strip whitespace, skip blanks, collect into a `Set<String>`
          - If `skillName` is NOT in the set → **block**
          - If it IS in the set → `continue` (check next pattern; if no more patterns, return `allow()`)
    3. If no pattern matches, return `BashHandler.allow()`
  - Block message format (use a text block):
    ```
    BLOCKED: This command requires the {skillName} skill.

    Load the skill first:
      /cat:{skill-base-name}

    Then retry the command.
    ```
    Where `skill-base-name` is the part after `cat:` (e.g., `git-rebase-agent`).
  - Exception handling: if reading the registry or marker file throws `IOException`, log the error
    and return `BashHandler.allow()` (fail-open to avoid blocking legitimate work during setup errors)
  - Registry loading: use `scope.getJsonMapper()` to parse the JSON. Parse into a simple list of
    `GuardEntry` records:
    ```java
    private record GuardEntry(Pattern pattern, String skill) {}
    ```
  - Use `Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)` for each regex
  - License header required (Java block comment style)
  - Full Javadoc on class and all methods

- Register in `PreToolUseHook.java`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java`
  - Add import: `io.github.cowwoc.cat.hooks.bash.RequireSkillForCommand`
  - Add `new RequireSkillForCommand(scope)` to the `handlers` list in the constructor
  - Position: add it LAST in the list (after all existing handlers) so existing safety checks run first

- Create `RequireSkillForCommandTest.java`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RequireSkillForCommandTest.java`
  - Package: `io.github.cowwoc.cat.hooks.test`
  - Framework: TestNG (`@Test`, no `@Before`/`@After`)
  - Test cases (each test is fully self-contained using `Files.createTempDirectory`):

    1. `guardedCommandBlockedWhenSkillNotLoaded()` — command matches pattern, marker file absent → blocked=true,
       reason contains skill name
    2. `guardedCommandAllowedWhenSkillLoaded()` — command matches, marker file contains required skill → blocked=false
    3. `ungardedCommandAlwaysAllowed()` — command like `git status` matches no pattern → blocked=false
    4. `subagentCommandBlockedWhenSkillNotLoaded()` — same as case 1 but with a non-empty native agent ID
       (use `TestUtils.bashInputWithAgentId`)
    5. `subagentCommandAllowedWhenSkillLoaded()` — subagent with skill loaded → blocked=false
    6. `emptyCommandAlwaysAllowed()` — empty command string → blocked=false

  - Test setup pattern (for tests needing a registry and marker):
    ```java
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempSessionBase = Files.createTempDirectory("session-");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempSessionBase))
    {
      // Write registry file
      Path configDir = tempPluginRoot.resolve("config");
      Files.createDirectories(configDir);
      Files.writeString(configDir.resolve("skill-command-registry.json"), """
        {
          "guards": [
            {"pattern": "\\\\bgit\\\\s+rebase\\\\b", "skill": "cat:git-rebase-agent"}
          ]
        }
        """);

      // Write skills-loaded marker (for "allowed" tests)
      String sessionId = "test-session-id";
      Path agentDir = tempSessionBase.resolve(sessionId);
      Files.createDirectories(agentDir);
      Files.writeString(agentDir.resolve("skills-loaded"), "cat:git-rebase-agent\n");

      RequireSkillForCommand handler = new RequireSkillForCommand(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashInput("git rebase origin/main", "/workspace", sessionId));
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempSessionBase);
    }
    ```

  - Full Javadoc on class and all test methods

- Run `mvn -f client/pom.xml test` and fix any failures
- Update STATE.md: set `Status: closed`, `Progress: 100%`

## Post-conditions

- [ ] `git rebase` command is blocked when `cat:git-rebase-agent` skill is not loaded
- [ ] `git rebase` command is allowed when `cat:git-rebase-agent` skill IS loaded
- [ ] Unguarded commands (e.g., `git status`, `git log`) are always allowed
- [ ] Block message names the required skill and explains how to load it
- [ ] Works for subagents (uses per-agent skill-loaded markers via `catAgentId`)
- [ ] Registry file exists at `plugin/config/skill-command-registry.json` with 6 initial guards
- [ ] `mvn -f client/pom.xml test` passes with zero failures
- [ ] No regressions in existing `PreToolUseHook` behavior
- [ ] E2E: simulate a command run without skill loaded → blocked; simulate same command after skill
  loaded → allowed (verifiable via unit tests in Wave 1)
