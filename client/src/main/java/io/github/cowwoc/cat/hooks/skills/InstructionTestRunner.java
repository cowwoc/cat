/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.ClaudePluginScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import static io.github.cowwoc.cat.hooks.Strings.block;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.SharedSecrets;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Incremental instruction-test driver for instruction-builder-agent.
 * <p>
 * Dispatches 11 subcommands: extract-units, detect-changes, map-units, extract-model, extract-test-dir,
 * persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results.
 * <p>
 * All output is written to stdout as JSON. Expected errors are reported as a block response
 * on stdout with exit code 0. Unexpected errors are logged
 * to stderr and also reported as a block response on stdout with exit code 0.
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
    SharedSecrets.setInstructionTestRunnerAccess(InstructionTestRunner::sha256Bytes);
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
   */
  public void run(String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length == 0)
      throw new IllegalArgumentException(
        "InstructionTestRunner: no command specified.\n" +
        "Usage: skill-test-runner <command> [args...]\n" +
        "Commands: extract-units, extract-model, extract-test-dir, detect-changes, map-units, " +
        "persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results");

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
      case "update-sprt" -> out.println(updateSprt(rest));
      case "check-boundary" -> out.println(checkBoundary(rest));
      case "smoke-status" -> out.println(smokeStatus(rest));
      case "merge-results" -> out.println(mergeResults(rest));
      default -> throw new IllegalArgumentException(
        "InstructionTestRunner: unknown command: " + command + "\n" +
        "Valid commands: extract-units, extract-model, extract-test-dir, detect-changes, map-units, " +
        "persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results");
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
      model = lookupModelInSkillModels(skillPath);
    return ModelIdResolver.resolve(claudeCodeVersion, model);
  }

  /**
   * Looks up the model for a skill in {@code skill-models.md}.
   * <p>
   * Derives the skill name from the file path (e.g., {@code .../skills/foo/SKILL.md} → {@code cat:foo}),
   * then scans {@code ${pluginRoot}/rules/skill-models.md} for a matching entry. Returns {@code "sonnet"}
   * if the skill is listed there, {@code "haiku"} otherwise.
   *
   * @param skillPath the path to the skill file (SKILL.md or first-use.md)
   * @return the model short name ({@code "sonnet"} or {@code "haiku"})
   * @throws IOException if skill-models.md cannot be read
   */
  private String lookupModelInSkillModels(Path skillPath) throws IOException
  {
    // Derive skill directory name from path: .../skills/<name>/SKILL.md or .../skills/<name>/first-use.md
    Path skillDir = skillPath.getParent();
    if (skillDir == null)
      return "haiku";
    String skillName = "cat:" + skillDir.getFileName().toString();

    Path skillModelsPath = scope.getPluginRoot().resolve("rules/skill-models.md");
    if (Files.notExists(skillModelsPath))
      return "haiku";

    // Scan for the skill name in the skill-models.md list entries (e.g., "- `cat:foo`")
    for (String line : Files.readAllLines(skillModelsPath, StandardCharsets.UTF_8))
    {
      String trimmed = line.trim();
      if (trimmed.equals("- `" + skillName + "`"))
        return "sonnet";
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
    Files.writeString(instructionTestJsonPath, instructionTestContent, StandardCharsets.UTF_8);

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
   * @param args {@code [rerun_tc_ids_json, prior_instruction_test_json_path, current_model_id, (--prior-boost)?]}
   * @return a JSON object containing the {@code sprt_state} map
   * @throws IllegalArgumentException if arguments are missing or the prior file is not found
   * @throws IOException              if the prior file cannot be read
   */
  public String initSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: expected at least 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner init-sprt <rerun_tc_ids_json> <prior_instruction_test_json_path> " +
        "<current_model_id> [--prior-boost]");

    String rerunJson = args[0];
    String priorPath = args[1];
    String currentModelId = args[2];
    boolean usePriorBoost = false;
    for (int i = 3; i < args.length; ++i)
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

    ObjectNode result = mapper.createObjectNode();
    result.set("sprt_state", sprtState);
    return compactJson(result);
  }

  /**
   * Implements the {@code update-sprt} command.
   * <p>
   * Applies one PASS or FAIL observation to the SPRT log-ratio for a single test case and
   * re-evaluates boundary conditions.
   *
   * @param args {@code [sprt_state_path, tc_id, passed]}
   * @return the updated SPRT state JSON
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read
   */
  public String updateSprt(String[] args) throws IOException
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

    newStateRoot.set("sprt_state", newSprtState);
    return compactJson(newStateRoot);
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
   * Parses the skill file at the given path and returns frontmatter and body lines.
   *
   * @param skillPath path to the skill file
   * @return the parsed skill with frontmatter string, body lines list, and body start line number
   * @throws IOException if the file cannot be read
   */
  private ParsedSkill parseSkill(Path skillPath) throws IOException
  {
    List<String> lines = Files.readAllLines(skillPath, StandardCharsets.UTF_8);
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
        digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
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
   * CLI entry point.
   * <p>
   * Reads the subcommand and its arguments from {@code args}, dispatches to the appropriate
   * handler, and prints the JSON result to {@code System.out}. Expected errors (invalid arguments,
   * I/O failures) are reported as a block response on stdout with exit code 0.
   * Unexpected errors are logged and also reported as a block response on stdout.
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
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(InstructionTestRunner.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
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
   */
  public static void run(ClaudePluginScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String claudeCodeVersion = ModelIdResolver.detectClaudeCodeVersion();
    new InstructionTestRunner(scope, claudeCodeVersion).run(args, out);
  }
}
