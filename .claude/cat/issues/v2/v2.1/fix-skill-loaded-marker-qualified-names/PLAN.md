# Plan: fix-skill-loaded-marker-qualified-names

## Problem

`GetSkill.markSkillLoaded()` stores marker files using bare skill names (e.g., `git-rebase-agent`),
but `RequireSkillForCommand.check()` reads loaded skills and compares them to fully-qualified names
from `skill-triggers.json` (e.g., `cat:git-rebase-agent`). The names never match, so guarded
commands are always blocked even after the required skill has been loaded.

The `GetSkill.LOADED_DIR` Javadoc already documents the intended behavior:
> Marker files for loaded skills use the URL-encoded skill name as the filename
> (e.g., `cat%3Aadd` for `cat:add`).

The implementation simply doesn't match the documentation.

## Parent Requirements

None

## Reproduction Code

```
// GetSkill invoked as: get-skill git-rebase-agent <agentId>
// markSkillLoaded stores:  /loaded/git-rebase-agent          (bare name)
// RequireSkillForCommand checks for: "cat:git-rebase-agent"  (qualified name)
// → never matches → always blocked
```

## Expected vs Actual

- **Expected:** After loading `cat:git-rebase-agent`, running `git rebase ...` is allowed
- **Actual:** `git rebase` is always blocked with "BLOCKED: This command requires the cat:git-rebase-agent skill"

## Root Cause

