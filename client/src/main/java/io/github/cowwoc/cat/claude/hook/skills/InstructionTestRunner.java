/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import io.github.cowwoc.cat.claude.internal.SharedSecrets;
import io.github.cowwoc.cat.claude.hook.util.ProcessRunner;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;
import io.github.cowwoc.cat.claude.hook.util.VersionUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Incremental instruction-test driver for instruction-builder-agent.
 * <p>
 * Dispatches subcommands: extract-units, extract-model, extract-test-dir, detect-changes,
 * map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results,
 * create-runner-worktrees, check-run-contamination, remove-runner-worktrees, remove-runner-worktree,
 * prepare-trial, get-json-field, run-sprt-batch, run-full-sprt.
 * <p>
 * {@code prepare-trial} outputs {@code key=value} lines (all scalar paths); the combined prompt
 * is written to a file so {@code claude-runner} receives a path via {@code --prompt}.
 * <p>
 * Scalar-valued commands output {@code key=value} lines (one per line). Commands that return arrays
 * or nested objects output compact JSON. Expected errors are reported as a block response on stdout
 * with exit code 0. Unexpected errors are logged to stderr and also reported as a block response on
 * stdout with exit code 0.
 */
public final class InstructionTestRunner
{
  /**
   * SPRT log-likelihood increment for a passing observation.
   * <p>
   * Equals {@code ln(p0/p1) = ln(0.95/0.85)}.
   */
  private static final double SPRT_LOG_PASS = 0.1112;
  /**
   * SPRT log-likelihood increment for a failing observation.
   * <p>
   * Equals {@code ln((1-p0)/(1-p1)) = ln(0.05/0.15)}.
   */
  private static final double SPRT_LOG_FAIL = -1.0986;
  /**
   * SPRT upper acceptance boundary.
   * <p>
   * Equals {@code ln((1-beta)/alpha) = ln(19)}.
   */
  private static final double SPRT_ACCEPT = 2.944;
  /**
   * SPRT lower rejection boundary.
   * <p>
   * Equals {@code ln(beta/(1-alpha)) = ln(0.0526)}.
   */
  private static final double SPRT_REJECT = -2.944;
  /**
   * Number of smoke-test runs before escalating to full SPRT.
   */
  private static final int SMOKE_RUNS = 3;
  /**
   * Prior compliance boost applied when {@code --prior-boost} is enabled.
   * <p>
   * Equivalent to approximately 10 prior PASS observations.
   */
  private static final double PRIOR_BOOST = 1.112;
  /**
   * ISO-8601 UTC timestamp formatter.
   */
  private static final DateTimeFormatter ISO_UTC =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  static
  {
    SharedSecrets.setInstructionTestRunnerAccess(new SharedSecrets.InstructionTestRunnerAccess()
    {
      @Override
      public String sha256Bytes(byte[] bytes)
      {
        return InstructionTestRunner.sha256Bytes(bytes);
      }

      @Override
      public String[] buildGraderArgs(Path graderPromptFile, String modelId, String runnerWorktree,
        Path jlinkBin)
      {
        return InstructionTestRunner.buildGraderArgs(graderPromptFile, modelId, runnerWorktree, jlinkBin);
      }
    });
  }

  private final Logger log = LoggerFactory.getLogger(InstructionTestRunner.class);
  private final ClaudePluginScope scope;
  private final String claudeCodeVersion;

