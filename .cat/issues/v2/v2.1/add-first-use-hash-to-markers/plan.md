# Plan: add-first-use-hash-to-markers

## Goal
Add SHA-256 content-hash tracking to skill marker files so that `GetSkill` can detect when
`first-use.md` has changed (e.g., after a plugin upgrade) and automatically invalidate stale
markers — ensuring Claude always receives the latest skill instructions.

## Parent Requirements
None

## Approaches

### A: Store hash as marker file content (Selected)
- **Risk:** LOW
- **Scope:** 2 files (GetSkill.java, GetSkillTest.java)
- **Description:** Replace the empty-string marker content with the SHA-256 hex digest of
  `first-use.md`. On each skill check, compute the current hash and compare to the stored value.

### B: Store hash in a JSON sidecar file
- **Risk:** LOW
- **Scope:** 3+ files
- **Description:** Keep marker files as zero-byte presence markers, add `{encodedName}.hash`
  sidecar files. More complex with no benefit over Approach A.

### C: Change marker format to JSON object
- **Risk:** MEDIUM
- **Scope:** 4+ files (GetSkill, ClearAgentMarkers, tests)
- **Description:** Store `{"hash": "..."}` JSON in marker files. Adds JSON-parsing overhead for
  no benefit at this scale.

**Rationale for A:** Minimal change, self-contained in `GetSkill`, and naturally handles the
migration case — an empty marker means no hash, which never matches a real SHA-256 digest, so
old markers for skills with `first-use.md` are automatically invalidated on first check.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** `ClearAgentMarkers` deletes marker files by directory listing, not by content —
  non-empty files are unaffected. Marker files grow from 0 bytes to ~64 chars (SHA-256 hex
  string), which has no performance impact.
- **Mitigation:** Existing `ClearAgentMarkersTest` verifies deletion behaviour; run full test
  suite after changes.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` — change data structure,
  loading logic, marker-writing logic, and skill-check logic
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetSkillTest.java` — add five new test
  methods covering hash match, hash mismatch, migration (empty marker), and no-first-use.md cases

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Modify `GetSkill.java` — data structure and import changes:
  - Change field declaration at line 117:
    `private final Set<String> loadedSkills;`  →  `private final Map<String, String> skillHashes;`
  - Remove imports: `java.util.HashSet`, `java.util.Set`
  - Add imports: `java.security.MessageDigest`, `java.security.NoSuchAlgorithmException`,
    `java.util.HashMap`, `java.util.HexFormat`, `java.util.Map`
  - Add import `java.util.stream.Stream` (replacing the inline FQN at the existing
    `try (java.util.stream.Stream<Path> stream = Files.list(loadedDir))` usage)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

- Modify `GetSkill.java` — constructor marker-loading block (lines 218-224):
  Replace:
  ```java
  this.loadedSkills = new HashSet<>();

  try (java.util.stream.Stream<Path> stream = Files.list(loadedDir))
  {
    stream.forEach(p -> loadedSkills.add(
      URLDecoder.decode(p.getFileName().toString(), StandardCharsets.UTF_8)));
  }
  ```
  With (converts forEach to for-loop per java.md conventions, reads file content as stored hash):
  ```java
  this.skillHashes = new HashMap<>();

  try (Stream<Path> stream = Files.list(loadedDir))
  {
    List<Path> markerFiles = stream.toList();
    for (Path markerFile : markerFiles)
    {
      String qualifiedName = URLDecoder.decode(markerFile.getFileName().toString(), UTF_8);
      String storedHash = Files.readString(markerFile, UTF_8);
      skillHashes.put(qualifiedName, storedHash);
    }
  }
  ```
  Note: add `import static java.nio.charset.StandardCharsets.UTF_8;` and use `UTF_8` throughout
  to replace verbose `StandardCharsets.UTF_8` usages (already-used pattern in the file if present,
  otherwise add the static import and replace occurrences in the changed methods only).
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