`GetSkill.main()` receives the bare skill name (e.g., `git-rebase-agent`) from `SKILL.md` directives
(`!`"get-skill" git-rebase-agent "$0"`). `markSkillLoaded()` stores this bare name without qualification.
`RequireSkillForCommand` loads the guard registry where skills are always fully qualified (`cat:git-rebase-agent`).
The sets never intersect.

## Approach

Add a `getPluginPrefix()` method to `JvmScope` / `AbstractJvmScope` that derives the plugin prefix from
the `pluginRoot` path structure (`.../{prefix}/{slug}/{version}/`). Use this prefix in `GetSkill` to
qualify bare skill names before storing or checking them.

`TestJvmScope` overrides `getPluginPrefix()` to return `"cat"` since test paths don't have the standard
directory structure.

**Rejected: Read prefix from a config file** — Requires distributing an additional file; path-based
derivation is sufficient since the plugin directory structure is already fixed by the plugin system.

**Rejected: Change all 74 SKILL.md files to pass qualified names** — High blast radius; no benefit over
deriving the prefix programmatically.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Tests that write marker files directly (e.g., `RequireSkillForCommandTest`) already
  use qualified names — they will continue to pass unchanged. Tests in `GetSkillTest` that check the
  "already loaded" path use bare names but the behavior (returning reference message) is unchanged.
- **Mitigation:** Add regression tests that verify the marker file name and the subsequent-load check.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — add `getPluginPrefix()` abstract method
- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` — implement `getPluginPrefix()`
  using `getClaudePluginRoot().getParent().getParent().getFileName().toString()`; throw `AssertionError`
  if any path component is null
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` — override `getPluginPrefix()`
  to return `"cat"`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` — add `qualifySkillName(String)`,
  update `load()` to use qualified name when checking/storing
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetSkillTest.java` — add tests verifying
  marker files use qualified names

## Test Cases

- [ ] `GetSkillTest.loadStoresQualifiedNameInMarkerFile` — after `load("git-rebase-agent")`, the
  `loaded/` directory contains a file named `cat%3Agit-rebase-agent` (URL-encoded)
- [ ] `GetSkillTest.loadRecognizesAlreadyLoadedByQualifiedName` — after `load("git-rebase-agent")`,
  calling `load("git-rebase-agent")` again returns the "already loaded" reference message
- [ ] `GetSkillTest.loadAcceptsAlreadyQualifiedName` — calling `load("cat:git-rebase-agent")` stores
  the marker as `cat%3Agit-rebase-agent` (no double-qualification)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Implement `getPluginPrefix()` in `JvmScope`, `AbstractJvmScope`, and `TestJvmScope`:
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`

  **JvmScope.java** — add after `getClaudePluginRoot()`:
  ```java
  /**
   * Returns the plugin prefix (e.g., {@code "cat"}).
   * <p>
   * For production environments, derived from the plugin root path structure
   * ({@code .../{prefix}/{slug}/{version}/}). The prefix is the directory component
   * two levels above the version directory.
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginPrefix();
  ```

  **AbstractJvmScope.java** — add concrete method:
  ```java
  @Override
  public String getPluginPrefix()
  {
    Path pluginRoot = getClaudePluginRoot().toAbsolutePath().normalize();
    Path slugDir = pluginRoot.getParent();
    if (slugDir == null)
      throw new AssertionError("Plugin root has no parent directory: " + pluginRoot);
    Path prefixDir = slugDir.getParent();
    if (prefixDir == null)
      throw new AssertionError("Plugin slug directory has no parent: " + slugDir);
    Path prefixName = prefixDir.getFileName();
    if (prefixName == null)
      throw new AssertionError("Cannot determine plugin prefix from path: " + pluginRoot +
        ". Expected structure: .../{prefix}/{slug}/{version}/");
    return prefixName.toString();
  }
  ```

  **TestJvmScope.java** — add override:
  ```java
  @Override
  public String getPluginPrefix()
  {
    return "cat";
  }
  ```

- Implement `qualifySkillName()` in `GetSkill` and update `load()` to use qualified names:
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

  Add field to `GetSkill`:
  ```java
  private final String pluginPrefix;
  ```

  Initialize in constructor (after `this.skillArgs` is set):
  ```java
  this.pluginPrefix = scope.getPluginPrefix();
  ```

  Add private method after `markSkillLoaded`:
  ```java
  /**
   * Qualifies a skill name with the plugin prefix if not already qualified.
   * <p>
   * For example, {@code "git-rebase-agent"} becomes {@code "cat:git-rebase-agent"}.
   * Names that already contain {@code ':'} are returned unchanged.
   *
   * @param skillName the skill name (bare or qualified)
   * @return the qualified skill name
   * @throws NullPointerException if {@code skillName} is null
   */
  private String qualifySkillName(String skillName)
  {
    if (skillName.contains(":"))
      return skillName;
    return pluginPrefix + ":" + skillName;
  }
  ```

  Update `load()` — replace the `loadedSkills.contains(skillName)` check and `markSkillLoaded(skillName)` call:
  ```java
  // Before (current code):
  if (loadedSkills.contains(skillName))
  {
    return buildSubsequentLoadResponse(skillName, content);
  }
  markSkillLoaded(skillName);

  // After:
  String qualifiedName = qualifySkillName(skillName);
  if (loadedSkills.contains(qualifiedName))
  {
    return buildSubsequentLoadResponse(skillName, content);
  }
  markSkillLoaded(qualifiedName);
  ```

  Update `markSkillLoaded()` Javadoc to reflect that the name passed in is always already qualified:
  ```java
  /**
   * Marks a skill as loaded by creating an empty marker file in the loaded directory.
   * <p>
   * The {@code qualifiedName} must include the plugin prefix (e.g., {@code "cat:git-rebase-agent"}).
   *
   * @param qualifiedName the fully-qualified skill name
   * @throws IOException if the marker file cannot be written
   */
  private void markSkillLoaded(String qualifiedName) throws IOException
  ```

### Wave 2

- Add regression tests to `GetSkillTest`:
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetSkillTest.java`

  Add three new test methods:

  **`loadStoresQualifiedNameInMarkerFile`:**
  ```java
  /**
   * Verifies that loading a skill by bare name stores the marker file with the qualified name
   * (URL-encoded prefix:skill-name format).
   */
  @Test
  public void loadStoresQualifiedNameInMarkerFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      String agentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope, List.of(agentId));
      loader.load("test-skill");

      Path agentDir = scope.getSessionBasePath().toAbsolutePath().normalize().resolve(agentId);
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      // Qualified name: "cat:test-skill" → URL-encoded: "cat%3Atest-skill"
      Path expectedMarker = loadedDir.resolve(URLEncoder.encode("cat:test-skill", UTF_8));
      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
  ```

  **`loadRecognizesAlreadyLoadedByQualifiedName`:**
  ```java
  /**
   * Verifies that a second load call for the same bare-name skill returns the "already loaded"
   * reference message, confirming that the qualified-name marker is recognized on the second call.
   */
  @Test
  public void loadRecognizesAlreadyLoadedByQualifiedName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      loader.load("test-skill");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").contains("skill instructions were already loaded");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
  ```

  **`loadAcceptsAlreadyQualifiedName`:**
  ```java
  /**
   * Verifies that loading a skill by its qualified name (e.g., "cat:test-skill") stores
   * the marker without double-qualifying it.
   */
  @Test
  public void loadAcceptsAlreadyQualifiedName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      String agentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope, List.of(agentId));
      loader.load("cat:test-skill");

      Path agentDir = scope.getSessionBasePath().toAbsolutePath().normalize().resolve(agentId);
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      Path expectedMarker = loadedDir.resolve(URLEncoder.encode("cat:test-skill", UTF_8));
      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
      // Verify no double-qualified file exists
      Path doubleQualifiedMarker = loadedDir.resolve(URLEncoder.encode("cat:cat:test-skill", UTF_8));
      requireThat(Files.exists(doubleQualifiedMarker), "doubleQualifiedExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
  ```

- Run full test suite and verify all tests pass:
  - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] `mvn -f client/pom.xml test` passes with zero failures
- [ ] After `GetSkill.load("git-rebase-agent")`, the marker file is named `cat%3Agit-rebase-agent`
  (verified by `loadStoresQualifiedNameInMarkerFile` test)
- [ ] `RequireSkillForCommand` allows `git rebase` after the skill is loaded (verified by existing
  `guardedCommandAllowedWhenSkillLoaded` test passing without modification)
- [ ] E2E: Load `cat:git-rebase-agent` skill in a session, then run `git rebase` — the command
  executes without the "BLOCKED" error