  /**
   * Creates a new InstructionTestRunner.
   *
   * @param scope             the JVM scope providing shared services
   * @param claudeCodeVersion the Claude Code version string (e.g., {@code "2.1.87"})
   * @throws NullPointerException     if {@code scope} or {@code claudeCodeVersion} are null
   * @throws IllegalArgumentException if {@code claudeCodeVersion} is blank
   */
  public InstructionTestRunner(ClaudePluginScope scope, String claudeCodeVersion)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    this.scope = scope;
    this.claudeCodeVersion = claudeCodeVersion;
  }

  /**
   * Dispatches the given command and arguments, writing JSON to {@code out}.
   *
   * @param args  the command-line arguments: {@code [command, arg1, ...]}
   * @param out   the stream to write JSON output to
   * @throws NullPointerException     if {@code args} or {@code out} are null
   * @throws IllegalArgumentException if no command is specified or the command is unknown
   * @throws IOException              if an I/O error occurs
   * @throws InterruptedException     if waiting for a runner process is interrupted
   */
  public void run(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length == 0)
      throw new IllegalArgumentException(
        "InstructionTestRunner: no command specified.\n" +
        "Usage: skill-test-runner <command> [args...]\n" +
        "Commands: extract-units, extract-model, extract-test-dir, detect-changes, " +
        "map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, " +
        "merge-results, create-isolation-branch, create-runner-worktrees, check-run-contamination, " +
        "remove-runner-worktrees, write-test-results, save-failed-run, remove-runner-worktree, " +
        "remove-sanitized-branch, prepare-run, prepare-trial, get-json-field, get-tc-name, " +
        "get-worktree-field, run-sprt-batch, run-full-sprt, run-single-test");

    String command = args[0];
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    switch (command)
    {
      case "extract-units" -> out.println(extractUnits(rest));
      case "extract-model" -> out.println(extractModel(rest));
      case "extract-test-dir" -> out.println(extractTestDir(rest));
      case "detect-changes" -> out.println(detectChanges(rest));
      case "map-units" -> out.println(mapUnits(rest));
      case "persist-artifacts" -> persistArtifacts(rest, out);
      case "init-sprt" -> out.println(initSprt(rest));
      case "update-sprt" -> updateSprt(rest);
      case "check-boundary" -> out.println(checkBoundary(rest));
      case "smoke-status" -> out.println(smokeStatus(rest));
      case "merge-results" -> out.println(mergeResults(rest));
      case "create-runner-worktrees" -> out.println(createRunnerWorktrees(rest));
      case "check-run-contamination" -> out.println(checkRunContamination(rest));
      case "remove-runner-worktrees" -> out.println(removeRunnerWorktrees(rest));
      case "remove-runner-worktree" -> out.println(removeRunnerWorktree(rest));
      case "prepare-trial" -> out.println(prepareTrial(rest));
      case "get-json-field" -> out.println(getJsonField(rest));
      case "run-sprt-batch" -> out.println(runSprtBatch(rest));
      case "run-full-sprt" -> runFullSprt(rest, out);
      case "run-single-test" -> runSingleTest(rest, out);
      default -> throw new IllegalArgumentException(
        "InstructionTestRunner: unknown command: " + command + "\n" +
        "Valid commands: extract-units, extract-model, extract-test-dir, detect-changes, " +
        "map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, " +
        "merge-results, create-runner-worktrees, check-run-contamination, remove-runner-worktrees, " +
        "remove-runner-worktree, prepare-trial, get-json-field, run-sprt-batch, run-full-sprt, " +
        "run-single-test");
    }
  }

  /**
   * Implements the {@code extract-units} command.
   * <p>
   * Parses the skill file at the given path, strips YAML frontmatter, and returns a tab-separated
   * line-numbered representation of the body suitable for semantic unit extraction.
   *
   * @param args {@code [skill_path]}
   * @return the line-numbered skill body
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String extractUnits(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-units: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-units <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-units: file not found: " + skillPath);
    return bodyWithLineNumbers(skillPath);
  }

  /**
   * Implements the {@code extract-model} command.
   * <p>
   * Reads the YAML frontmatter of the skill and returns the fully-qualified model identifier.
   * The short name from the {@code model:} field is resolved via {@link ModelIdResolver}.
   * Falls back to {@code "haiku"} (resolved to its fully-qualified ID) when the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the fully-qualified model identifier
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String extractModel(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-model: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-model <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-model: file not found: " + skillPath);

    ParsedSkill parsed = parseSkill(skillPath);
    String model = SkillDiscovery.extractField(parsed.frontmatter(), "model");
    if (model.isBlank())
      model = getModel(skillPath);
    return ModelIdResolver.resolve(claudeCodeVersion, model);
  }

  /**
   * Gets the model for a skill or agent by looking it up in {@code model-selection.md}.
   * <p>
   * Derives the skill/agent name from the file path (e.g., {@code .../skills/foo/SKILL.md} → {@code cat:foo}),
   * then scans {@code ${pluginRoot}/rules/model-selection.md} for a matching entry. Returns the model
   * associated with the section the entry appears in ({@code "sonnet"} or {@code "opus"}), or
   * {@code "haiku"} if the entry is not listed.
   *
   * @param skillOrSubagent the path to the skill or agent file (SKILL.md, first-use.md, or agent .md)
   * @return the model short name ({@code "sonnet"}, {@code "opus"}, or {@code "haiku"})
   * @throws IOException if model-selection.md cannot be read
   */
  private String getModel(Path skillOrSubagent) throws IOException
  {
    // Skills use a subdirectory per skill: .../skills/<name>/SKILL.md → parent dir name is the skill name.
    // Agents are flat files: .../agents/<name>.md → file stem is the agent name.
    String fileName = skillOrSubagent.getFileName().toString();
    String entryName;
    if (fileName.equals("SKILL.md") || fileName.equals("first-use.md"))
    {
      Path parentDir = skillOrSubagent.getParent();
      if (parentDir == null)
        return "haiku";
      entryName = "cat:" + parentDir.getFileName().toString();
    }
    else
    {
      // Strip .md extension to get agent name
      String stem;
      if (fileName.endsWith(".md"))
        stem = fileName.substring(0, fileName.length() - 3);
      else
        stem = fileName;
      entryName = "cat:" + stem;
    }

    Path modelSelectionPath = scope.getPluginRoot().resolve("rules/model-selection.md");
    if (Files.notExists(modelSelectionPath))
      return "haiku";

    // Track which section we're in to know which model to return.
    // Section headers use bold markdown: "**Sonnet-preferred ...**" or "**Opus-preferred ...**".
    String currentModel = "haiku";
    for (String line : Files.readAllLines(modelSelectionPath, UTF_8))
    {
      String trimmed = line.trim();
      if (trimmed.startsWith("**Sonnet-preferred"))
        currentModel = "sonnet";
      else if (trimmed.startsWith("**Opus-preferred"))
        currentModel = "opus";
      else if (trimmed.equals("- `" + entryName + "`"))
        return currentModel;
    }
    return "haiku";
  }

  /**
   * Implements the {@code extract-test-dir} command.
   * <p>
   * Computes the test directory path for a given instruction file path. Maps plugin-relative paths by
   * stripping the {@code plugin/} prefix, then prefixes with {@code {projectDir}/plugin/tests/}.
   * <p>
   * Examples:
   * <ul>
   * <li>{@code plugin/skills/foo/first-use.md} → {@code {projectDir}/plugin/tests/skills/foo/first-use}</li>
   * <li>{@code CLAUDE.md} → {@code {projectDir}/plugin/tests/CLAUDE}</li>
   * <li>{@code .claude/rules/common.md} → {@code {projectDir}/plugin/tests/.claude/rules/common}</li>
   * </ul>
   *
   * @param args {@code [instruction-text-path, project-dir]} where {@code instruction-text-path} is
   *             worktree-relative
   * @return the absolute test directory path (no trailing slash)
   * @throws IllegalArgumentException if the wrong number of arguments is supplied
   */
  public String extractTestDir(String[] args)
  {
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-test-dir: expected 2 arguments <instruction-path> <project-dir>, got " +
        args.length + ".\nUsage: instruction-test-runner extract-test-dir " +
        "<instruction-text-path> <project-dir>");
    String instructionPath = args[0];
    String projectDir = args[1];

    // Strip file extension
    int dotIndex = instructionPath.lastIndexOf('.');
    String noExtension;
    if (dotIndex > 0 && dotIndex > instructionPath.lastIndexOf('/'))
      noExtension = instructionPath.substring(0, dotIndex);
    else
      noExtension = instructionPath;

    // Strip "plugin/" prefix for plugin files so tests mirror the plugin/ structure
    String testRelative;
    if (noExtension.startsWith("plugin/"))
      testRelative = noExtension.substring("plugin/".length());
    else
      testRelative = noExtension;

    return projectDir + "/plugin/tests/" + testRelative;
  }

  /**
   * Implements the {@code detect-changes} command.
   * <p>
   * Compares the SHA-256 content hash of the current skill file against the provided hash,
   * and partitions test cases into rerun vs carry-forward.
   *
   * @param args {@code [old_skill_sha256, new_skill_path, test_dir_path]}
   * @return a JSON object with {@code skill_changed}, {@code all_test_case_ids},
   *   {@code rerun_test_case_ids}, and {@code carryforward_test_case_ids}
   * @throws IllegalArgumentException if arguments are missing, the SHA-256 is malformed, or files
   *   are not found
   * @throws IOException if files cannot be read
   */
  public String detectChanges(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner detect-changes: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner detect-changes <old_skill_sha256> <new_skill_path> <test_dir_path>");

    String oldSha = args[0];
    Path newSkillPath = Path.of(args[1]);
    Path testDirPath = Path.of(args[2]);

    if (!oldSha.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException(
        "InstructionTestRunner detect-changes: invalid SHA-256 content hash format: '" + oldSha +
        "'. Expected 64 lowercase hex characters (got " + oldSha.length() + " characters).");

    if (Files.notExists(newSkillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner detect-changes: new skill file not found: " + newSkillPath);
    if (Files.notExists(testDirPath) || !Files.isDirectory(testDirPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner detect-changes: test directory not found: " + testDirPath);

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
      result.put("semantic_units_path_hint",
        "Run: skill-test-runner extract-units " + args[1]);
    }

    // Produce compact single-line JSON to match Bash output style
    return compactJson(result);
  }

  /**
   * Implements the {@code map-units} command.
   * <p>
   * Given a test directory of {@code .md} files and a JSON array of changed semantic unit IDs,
   * determines which test cases must re-run and which carry forward.
   * <p>
   * Each {@code .md} file's filename stem serves as both the test case ID and the semantic unit ID.
   *
   * @param args {@code [test_dir_path, changed_units_json]}
   * @return a JSON object with rerun and carryforward test case ID lists
   * @throws IllegalArgumentException if arguments are missing or the directory is not found
   * @throws IOException              if files cannot be read
   */
  public String mapUnits(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner map-units: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner map-units <test_dir_path> <changed_units_json>");

    Path testDirPath = Path.of(args[0]);
    String changedUnitsJson = args[1];

    if (Files.notExists(testDirPath) || !Files.isDirectory(testDirPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner map-units: test directory not found: " + testDirPath);

    // Parse changed_units_json: a JSON array of string IDs
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

    // Enumerate .md files in the test directory; filename stem is both test case ID and semantic unit ID
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

    return compactJson(result);
  }

  /**
   * Implements the {@code persist-artifacts} command.
   * <p>
   * Records instruction-test run artifacts: computes SHA-256 hashes, writes instruction-test.json, copies
   * .md test case files into the skill's instruction-test directory, and commits via git.
   *
   * @param args {@code [skill_path, artifacts_dir, session_id, worktree_root, phase]}
   * @param out  the stream to write status messages to
   * @throws IllegalArgumentException if arguments are missing or paths are not found
   * @throws IOException              if files cannot be read/written or git commit fails
   */
  public void persistArtifacts(String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length != 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: expected 5 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner persist-artifacts <skill_path> <artifacts_dir> " +
        "<session_id> <worktree_root> <phase>");

    String skillPathArg = args[0];
    Path artifactsDir = Path.of(args[1]);
    String sessionId = args[2];
    Path worktreeRoot = Path.of(args[3]);
    String phase = args[4];

    if (Files.notExists(worktreeRoot))
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: worktree root not found: " + worktreeRoot);
    if (Files.notExists(artifactsDir))
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: artifacts directory not found: " + artifactsDir);

    Path absSkillPath = worktreeRoot.resolve(skillPathArg).normalize();
    validatePathWithinBoundary(worktreeRoot, absSkillPath);
    if (Files.notExists(absSkillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: skill file not found: " + absSkillPath);

    // Enumerate .md test case files from the artifacts directory
    List<Path> testCaseMdFiles = listMdFiles(artifactsDir);
    if (testCaseMdFiles.isEmpty())
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: no .md test case files found in: " + artifactsDir);

    // Compute skill directory for test case copy destination
    Path skillDir = absSkillPath.getParent();
    // Extract skill name from path (e.g., "grep-and-read-agent")
    String skillName = skillDir.getFileName().toString();

    // Copy each .md test case file into the skill's test directory (first-use/)
    Path testCaseDir = skillDir.resolve("first-use");
    Files.createDirectories(testCaseDir);
    List<String> relTestCasePaths = new ArrayList<>();
    Path skillParent = Path.of(skillPathArg).getParent();
    Path skillParentOrDot;
    if (skillParent != null)
      skillParentOrDot = skillParent;
    else
      skillParentOrDot = Path.of(".");
    for (Path srcFile : testCaseMdFiles)
    {
      Path destFile = testCaseDir.resolve(srcFile.getFileName());
      validatePathWithinBoundary(skillDir, destFile);
      Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING);
      relTestCasePaths.add(
        skillParentOrDot.resolve("first-use").resolve(srcFile.getFileName()).toString());
    }

    // Compute hashes and paths for instruction-test.json (stored in .cat/work/instruction-test/{skill}/)
    String skillHash = sha256File(absSkillPath);
    String relInstructionTestDir = skillParentOrDot.resolve("first-use").toString();
    Path catWorkInstructionTestDir = worktreeRoot.resolve(".cat").resolve("work").resolve("instruction-test").
      resolve(skillName);
    Files.createDirectories(catWorkInstructionTestDir);

    String timestamp = ISO_UTC.format(Instant.now());

    // Resolve model_id from skill frontmatter
    ParsedSkill parsed = parseSkill(absSkillPath);
    String model = SkillDiscovery.extractField(parsed.frontmatter(), "model");
    if (model.isBlank())
    {
      throw new IllegalArgumentException(
        "InstructionTestRunner persist-artifacts: no 'model:' field in frontmatter of " +
        absSkillPath + ". Every skill must declare a model.");
    }
    String modelId = ModelIdResolver.resolve(claudeCodeVersion, model);

    // Write instruction-test.json to .cat/work/instruction-test/{skill}/instruction-test.json
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
    // Record the test cases directory as the location of .md test files
    String testCasesHash = sha256Directory(artifactsDir);
    ObjectNode testCasesNode = root.putObject("test_cases");
    testCasesNode.put("path", relInstructionTestDir);
    testCasesNode.put("sha256", testCasesHash);
    String instructionTestContent = mapper.writeValueAsString(root);
    Files.writeString(instructionTestJsonPath, instructionTestContent, UTF_8);

    // Stage all copied .md test case files
    for (String relPath : relTestCasePaths)
    {
      ProcessRunner.Result addResult = ProcessRunner.run(worktreeRoot, "git", "add", relPath);
      if (addResult.exitCode() != 0)
        throw new IOException("git add failed for " + relPath +
          ": exit code " + addResult.exitCode() + ", output: " + addResult.stdout());
    }

    // Commit with retry on lock contention (exponential backoff)
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
        log.warn("InstructionTestRunner: git commit failed (attempt {}/{}), retrying in {}s...",
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
      throw new IOException(
        "InstructionTestRunner persist-artifacts: git commit failed after " + maxRetries + " attempts");

    out.println(
      "skill-test-runner: artifacts committed for phase=" + phase + ", session=" + sessionId);
  }

  /**
   * Implements the {@code init-sprt} command.
   * <p>
   * Initialises per-test-case SPRT state: fresh state for re-run cases, and carry-forward state
   * from the prior instruction-test for unchanged cases. When the prior instruction-test was produced
   * by a different model (detected via the {@code model_id} field), all prior results are treated as
   * stale and carry-forward is skipped entirely.
   *
   * @param args {@code [sprt_state_path, rerun_tc_ids_json, prior_instruction_test_json_path,
   *             current_model_id, (--prior-boost)?]}
   * @return compact JSON {@code {"ok":true}} after writing the initial state to {@code sprt_state_path}
   * @throws IllegalArgumentException if arguments are missing or the prior file is not found
   * @throws IOException              if the prior file cannot be read or the state file cannot be written
   */
  public String initSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 4)
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: expected at least 4 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner init-sprt <sprt_state_path> <rerun_tc_ids_json> " +
        "<prior_instruction_test_json_path> <current_model_id> [--prior-boost]");

    Path sprtStatePath = Path.of(args[0]);
    String currentModelId = args[3];
    if (currentModelId.isBlank())
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: current_model_id must not be blank");
    String rerunJson = args[1];
    String priorPath = args[2];
    boolean usePriorBoost = false;
    for (int i = 4; i < args.length; ++i)
    {
      if (args[i].equals("--prior-boost"))
        usePriorBoost = true;
    }

    boolean hasPrior = !priorPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorPath)))
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: prior instruction-test file not found: " + priorPath);

    JsonMapper mapper = scope.getJsonMapper();

    // Parse rerun IDs
    JsonNode rerunNode = mapper.readTree(rerunJson);
    Set<String> rerunIds = new HashSet<>();
    if (rerunNode.isArray())
    {
      for (JsonNode element : rerunNode)
      {
        if (element.isString())
          rerunIds.add(element.asString());
      }
    }

    // Read prior instruction-test (if any), building a lookup map in a single pass over test_cases
    // so priorIds can be derived from the map's key set without a second traversal.
    // When the prior model_id differs from the current model, invalidate all cached results.
    Map<String, JsonNode> priorByTestCaseId = new HashMap<>();
    if (hasPrior)
    {
      JsonNode priorRoot = mapper.readTree(Path.of(priorPath).toFile());
      String priorModelId = priorRoot.path("model_id").asString("");
      // Only carry forward prior results when model_id matches the current model
      boolean modelMatches = !priorModelId.isBlank() && priorModelId.equals(currentModelId);
      if (!modelMatches && !priorModelId.isBlank())
      {
        log.warn("Model changed from {} to {}, invalidating cached SPRT results",
          priorModelId, currentModelId);
      }
      else if (priorModelId.isBlank())
        log.warn("Prior instruction-test has no model_id, invalidating cached SPRT results");
      if (modelMatches)
      {
        JsonNode priorTestCases = priorRoot.path("test_cases");
        if (priorTestCases.isArray())
        {
          for (JsonNode tc : priorTestCases)
          {
            String tcId = tc.path("test_case_id").asString("");
            if (!tcId.isBlank())
              priorByTestCaseId.put(tcId, tc);
          }
        }
      }
    }

    // Build union of prior IDs and rerun IDs (preserving insertion order, O(1) deduplication).
    // Prior IDs come first so carry-forward cases precede fresh re-runs in iteration order.
    SequencedSet<String> allIds = new LinkedHashSet<>(priorByTestCaseId.keySet());
    allIds.addAll(rerunIds);

    ObjectNode sprtState = mapper.createObjectNode();

    for (String testCaseId : allIds)
    {
      ObjectNode entry = mapper.createObjectNode();
      if (rerunIds.contains(testCaseId))
      {
        // Fresh SPRT state for re-run cases
        double initialLogRatio = 0.0;
        if (usePriorBoost)
        {
          JsonNode priorTc = priorByTestCaseId.get(testCaseId);
          if (priorTc != null && priorTc.path("decision").asString("").equals("ACCEPT"))
            initialLogRatio = PRIOR_BOOST;
        }
        entry.put("log_ratio", initialLogRatio);
        entry.put("passes", 0);
        entry.put("fails", 0);
        entry.put("runs", 0);
        entry.put("decision", "INCONCLUSIVE");
        entry.put("carried_forward", false);
        entry.put("smoke_runs_done", 0);
      }
      else
      {
        // Carry-forward state from prior instruction-test
        double logRatio = 0.0;
        int passes = 0;
        int fails = 0;
        int runs = 0;
        String decision = "INCONCLUSIVE";
        int smokeRunsDone = 0;
        JsonNode priorTc = priorByTestCaseId.get(testCaseId);
        if (priorTc != null)
        {
          JsonNode logRatioNode = priorTc.path("log_ratio");
          if (logRatioNode.isNumber())
            logRatio = logRatioNode.asDouble();
          JsonNode passesNode = priorTc.path("passes");
          if (passesNode.isNumber())
            passes = passesNode.asInt();
          JsonNode failsNode = priorTc.path("fails");
          if (failsNode.isNumber())
            fails = failsNode.asInt();
          JsonNode runsNode = priorTc.path("runs");
          if (runsNode.isNumber())
            runs = runsNode.asInt();
          String priorDecision = priorTc.path("decision").asString("");
          if (!priorDecision.isBlank())
            decision = priorDecision;
          JsonNode smokeNode = priorTc.path("smoke_runs_done");
          if (smokeNode.isNumber())
            smokeRunsDone = smokeNode.asInt();
        }
        entry.put("log_ratio", logRatio);
        entry.put("passes", passes);
        entry.put("fails", fails);
        entry.put("runs", runs);
        entry.put("decision", decision);
        entry.put("carried_forward", true);
        entry.put("smoke_runs_done", smokeRunsDone);
      }
      sprtState.set(testCaseId, entry);
    }

    ObjectNode stateDoc = mapper.createObjectNode();
    stateDoc.put("model_id", currentModelId);
    stateDoc.set("sprt_state", sprtState);
    Files.createDirectories(sprtStatePath.getParent());
    Files.writeString(sprtStatePath, compactJson(stateDoc), UTF_8);

    ObjectNode result = mapper.createObjectNode();
    result.put("ok", true);
    return compactJson(result);
  }

  /**
   * Implements the {@code update-sprt} command.
   * <p>
   * Applies one PASS or FAIL observation to the SPRT log-ratio for a single test case and
   * re-evaluates boundary conditions.
   *
   * @param args {@code [sprt_state_path, tc_id, passed]}
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read or written
   */
  public void updateSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner update-sprt <sprt_state_path> <tc_id> <passed>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];
    String passedStr = args[2];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: state file not found: " + statePath);
    if (!passedStr.equals("true") && !passedStr.equals("false"))
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: <passed> must be 'true' or 'false', got: " + passedStr);

    boolean passed = passedStr.equals("true");
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");

    // Read current values
    JsonNode tcNode = sprtStateNode.path(testCaseId);
    double currentLogRatio = tcNode.path("log_ratio").asDouble(0.0);
    int currentPasses = tcNode.path("passes").asInt(0);
    int currentFails = tcNode.path("fails").asInt(0);
    int currentRuns = tcNode.path("runs").asInt(0);
    int currentSmoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);

    // Apply observation
    double increment;
    if (passed)
      increment = SPRT_LOG_PASS;
    else
      increment = SPRT_LOG_FAIL;
    double newLogRatio = currentLogRatio + increment;
    int newRuns = currentRuns + 1;
    int newSmoke;
    if (currentSmoke < SMOKE_RUNS)
      newSmoke = currentSmoke + 1;
    else
      newSmoke = currentSmoke;
    int newPasses;
    int newFails;
    if (passed)
    {
      newPasses = currentPasses + 1;
      newFails = currentFails;
    }
    else
    {
      newPasses = currentPasses;
      newFails = currentFails + 1;
    }

    String newDecision;
    if (newLogRatio >= SPRT_ACCEPT)
      newDecision = "ACCEPT";
    else if (newLogRatio <= SPRT_REJECT)
      newDecision = "REJECT";
    else
      newDecision = "INCONCLUSIVE";

    // Build updated state
    ObjectNode newStateRoot = mapper.createObjectNode();
    ObjectNode newSprtState = mapper.createObjectNode();

    // Copy all existing entries, replacing the updated one
    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        if (entry.getKey().equals(testCaseId))
        {
          ObjectNode updatedEntry = mapper.createObjectNode();
          updatedEntry.put("log_ratio", newLogRatio);
          updatedEntry.put("passes", newPasses);
          updatedEntry.put("fails", newFails);
          updatedEntry.put("runs", newRuns);
          updatedEntry.put("decision", newDecision);
          updatedEntry.put("carried_forward", carriedForward);
          updatedEntry.put("smoke_runs_done", newSmoke);
          newSprtState.set(entry.getKey(), updatedEntry);
        }
        else
        {
          newSprtState.set(entry.getKey(), entry.getValue());
        }
      }
    }

    // Copy all top-level fields from the original state (e.g., model_id) before overwriting
    // sprt_state. Without this, a round-trip through update-sprt would strip fields written by
    // init-sprt, breaking downstream commands that rely on model_id.
    if (stateRoot.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : stateRoot.properties())
      {
        if (!entry.getKey().equals("sprt_state"))
          newStateRoot.set(entry.getKey(), entry.getValue());
      }
    }
    newStateRoot.set("sprt_state", newSprtState);
    // Atomic temp-file swap: write to .tmp then rename to avoid truncating the input file
    Path tempPath = statePath.resolveSibling(statePath.getFileName() + ".tmp");
    Files.writeString(tempPath, compactJson(newStateRoot), UTF_8);
    Files.move(tempPath, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Implements the {@code check-boundary} command.
   * <p>
   * Returns the current SPRT boundary decision for a single test case.
   *
   * @param args {@code [sprt_state_path, tc_id]}
   * @return a JSON object with decision, log_ratio, runs, smoke_runs_done, and carried_forward
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read
   */
  public String checkBoundary(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner check-boundary: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner check-boundary <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner check-boundary: state file not found: " + statePath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode tcNode = stateRoot.path("sprt_state").path(testCaseId);

    double logRatio = tcNode.path("log_ratio").asDouble(0.0);
    int runs = tcNode.path("runs").asInt(0);
    int smoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);
    String decision = tcNode.path("decision").asString("INCONCLUSIVE");

    ObjectNode result = mapper.createObjectNode();
    result.put("test_case_id", testCaseId);
    result.put("decision", decision);
    result.put("log_ratio", logRatio);
    result.put("runs", runs);
    result.put("smoke_runs_done", smoke);
    result.put("carried_forward", carriedForward);
    return compactJson(result);
  }

  /**
   * Implements the {@code smoke-status} command.
   * <p>
   * Determines whether a test case is in the smoke-test phase or should escalate to full SPRT.
   *
   * @param args {@code [sprt_state_path, tc_id]}
   * @return a JSON object describing smoke-test phase status
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read
   */
  public String smokeStatus(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner smoke-status: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner smoke-status <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner smoke-status: state file not found: " + statePath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode tcNode = stateRoot.path("sprt_state").path(testCaseId);

    int smoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);
    String decision = tcNode.path("decision").asString("INCONCLUSIVE");

    int remaining = Math.max(0, SMOKE_RUNS - smoke);
    boolean inSmoke = !carriedForward && smoke < SMOKE_RUNS;
    boolean escalate = !carriedForward && smoke >= SMOKE_RUNS && decision.equals("INCONCLUSIVE");

    ObjectNode result = mapper.createObjectNode();
    result.put("test_case_id", testCaseId);
    result.put("in_smoke_phase", inSmoke);
    result.put("smoke_runs_done", smoke);
    result.put("smoke_runs_remaining", remaining);
    result.put("escalate_to_full_sprt", escalate);
    return compactJson(result);
  }

  /**
   * Implements the {@code merge-results} command.
   * <p>
   * Merges new SPRT decisions with carried-forward results to produce a complete instruction-test.json
   * summary ready for committing. The {@code model_id} parameter is included in the output to enable
   * staleness detection on subsequent runs.
   *
   * @param args {@code [new_sprt_state_path, prior_instruction_test_json_path, carryforward_ids_json, model_id]}
   * @return a JSON object with model_id, overall_decision, timestamp, incremental flag, and test_cases
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if files cannot be read
   */
  public String mergeResults(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 4)
      throw new IllegalArgumentException(
        "InstructionTestRunner merge-results: expected 4 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner merge-results <new_sprt_state_path> " +
        "<prior_instruction_test_json_path> <carryforward_ids_json> <model_id>");

    Path statePath = Path.of(args[0]);
    String priorInstructionTestPath = args[1];
    String carryforwardIdsJson = args[2];
    String modelId = args[3];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner merge-results: state file not found: " + statePath);

    boolean hasPrior = !priorInstructionTestPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorInstructionTestPath)))
      throw new IllegalArgumentException(
        "InstructionTestRunner merge-results: prior instruction-test file not found: " + priorInstructionTestPath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");

    // Parse the set of carryforward IDs whose stats should come from the prior instruction-test
    Set<String> carryforwardIds = new HashSet<>();
    JsonNode carryforwardNode = mapper.readTree(carryforwardIdsJson);
    if (carryforwardNode.isArray())
    {
      for (JsonNode element : carryforwardNode)
      {
        if (element.isString())
          carryforwardIds.add(element.asString());
      }
    }

    // Build prior instruction-test lookup map for O(1) access when emitting carryforward stats
    Map<String, JsonNode> priorByTestCaseId = new HashMap<>();
    if (hasPrior)
    {
      JsonNode priorRoot = mapper.readTree(Path.of(priorInstructionTestPath).toFile());
      JsonNode priorTestCases = priorRoot.path("test_cases");
      if (priorTestCases.isArray())
      {
        for (JsonNode tc : priorTestCases)
        {
          String tcId = tc.path("test_case_id").asString("");
          if (!tcId.isBlank())
            priorByTestCaseId.put(tcId, tc);
        }
      }
    }

    // Collect all test case IDs from SPRT state
    List<String> allIds = new ArrayList<>();
    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
        allIds.add(entry.getKey());
    }

    String overallDecision = "ACCEPT";
    ArrayNode testCasesArray = mapper.createArrayNode();

    for (String testCaseId : allIds)
    {
      // When a test case is in the carryforward set and a prior instruction-test is available, emit prior
      // stats rather than the current SPRT state values so the output reflects the original run.
      boolean usePriorStats = carryforwardIds.contains(testCaseId) && priorByTestCaseId.containsKey(testCaseId);

      double logRatio;
      int passes;
      int fails;
      int runs;
      String decision;
      boolean carriedForward;

      if (usePriorStats)
      {
        JsonNode priorTc = priorByTestCaseId.get(testCaseId);
        logRatio = priorTc.path("log_ratio").asDouble(0.0);
        passes = priorTc.path("passes").asInt(0);
        fails = priorTc.path("fails").asInt(0);
        runs = priorTc.path("runs").asInt(0);
        decision = priorTc.path("decision").asString("INCONCLUSIVE");
        carriedForward = true;
      }
      else
      {
        JsonNode tcNode = sprtStateNode.path(testCaseId);
        logRatio = tcNode.path("log_ratio").asDouble(0.0);
        passes = tcNode.path("passes").asInt(0);
        fails = tcNode.path("fails").asInt(0);
        runs = tcNode.path("runs").asInt(0);
        decision = tcNode.path("decision").asString("INCONCLUSIVE");
        carriedForward = tcNode.path("carried_forward").asBoolean(false);
      }

      if (decision.equals("REJECT"))
        overallDecision = "REJECT";
      else if (decision.equals("INCONCLUSIVE") && !overallDecision.equals("REJECT"))
        overallDecision = "INCONCLUSIVE";

      ObjectNode tcEntry = mapper.createObjectNode();
      tcEntry.put("test_case_id", testCaseId);
      tcEntry.put("log_ratio", logRatio);
      tcEntry.put("passes", passes);
      tcEntry.put("fails", fails);
      tcEntry.put("runs", runs);
      tcEntry.put("decision", decision);
      tcEntry.put("carried_forward", carriedForward);
      testCasesArray.add(tcEntry);
    }

    String timestamp = ISO_UTC.format(Instant.now());
    ObjectNode result = mapper.createObjectNode();
    result.put("model_id", modelId);
    result.put("timestamp", timestamp);
    result.put("overall_decision", overallDecision);
    result.put("incremental", true);
    result.set("test_cases", testCasesArray);
    return compactJson(result);
  }

  /**
   * Implements the {@code create-isolation-branch} command.
   * <p>
   * Creates an orphan branch {@code ${issue_name}-sanitized} containing stripped test case files.
   * Frontmatter and the {@code ## Assertions} section are removed from each test case file before
   * committing. The original branch is restored even if an error occurs.
   *
   * @param args {@code [worktree_path, test_dir, issue_name]}
   * @return compact JSON {@code {"isolation_branch":"...","tc_id_map":{"stem":"1",...},
   *   "tc_name_map":{"1":"stem",...},"tc_ids_json":["tc1","tc2",...]}}
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if the working tree is dirty, or a git or file operation fails
   */
  public String createIsolationBranch(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner create-isolation-branch: expected 3 arguments " +
        "<worktree_path> <test_dir> <issue_name>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner create-isolation-branch " +
        "<worktree_path> <test_dir> <issue_name>");

    Path worktreePath = Path.of(args[0]);
    Path testDir = Path.of(args[1]);
    String issueName = args[2];

    // Verify clean working tree before creating the isolation branch
    ProcessRunner.Result statusResult = ProcessRunner.run(worktreePath, "git", "status", "--porcelain");
    if (!statusResult.stdout().isBlank())
      throw new IOException(
        "InstructionTestRunner create-isolation-branch: worktree has uncommitted changes in " +
        worktreePath + ". Commit or stash all changes before creating the isolation branch.\n" +
        "Uncommitted changes:\n" + statusResult.stdout());

    // Enumerate .md files in testDir sorted, excluding test-results.json (which is not .md anyway)
    List<Path> mdFiles = listMdFiles(testDir);

    // Assign sequential opaque IDs: tc1, tc2, ...
    // Maps: stem -> opaque id (string), opaque id -> stem
    Map<String, String> tcIdMap = new LinkedHashMap<>();
    Map<String, String> tcNameMap = new LinkedHashMap<>();
    int opaqueIndex = 1;
    for (Path mdFile : mdFiles)
    {
      String stem = stemOf(mdFile);
      String opaqueId = String.valueOf(opaqueIndex);
      tcIdMap.put(stem, opaqueId);
      tcNameMap.put(opaqueId, stem);
      ++opaqueIndex;
    }

    // Get current branch to restore later
    ProcessRunner.Result branchResult = ProcessRunner.run(worktreePath, "git", "branch", "--show-current");
    String originalBranch = branchResult.stdout().strip();
    if (originalBranch.isBlank())
      throw new IOException(
        "InstructionTestRunner create-isolation-branch: git branch --show-current returned no output " +
        "in directory: " + worktreePath);

    String isolationBranch = issueName + "-sanitized";

    try
    {
      // Create the orphan branch
      ProcessRunner.Result orphanResult = ProcessRunner.run(worktreePath, "git", "checkout",
        "--orphan", isolationBranch);
      if (orphanResult.exitCode() != 0)
        throw new IOException(
          "InstructionTestRunner create-isolation-branch: git checkout --orphan failed with exit code " +
          orphanResult.exitCode() + ": " + orphanResult.stdout());

      // Strip each test case file (remove frontmatter and ## Assertions section)
      for (Path mdFile : mdFiles)
        stripTestCaseFile(mdFile);

      // For each file, call extract-turns binary
      Path extractTurnsBin = scope.getPluginRoot().resolve("client/bin/extract-turns");
      for (Path mdFile : mdFiles)
      {
        String stem = stemOf(mdFile);
        String opaqueId = tcIdMap.get(stem);
        Path destDir = testDir.resolve("tc" + opaqueId);
        ProcessRunner.Result extractResult = ProcessRunner.run(worktreePath,
          extractTurnsBin.toString(), mdFile.toString(), destDir.toString());
        if (extractResult.exitCode() != 0)
          throw new IOException(
            "InstructionTestRunner create-isolation-branch: extract-turns failed for " + mdFile +
            " with exit code " + extractResult.exitCode() + ": " + extractResult.stdout());

        // Delete the original .md file after extraction
        Files.delete(mdFile);
      }

      // Stage and commit
      ProcessRunner.Result addResult = ProcessRunner.run(worktreePath, "git", "add", "-A");
      if (addResult.exitCode() != 0)
        throw new IOException(
          "InstructionTestRunner create-isolation-branch: git add -A failed with exit code " +
          addResult.exitCode() + ": " + addResult.stdout());

      ProcessRunner.Result commitResult = ProcessRunner.run(worktreePath, "git", "commit",
        "-m", "test-runner workspace");
      if (commitResult.exitCode() != 0)
        throw new IOException(
          "InstructionTestRunner create-isolation-branch: git commit failed with exit code " +
          commitResult.exitCode() + ": " + commitResult.stdout());
    }
    finally
    {
      // Always restore the original branch
      ProcessRunner.run(worktreePath, "git", "checkout", originalBranch);
    }

    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode result = mapper.createObjectNode();
    result.put("isolation_branch", isolationBranch);
    ObjectNode tcIdMapNode = mapper.createObjectNode();
    for (Map.Entry<String, String> entry : tcIdMap.entrySet())
      tcIdMapNode.put(entry.getKey(), entry.getValue());
    result.set("tc_id_map", tcIdMapNode);
    ObjectNode tcNameMapNode = mapper.createObjectNode();
    for (Map.Entry<String, String> entry : tcNameMap.entrySet())
      tcNameMapNode.put(entry.getKey(), entry.getValue());
    result.set("tc_name_map", tcNameMapNode);
    // Ordered array of opaque TC IDs (e.g. ["tc1","tc2"]) for direct use in init-sprt
    ArrayNode tcIdsJsonNode = mapper.createArrayNode();
    for (String opaqueId : tcNameMap.keySet())
      tcIdsJsonNode.add("tc" + opaqueId);
    result.set("tc_ids_json", tcIdsJsonNode);
    return compactJson(result);
  }

  /**
   * Implements the {@code create-runner-worktrees} command.
   * <p>
   * Creates one git worktree per UNDECIDED (INCONCLUSIVE) test case in the SPRT state.
   *
   * @param args {@code [worktree_path, sprt_state_path, issue_name, project_dir, session_id]}
   * @return compact JSON object with {@code output_dir} (path to the test-runs session directory,
   *   which is created by this command) and {@code worktrees} (array of worktree descriptors, each
   *   with {@code tc_id}, {@code runner_branch}, {@code runner_worktree}, and {@code trial_num})
   * @throws IllegalArgumentException if the argument count is wrong or the state file is not found
   * @throws IOException              if the state file cannot be read or a git operation fails
   */
  public String createRunnerWorktrees(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner create-runner-worktrees: expected 5 arguments " +
        "<worktree_path> <sprt_state_path> <issue_name> <project_dir> <session_id>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner create-runner-worktrees " +
        "<worktree_path> <sprt_state_path> <issue_name> <project_dir> <session_id>");

    Path worktreePath = Path.of(args[0]);
    Path sprtStatePath = Path.of(args[1]);
    String issueName = args[2];
    String projectDir = args[3];
    String sessionId = args[4];

    if (Files.notExists(sprtStatePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner create-runner-worktrees: state file not found: " + sprtStatePath);

    // Create the output directory for this session's test run JSON files
    Path outputDir = worktreePath.resolve(".cat/work/test-runs").resolve(sessionId);
    Files.createDirectories(outputDir);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(sprtStatePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");

    ArrayNode worktreesArray = mapper.createArrayNode();

    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        String tcId = entry.getKey();
        JsonNode tcNode = entry.getValue();
        String decision = tcNode.path("decision").asString("INCONCLUSIVE");
        if (!decision.equals("INCONCLUSIVE"))
          continue;

        int runs = tcNode.path("runs").asInt(0);
        int trialNum = runs + 1;
        String runnerBranch = issueName + "-" + tcId + "-r" + trialNum;
        String runnerWorktree = projectDir + "/.cat/work/worktrees/" + runnerBranch;

        ProcessRunner.Result worktreeResult = ProcessRunner.run(worktreePath,
          "git", "-C", worktreePath.toString(), "worktree", "add", "-b", runnerBranch,
          runnerWorktree, issueName + "-sanitized");
        if (worktreeResult.exitCode() != 0)
          throw new IOException(
            "InstructionTestRunner create-runner-worktrees: git worktree add failed for " +
            tcId + " with exit code " + worktreeResult.exitCode() + ": " + worktreeResult.stdout());

        ObjectNode worktreeEntry = mapper.createObjectNode();
        worktreeEntry.put("tc_id", tcId);
        worktreeEntry.put("runner_branch", runnerBranch);
        worktreeEntry.put("runner_worktree", runnerWorktree);
        worktreeEntry.put("trial_num", trialNum);
        worktreesArray.add(worktreeEntry);
      }
    }

    ObjectNode result = mapper.createObjectNode();
    result.put("output_dir", outputDir.toString());
    result.set("worktrees", worktreesArray);
    return compactJson(result);
  }

  /**
   * Implements the {@code check-run-contamination} command.
   * <p>
   * Reads the stdout file and checks for cross-run contamination phrases (case-insensitive).
   *
   * @param args {@code [stdout_file]}
   * @return {@code key=value} lines: {@code status=PASS} or
   *   {@code status=FAIL\nviolation=Output contains cross-run reference: "phrase"}
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String checkRunContamination(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner check-run-contamination: expected 1 argument <stdout_file>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner check-run-contamination <stdout_file>");

    Path stdoutFile = Path.of(args[0]);
    if (Files.notExists(stdoutFile))
      throw new IllegalArgumentException(
        "InstructionTestRunner check-run-contamination: file not found: " + stdoutFile);

    String content = Files.readString(stdoutFile, UTF_8);
    String lower = content.toLowerCase(Locale.ROOT);

    String[] contaminationPhrases = {
      "previous run", "earlier attempt", "last time", "as seen before", "prior result",
      "building on", "same approach as run", "consistent with earlier", "in the last run",
      "from the previous", "as before"
    };

    for (String phrase : contaminationPhrases)
    {
      if (lower.contains(phrase))
        return "status=FAIL\nviolation=Output contains cross-run reference: \"" + phrase + "\"";
    }

    return "status=PASS";
  }

  /**
   * Implements the {@code remove-runner-worktrees} command.
   * <p>
   * Bulk-removes all git worktrees and branches whose branch name starts with
   * {@code ${issue_name}-tc}. Also attempts to delete the {@code ${issue_name}-sanitized} branch.
   *
   * @param args {@code [worktree_path, issue_name]}
   * @return {@code key=value} line: {@code removed_count=N}
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if a git operation fails
   */
  public String removeRunnerWorktrees(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner remove-runner-worktrees: expected 2 arguments " +
        "<worktree_path> <issue_name>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner remove-runner-worktrees <worktree_path> <issue_name>");

    Path worktreePath = Path.of(args[0]);
    String issueName = args[1];
    String prefix = issueName + "-tc";

    // Parse git worktree list --porcelain output
    ProcessRunner.Result listResult = ProcessRunner.run(worktreePath,
      "git", "worktree", "list", "--porcelain");

    // Each block is: "worktree /path\nHEAD sha\nbranch refs/heads/branchname\n"
    // Parse blocks separated by blank lines
    List<String> worktreePathsToRemove = new ArrayList<>();
    List<String> branchesToDelete = new ArrayList<>();

    String[] blocks = listResult.stdout().split("\n\n");
    for (String block : blocks)
    {
      String[] lines = block.trim().split("\n");
      String blockPath = "";
      String blockBranch = "";
      for (String line : lines)
      {
        if (line.startsWith("worktree "))
          blockPath = line.substring("worktree ".length()).strip();
        else if (line.startsWith("branch "))
        {
          String ref = line.substring("branch ".length()).strip();
          // ref is "refs/heads/branchname"
          if (ref.startsWith("refs/heads/"))
            blockBranch = ref.substring("refs/heads/".length());
          else
            blockBranch = ref;
        }
      }
      if (!blockBranch.isBlank() && blockBranch.startsWith(prefix))
      {
        worktreePathsToRemove.add(blockPath);
        branchesToDelete.add(blockBranch);
      }
    }

    int removedCount = 0;
    for (int i = 0; i < worktreePathsToRemove.size(); ++i)
    {
      String wtPath = worktreePathsToRemove.get(i);
      String branch = branchesToDelete.get(i);

      ProcessRunner.Result removeResult = ProcessRunner.run(worktreePath,
        "git", "worktree", "remove", "--force", wtPath);
      if (removeResult.exitCode() != 0)
        throw new IOException(
          "InstructionTestRunner remove-runner-worktrees: git worktree remove failed for " +
          wtPath + " with exit code " + removeResult.exitCode() + ": " + removeResult.stdout());

      ProcessRunner.Result deleteBranchResult = ProcessRunner.run(worktreePath,
        "git", "branch", "-D", branch);
      if (deleteBranchResult.exitCode() != 0)
        throw new IOException(
          "InstructionTestRunner remove-runner-worktrees: git branch -D failed for " +
          branch + " with exit code " + deleteBranchResult.exitCode() + ": " +
          deleteBranchResult.stdout());

      ++removedCount;
    }

    return "removed_count=" + removedCount;
  }

  /**
   * Implements the {@code remove-sanitized-branch} command.
   * <p>
   * Deletes the sanitized isolation branch created by {@code create-isolation-branch}. This is the
   * cleanup step called after SPRT completes and the caller has finished examining any failures.
   * <p>
   * Branch deletion failure is silently ignored — the branch may have already been deleted by
   * {@code remove-runner-worktrees}.
   *
   * @param args {@code [worktree_path, isolation_branch]}
   * @return {@code "ok"} always (branch deletion failures are silently ignored)
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if a git operation fails unexpectedly
   */
  public String removeSanitizedBranch(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner remove-sanitized-branch: expected 2 arguments " +
        "<worktree_path> <isolation_branch>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner remove-sanitized-branch <worktree_path> <isolation_branch>");

    Path worktreePath = Path.of(args[0]);
    String isolationBranch = args[1];

    // Ignore failure — branch may already be deleted by remove-runner-worktrees
    ProcessRunner.run(worktreePath, "git", "branch", "-D", isolationBranch);

    return "ok";
  }

  /**
   * Implements the {@code save-failed-run} command.
   * <p>
   * Copies the source file to {@code ${worktree_path}/.cat/work/failed-runs/} using the same
   * filename as the source. Creates the destination directory if it does not exist.
   *
   * @param args {@code [worktree_path, source_file]}
   * @return {@code key=value} line: {@code dest_path=...}
   * @throws IllegalArgumentException if the argument count is wrong or the source file is not found
   * @throws IOException              if the copy operation fails
   */
  public String saveFailedRun(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner save-failed-run: expected 2 arguments " +
        "<worktree_path> <source_file>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner save-failed-run <worktree_path> <source_file>");

    Path worktreePath = Path.of(args[0]);
    Path sourceFile = Path.of(args[1]);

    if (Files.notExists(sourceFile))
      throw new IllegalArgumentException(
        "InstructionTestRunner save-failed-run: file not found: " + sourceFile);

    Path failedRunsDir = worktreePath.resolve(".cat/work/failed-runs");
    Files.createDirectories(failedRunsDir);
    Path destFile = failedRunsDir.resolve(sourceFile.getFileName());
    Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);

    return "dest_path=" + destFile;
  }

  /**
   * Implements the {@code remove-runner-worktree} command.
   * <p>
   * Removes a single runner worktree and its associated branch. Branch deletion failure is
   * silently ignored (the branch may have already been removed or may not exist).
   *
   * @param args {@code [worktree_path, runner_worktree, runner_branch]}
   * @return {@code key=value} line: {@code removed=true}
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if {@code git worktree remove} fails
   */
  public String removeRunnerWorktree(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner remove-runner-worktree: expected 3 arguments " +
        "<worktree_path> <runner_worktree> <runner_branch>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner remove-runner-worktree " +
        "<worktree_path> <runner_worktree> <runner_branch>");

    Path worktreePath = Path.of(args[0]);
    String runnerWorktree = args[1];
    String runnerBranch = args[2];

    ProcessRunner.Result removeResult = ProcessRunner.run(worktreePath,
      "git", "worktree", "remove", "--force", runnerWorktree);
    if (removeResult.exitCode() != 0)
      throw new IOException(
        "InstructionTestRunner remove-runner-worktree: git worktree remove failed for " +
        runnerWorktree + " with exit code " + removeResult.exitCode() + ": " +
        removeResult.stdout());

    // Delete branch — ignore failure if branch is already gone
    ProcessRunner.run(worktreePath, "git", "branch", "-D", runnerBranch);

    return "removed=true";
  }

  /**
   * Implements the {@code prepare-run} command.
   * <p>
   * Derives absolute test directory path, relative path, issue name, and SPRT state path
   * from the worktree path and test directory argument.
   *
   * @param args {@code [worktree_path, test_dir]} where {@code test_dir} may be absolute or
   *             relative to {@code worktree_path}
   * @return {@code key=value} lines: {@code test_dir_abs}, {@code test_dir_rel},
   *   {@code issue_name}, {@code sprt_state_path}
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong, worktree_path is not a directory,
   *                                  or test_dir does not exist
   * @throws IOException              if any path resolution fails
   */
  public String prepareRun(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-run: expected 2 arguments " +
        "<worktree_path> <test_dir>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner prepare-run <worktree_path> <test_dir>");
    Path worktreePath = Path.of(args[0]);
    if (!Files.isDirectory(worktreePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-run: worktree_path is not a directory: " + worktreePath);
    Path testDir = Path.of(args[1]);
    if (!testDir.isAbsolute())
      testDir = worktreePath.resolve(testDir);
    if (!Files.isDirectory(testDir))
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-run: test_dir does not exist: " + testDir);
    boolean hasMdFile;
    try (Stream<Path> stream = Files.list(testDir))
    {
      hasMdFile = stream.anyMatch(p ->
      {
        String name = p.getFileName().toString();
        return name.endsWith(".md") && !name.equals("test-results.json");
      });
    }
    if (!hasMdFile)
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-run: test_dir contains no .md test case files " +
        "(excluding test-results.json): " + testDir);
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
   * <p>
   * Reads a turn file from the isolation branch, constructs the preamble, writes the combined
   * prompt to a file, and returns all subprocess inputs as {@code key=value} lines.
   * <p>
   * Also writes a VERSION file to the jlink directory if absent so that the runner session's
   * {@code session-start.sh} hook does not attempt to download the bundle from GitHub.
   *
   * @param args {@code [worktree_path, isolation_branch, test_dir_rel, tc_id, runner_worktree,
   *             output_dir, trial_num, claude_project_dir]}
   * @return {@code key=value} lines: {@code prompt_file}, {@code jlink_bin}, {@code plugin_source},
   *   {@code output_json}
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if the turn file cannot be read from git
   */
  public String prepareTrial(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 8)
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-trial: expected 8 arguments " +
        "<worktree_path> <isolation_branch> <test_dir_rel> <tc_id> <runner_worktree> " +
        "<output_dir> <trial_num> <claude_project_dir>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner prepare-trial <worktree_path> <isolation_branch> " +
        "<test_dir_rel> <tc_id> <runner_worktree> <output_dir> <trial_num> <claude_project_dir>");
    String worktreePath = args[0];
    String isolationBranch = args[1];
    String testDirRel = args[2];
    String tcId = args[3];
    requireThat(tcId, "tcId").matches("tc\\d+");
    String runnerWorktree = args[4];
    String outputDir = args[5];
    String trialNum = args[6];
    String claudeProjectDir = args[7];

    // Read the turn file content from the isolation branch
    ProcessRunner.Result showResult = ProcessRunner.run(Path.of(worktreePath),
      "git", "show", isolationBranch + ":" + testDirRel + "/" + tcId + "_turn1");
    if (showResult.exitCode() != 0)
      throw new IOException(
        "InstructionTestRunner prepare-trial: git show failed for " +
        isolationBranch + ":" + testDirRel + "/" + tcId + "_turn1" +
        " in " + worktreePath + ": " + showResult.stdout());
    String turnContent = showResult.stdout();

    // Build the preamble: provides operational context (CWD, resolve-paths instruction)
    // without revealing assertion content
    String preamble = "[CWD: " + runnerWorktree + "]\n" +
      "Execute the task below immediately. Do not ask for clarification or confirmation.\n" +
      "Every path argument passed to Write, Edit, or Bash MUST begin with the exact CWD value above " +
      "(both relative and absolute paths). " +
      "Example: " + runnerWorktree + "/some/file.txt\n" +
      "Never use any other root for file operations.";

    // Prefer jlink binaries from the runner worktree when available so the test uses
    // the committed build from the isolation branch; fall back to the project-level build
    String runnerJlinkBin = runnerWorktree + "/client/target/jlink/bin";
    String jlinkBin;
    if (Files.isDirectory(Path.of(runnerJlinkBin)))
      jlinkBin = runnerJlinkBin;
    else
      jlinkBin = claudeProjectDir + "/client/target/jlink/bin";

    // Ensure the jlink directory has a VERSION file. session-start.sh checks for this file
    // before running the Java dispatcher; without it, it tries to download the bundle from
    // GitHub (which will fail in SPRT runner sessions that have no network release). The
    // jlink directory is a build artifact not managed by the plugin install process, so the
    // VERSION stamp must be written here.
    Path jlinkDir = Path.of(jlinkBin).getParent();
    Path versionFile = jlinkDir.resolve("VERSION");
    if (!Files.exists(versionFile) && Files.isDirectory(jlinkDir))
    {
      String pluginVersion = VersionUtils.getPluginVersion(scope);
      Files.writeString(versionFile, pluginVersion, UTF_8);
    }

    String outputJson = outputDir + "/" + tcId + "_run" + trialNum + ".json";

    // Write the combined prompt to a file so claude-runner reads it via --prompt <path>
    // rather than receiving multiline content inline. This keeps all prepare-trial outputs scalar.
    String promptFile = outputDir + "/" + tcId + "_run" + trialNum + "_prompt.txt";
    Files.createDirectories(Path.of(outputDir));
    Files.writeString(Path.of(promptFile), preamble + "\n\n" + turnContent, UTF_8);

    // Always use the runner worktree's plugin directory so the test run uses the committed plugin
    // version from the isolation branch, not the globally installed plugin cache.
    String pluginSource = runnerWorktree + "/plugin/";

    StringJoiner output = new StringJoiner("\n");
    output.add("prompt_file=" + promptFile);
    output.add("jlink_bin=" + jlinkBin);
    output.add("plugin_source=" + pluginSource);
    output.add("output_json=" + outputJson);
    return output.toString();
  }

  /**
   * Implements the {@code get-json-field} command.
   * <p>
   * Extracts a top-level field from a JSON object string. Scalar values (strings, numbers,
   * booleans) are returned as plain text without JSON quoting. Non-scalar values (arrays,
   * objects) are returned as compact JSON.
   *
   * @param args {@code [json_string, field_name]}
   * @return the field value: unquoted text for scalars, compact JSON for arrays and objects
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong or the field is not found
   * @throws IOException              if the JSON cannot be parsed
   */
  public String getJsonField(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner get-json-field: expected 2 arguments " +
        "<json_string> <field_name>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner get-json-field <json_string> <field_name>");
    String jsonString = args[0];
    String fieldName = args[1];
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(jsonString);
    JsonNode fieldNode = root.path(fieldName);
    if (fieldNode.isMissingNode())
      throw new IllegalArgumentException(
        "InstructionTestRunner get-json-field: field '" + fieldName +
        "' not found in JSON: " + jsonString);
    // Scalar values (string, number, boolean, null) are returned as unquoted plain text.
    // Non-scalar values (arrays, objects) are returned as compact JSON for downstream CLI tools.
    if (fieldNode.isValueNode())
      return fieldNode.asString();
    return fieldNode.toString();
  }

  /**
   * Implements the {@code get-tc-name} command.
   * <p>
   * Looks up the original filename stem for an opaque test-case ID using the
   * {@code tc_name_map} field of the JSON returned by {@code create-isolation-branch}.
   *
   * @param args {@code [isolation_result_json, tc_id]}
   * @return the original filename stem (e.g., {@code "creates-hello-file"})
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong, the JSON is invalid,
   *                                  {@code tc_id} does not start with {@code "tc"}, or the
   *                                  ID is not found in the map
   * @throws IOException              if the JSON cannot be parsed
   */
  public String getTcName(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner get-tc-name: expected 2 arguments " +
        "<isolation_result_json> <tc_id>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner get-tc-name <isolation_result_json> <tc_id>");
    String isolationResultJson = args[0];
    String tcId = args[1];
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(isolationResultJson);
    JsonNode tcNameMapNode = root.path("tc_name_map");
    if (tcNameMapNode.isMissingNode())
      throw new IllegalArgumentException(
        "InstructionTestRunner get-tc-name: 'tc_name_map' field not found in isolation " +
        "result JSON");
    if (!tcId.startsWith("tc"))
      throw new IllegalArgumentException(
        "InstructionTestRunner get-tc-name: tc_id must start with 'tc', got: " + tcId);
    // Strip "tc" prefix to get the numeric key used in tc_name_map (e.g., "tc1" → "1")
    String numericKey = tcId.substring(2);
    JsonNode stemNode = tcNameMapNode.path(numericKey);
    if (stemNode.isMissingNode())
      throw new IllegalArgumentException(
        "InstructionTestRunner get-tc-name: tc_id '" + tcId + "' (key '" + numericKey +
        "') not found in tc_name_map");
    return stemNode.asString();
  }

  /**
   * Implements the {@code get-worktree-field} command.
   * <p>
   * Extracts a named field from the worktree descriptor for a given test-case ID, using the
   * JSON returned by {@code create-runner-worktrees}.
   *
   * @param args {@code [create_runner_worktrees_json, tc_id, field_name]}
   * @return the string value of the named field
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong, the JSON is invalid,
   *                                  {@code tc_id} is not found in the worktrees array, or
   *                                  {@code field_name} does not exist in the descriptor
   * @throws IOException              if the JSON cannot be parsed
   */
  public String getWorktreeField(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner get-worktree-field: expected 3 arguments " +
        "<create_runner_worktrees_json> <tc_id> <field_name>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner get-worktree-field " +
        "<create_runner_worktrees_json> <tc_id> <field_name>");
    String worktreesJson = args[0];
    String tcId = args[1];
    String fieldName = args[2];
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(worktreesJson);
    JsonNode worktreesNode = root.path("worktrees");
    if (worktreesNode.isMissingNode() || !worktreesNode.isArray())
      throw new IllegalArgumentException(
        "InstructionTestRunner get-worktree-field: 'worktrees' array not found in JSON");
    for (JsonNode worktree : worktreesNode)
    {
      JsonNode tcIdNode = worktree.path("tc_id");
      if (!tcIdNode.isMissingNode() && tcIdNode.asString().equals(tcId))
      {
        JsonNode fieldNode = worktree.path(fieldName);
        if (fieldNode.isMissingNode())
          throw new IllegalArgumentException(
            "InstructionTestRunner get-worktree-field: field '" + fieldName +
            "' not found in worktree descriptor for tc_id '" + tcId + "'");
        return fieldNode.asString();
      }
    }
    throw new IllegalArgumentException(
      "InstructionTestRunner get-worktree-field: tc_id '" + tcId +
      "' not found in worktrees array");
  }
  /**
   * Implements the {@code run-sprt-batch} command.
   * <p>
   * Orchestrates a single SPRT batch run: creates runner worktrees, prepares trials, launches
   * claude-runner instances, grades results, updates SPRT state, checks boundaries, and cleans up.
   *
   * @param args {@code [worktree_path, sprt_state_json, issue_name, test_dir_rel, project_dir, session_id,
   *   model_id, batch_num, isolation_result_json]}
   * @return compact JSON: {@code {"decided_count": N, "inconclusive_tcs": ["tc1", ...]}}
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   * @throws InterruptedException     if waiting for a runner process is interrupted
   */
  public String runSprtBatch(String[] args) throws IOException, InterruptedException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 9)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-sprt-batch: expected 9 arguments, got " + args.length + ".\n" +
        "Usage: instruction-test-runner run-sprt-batch <worktree_path> <sprt_state_json> " +
        "<issue_name> <test_dir_rel> <project_dir> <session_id> <model_id> <batch_num> <isolation_result_json>");

    String worktreePathStr = args[0];
    String sprtStateJson = args[1];
    String issueName = args[2];
    String testDirRel = args[3];
    String projectDir = args[4];
    String sessionId = args[5];
    String modelId = args[6];
    // args[7] is batch_num, not used (trial numbers come from worktree descriptors)
    String isolationResultJson = args[8];

    Path jlinkBin = scope.getPluginRoot().resolve("client/bin");
    JsonMapper mapper = scope.getJsonMapper();
    Object sprtLock = new Object();

    // Step 1: Create runner worktrees
    String[] createArgs = {worktreePathStr, sprtStateJson, issueName, projectDir, sessionId};
    String worktreesJson = createRunnerWorktrees(createArgs);
    JsonNode worktreesRoot = mapper.readTree(worktreesJson);
    String outputDir = worktreesRoot.path("output_dir").asString();
    ArrayNode worktreesArray = (ArrayNode) worktreesRoot.path("worktrees");

    int decidedCount = 0;
    ArrayNode inconclusiveTcs = mapper.createArrayNode();

    // Read SPRT state to prioritize failed TCs
    JsonNode sprtState = mapper.readTree(Files.readString(Path.of(sprtStateJson), UTF_8));
    JsonNode sprtStateData = sprtState.path("sprt_state");
    List<JsonNode> sortedWorktrees = new ArrayList<>();
    for (JsonNode node : worktreesArray)
      sortedWorktrees.add(node);
    sortedWorktrees.sort((a, b) ->
    {
      String tcIdA = a.path("tc_id").asString();
      String tcIdB = b.path("tc_id").asString();
      int failsA = sprtStateData.path(tcIdA).path("fails").asInt(0);
      int failsB = sprtStateData.path(tcIdB).path("fails").asInt(0);
      return Integer.compare(failsB, failsA);
    });

    // Adaptive parallelism: start at 2, double on success, halve on failure, cap at core count
    int maxParallelism = Runtime.getRuntime().availableProcessors();
    int currentParallelism = Math.min(2, maxParallelism);
    int processedCount = 0;
    List<String> graderFailures = Collections.synchronizedList(new ArrayList<>());

    // Step 2: Process TCs with full pipeline per TC (run → grade → update SPRT)
    while (processedCount < sortedWorktrees.size())
    {
      int batchSize = Math.min(currentParallelism, sortedWorktrees.size() - processedCount);
      List<Thread> pipelineThreads = new ArrayList<>();
      List<Boolean> pipelineFailures = Collections.synchronizedList(new ArrayList<>());
      List<IOException> pipelineErrors = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger batchDecidedCount = new AtomicInteger(0);

      for (int i = 0; i < batchSize; ++i)
      {
        JsonNode worktreeNode = sortedWorktrees.get(processedCount + i);
        String tcId = worktreeNode.path("tc_id").asString();
        String runnerWorktree = worktreeNode.path("runner_worktree").asString();
        int trialNum = worktreeNode.path("trial_num").asInt();
        int tcNum = Integer.parseInt(tcId.substring(2));

        // Prepare trial
        String[] prepareArgs = {
          worktreePathStr,
          issueName + "-sanitized",
          testDirRel,
          tcId,
          runnerWorktree,
          outputDir,
          String.valueOf(trialNum),
          projectDir
        };
        String prepareResult = prepareTrial(prepareArgs);
        String promptFile = null;
        String pluginSource = null;
        String outputJson = null;
        for (String line : prepareResult.split("\n"))
        {
          if (line.startsWith("prompt_file="))
            promptFile = line.substring("prompt_file=".length());
          else if (line.startsWith("plugin_source="))
            pluginSource = line.substring("plugin_source=".length());
          else if (line.startsWith("output_json="))
            outputJson = line.substring("output_json=".length());
        }

        if (promptFile == null || pluginSource == null || outputJson == null)
          throw new IOException("prepare-trial did not return all required fields for " + tcId);

        // Launch full pipeline (run → grade → update SPRT) in one thread
        String finalPromptFile = promptFile;
        String finalPluginSource = pluginSource;
        String finalOutputJson = outputJson;
        Thread pipelineThread = Thread.ofVirtual().start(() ->
        {
          boolean failed = false;
          try
          {
            // Step 1: Run trial
            String[] runnerArgs = {
              "--prompt-file", finalPromptFile,
              "--model", modelId,
              "--plugin-source", finalPluginSource,
              "--jlink-bin", jlinkBin.toString(),
              "--cwd", runnerWorktree,
              "--output", finalOutputJson
            };
            int exitCode;
            try (ClaudeTool runnerScope = new MainClaudeTool();
              PrintStream nullOut = new PrintStream(OutputStream.nullOutputStream(), false, UTF_8))
            {
              exitCode = ClaudeRunner.run(runnerScope, runnerArgs, nullOut);
            }

            if (exitCode != 0 || !Files.exists(Path.of(finalOutputJson)))
            {
              log.warn("TC{}: runner failed (exit={})", tcNum, exitCode);
              failed = true;
              return;
            }

            // Step 2: Check contamination
            String contamResult = checkRunContamination(new String[]{finalOutputJson});
            if (contamResult.contains("status=FAIL"))
            {
              log.warn("TC{}: contamination detected", tcNum);
              failed = true;
              return;
            }

            // Step 3: Grade
            String gradeFilePath = Path.of(outputDir, tcId + "_run" + trialNum + "_grade.json").
              toString();
            String verdict;
            boolean graderFailed = false;
            try
            {
              // Use worktreePathStr (not projectDir) so the grader reads assertions from the
              // issue worktree, where test files still exist with their ## Assertions sections.
              // projectDir is the main workspace (e.g. /workspace), which may not have the
              // new test files when they were added as part of this issue branch.
              verdict = gradeTc(tcId, trialNum, finalOutputJson, runnerWorktree,
                Path.of(worktreePathStr, testDirRel).toString(), modelId, jlinkBin,
                gradeFilePath, isolationResultJson);
            }
            catch (IOException e)
            {
              // Check if this is a grader failure (not infrastructure failure)
              String message = e.getMessage();
              if (message.contains("Grader for") || message.contains("Grader did not write"))
              {
                log.error("TC{}: grader failed - {}", tcNum, message);
                verdict = "FAIL";
                graderFailed = true;
              }
              else
              {
                // Infrastructure failure - rethrow to be caught by outer catch
                throw e;
              }
            }

            boolean passed = verdict.equals("PASS");
            if (!passed)
              failed = true;

            // Step 4: Update SPRT (synchronized - modifies shared file)
            synchronized (sprtLock)
            {
              String[] updateArgs = {sprtStateJson, tcId, String.valueOf(passed)};
              updateSprt(updateArgs);

              // Check boundary
              String[] boundaryArgs = {sprtStateJson, tcId};
              String boundaryResult = checkBoundary(boundaryArgs);
              JsonNode boundaryNode = mapper.readTree(boundaryResult);
              String decision = boundaryNode.path("decision").asString();

              log.info("TC{}: {} (trial={})", tcNum, decision, trialNum);

              if (decision.equals("ACCEPT") || decision.equals("REJECT"))
                batchDecidedCount.incrementAndGet();
              else
                inconclusiveTcs.add(tcId);

              // Track grader failures for error reporting after batch
              if (graderFailed)
                graderFailures.add(tcId + "_run" + trialNum);
            }
          }
          catch (IOException e)
          {
            log.error("Pipeline for {} failed", tcId, e);
            pipelineErrors.add(e);
            failed = true;
          }
          finally
          {
            pipelineFailures.add(failed);
          }
        });
        pipelineThreads.add(pipelineThread);
      }

      // Wait for all pipelines to complete
      for (Thread pipelineThread : pipelineThreads)
        pipelineThread.join();

      // Exit immediately if any pipeline had an infrastructure error
      if (!pipelineErrors.isEmpty())
        throw pipelineErrors.getFirst();

      // Update counters
      decidedCount += batchDecidedCount.get();
      boolean anyFailed = pipelineFailures.stream().anyMatch(f -> f);
      processedCount += batchSize;

      // Adjust parallelism
      if (anyFailed)
      {
        currentParallelism = Math.max(1, currentParallelism / 2);
        log.info("Failures detected, reducing parallelism to {}", currentParallelism);
      }
      else if (batchSize == currentParallelism)
      {
        currentParallelism = Math.min(maxParallelism, currentParallelism * 2);
        log.info("Batch succeeded, increasing parallelism to {}", currentParallelism);
      }
    }

    // Step 3: Cleanup
    String[] removeArgs = {worktreePathStr, issueName};
    removeRunnerWorktrees(removeArgs);

    // Grader failures are already marked as FAIL in SPRT state (lines 2113-2127).
    // No need to abort the SPRT loop - let it continue to next batch.

    ObjectNode result = mapper.createObjectNode();
    result.put("decided_count", decidedCount);
    result.set("inconclusive_tcs", inconclusiveTcs);
    return compactJson(result);
  }

  /**
   * Grades a single test case by spawning a grader agent via ClaudeRunner.
   * <p>
   * The grader agent reads the test scenario file and transcript, evaluates assertions,
   * and writes a grade.json file to the specified output path.
   *
   * @param tcId           the test case ID (e.g., "tc1")
   * @param trialNum       the trial number
   * @param outputJson     path to the runner output JSON (transcript)
   * @param runnerWorktree path to the runner worktree
   * @param testDir        path to the test directory containing scenario MD files
   * @param modelId        model ID to use for grading
   * @param jlinkBin       path to jlink bin directory
   * @param gradeOutputPath path where grader should write grade.json
   * @param isolationResult JSON string from create-isolation-branch (contains tc_name_map)
   * @return {@code "PASS"} or {@code "FAIL"}
   * @throws IOException if grading fails
   */
  private String gradeTc(String tcId, int trialNum, String outputJson,
    String runnerWorktree, String testDir, String modelId,
    Path jlinkBin, String gradeOutputPath, String isolationResult)
    throws IOException
  {
    String[] getTcNameArgs = {isolationResult, tcId};
    String originalStem = getTcName(getTcNameArgs);

    Path graderPromptFile = Files.createTempFile("grader-prompt-", ".txt");
    try
    {
      String graderPrompt = buildGraderPrompt(outputJson, testDir, originalStem, tcId,
        trialNum, gradeOutputPath, runnerWorktree);
      Files.writeString(graderPromptFile, graderPrompt, UTF_8);

      Path actualGradePath = invokeGrader(tcId, graderPromptFile, modelId, runnerWorktree,
        jlinkBin, gradeOutputPath, trialNum);

      return extractVerdict(actualGradePath);
    }
    finally
    {
      Files.deleteIfExists(graderPromptFile);
    }
  }

  /**
   * Builds the grader prompt for a test case run.
   *
   * @param outputJson the claude-runner JSON output file path
   * @param testDir the test directory containing scenario files
   * @param originalStem the original test case filename stem
   * @param tcId the test case ID
   * @param trialNum the trial number
   * @param gradeOutputPath the expected output path for the grade file
   * @param runnerWorktree the runner worktree path
   * @return the grader prompt text
   */
  private String buildGraderPrompt(String outputJson, String testDir, String originalStem,
    String tcId, int trialNum, String gradeOutputPath, String runnerWorktree)
  {
    return String.format("""
      Grade the following test run:

      1. **Transcript**: %s (claude-runner JSON output file)
      2. **Scenario file path**: %s
      3. **Run ID**: %s_run_%d
      4. **Output path**: %s
      5. **Runner worktree**: %s
      """,
      outputJson,
      Path.of(testDir, originalStem + ".md"),
      tcId,
      trialNum,
      gradeOutputPath,
      runnerWorktree);
  }

  /**
   * Invokes the grader and returns the actual grade file path.
   * <p>
   * The grader may write the grade file to either the expected location or an alternative
   * location in the runner worktree. This method checks both locations.
   *
   * @param tcId the test case ID
   * @param graderPromptFile the grader prompt file
   * @param modelId the model ID to use for grading
   * @param runnerWorktree the runner worktree path
   * @param jlinkBin the jlink binary path
   * @param gradeOutputPath the expected output path for the grade file
   * @param trialNum the trial number
   * @return the actual path where the grade file was written
   * @throws IOException if the grader fails or the grade file is not found
   */
  private Path invokeGrader(String tcId, Path graderPromptFile, String modelId,
    String runnerWorktree, Path jlinkBin, String gradeOutputPath, int trialNum)
    throws IOException
  {
    try (ClaudeTool graderScope = new MainClaudeTool())
    {
      String[] graderArgs = {
        "--prompt-file", graderPromptFile.toString(),
        "--model", modelId,
        "--agent", "instruction-grader-agent",
        "--plugin-source", Path.of(runnerWorktree, "plugin").toString(),
        "--jlink-bin", jlinkBin.toString(),
        "--cwd", runnerWorktree
      };

      Path graderStdout = Files.createTempFile("grader-stdout-", ".txt");
      try (PrintStream graderOut = new PrintStream(graderStdout.toFile(), UTF_8))
      {
        int exitCode = ClaudeRunner.run(graderScope, graderArgs, graderOut);

        if (exitCode != 0)
        {
          String graderOutput = Files.readString(graderStdout, UTF_8);
          throw new IOException("Grader for " + tcId + " exited with code " + exitCode +
            "\nGrader output:\n" + graderOutput);
        }

        return findGradeFile(tcId, gradeOutputPath, runnerWorktree, trialNum, graderStdout);
      }
      finally
      {
        Files.deleteIfExists(graderStdout);
      }
    }
  }

  /**
   * Finds the actual grade file path.
   * <p>
   * The grader may write to either the expected location or an alternative location in the
   * runner worktree. This method checks both locations.
   *
   * @param tcId the test case ID
   * @param gradeOutputPath the expected output path
   * @param runnerWorktree the runner worktree path
   * @param trialNum the trial number
   * @param graderStdout the grader stdout file (for diagnostics if not found)
   * @return the actual path where the grade file was written
   * @throws IOException if the grade file is not found at either location
   */
  private Path findGradeFile(String tcId, String gradeOutputPath, String runnerWorktree,
    int trialNum, Path graderStdout)
    throws IOException
  {
    Path gradePath = Path.of(gradeOutputPath);
    if (Files.exists(gradePath))
      return gradePath;

    // Extract session ID from gradeOutputPath
    // Format: /path/.cat/work/test-runs/{sessionId}/{tcId}_run{trialNum}_grade.json
    Path gradeParent = gradePath.getParent();
    if (gradeParent != null)
    {
      Path sessionDir = gradeParent.getParent();
      if (sessionDir != null)
      {
        String sessionId = sessionDir.getFileName().toString();
        // Construct alternative path in runner worktree
        Path runnerGradePath = Path.of(runnerWorktree, ".cat", "work", "test-runs",
          sessionId, tcId + "_run" + trialNum + "_grade.json");
        if (Files.exists(runnerGradePath))
          return runnerGradePath;
      }
    }

    // If still not found at either location, throw error
    String graderOutput = Files.readString(graderStdout, UTF_8);
    throw new IOException("Grader for " + tcId + " exited 0 but did not write grade file.\n" +
      "Checked locations:\n  1. " + gradeOutputPath + "\n  2. " +
      Path.of(runnerWorktree, ".cat/work/test-runs/...") + "\n" +
      "Grader output:\n" + graderOutput);
  }

  /**
   * Extracts the overall verdict from a grade file.
   * <p>
   * The overall verdict is PASS only if all assertion results pass.
   *
   * @param gradePath the grade file path
   * @return "PASS" if all assertions passed, "FAIL" otherwise
   * @throws IOException if the grade file is malformed or cannot be read
   */
  private String extractVerdict(Path gradePath) throws IOException
  {
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode gradeNode = mapper.readTree(Files.readString(gradePath, UTF_8));

    // Extract overall verdict from assertion_results array per documented schema
    // Tolerant reader: accept both "assertion_results" (correct) and "assertions" (grader error)
    JsonNode assertionResults = gradeNode.path("assertion_results");
    if (assertionResults.isMissingNode())
      assertionResults = gradeNode.path("assertions");
    if (assertionResults.isMissingNode() || !assertionResults.isArray())
      throw new IOException("Grade file missing assertion_results or assertions field: " + gradePath);

    ArrayNode results = (ArrayNode) assertionResults;
    if (results.isEmpty())
      throw new IOException("Grade file has no assertion_results: " + gradePath);

    // Overall verdict is PASS only if all assertion results pass
    for (JsonNode result : results)
    {
      String verdict = result.path("verdict").asString();
      if (!verdict.equals("PASS"))
        return "FAIL";
    }

    return "PASS";
  }

  /**
   * Implements the {@code run-full-sprt} command.
   * <p>
   * Orchestrates the complete SPRT workflow: prepare run, create isolation branch, initialize SPRT state,
   * run batches until all test cases reach decisions, write test results, and cleanup.
   *
   * @param args {@code [worktree_path, test_dir, test_model, project_dir, session_id]}
   * @param out  the output stream for progress messages (goes to stderr in bash)
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if any I/O operation fails
   * @throws InterruptedException     if a batch run is interrupted
   */
  private void runFullSprt(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-full-sprt: expected 5 arguments " +
        "<worktree_path> <test_dir> <test_model> <project_dir> <session_id>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner run-full-sprt <worktree_path> <test_dir> <test_model> " +
        "<project_dir> <session_id>");

    String worktreePath = args[0];
    String testDir = args[1];
    String testModel = args[2];
    String projectDir = args[3];
    String sessionId = args[4];

    JsonMapper mapper = scope.getJsonMapper();

    // Step 1: prepare-run
    out.println("Step 1: Running prepare-run...");
    String prepareOutput = prepareRun(new String[]{worktreePath, testDir});
    Map<String, String> prepareVars = parseKeyValue(prepareOutput);
    String testDirAbs = prepareVars.get("test_dir_abs");
    String testDirRel = prepareVars.get("test_dir_rel");
    String issueName = prepareVars.get("issue_name");
    String sprtStatePath = prepareVars.get("sprt_state_path");
    out.println("  TEST_DIR_ABS: " + testDirAbs);
    out.println("  ISSUE_NAME: " + issueName);
    out.println("  SPRT_STATE_PATH: " + sprtStatePath);
    out.println();

    // Step 2: Cleanup previous run
    out.println("Step 2: Cleaning up previous run...");
    removeSanitizedBranch(new String[]{worktreePath, issueName + "-sanitized"});
    removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();

    // Step 3: Create isolation branch
    out.println("Step 3: Creating sanitized isolation branch...");
    String isolationResult = createIsolationBranch(new String[]{worktreePath, testDirAbs, issueName});
    JsonNode isolationNode = mapper.readTree(isolationResult);
    String isolationBranch = isolationNode.path("isolation_branch").asString();
    ArrayNode tcIdsArray = (ArrayNode) isolationNode.path("tc_ids_json");
    out.println("  Isolation branch: " + isolationBranch);
    out.println("  Test cases: " + tcIdsArray.size());
    out.println();

    // Step 4: Initialize SPRT
    out.println("Step 4: Initializing SPRT state...");
    initSprt(new String[]{sprtStatePath, mapper.writeValueAsString(tcIdsArray), "/dev/null", testModel});
    out.println("  SPRT state initialized at: " + sprtStatePath);
    out.println();

    // Step 5: SPRT loop
    List<String> tcIds = new ArrayList<>();
    for (JsonNode tcIdNode : tcIdsArray)
      tcIds.add(tcIdNode.asString());

    out.println("=== Starting SPRT Loop ===");
    out.println("Test cases: " + tcIds.size());
    out.println();

    int batchNum = 0;
    List<String> undecided = new ArrayList<>(tcIds);
    Map<String, Integer> runCounts = new HashMap<>();
    Map<String, String> decisions = new HashMap<>();
    for (String tcId : tcIds)
      runCounts.put(tcId, 0);

    while (!undecided.isEmpty())
    {
      ++batchNum;
      out.println("=== Batch " + batchNum + ": " + undecided.size() + " test case(s) remaining ===");

      // Run batch
      runSprtBatch(new String[]{
        worktreePath, sprtStatePath, issueName, testDirRel, projectDir,
        sessionId, testModel, String.valueOf(batchNum), isolationResult
      });

      // Check decisions
      boolean anyReject = false;
      List<String> stillUndecided = new ArrayList<>();
      for (String tcId : undecided)
      {
        String boundaryResult = checkBoundary(new String[]{sprtStatePath, tcId});
        JsonNode boundaryNode = mapper.readTree(boundaryResult);
        String decision = boundaryNode.path("decision").asString();
        int runs = runCounts.get(tcId) + 1;
        runCounts.put(tcId, runs);

        if (decision.equals("ACCEPT") || decision.equals("REJECT"))
        {
          decisions.put(tcId, decision);
          out.println("  ✓ " + tcId + ": " + decision + " (" + runs + " runs)");
          if (decision.equals("REJECT"))
            anyReject = true;
        }
        else if (runs >= 50)
        {
          decisions.put(tcId, "REJECT");
          out.println("  ✗ " + tcId + ": REJECT (truncated at 50 runs)");
          anyReject = true;
        }
        else
        {
          stillUndecided.add(tcId);
        }
      }
      undecided = stillUndecided;
      out.println();

      // Print batch status summary
      out.println("=== Batch " + batchNum + " Summary ===");
      out.println();
      String sprtStateJson = Files.readString(Path.of(sprtStatePath));
      JsonNode sprtState = mapper.readTree(sprtStateJson);
      JsonNode sprtNode = sprtState.path("sprt_state");

      out.printf("%-10s %-7s %-7s %-12s %-6s %-20s%n",
        "TC", "Passes", "Fails", "Decision", "Runs", "Runs to Convergence");
      out.println("-".repeat(72));

      for (String tcId : tcIds)
      {
        JsonNode tcNode = sprtNode.path(tcId);
        int passes = tcNode.path("passes").asInt(0);
        int fails = tcNode.path("fails").asInt(0);
        double logRatio = tcNode.path("log_ratio").asDouble(0.0);
        String decision = decisions.getOrDefault(tcId, "INCONCLUSIVE");
        int runs = runCounts.get(tcId);

        // Estimate runs to convergence for INCONCLUSIVE
        String convergence;
        if (decision.equals("INCONCLUSIVE"))
        {
          // ACCEPT boundary: 2.944, REJECT boundary: -2.944
          // PASS adds ~0.1112, FAIL adds ~-1.0986
          // Show runsToAccept: the number of consecutive passes still needed,
          // so the estimate reflects the accept trajectory we are targeting.
          double toAccept = 2.944 - logRatio;
          int runsToAccept = (int) Math.ceil(toAccept / 0.1112);
          convergence = "~" + runsToAccept + " more";
        }
        else
        {
          convergence = "-";
        }

        out.printf("%-10s %-7d %-7d %-12s %-6d %-20s%n",
          tcId, passes, fails, decision, runs, convergence);
      }
      out.println();

      // Early abort if any test case failed
      if (anyReject)
      {
        out.println("=== SPRT Aborted: At least one test case REJECT detected ===");
        out.println("Remaining test cases (" + undecided.size() + ") will be marked INCONCLUSIVE.");
        // Mark all remaining undecided test cases as INCONCLUSIVE
        for (String tcId : undecided)
        {
          decisions.put(tcId, "INCONCLUSIVE");
          out.println("  " + tcId + ": INCONCLUSIVE (aborted after " + runCounts.get(tcId) + " runs)");
        }
        out.println();
        break;
      }
    }

    out.println("=== SPRT Loop Complete ===");
    out.println();

    // Step 6: Write test results
    out.println("Step 6: Writing test results...");
    String writeOutput = writeTestResults(new String[]{worktreePath, sprtStatePath, testDirAbs});
    Map<String, String> writeVars = parseKeyValue(writeOutput);
    String overallDecision = writeVars.get("overall_decision");
    String testSha = writeVars.get("test_sha");
    out.println("  Overall decision: " + overallDecision);
    out.println("  Test SHA: " + testSha);
    out.println();

    // Step 7: Cleanup
    out.println("Step 7: Cleanup...");
    removeSanitizedBranch(new String[]{worktreePath, isolationBranch});
    removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();

    // Step 8: Report results
    out.println("=== SPRT Results ===");
    out.println();
    out.println("Overall Decision: " + overallDecision);
    out.println("Test SHA: " + testSha);
    out.println();

    for (String tcId : tcIds)
    {
      String originalStem = getTcName(new String[]{isolationResult, tcId});
      out.println(tcId + ": " + decisions.get(tcId) + " (" + runCounts.get(tcId) +
        " runs) - " + originalStem + ".md");
    }

    out.println();
    out.println("COMPLETE: overall_decision=" + overallDecision);
  }

  /**
   * Context data for running a single SPRT test or filtered subset of tests.
   *
   * @param issueName the issue name derived from the worktree path
   * @param sprtStatePath the path to the SPRT state JSON file
   * @param isolationResult the JSON output from the isolation branch creation
   * @param isolationBranch the name of the isolation branch
   * @param filteredTcIds the list of test case IDs matching the filter pattern
   * @param decisions the map of test case IDs to their current SPRT decisions
   */
  private record SingleTestContext(
    String issueName,
    String sprtStatePath,
    String isolationResult,
    String isolationBranch,
    List<String> filteredTcIds,
    Map<String, String> decisions)
  {
  }

  /**
   * Implements the {@code run-single-test} command.
   * <p>
   * Runs a subset of SPRT tests matching the specified name pattern. This is a simplified interface
   * compared to {@code run-full-sprt} that allows running individual tests or a filtered set of tests.
   *
   * @param args {@code [worktree_path, test_dir, test_pattern, test_model, project_dir, session_id]}
   *             where {@code test_pattern} is a test name or glob pattern (e.g., "cache_fix_warning_conveyed"
   *             or "*warning*")
   * @param out  the output stream
   * @throws IOException          if an I/O error occurs
   * @throws InterruptedException if waiting for a runner process is interrupted
   */
  private void runSingleTest(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    validateRunSingleTestArgs(args);

    String worktreePath = args[0];
    String testDir = args[1];
    String testPattern = args[2];
    String testModel = args[3];
    String projectDir = args[4];
    String sessionId = args[5];

    JsonMapper mapper = scope.getJsonMapper();
    SingleTestContext context = prepareSingleTestRun(worktreePath, testDir, testPattern, testModel,
      mapper, out);

    Map<String, Integer> runCounts = runSprtLoop(worktreePath, projectDir, sessionId, testModel,
      context, mapper, out);

    finalizeSingleTestRun(worktreePath, testDir, context, runCounts, out);
  }

  /**
   * Validates arguments for run-single-test command.
   *
   * @param args the command arguments
   * @throws IllegalArgumentException if arguments are invalid
   */
  private void validateRunSingleTestArgs(String[] args)
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 6)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-single-test: expected 6 arguments " +
        "<worktree_path> <test_dir> <test_pattern> <test_model> <project_dir> <session_id>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner run-single-test <worktree_path> <test_dir> <test_pattern> " +
        "<test_model> <project_dir> <session_id>");
  }

  /**
   * Prepares a single test run by cleaning up prior runs, creating an isolation branch,
   * filtering test cases by pattern, and initializing SPRT state.
   *
   * @param worktreePath the worktree path
   * @param testDir the test directory
   * @param testPattern the test pattern (exact name or glob)
   * @param testModel the test model
   * @param mapper the JSON mapper
   * @param out the output stream
   * @return context containing issue name, SPRT state path, isolation result, and filtered test case IDs
   * @throws IOException if an I/O error occurs
   */
  private SingleTestContext prepareSingleTestRun(String worktreePath, String testDir,
    String testPattern, String testModel, JsonMapper mapper, PrintStream out)
    throws IOException
  {
    // Step 1: prepare-run
    out.println("Step 1: Running prepare-run...");
    String prepareOutput = prepareRun(new String[]{worktreePath, testDir});
    Map<String, String> prepareVars = parseKeyValue(prepareOutput);
    String testDirAbs = prepareVars.get("test_dir_abs");
    String issueName = prepareVars.get("issue_name");
    String sprtStatePath = prepareVars.get("sprt_state_path");
    out.println("  TEST_DIR_ABS: " + testDirAbs);
    out.println("  ISSUE_NAME: " + issueName);
    out.println("  SPRT_STATE_PATH: " + sprtStatePath);
    out.println("  TEST_PATTERN: " + testPattern);
    out.println();

    // Step 2: Cleanup previous run
    out.println("Step 2: Cleaning up previous run...");
    removeSanitizedBranch(new String[]{worktreePath, issueName + "-sanitized"});
    removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();

    // Step 3: Create isolation branch
    out.println("Step 3: Creating sanitized isolation branch...");
    String isolationResult = createIsolationBranch(new String[]{worktreePath, testDirAbs, issueName});
    JsonNode isolationNode = mapper.readTree(isolationResult);
    String isolationBranch = isolationNode.path("isolation_branch").asString();
    ArrayNode tcIdsArray = (ArrayNode) isolationNode.path("tc_ids_json");

    // Step 3b: Filter tests by pattern
    List<String> allTcIds = new ArrayList<>();
    for (JsonNode tcIdNode : tcIdsArray)
      allTcIds.add(tcIdNode.asString());

    List<String> filteredTcIds = new ArrayList<>();
    for (String tcId : allTcIds)
    {
      String originalStem = getTcName(new String[]{isolationResult, tcId});
      if (matchesPattern(originalStem, testPattern))
        filteredTcIds.add(tcId);
    }

    if (filteredTcIds.isEmpty())
    {
      out.println("ERROR: No tests match pattern '" + testPattern + "'");
      out.println("Available tests:");
      for (String tcId : allTcIds)
      {
        String stem = getTcName(new String[]{isolationResult, tcId});
        out.println("  - " + stem);
      }
      throw new IllegalArgumentException("No tests match pattern: " + testPattern);
    }

    out.println("  Isolation branch: " + isolationBranch);
    out.println("  Total test cases: " + allTcIds.size());
    out.println("  Filtered test cases: " + filteredTcIds.size());
    for (String tcId : filteredTcIds)
    {
      String stem = getTcName(new String[]{isolationResult, tcId});
      out.println("    " + tcId + ": " + stem);
    }
    out.println();

    // Step 4: Initialize SPRT with filtered test cases
    out.println("Step 4: Initializing SPRT state...");
    ArrayNode filteredTcIdsArray = mapper.createArrayNode();
    for (String tcId : filteredTcIds)
      filteredTcIdsArray.add(tcId);
    initSprt(new String[]{sprtStatePath, mapper.writeValueAsString(filteredTcIdsArray), "/dev/null",
      testModel});
    out.println("  SPRT state initialized at: " + sprtStatePath);
    out.println();

    Map<String, String> decisions = new HashMap<>();
    return new SingleTestContext(issueName, sprtStatePath, isolationResult, isolationBranch,
      filteredTcIds, decisions);
  }

  /**
   * Runs the SPRT loop until all test cases reach a decision or the 50-run truncation limit.
   *
   * @param worktreePath the worktree path
   * @param projectDir the project directory
   * @param sessionId the Claude session ID
   * @param testModel the test model
   * @param context the test context
   * @param mapper the JSON mapper
   * @param out the output stream
   * @return a map of test case IDs to run counts
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if waiting is interrupted
   */
  private Map<String, Integer> runSprtLoop(String worktreePath, String projectDir,
    String sessionId, String testModel, SingleTestContext context, JsonMapper mapper,
    PrintStream out)
    throws IOException, InterruptedException
  {
    out.println("=== Starting SPRT Loop ===");
    out.println("Test cases: " + context.filteredTcIds().size());
    out.println();

    int batchNum = 0;
    List<String> undecided = new ArrayList<>(context.filteredTcIds());
    Map<String, Integer> runCounts = new HashMap<>();
    for (String tcId : context.filteredTcIds())
      runCounts.put(tcId, 0);

    while (!undecided.isEmpty())
    {
      ++batchNum;
      out.println("=== Batch " + batchNum + ": " + undecided.size() + " test case(s) remaining ===");

      runSprtBatch(new String[]{
        worktreePath, context.sprtStatePath(), context.issueName(), context.issueName(),
        projectDir, sessionId, testModel, String.valueOf(batchNum), context.isolationResult()
      });

      boolean anyReject = processBatchResults(undecided, runCounts, context, mapper, out);

      printBatchSummary(batchNum, context, runCounts, mapper, out);

      if (anyReject)
      {
        handleEarlyAbort(undecided, runCounts, context, out);
        break;
      }
    }

    out.println("=== SPRT Loop Complete ===");
    out.println();
    return runCounts;
  }

  /**
   * Processes batch results and updates decisions.
   *
   * @param undecided the list of undecided test cases (mutated)
   * @param runCounts the run counts map
   * @param context the test context
   * @param mapper the JSON mapper
   * @param out the output stream
   * @return {@code true} if any test case was rejected
   * @throws IOException if an I/O error occurs
   */
  private boolean processBatchResults(List<String> undecided, Map<String, Integer> runCounts,
    SingleTestContext context, JsonMapper mapper, PrintStream out)
    throws IOException
  {
    boolean anyReject = false;
    List<String> stillUndecided = new ArrayList<>();

    for (String tcId : undecided)
    {
      String boundaryResult = checkBoundary(new String[]{context.sprtStatePath(), tcId});
      JsonNode boundaryNode = mapper.readTree(boundaryResult);
      String decision = boundaryNode.path("decision").asString();
      int runs = runCounts.get(tcId) + 1;
      runCounts.put(tcId, runs);

      if (decision.equals("ACCEPT") || decision.equals("REJECT"))
      {
        context.decisions().put(tcId, decision);
        out.println("  ✓ " + tcId + ": " + decision + " (" + runs + " runs)");
        if (decision.equals("REJECT"))
          anyReject = true;
      }
      else if (runs >= 50)
      {
        context.decisions().put(tcId, "REJECT");
        out.println("  ✗ " + tcId + ": REJECT (truncated at 50 runs)");
        anyReject = true;
      }
      else
      {
        stillUndecided.add(tcId);
      }
    }

    undecided.clear();
    undecided.addAll(stillUndecided);
    out.println();
    return anyReject;
  }

  /**
   * Prints a batch summary showing SPRT state for all test cases.
   *
   * @param batchNum the batch number
   * @param context the test context
   * @param runCounts the run counts map
   * @param mapper the JSON mapper
   * @param out the output stream
   * @throws IOException if an I/O error occurs
   */
  private void printBatchSummary(int batchNum, SingleTestContext context,
    Map<String, Integer> runCounts, JsonMapper mapper, PrintStream out)
    throws IOException
  {
    out.println("=== Batch " + batchNum + " Summary ===");
    out.println();

    String sprtStateJson = Files.readString(Path.of(context.sprtStatePath()));
    JsonNode sprtState = mapper.readTree(sprtStateJson);
    JsonNode sprtNode = sprtState.path("sprt_state");

    out.printf("%-10s %-7s %-7s %-12s %-6s %-20s%n",
      "TC", "Passes", "Fails", "Decision", "Runs", "Runs to Convergence");
    out.println("-".repeat(72));

    for (String tcId : context.filteredTcIds())
    {
      JsonNode tcNode = sprtNode.path(tcId);
      int passes = tcNode.path("passes").asInt(0);
      int fails = tcNode.path("fails").asInt(0);
      double logRatio = tcNode.path("log_ratio").asDouble(0.0);
      String decision = context.decisions().getOrDefault(tcId, "INCONCLUSIVE");
      int runs = runCounts.get(tcId);

      String convergence;
      if (decision.equals("INCONCLUSIVE"))
      {
        double toAccept = 2.944 - logRatio;
        int runsToAccept = (int) Math.ceil(toAccept / 0.1112);
        convergence = "~" + runsToAccept + " more";
      }
      else
      {
        convergence = "-";
      }

      out.printf("%-10s %-7d %-7d %-12s %-6d %-20s%n",
        tcId, passes, fails, decision, runs, convergence);
    }
    out.println();
  }

  /**
   * Handles early SPRT abort when a test case is rejected.
   *
   * @param undecided the list of undecided test cases
   * @param runCounts the run counts map
   * @param context the test context
   * @param out the output stream
   */
  private void handleEarlyAbort(List<String> undecided, Map<String, Integer> runCounts,
    SingleTestContext context, PrintStream out)
  {
    out.println("=== SPRT Aborted: At least one test case REJECT detected ===");
    out.println("Remaining test cases (" + undecided.size() + ") will be marked INCONCLUSIVE.");
    for (String tcId : undecided)
    {
      context.decisions().put(tcId, "INCONCLUSIVE");
      out.println("  " + tcId + ": INCONCLUSIVE (aborted after " + runCounts.get(tcId) + " runs)");
    }
    out.println();
  }

  /**
   * Finalizes a single test run by writing results, cleaning up, and reporting.
   *
   * @param worktreePath the worktree path
   * @param testDir the test directory
   * @param context the test context
   * @param runCounts the run counts map
   * @param out the output stream
   * @throws IOException if an I/O error occurs
   */
  private void finalizeSingleTestRun(String worktreePath, String testDir,
    SingleTestContext context, Map<String, Integer> runCounts, PrintStream out)
    throws IOException
  {
    // Step 6: Write test results
    out.println("Step 6: Writing test results...");
    Map<String, String> prepareVars = parseKeyValue(
      prepareRun(new String[]{worktreePath, testDir}));
    String testDirAbs = prepareVars.get("test_dir_abs");
    String writeOutput = writeTestResults(new String[]{worktreePath, context.sprtStatePath(),
      testDirAbs});
    Map<String, String> writeVars = parseKeyValue(writeOutput);
    String overallDecision = writeVars.get("overall_decision");
    String testSha = writeVars.get("test_sha");
    out.println("  Overall decision: " + overallDecision);
    out.println("  Test SHA: " + testSha);
    out.println();

    // Step 7: Cleanup
    out.println("Step 7: Cleanup...");
    removeSanitizedBranch(new String[]{worktreePath, context.isolationBranch()});
    removeRunnerWorktrees(new String[]{worktreePath, context.issueName()});
    out.println();

    // Step 8: Report results
    out.println("=== SPRT Results ===");
    out.println();
    out.println("Overall Decision: " + overallDecision);
    out.println("Test SHA: " + testSha);
    out.println();

    for (String tcId : context.filteredTcIds())
    {
      String originalStem = getTcName(new String[]{context.isolationResult(), tcId});
      out.println(tcId + ": " + context.decisions().get(tcId) + " (" + runCounts.get(tcId) +
        " runs) - " + originalStem + ".md");
    }

    out.println();
    out.println("COMPLETE: overall_decision=" + overallDecision);
  }

  /**
   * Checks if a test name matches the given pattern.
   * <p>
   * Supports simple glob patterns with {@code *} wildcard.
   *
   * @param testName the test name to check
   * @param pattern  the pattern (exact name or glob with *)
   * @return {@code true} if the test name matches the pattern
   */
  private static boolean matchesPattern(String testName, String pattern)
  {
    requireThat(testName, "testName").isNotNull();
    requireThat(pattern, "pattern").isNotNull();

    if (pattern.equals(testName))
      return true;

    if (!pattern.contains("*"))
      return false;

    // Convert glob pattern to regex
    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    return testName.matches(regex);
  }

  /**
   * Parses key=value output lines into a map.
   *
   * @param output the key=value output string
   * @return a map of keys to values
   */
  private Map<String, String> parseKeyValue(String output)
  {
    Map<String, String> result = new HashMap<>();
    for (String line : output.split("\n"))
    {
      int eqIndex = line.indexOf('=');
      if (eqIndex > 0)
      {
        String key = line.substring(0, eqIndex);
        String value = line.substring(eqIndex + 1);
        result.put(key, value);
      }
    }
    return result;
  }

  /**
   * Implements the {@code write-test-results} command.
   * <p>
   * Reads the SPRT state, computes an overall decision, writes
   * {@code test-results.json} to the test directory, stages it, and commits with retry.
   *
   * @param args {@code [worktree_path, sprt_state_path, test_dir_path]}
   * @return {@code key=value} lines on success:
   *   {@code status=ok\noverall_decision=ACCEPT|REJECT|INCONCLUSIVE\ntest_sha=<sha>};
   *   on failure: {@code status=error\nmessage=git commit failed after 3 attempts}
   * @throws IllegalArgumentException if the argument count is wrong or the state file is not found
   * @throws IOException              if the state file cannot be read or the JSON cannot be written
   */
  public String writeTestResults(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner write-test-results: expected 3 arguments " +
        "<worktree_path> <sprt_state_path> <test_dir_path>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner write-test-results " +
        "<worktree_path> <sprt_state_path> <test_dir_path>");

    Path worktreePath = Path.of(args[0]);
    Path sprtStatePath = Path.of(args[1]);
    Path testDirPath = Path.of(args[2]);

    if (Files.notExists(sprtStatePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner write-test-results: state file not found: " + sprtStatePath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(sprtStatePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");

    // Compute overall_decision: REJECT if any REJECT; INCONCLUSIVE if any INCONCLUSIVE and no REJECT;
    // ACCEPT if all ACCEPT
    String overallDecision = "ACCEPT";
    ArrayNode testCasesArray = mapper.createArrayNode();

    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        String tcId = entry.getKey();
        JsonNode tcNode = entry.getValue();
        String decision = tcNode.path("decision").asString("INCONCLUSIVE");
        double logRatio = tcNode.path("log_ratio").asDouble(0.0);
        int passCount = tcNode.path("passes").asInt(0);
        int failCount = tcNode.path("fails").asInt(0);
        int totalRuns = tcNode.path("runs").asInt(0);

        if (decision.equals("REJECT"))
          overallDecision = "REJECT";
        else if (decision.equals("INCONCLUSIVE") && !overallDecision.equals("REJECT"))
          overallDecision = "INCONCLUSIVE";

        ObjectNode tcEntry = mapper.createObjectNode();
        tcEntry.put("test_case_id", tcId);
        tcEntry.put("decision", decision);
        tcEntry.put("log_ratio", logRatio);
        tcEntry.put("pass_count", passCount);
        tcEntry.put("fail_count", failCount);
        tcEntry.put("total_runs", totalRuns);
        tcEntry.put("total_tokens", 0);
        tcEntry.put("total_duration_ms", 0);
        testCasesArray.add(tcEntry);
      }
    }

    ObjectNode sprtNode = mapper.createObjectNode();
    sprtNode.set("test_cases", testCasesArray);
    sprtNode.put("overall_decision", overallDecision);
    sprtNode.put("total_tokens", 0);
    sprtNode.put("total_duration_ms", 0);

    ObjectNode output = mapper.createObjectNode();
    output.set("sprt", sprtNode);

    // Write test-results.json
    Path testResultsFile = testDirPath.resolve("test-results.json");
    Files.createDirectories(testDirPath);
    Files.writeString(testResultsFile, prettyJson(output), UTF_8);

    // Stage the file
    ProcessRunner.Result addResult = ProcessRunner.run(worktreePath,
      "git", "add", "--", testResultsFile.toAbsolutePath().toString());
    if (addResult.exitCode() != 0)
      throw new IOException(
        "InstructionTestRunner write-test-results: git add failed with exit code " +
        addResult.exitCode() + ": " + addResult.stdout());

    // Commit with retry (3 attempts, exponential backoff)
    String commitMessage = "test-results: update " + testDirPath.getFileName();
    Random random = new Random();
    boolean committed = false;
    for (int attempt = 1; attempt <= 3; ++attempt)
    {
      ProcessRunner.Result commitResult = ProcessRunner.run(worktreePath,
        "git", "commit", "-m", commitMessage);
      if (commitResult.exitCode() == 0)
      {
        committed = true;
        break;
      }
      if (attempt < 3)
      {
        // Exponential backoff: attempt 1 → 2±rand(2)s, attempt 2 → 4±rand(4)s
        long baseMs = (long) Math.pow(2, attempt) * 1000L;
        long jitterMs = (long) (random.nextDouble() * baseMs);
        try
        {
          Thread.sleep(baseMs + jitterMs);
        }
        catch (InterruptedException _)
        {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    if (committed)
    {
      // Read the SHA of the commit just made so callers can reference the exact test state
      ProcessRunner.Result shaResult = ProcessRunner.run(worktreePath,
        "git", "rev-parse", "HEAD");
      String testSha;
      if (shaResult.exitCode() == 0)
        testSha = shaResult.stdout().trim();
      else
        testSha = "";
      StringJoiner resultLines = new StringJoiner("\n");
      resultLines.add("status=ok");
      resultLines.add("overall_decision=" + overallDecision);
      resultLines.add("test_sha=" + testSha);
      return resultLines.toString();
    }
    return "status=error\nmessage=git commit failed after 3 attempts";
  }

  /**
   * Strips YAML frontmatter (the first {@code ---...---} block) and the {@code ## Assertions} section
   * (everything from that heading to EOF) from a test case file, writing the result back in place.
   *
   * @param filePath the path to the test case file to strip in place
   * @throws IOException if the file cannot be read or written
   */
  private void stripTestCaseFile(Path filePath) throws IOException
  {
    List<String> lines = Files.readAllLines(filePath, UTF_8);
    List<String> stripped = new ArrayList<>();

    // Skip frontmatter: first ---...--- block
    int startIndex = 0;
    if (!lines.isEmpty() && lines.get(0).equals("---"))
    {
      int closingIndex = -1;
      for (int i = 1; i < lines.size(); ++i)
      {
        if (lines.get(i).equals("---"))
        {
          closingIndex = i;
          break;
        }
      }
      if (closingIndex >= 0)
        startIndex = closingIndex + 1;
    }

    // Copy body lines up to (but not including) ## Assertions
    for (int i = startIndex; i < lines.size(); ++i)
    {
      if (lines.get(i).equals("## Assertions"))
        break;
      stripped.add(lines.get(i));
    }

    // Write stripped content back
    StringBuilder content = new StringBuilder();
    for (String line : stripped)
      content.append(line).append('\n');
    Files.writeString(filePath, content.toString(), UTF_8);
  }

  /**
   * Parses the skill file at the given path and returns frontmatter and body lines.
   *
   * @param skillPath path to the skill file
   * @return the parsed skill with frontmatter string, body lines list, and body start line number
   * @throws IOException if the file cannot be read
   */
  private ParsedSkill parseSkill(Path skillPath) throws IOException
  {
    List<String> lines = Files.readAllLines(skillPath, UTF_8);
    if (lines.isEmpty())
      return new ParsedSkill("", List.of(), 1);

    // Check if file has YAML frontmatter (starts with "---")
    if (!lines.get(0).equals("---"))
      return new ParsedSkill("", lines, 1);

    // Find the closing "---" delimiter to locate body start
    int closingIndex = -1;
    for (int i = 1; i < lines.size(); ++i)
    {
      if (lines.get(i).equals("---"))
      {
        closingIndex = i;
        break;
      }
    }

    // If no closing delimiter found, treat as no frontmatter
    if (closingIndex < 0)
      return new ParsedSkill("", lines, 1);

    // Reconstruct frontmatter with delimiters for hash consistency with previous behavior
    String frontmatter = String.join("\n", lines.subList(0, closingIndex + 1));
    // Body begins on the line immediately after the closing ---
    int bodyStartLine = closingIndex + 2;
    List<String> bodyLines = lines.subList(closingIndex + 1, lines.size());
    return new ParsedSkill(frontmatter, bodyLines, bodyStartLine);
  }

  /**
   * Converts an object to compact JSON (single line without indentation).
   *
   * @param value the object to serialize
   * @return compact JSON representation
   */
  private String compactJson(Object value)
  {
    try
    {
      return scope.getJsonMapper().writer().
        withoutFeatures(SerializationFeature.INDENT_OUTPUT).
        writeValueAsString(value);
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Serializes the given value to a pretty-printed JSON string using the shared mapper's default
   * {@code INDENT_OUTPUT} configuration.
   *
   * @param value the object to serialize
   * @return pretty-printed JSON representation
   */
  private String prettyJson(Object value)
  {
    try
    {
      return scope.getJsonMapper().writeValueAsString(value);
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Produces a tab-separated line-numbered representation of a skill's body, using original
   * file line numbers (i.e., offset by the frontmatter line count).
   *
   * @param skillPath path to the skill file
   * @return the line-numbered body text
   * @throws IOException if the file cannot be read
   */
  private String bodyWithLineNumbers(Path skillPath) throws IOException
  {
    ParsedSkill parsed = parseSkill(skillPath);
    List<String> bodyLines = parsed.bodyLines();
    int bodyStartLine = parsed.bodyStartLine();

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < bodyLines.size(); ++i)
    {
      int originalLineNumber = bodyStartLine + i;
      result.append(originalLineNumber).append('\t').append(bodyLines.get(i)).append('\n');
    }
    return result.toString().stripTrailing();
  }

  /**
   * Computes the SHA-256 hex digest of the given bytes.
   *
   * @param bytes the bytes to hash
   * @return lowercase hex SHA-256 digest
   */
  private static String sha256Bytes(byte[] bytes)
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new AssertionError("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Builds the grader argument array for ClaudeRunner invocation.
   * <p>
   * Exposed for testing to validate the --agent argument is correctly constructed.
   *
   * @param graderPromptFile the grader prompt file path
   * @param modelId the model ID to use
   * @param runnerWorktree the runner worktree path
   * @param jlinkBin the jlink binary path
   * @return the grader arguments array
   * @throws NullPointerException if any parameter is null
   */
  private static String[] buildGraderArgs(Path graderPromptFile, String modelId, String runnerWorktree,
    Path jlinkBin)
  {
    return new String[]{
      "--prompt-file", graderPromptFile.toString(),
      "--model", modelId,
      "--agent", "instruction-grader-agent",
      "--plugin-source", Path.of(runnerWorktree, "plugin").toString(),
      "--jlink-bin", jlinkBin.toString(),
      "--cwd", runnerWorktree
    };
  }

  /**
   * Computes the SHA-256 hex digest of the contents of a file.
   *
   * @param filePath path to the file
   * @return lowercase hex SHA-256 digest
   * @throws IOException if the file cannot be read
   */
  private String sha256File(Path filePath) throws IOException
  {
    return sha256Bytes(Files.readAllBytes(filePath));
  }

  /**
   * Computes a combined SHA-256 hex digest over all .md files in a directory, sorted by filename for
   * determinism.
   *
   * @param directory the directory containing .md files
   * @return lowercase hex SHA-256 digest
   * @throws IOException if reading fails
   */
  private String sha256Directory(Path directory) throws IOException
  {
    List<Path> mdFiles = listMdFiles(directory);
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Path file : mdFiles)
      {
        digest.update(file.getFileName().toString().getBytes(UTF_8));
        digest.update(Files.readAllBytes(file));
      }
      return HexFormat.of().formatHex(digest.digest());
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new AssertionError("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Reads all test case IDs from a directory of {@code .md} test case files.
   * <p>
   * Each {@code .md} file's filename stem (without the {@code .md} extension) is its test case ID.
   * Files are returned in sorted order for determinism.
   *
   * @param testDirPath path to the directory containing {@code .md} test case files
   * @return list of test case ID strings in sorted filename order
   * @throws IOException if the directory cannot be read
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
   * Lists all {@code .md} files in the given directory, sorted by filename for determinism.
   *
   * @param directory the directory to scan
   * @return list of {@code .md} file paths in sorted order
   * @throws IOException if the directory cannot be read
   */
  private List<Path> listMdFiles(Path directory) throws IOException
  {
    List<Path> result = new ArrayList<>();
    try (Stream<Path> entries = Files.list(directory))
    {
      List<Path> sorted = entries.sorted(
        Comparator.comparing(p -> p.getFileName().toString())).
        toList();
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
   * Returns the filename stem (name without the last {@code .} extension) of the given path.
   *
   * @param path the file path
   * @return the stem portion of the filename
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
   * Validates that a candidate path is within the given boundary directory.
   *
   * @param boundary  the allowed root directory
   * @param candidate the path to check
   * @throws IllegalArgumentException if {@code candidate} is outside {@code boundary}
   */
  private void validatePathWithinBoundary(Path boundary, Path candidate)
  {
    Path resolvedBoundary = boundary.toAbsolutePath().normalize();
    Path resolvedCandidate = candidate.toAbsolutePath().normalize();
    if (!resolvedCandidate.startsWith(resolvedBoundary))
      throw new IllegalArgumentException(
        "InstructionTestRunner: path traversal detected: '" + candidate + "' is outside '" + boundary + "'");
  }

  /**
   * Parsed skill file content.
   *
   * @param frontmatter   the YAML frontmatter string (empty when absent)
   * @param bodyLines     the body lines after the frontmatter
   * @param bodyStartLine the 1-based line number in the original file where the body begins
   */
  private record ParsedSkill(String frontmatter, List<String> bodyLines, int bodyStartLine)
  {
    /**
     * Creates a new ParsedSkill.
     *
     * @param frontmatter   the YAML frontmatter string
     * @param bodyLines     the body lines after the frontmatter
     * @param bodyStartLine the 1-based line number in the original file where the body begins
     * @throws NullPointerException     if {@code frontmatter} or {@code bodyLines} are null
     * @throws IllegalArgumentException if {@code bodyStartLine} is not positive
     */
    ParsedSkill
    {
      requireThat(frontmatter, "frontmatter").isNotNull();
      requireThat(bodyLines, "bodyLines").isNotNull();
      requireThat(bodyStartLine, "bodyStartLine").isPositive();
    }
  }

  /**
   * Produces a business-format JSON error string with properly escaped {@code message}.
   * <p>
   * Uses {@link JsonMapper#writeValueAsString(Object)} for correct JSON encoding of all control
   * characters (newlines, tabs, carriage returns) in addition to {@code "} and {@code \}.
   *
   * @param scope   the JVM scope providing the shared {@link JsonMapper}
   * @param message the error message to include in the JSON
   * @return a JSON string of the form {@code {"status":"ERROR","message":"..."}}
   * @throws NullPointerException if {@code scope} or {@code message} are null
   * @throws IOException if JSON serialization fails
   */
  public static String toErrorJson(ClaudeTool scope, String message) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(message, "message").isNotNull();
    String escapedMessage = scope.getJsonMapper().writeValueAsString(message);
    return "{\"status\":\"ERROR\",\"message\":" + escapedMessage + "}";
  }

  /**
   * CLI entry point.
   * <p>
   * Reads the subcommand and its arguments from {@code args}, dispatches to the appropriate
   * handler, and prints the JSON result to {@code System.out}. Expected errors (invalid arguments,
   * I/O failures) are reported as business-format JSON on stdout with exit code 0.
   * Unexpected errors are logged and also reported as business-format JSON on stdout.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException | InterruptedException e)
      {
        try
        {
          String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
          System.out.println(toErrorJson(scope, message));
        }
        catch (IOException jsonException)
        {
          Logger log = LoggerFactory.getLogger(InstructionTestRunner.class);
          log.error("Failed to serialize error message", jsonException);
          System.out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
        }
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(InstructionTestRunner.class);
        log.error("Unexpected error", e);
        try
        {
          String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
          System.out.println(toErrorJson(scope, message));
        }
        catch (IOException jsonException)
        {
          log.error("Failed to serialize error message", jsonException);
          System.out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
        }
      }
    }
  }

  /**
   * Executes the skill test runner logic with a caller-provided output stream.
   *
   * @param scope the plugin scope
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   * @throws InterruptedException     if waiting for a runner process is interrupted
   */
  public static void run(ClaudePluginScope scope, String[] args, PrintStream out)
    throws IOException, InterruptedException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String claudeCodeVersion = ModelIdResolver.detectClaudeCodeVersion();
    new InstructionTestRunner(scope, claudeCodeVersion).run(args, out);
  }
}