- Modify `GetSkill.java` — `load()` method check (lines 250-258):
  Replace:
  ```java
  String qualifiedName = qualifySkillName(skillName);
  if (loadedSkills.contains(qualifiedName))
  {
    // Subsequent load: return reference + re-execute single directive if present
    return buildSubsequentLoadResponse(skillName, content);
  }
  // First load: return full expanded content
  markSkillLoaded(qualifiedName);
  return processPreprocessorDirectives(content, skillName);
  ```
  With:
  ```java
  String qualifiedName = qualifySkillName(skillName);
  String storedHash = skillHashes.get(qualifiedName);
  if (storedHash != null)
  {
    String currentHash = computeFirstUseHash(skillName);
    if (storedHash.equals(currentHash))
    {
      // Hash matches: skill content unchanged, use cached marker
      return buildSubsequentLoadResponse(skillName, content);
    }
    // Hash mismatch: first-use.md was updated (e.g., plugin upgrade) — invalidate marker
    String encodedName = URLEncoder.encode(qualifiedName, UTF_8);
    Files.deleteIfExists(loadedDir.resolve(encodedName));
    skillHashes.remove(qualifiedName);
  }
  // First load or invalidated marker: return full expanded content
  markSkillLoaded(qualifiedName, skillName);
  return processPreprocessorDirectives(content, skillName);
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

- Modify `GetSkill.java` — `markSkillLoaded()` signature and body (lines 699-704):
  Replace:
  ```java
  private void markSkillLoaded(String qualifiedName) throws IOException
  {
    loadedSkills.add(qualifiedName);
    String encodedName = URLEncoder.encode(qualifiedName, StandardCharsets.UTF_8);
    Files.writeString(loadedDir.resolve(encodedName), "", StandardCharsets.UTF_8);
  }
  ```
  With:
  ```java
  private void markSkillLoaded(String qualifiedName, String skillName) throws IOException
  {
    String hash = computeFirstUseHash(skillName);
    skillHashes.put(qualifiedName, hash);
    String encodedName = URLEncoder.encode(qualifiedName, UTF_8);
    Files.writeString(loadedDir.resolve(encodedName), hash, UTF_8);
  }
  ```
  Update Javadoc to describe the new hash-writing behaviour and the `skillName` parameter.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

- Add private method `computeFirstUseHash(String skillName)` to `GetSkill.java`, inserted just
  before `markSkillLoaded()`:
  ```java
  /**
   * Computes a SHA-256 hex digest of the {@code first-use.md} file for the given skill.
   * <p>
   * Applies the same {@code -agent} fallback logic as {@link #loadRawContent}: if the skill has
   * no {@code first-use.md} of its own and its name ends with {@code -agent}, the parent skill's
   * {@code first-use.md} is tried.
   * <p>
   * Returns {@code ""} (empty string) when no {@code first-use.md} can be found. An empty hash
   * stored in a marker file matches an empty computed hash, so skills without {@code first-use.md}
   * are always treated as valid once marked — regardless of whether the marker was written by
   * this version or an older version.
   *
   * @param skillName the skill name (bare or qualified)
   * @return the SHA-256 hex digest of the file contents, or {@code ""} if no file is found
   * @throws IOException if the file exists but cannot be read
   */
  private String computeFirstUseHash(String skillName) throws IOException
  {
    String dirName = stripPrefix(skillName);
    Path firstUsePath = pluginRoot.resolve("skills/" + dirName + "/first-use.md");
    if (Files.notExists(firstUsePath) && dirName.endsWith("-agent"))
    {
      String parentDirName = dirName.substring(0, dirName.length() - "-agent".length());
      firstUsePath = pluginRoot.resolve("skills/" + parentDirName + "/first-use.md");
    }
    if (Files.notExists(firstUsePath))
      return "";
    byte[] fileBytes = Files.readAllBytes(firstUsePath);
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(fileBytes);
      return HexFormat.of().formatHex(hashBytes);
    }
    catch (NoSuchAlgorithmException e)
    {
      // SHA-256 is guaranteed by the Java SE specification to be available on all compliant JVMs.
      throw new AssertionError("SHA-256 MessageDigest not available", e);
    }
  }
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

- Update Javadoc on `GetSkill` class (lines 34-88) to document:
  - Marker files now contain the SHA-256 hex digest of `first-use.md` (not empty)
  - On each load, the current digest is compared to the stored one; a mismatch invalidates the
    marker and triggers a first-use reload
  - Migration: existing empty marker files are treated as a mismatch for skills that have
    `first-use.md`, causing one-time re-delivery of the full skill content
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

### Wave 2

