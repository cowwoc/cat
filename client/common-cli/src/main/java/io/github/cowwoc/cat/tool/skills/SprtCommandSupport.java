/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.agent.ProcessRunner;
import io.github.cowwoc.cat.agent.VersionUtils;
import io.github.cowwoc.cat.tool.CliTool;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Handles file-oriented SPRT commands such as trial preparation and artifact persistence.
 * <p>
 * This helper isolates command-specific file, JSON, and path work so {@link SprtRunner} can stay focused on
 * orchestration and runner process management.
 */
final class SprtCommandSupport
{
  private static final DateTimeFormatter ISO_UTC =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private final CliTool scope;
  private final String runtimeId;
  private final String claudeCodeVersion;
  private final SkillMetadataExtractor skillMetadataExtractor;
  private final SprtResultsManager sprtResultsManager;
  private final Logger log;

  /**
   * Creates a new command-support helper.
   *
   * @param scope the shared CLI scope
   * @param runtimeId the active engine runtime identifier
   * @param claudeCodeVersion the Claude Code version used for model resolution
   * @param skillMetadataExtractor extracts skill frontmatter and test metadata
   * @param sprtResultsManager renders compact JSON for CLI responses
   * @param log the runner logger
   */
  SprtCommandSupport(CliTool scope, String runtimeId, String claudeCodeVersion,
    SkillMetadataExtractor skillMetadataExtractor, SprtResultsManager sprtResultsManager, Logger log)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(runtimeId, "runtimeId").isNotBlank();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    requireThat(skillMetadataExtractor, "skillMetadataExtractor").isNotNull();
    requireThat(sprtResultsManager, "sprtResultsManager").isNotNull();
    requireThat(log, "log").isNotNull();
    this.scope = scope;
    this.runtimeId = runtimeId;
    this.claudeCodeVersion = claudeCodeVersion;
    this.skillMetadataExtractor = skillMetadataExtractor;
    this.sprtResultsManager = sprtResultsManager;
    this.log = log;
  }

  /**
   * Implements the {@code detect-changes} command.
   *
   * @param args {@code [old_skill_sha256, new_skill_path, test_dir_path]}
   * @return a JSON object describing which test cases should rerun
   * @throws IOException if file reading fails
   */
  String detectChanges(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "SprtRunner detect-changes: expected 3 arguments, got " + args.length + ".\n" +
          "Usage: skill-test-runner detect-changes <old_skill_sha256> <new_skill_path> <test_dir_path>");

    String oldSha = args[0];
    Path newSkillPath = Path.of(args[1]);
    Path testDirPath = Path.of(args[2]);

    if (!oldSha.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException(
        "SprtRunner detect-changes: invalid SHA-256 content hash format: '" + oldSha +
          "'. Expected 64 lowercase hex characters (got " + oldSha.length() + " characters).");
    if (Files.notExists(newSkillPath))
      throw new IllegalArgumentException(
        "SprtRunner detect-changes: new skill file not found: " + newSkillPath);
    if (Files.notExists(testDirPath) || !Files.isDirectory(testDirPath))
      throw new IllegalArgumentException(
        "SprtRunner detect-changes: test directory not found: " + testDirPath);

    String currentSha = sha256File(newSkillPath);
    boolean skillChanged = !currentSha.equals(oldSha);
    List<String> allTestCaseIds = readAllTestCaseIds(testDirPath);

    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode result = mapper.createObjectNode();
    result.put("skill_changed", skillChanged);

    ArrayNode allIdsArray = mapper.createArrayNode();
    for (String id : allTestCaseIds)
      allIdsArray.add(id);
    result.set("all_test_case_ids", allIdsArray);

    if (skillChanged)
    {
      result.set("rerun_test_case_ids", allIdsArray.deepCopy());
      result.set("carryforward_test_case_ids", mapper.createArrayNode());
    }
    else
    {
      result.set("rerun_test_case_ids", mapper.createArrayNode());
      result.set("carryforward_test_case_ids", allIdsArray.deepCopy());
      result.put("semantic_units_path_hint", "Run: skill-test-runner extract-units " + args[1]);
    }
    return sprtResultsManager.compactJson(result);
  }

  /**
   * Implements the {@code map-units} command.
   *
   * @param args {@code [test_dir_path, changed_units_json]}
   * @return a JSON object with rerun and carry-forward test case IDs
   * @throws IOException if file reading fails
   */
  String mapUnits(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SprtRunner map-units: expected 2 arguments, got " + args.length + ".\n" +
          "Usage: skill-test-runner map-units <test_dir_path> <changed_units_json>");

    Path testDirPath = Path.of(args[0]);
    String changedUnitsJson = args[1];

    if (Files.notExists(testDirPath) || !Files.isDirectory(testDirPath))
      throw new IllegalArgumentException(
        "SprtRunner map-units: test directory not found: " + testDirPath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode changedUnitsNode = mapper.readTree(changedUnitsJson);
    Set<String> changedUnits = new HashSet<>();
    if (changedUnitsNode.isArray())
    {
      for (JsonNode element : changedUnitsNode)
      {
        if (element.isString())
          changedUnits.add(element.asString());
      }
    }

    List<String> allIds = new ArrayList<>();
    List<String> rerunIds = new ArrayList<>();
    List<String> carryforwardIds = new ArrayList<>();

    List<Path> mdFiles = listMdFiles(testDirPath);
    for (Path mdFile : mdFiles)
    {
      String testCaseId = stemOf(mdFile);
      if (testCaseId.isBlank())
        continue;
      allIds.add(testCaseId);
      if (changedUnits.isEmpty() || !changedUnits.contains(testCaseId))
        carryforwardIds.add(testCaseId);
      else
        rerunIds.add(testCaseId);
    }

    ObjectNode result = mapper.createObjectNode();
    ArrayNode allArray = mapper.createArrayNode();
    for (String id : allIds)
      allArray.add(id);
    result.set("all_test_case_ids", allArray);

    ArrayNode rerunArray = mapper.createArrayNode();
    for (String id : rerunIds)
      rerunArray.add(id);
    result.set("rerun_test_case_ids", rerunArray);

    ArrayNode carryArray = mapper.createArrayNode();
    for (String id : carryforwardIds)
      carryArray.add(id);
    result.set("carryforward_test_case_ids", carryArray);
    return sprtResultsManager.compactJson(result);
  }

  /**
   * Implements the {@code persist-artifacts} command.
   *
   * @param args {@code [skill_path, artifacts_dir, session_id, worktree_root, phase]}
   * @param out the output stream for status messages
   * @throws IOException if file writing or git commit fails
   */
  void persistArtifacts(String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length != 5)
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: expected 5 arguments, got " + args.length + ".\n" +
          "Usage: skill-test-runner persist-artifacts <skill_path> <artifacts_dir> " +
          "<session_id> <worktree_root> <phase>");

    String skillPathArg = args[0];
    Path artifactsDir = Path.of(args[1]);
    String sessionId = args[2];
    Path worktreeRoot = Path.of(args[3]);
    String phase = args[4];

    if (Files.notExists(worktreeRoot))
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: worktree root not found: " + worktreeRoot);
    if (Files.notExists(artifactsDir))
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: artifacts directory not found: " + artifactsDir);

    Path absSkillPath = worktreeRoot.resolve(skillPathArg).normalize();
    validatePathWithinBoundary(worktreeRoot, absSkillPath);
    if (Files.notExists(absSkillPath))
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: skill file not found: " + absSkillPath);

    List<Path> testCaseMdFiles = listMdFiles(artifactsDir);
    if (testCaseMdFiles.isEmpty())
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: no .md test case files found in: " + artifactsDir);

    Path skillDir = absSkillPath.getParent();
    String skillName = skillDir.getFileName().toString();
    Path testCaseDir = skillDir.resolve("first-use");
    Files.createDirectories(testCaseDir);
    List<String> relTestCasePaths = new ArrayList<>();
    Path skillParent = Path.of(skillPathArg).getParent();
    Path skillParentOrDot;
    if (skillParent == null)
      skillParentOrDot = Path.of(".");
    else
      skillParentOrDot = skillParent;
    for (Path srcFile : testCaseMdFiles)
    {
      Path destFile = testCaseDir.resolve(srcFile.getFileName());
      validatePathWithinBoundary(skillDir, destFile);
      Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING);
      relTestCasePaths.add(skillParentOrDot.resolve("first-use").resolve(srcFile.getFileName()).toString());
    }

    String skillHash = sha256File(absSkillPath);
    String relInstructionTestDir = skillParentOrDot.resolve("first-use").toString();
    Path catWorkInstructionTestDir = worktreeRoot.resolve(".cat").resolve("work").
      resolve("instruction-test").resolve(skillName);
    Files.createDirectories(catWorkInstructionTestDir);

    String timestamp = ISO_UTC.format(Instant.now());
    String model = skillMetadataExtractor.extractStringField(absSkillPath, "model");
    if (model.isBlank())
    {
      throw new IllegalArgumentException(
        "SprtRunner persist-artifacts: no 'model:' field in frontmatter of " +
          absSkillPath + ". Every skill must declare a model.");
    }
    String modelId = ModelIdResolver.resolve(claudeCodeVersion, model);

    Path instructionTestJsonPath = catWorkInstructionTestDir.resolve("instruction-test.json");
    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("session_id", sessionId);
    root.put("model_id", modelId);
    root.put("phase", phase);
    root.put("timestamp", timestamp);
    ObjectNode skillNode = root.putObject("skill");
    skillNode.put("path", skillPathArg);
    skillNode.put("sha256", skillHash);
    String testCasesHash = sha256Directory(artifactsDir);
    ObjectNode testCasesNode = root.putObject("test_cases");
    testCasesNode.put("path", relInstructionTestDir);
    testCasesNode.put("sha256", testCasesHash);
    Files.writeString(instructionTestJsonPath, mapper.writeValueAsString(root), UTF_8);

    for (String relPath : relTestCasePaths)
    {
      ProcessRunner.Result addResult = ProcessRunner.run(worktreeRoot, "git", "add", relPath);
      if (addResult.exitCode() != 0)
      {
        throw new IOException("git add failed for " + relPath +
          ": exit code " + addResult.exitCode() + ", output: " + addResult.output());
      }
    }

    String commitMessage =
      "instruction-test: persist artifacts [session: " + sessionId + ", phase: " + phase + "]";
    int maxRetries = 3;
    boolean committed = false;
    for (int attempt = 0; attempt < maxRetries; ++attempt)
    {
      ProcessRunner.Result commitResult =
        ProcessRunner.run(worktreeRoot, "git", "commit", "-m", commitMessage);
      if (commitResult.exitCode() == 0)
      {
        committed = true;
        break;
      }
      if (attempt + 1 < maxRetries)
      {
        int baseSleepSeconds = 1 << (attempt + 1);
        int jitter = (int) (Math.random() * (baseSleepSeconds + 1));
        int sleepSeconds = baseSleepSeconds + jitter;
        log.warn("SprtRunner: git commit failed (attempt {}/{}), retrying in {}s...",
          attempt + 1, maxRetries, sleepSeconds);
        try
        {
          Thread.sleep(sleepSeconds * 1000L);
        }
        catch (InterruptedException _)
        {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    if (!committed)
      throw new IOException("SprtRunner persist-artifacts: git commit failed after " + maxRetries + " attempts");
    out.println("skill-test-runner: artifacts committed for phase=" + phase + ", session=" + sessionId);
  }

  /**
   * Implements the {@code save-failed-run} command.
   *
   * @param args {@code [worktree_path, source_file]}
   * @return the destination path line
   * @throws IOException if copying the file fails
   */
  String saveFailedRun(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SprtRunner save-failed-run: expected 2 arguments " +
          "<worktree_path> <source_file>, got " + args.length + ".\n" +
          "Usage: sprt-runner save-failed-run <worktree_path> <source_file>");

    Path worktreePath = Path.of(args[0]);
    Path sourceFile = Path.of(args[1]);
    if (Files.notExists(sourceFile))
      throw new IllegalArgumentException("SprtRunner save-failed-run: file not found: " + sourceFile);
    Path failedRunsDir = worktreePath.resolve(".cat/work/failed-runs");
    Files.createDirectories(failedRunsDir);
    Path destFile = failedRunsDir.resolve(sourceFile.getFileName());
    Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
    return "dest_path=" + destFile;
  }

  /**
   * Implements the {@code prepare-run} command.
   *
   * @param args {@code [worktree_path, test_dir]}
   * @return key-value lines describing the run inputs
   * @throws IOException if path resolution fails
   */
  String prepareRun(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SprtRunner prepare-run: expected 2 arguments " +
          "<worktree_path> <test_dir>, got " + args.length + ".\n" +
          "Usage: sprt-runner prepare-run <worktree_path> <test_dir>");
    Path worktreePath = Path.of(args[0]);
    if (!Files.isDirectory(worktreePath))
      throw new IllegalArgumentException(
        "SprtRunner prepare-run: worktree_path is not a directory: " + worktreePath);
    Path testDir = Path.of(args[1]);
    if (!testDir.isAbsolute())
      testDir = worktreePath.resolve(testDir);
    validatePathWithinBoundary(worktreePath, testDir);
    if (!Files.isDirectory(testDir))
      throw new IllegalArgumentException("SprtRunner prepare-run: test_dir does not exist: " + testDir);
    boolean hasMdFile;
    try (Stream<Path> stream = Files.list(testDir))
    {
      hasMdFile = stream.anyMatch(path ->
      {
        String name = path.getFileName().toString();
        return name.endsWith(".md") && !name.equals("test-results.json");
      });
    }
    if (!hasMdFile)
    {
      throw new IllegalArgumentException(
        "SprtRunner prepare-run: test_dir contains no .md test case files " +
          "(excluding test-results.json): " + testDir);
    }
    Path testDirRel = worktreePath.relativize(testDir);
    String issueName = worktreePath.getFileName().toString();
    Path sprtStatePath = worktreePath.resolve(".cat/work/sprt-state.json");

    StringJoiner output = new StringJoiner("\n");
    output.add("test_dir_abs=" + testDir);
    output.add("test_dir_rel=" + testDirRel);
    output.add("issue_name=" + issueName);
    output.add("sprt_state_path=" + sprtStatePath);
    return output.toString();
  }

  /**
   * Implements the {@code prepare-trial} command.
   *
   * @param args {@code [worktree_path, isolation_branch, test_dir_rel, tc_id, runner_worktree, output_dir, trial_num]}
   * @return key-value lines describing trial inputs
   * @throws IOException if prompt or manifest loading fails
   */
  String prepareTrial(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 7)
      throw new IllegalArgumentException(
        "SprtRunner prepare-trial: expected 7 arguments " +
          "<worktree_path> <isolation_branch> <test_dir_rel> <tc_id> <runner_worktree> " +
          "<output_dir> <trial_num>, got " + args.length + ".\n" +
          "Usage: sprt-runner prepare-trial <worktree_path> <isolation_branch> " +
          "<test_dir_rel> <tc_id> <runner_worktree> <output_dir> <trial_num>");
    String worktreePath = args[0];
    String isolationBranch = args[1];
    String testDirRel = args[2];
    String tcId = args[3];
    requireThat(tcId, "tcId").matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    String runnerWorktree = args[4];
    String outputDir = args[5];
    String trialNum = args[6];

    String outputJson = outputDir + "/" + tcId + "_run" + trialNum + ".json";
    ProcessRunner.Result fixtureResult = ProcessRunner.run(Path.of(worktreePath),
      "git", "show", isolationBranch + ":" + testDirRel + "/" + tcId + "_runner.json");
    if (fixtureResult.exitCode() == 0)
    {
      Files.createDirectories(Path.of(outputDir));
      Files.writeString(Path.of(outputJson), fixtureResult.output(), UTF_8);
      StringJoiner fixtureOutput = new StringJoiner("\n");
      fixtureOutput.add("runner_fixture=yes");
      fixtureOutput.add("output_json=" + outputJson);
      return fixtureOutput.toString();
    }

    String preamble = "[CWD: " + runnerWorktree + "]\n" +
      "Execute the task below immediately. Do not ask for clarification or confirmation.\n" +
      "Every path argument passed to Write, Edit, or Bash MUST begin with the exact CWD value above " +
      "(both relative and absolute paths). Example: " + runnerWorktree + "/some/file.txt\n" +
      "Never use any other root for file operations.";

    String jlinkBin = runnerWorktree + "/client/distribution/target/jlink/" + runtimeId + "/bin";
    if (!Files.isDirectory(Path.of(jlinkBin)))
    {
      throw new IOException(
        "SprtRunner prepare-trial: jlink directory not found in runner worktree: " +
          jlinkBin + ". Run 'mvn -f client/pom.xml package' before starting SPRT.");
    }

    Path jlinkDir = Path.of(jlinkBin).getParent();
    Path versionFile = jlinkDir.resolve("VERSION");
    if (!Files.exists(versionFile) && Files.isDirectory(jlinkDir))
    {
      String pluginVersion = readJlinkPluginVersion(jlinkDir);
      Files.writeString(versionFile, pluginVersion, UTF_8);
    }

    Files.createDirectories(Path.of(outputDir));
    List<String> promptFiles = new ArrayList<>();
    for (int turnNumber = 1; ; ++turnNumber)
    {
      String turnPath = isolationBranch + ":" + testDirRel + "/" + tcId + "_turn" + turnNumber + ".md";
      ProcessRunner.Result showResult = ProcessRunner.run(Path.of(worktreePath), "git", "show", turnPath);
      if (showResult.exitCode() != 0)
      {
        if (turnNumber == 1)
        {
          throw new IOException(
            "SprtRunner prepare-trial: git show failed for " + turnPath + " in " +
              worktreePath + ": " + showResult.output());
        }
        break;
      }
      String promptFile = outputDir + "/" + tcId + "_run" + trialNum + "_turn" + turnNumber + "_prompt.txt";
      Files.writeString(Path.of(promptFile), preamble + "\n\n" + showResult.output(), UTF_8);
      promptFiles.add(promptFile);
    }

    String pluginSource = runnerWorktree + "/plugin/";
    StringJoiner output = new StringJoiner("\n");
    output.add("prompt_file=" + promptFiles.getFirst());
    output.add("prompt_files_json=" + scope.getJsonMapper().writeValueAsString(promptFiles));
    output.add("jlink_bin=" + jlinkBin);
    output.add("plugin_source=" + pluginSource);
    output.add("output_json=" + outputJson);
    return output.toString();
  }

  /**
   * Reads the plugin version from the runner or installed plugin manifest.
   *
   * @param jlinkDir the engine-specific jlink directory
   * @return the plugin version string
   * @throws IOException if manifest reading fails
   */
  private String readJlinkPluginVersion(Path jlinkDir) throws IOException
  {
    Path manifest = resolvePluginManifest(jlinkDir);
    return readPluginVersion(manifest);
  }

  /**
   * Resolves the runner or installed plugin manifest used for version lookup.
   *
   * @param jlinkDir the engine-specific jlink directory
   * @return the manifest path
   */
  private Path resolvePluginManifest(Path jlinkDir)
  {
    Path manifest = jlinkDir.resolve(scope.getPluginDescriptor());
    if (Files.isRegularFile(manifest))
      return manifest;
    Path installedManifest = scope.getPluginRoot().resolve(scope.getPluginDescriptor());
    if (Files.isRegularFile(installedManifest))
      return installedManifest;
    throw new AssertionError("Plugin version not found: " + manifest + "\n" +
      "Build CAT distribution artifacts before running SPRT.");
  }

  /**
   * Reads and validates the plugin version from a manifest.
   *
   * @param manifest the manifest path
   * @return the validated plugin version
   * @throws IOException if manifest reading fails
   */
  private String readPluginVersion(Path manifest) throws IOException
  {
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(manifest, UTF_8));
    JsonNode version = root.get("version");
    if (version == null || !version.isString())
      throw new AssertionError("Invalid plugin.json: missing or non-string 'version' field in " + manifest);
    return validatePluginVersion(manifest, version.stringValue());
  }

  /**
   * Validates the plugin version string from a manifest.
   *
   * @param manifest the manifest path
   * @param version the raw version string
   * @return the normalized version string
   */
  private String validatePluginVersion(Path manifest, String version)
  {
    String normalized = null;
    if (version != null)
      normalized = version.strip();
    if (normalized == null || !VersionUtils.isValidVersion(normalized))
    {
      throw new AssertionError("Invalid version format in " + manifest + ": '" + version +
        "'. Expected X.Y or X.Y.Z");
    }
    return normalized;
  }

  /**
   * Implements the {@code get-json-field} command.
   *
   * @param args {@code [json_string, field_name]}
   * @return the requested field as plain text or compact JSON
   * @throws IOException if JSON parsing fails
   */
  String getJsonField(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SprtRunner get-json-field: expected 2 arguments " +
          "<json_string> <field_name>, got " + args.length + ".\n" +
          "Usage: sprt-runner get-json-field <json_string> <field_name>");
    String jsonString = args[0];
    String fieldName = args[1];
    JsonNode root = scope.getJsonMapper().readTree(jsonString);
    JsonNode fieldNode = root.path(fieldName);
    if (fieldNode.isMissingNode())
    {
      throw new IllegalArgumentException(
        "SprtRunner get-json-field: field '" + fieldName + "' not found in JSON: " + jsonString);
    }
    if (fieldNode.isValueNode())
      return fieldNode.asString();
    return fieldNode.toString();
  }

  /**
   * Implements the {@code get-tc-name} command.
   *
   * @param args {@code [isolation_result_json, tc_id]}
   * @return the original test-case filename stem
   * @throws IOException if JSON parsing fails
   */
  String getTcName(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SprtRunner get-tc-name: expected 2 arguments " +
          "<isolation_result_json> <tc_id>, got " + args.length + ".\n" +
          "Usage: sprt-runner get-tc-name <isolation_result_json> <tc_id>");
    JsonNode root = scope.getJsonMapper().readTree(args[0]);
    JsonNode tcNameMapNode = root.path("tc_name_map");
    if (tcNameMapNode.isMissingNode())
    {
      throw new IllegalArgumentException(
        "SprtRunner get-tc-name: 'tc_name_map' field not found in isolation result JSON");
    }
    JsonNode stemNode = tcNameMapNode.path(args[1]);
    if (stemNode.isMissingNode())
      throw new IllegalArgumentException(
        "SprtRunner get-tc-name: tc_id '" + args[1] + "' not found in tc_name_map");
    return stemNode.asString();
  }

  /**
   * Implements the {@code get-worktree-field} command.
   *
   * @param args {@code [create_runner_worktrees_json, tc_id, field_name]}
   * @return the requested descriptor field
   * @throws IOException if JSON parsing fails
   */
  String getWorktreeField(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "SprtRunner get-worktree-field: expected 3 arguments " +
          "<create_runner_worktrees_json> <tc_id> <field_name>, got " + args.length + ".\n" +
          "Usage: sprt-runner get-worktree-field <create_runner_worktrees_json> <tc_id> <field_name>");
    JsonNode root = scope.getJsonMapper().readTree(args[0]);
    JsonNode worktreesNode = root.path("worktrees");
    if (worktreesNode.isMissingNode() || !worktreesNode.isArray())
      throw new IllegalArgumentException("SprtRunner get-worktree-field: 'worktrees' array not found in JSON");
    String tcId = args[1];
    String fieldName = args[2];
    for (JsonNode worktree : worktreesNode)
    {
      JsonNode tcIdNode = worktree.path("tc_id");
      if (!tcIdNode.isMissingNode() && tcIdNode.asString().equals(tcId))
      {
        JsonNode fieldNode = worktree.path(fieldName);
        if (fieldNode.isMissingNode())
        {
          throw new IllegalArgumentException(
            "SprtRunner get-worktree-field: field '" + fieldName +
              "' not found in worktree descriptor for tc_id '" + tcId + "'");
        }
        return fieldNode.asString();
      }
    }
    throw new IllegalArgumentException(
      "SprtRunner get-worktree-field: tc_id '" + tcId + "' not found in worktrees array");
  }

  /**
   * Validates a session identifier path segment.
   *
   * @param sessionId the session identifier to validate
   * @return the validated identifier
   */
  static String validateSessionIdSegment(String sessionId)
  {
    requireThat(sessionId, "session_id").isNotBlank();
    if (!SESSION_ID_PATTERN.matcher(sessionId).matches())
      throw new IllegalArgumentException("session_id must contain only letters, digits, underscores, or hyphens");
    return sessionId;
  }

  /**
   * Resolves the session-specific test-runs directory under a worktree boundary.
   *
   * @param worktreePath the worktree root
   * @param sessionId the session identifier
   * @return the resolved session directory path
   * @throws IOException if real-path resolution fails
   */
  static Path resolveTestRunSessionDir(Path worktreePath, String sessionId) throws IOException
  {
    requireThat(worktreePath, "worktreePath").isNotNull();
    String validatedSessionId = validateSessionIdSegment(sessionId);
    Path boundary = worktreePath.toRealPath();
    Path testRunsRoot = worktreePath.resolve(".cat/work/test-runs").toAbsolutePath().normalize();
    Path existingAncestor = testRunsRoot;
    while (existingAncestor != null && Files.notExists(existingAncestor))
      existingAncestor = existingAncestor.getParent();
    if (existingAncestor != null)
    {
      Path resolvedAncestor = existingAncestor.toRealPath();
      if (!resolvedAncestor.startsWith(boundary))
        throw new IllegalArgumentException("session_id escapes the test-runs directory: " + sessionId);
    }
    if (Files.exists(testRunsRoot))
    {
      Path resolvedRoot = testRunsRoot.toRealPath();
      if (!resolvedRoot.startsWith(boundary))
        throw new IllegalArgumentException("session_id escapes the test-runs directory: " + sessionId);
    }
    Path sessionDir = testRunsRoot.resolve(validatedSessionId).normalize();
    if (!sessionDir.startsWith(testRunsRoot))
      throw new IllegalArgumentException("session_id contains path traversal: " + sessionId);
    if (Files.exists(sessionDir))
    {
      Path resolvedRoot;
      if (Files.exists(testRunsRoot))
        resolvedRoot = testRunsRoot.toRealPath();
      else
        resolvedRoot = testRunsRoot;
      Path resolvedSessionDir = sessionDir.toRealPath();
      if (!resolvedSessionDir.startsWith(resolvedRoot))
        throw new IllegalArgumentException("session_id escapes the test-runs directory: " + sessionId);
    }
    return sessionDir;
  }

  /**
   * Computes the SHA-256 of a single file.
   *
   * @param filePath the file to hash
   * @return the lowercase hexadecimal digest
   * @throws IOException if file reading fails
   */
  private String sha256File(Path filePath) throws IOException
  {
    byte[] bytes = Files.readAllBytes(filePath);
    return SprtRunner.sha256Bytes(bytes);
  }

  /**
   * Computes the SHA-256 over the concatenated bytes of all `.md` files in a directory.
   *
   * @param directory the directory to hash
   * @return the directory digest
   * @throws IOException if file enumeration or reading fails
   */
  private String sha256Directory(Path directory) throws IOException
  {
    List<Path> mdFiles = listMdFiles(directory);
    MessageDigest digest;
    try
    {
      digest = MessageDigest.getInstance("SHA-256");
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new AssertionError("SHA-256 not available in JDK", e);
    }
    for (Path mdFile : mdFiles)
      digest.update(Files.readAllBytes(mdFile));
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Reads all test-case IDs from a test directory.
   *
   * @param testDirPath the directory containing `.md` test cases
   * @return the ordered list of test-case IDs
   * @throws IOException if directory reading fails
   */
  private List<String> readAllTestCaseIds(Path testDirPath) throws IOException
  {
    List<String> ids = new ArrayList<>();
    for (Path mdFile : listMdFiles(testDirPath))
    {
      String id = stemOf(mdFile);
      if (!id.isBlank())
        ids.add(id);
    }
    return ids;
  }

  /**
   * Lists `.md` files in lexicographic order, excluding `test-results.json`.
   *
   * @param directory the directory to inspect
   * @return the ordered markdown file list
   * @throws IOException if directory reading fails
   */
  private List<Path> listMdFiles(Path directory) throws IOException
  {
    List<Path> result = new ArrayList<>();
    try (Stream<Path> stream = Files.list(directory))
    {
      List<Path> sorted = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
      for (Path entry : sorted)
      {
        String name = entry.getFileName().toString();
        if (name.endsWith(".md") && Files.isRegularFile(entry))
          result.add(entry);
      }
    }
    return result;
  }

  /**
   * Returns a filename stem.
   *
   * @param path the file path
   * @return the filename without its final extension
   */
  private String stemOf(Path path)
  {
    String name = path.getFileName().toString();
    int dotIndex = name.lastIndexOf('.');
    if (dotIndex > 0)
      return name.substring(0, dotIndex);
    return name;
  }

  /**
   * Ensures a candidate path stays within a boundary directory.
   *
   * @param boundary the allowed root directory
   * @param candidate the path to validate
   * @throws IOException if real-path resolution fails
   */
  private void validatePathWithinBoundary(Path boundary, Path candidate) throws IOException
  {
    Path resolvedBoundary = boundary.toRealPath();
    Path resolvedCandidate;
    if (Files.exists(candidate))
      resolvedCandidate = candidate.toRealPath();
    else
      resolvedCandidate = candidate.toAbsolutePath().normalize();
    if (!resolvedCandidate.startsWith(resolvedBoundary))
    {
      throw new IllegalArgumentException(
        "SprtRunner: path traversal detected: '" + candidate + "' is outside '" + boundary + "'");
    }
  }
}