- Add five test methods to `GetSkillTest.java`. Each test must be fully self-contained (no class
  fields, no shared state) and use `TestJvmScope`. Create a temporary plugin directory structure
  with a fake skill under `skills/{skillName}/first-use.md` and a temporary loaded directory.

  **Test 1 — first load writes hash to marker file:**
  ```
  /**
   * Verifies that loading a skill for the first time writes the SHA-256 hash of first-use.md
   * to the marker file, not an empty string.
   */
  @Test
  public void firstLoad_writesHashToMarkerFile() throws IOException
  ```
  Setup: create temp plugin dir with `skills/test-skill/first-use.md` containing known text.
  Act: create `GetSkill` and call `load("test-skill")`.
  Assert: marker file at `loadedDir/cat%3Atest-skill` exists and its content equals the
  SHA-256 hex of the `first-use.md` bytes.

  **Test 2 — second load with unchanged first-use.md returns subsequent response:**
  ```
  /**
   * Verifies that a second load for the same skill returns the "already loaded" reference message
   * when first-use.md content has not changed since the first load.
   */
  @Test
  public void secondLoad_withMatchingHash_returnsSubsequentResponse() throws IOException
  ```
  Setup: pre-write a marker file containing the correct SHA-256 hash of `first-use.md`.
  Act: create `GetSkill` (reads existing marker) and call `load("test-skill")`.
  Assert: result contains "already loaded" (the subsequent-load message text from
  `buildSubsequentLoadResponse`).

  **Test 3 — second load with changed first-use.md returns full content:**
  ```
  /**
   * Verifies that when the stored hash in the marker file does not match the current first-use.md
   * content (e.g., after a plugin upgrade), the marker is invalidated and the full skill content
   * is returned.
   */
  @Test
  public void secondLoad_withMismatchedHash_returnsFullContent() throws IOException
  ```
  Setup: pre-write a marker file containing a stale/wrong hash (e.g., `"stale-hash"`).
  Act: create `GetSkill` (reads stale marker) and call `load("test-skill")`.
  Assert: result contains the actual text content of `first-use.md` (i.e., the full first-use
  content, not the "already loaded" message). Also assert that the marker file now contains
  the correct SHA-256 hash (not `"stale-hash"`).

  **Test 4 — existing empty marker (migration) triggers first-use reload:**
  ```
  /**
   * Verifies that a pre-existing empty marker file (written by an older plugin version) is treated
   * as a hash mismatch for skills that have a first-use.md, causing the full skill content to be
   * returned on the next load.
   */
  @Test
  public void legacyEmptyMarker_withFirstUseMd_triggersFirstUseReload() throws IOException
  ```
  Setup: pre-write a marker file with empty content (`""`).
  Act: create `GetSkill` (reads empty marker) and call `load("test-skill")`.
  Assert: result contains the actual `first-use.md` text (not "already loaded"). Also assert
  that the marker file now contains a non-empty SHA-256 hash.

  **Test 5 — marker hash updated after invalidation:**
  ```
  /**
   * Verifies that after a stale marker is invalidated, the newly written marker contains the
   * correct current hash, so a subsequent third load is served from cache without re-invalidating.
   */
  @Test
  public void afterInvalidation_newMarkerIsValid_forThirdLoad() throws IOException
  ```
  Setup: pre-write marker with stale hash (e.g., `"stale-hash"`). Create temp plugin dir with
  `skills/test-skill/first-use.md` containing known text.
  Act: create `GetSkill`, call `load("test-skill")` (first call: stale hash detected, marker
  invalidated, full content returned), then call `load("test-skill")` again (second call should
  hit the now-valid marker written after invalidation).
  Assert: second `load()` result contains "already loaded".

  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetSkillTest.java`

- Fix 5 pre-existing PMD `CloseResource` violations in files unrelated to this issue so that
  `mvn -f client/pom.xml test` exits 0. The violations are in:
  `EmpiricalTestRunner.java`, `Feedback.java`, `GitMergeLinear.java`, `WriteAndCommit.java`,
  `GetDiffOutputTest.java`. For each file, wrap the resource(s) flagged by PMD in a
  try-with-resources block (or close them in a `finally` block) so they are always closed.
  Run `mvn -f client/pom.xml test` after all fixes and confirm exit code 0.
  - Files: the five Java files listed above (locate under `client/src/`)

- Run full test suite to verify no regressions:
  ```bash
  mvn -f client/pom.xml test
  ```

## Post-conditions
- [ ] Marker files contain a SHA-256 hex digest of `first-use.md` content (not empty string) after
      the first skill load. Verify: `cat` the marker file for a loaded skill; it must be a 64-char
      hex string.
- [ ] `GetSkill.load()` returns full `first-use.md` content when the stored hash in the marker
      does not match the current file hash (including when the marker is empty — the migration case).
- [ ] `GetSkill.load()` returns the subsequent-load reference message when the stored hash matches
      the current `first-use.md` hash.
- [ ] All five new test methods pass and no existing tests regress: `mvn -f client/pom.xml test`
      exits 0.
- [ ] E2E: manually modify a skill's `first-use.md`, then invoke the skill via the Skill tool in a
      session that has an existing marker for it; confirm the full updated skill content is returned
      (not the "already loaded" message).
