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
import java.util.List;
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
   * Minimum total failures across all test cases within the early-detection window to trigger early stop.
   */
  private static final int EARLY_FAIL_THRESHOLD = 2;
  /**
   * Maximum number of batches during which early-failure-detection is active.
   */
  private static final int EARLY_FAIL_WINDOW = 5;
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
  private final SprtStateManager sprtStateManager;
  private final SprtIsolationManager sprtIsolationManager;
  private final SprtGrader sprtGrader;
  private final SkillMetadataExtractor skillMetadataExtractor;

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
    this.sprtStateManager = new SprtStateManager(scope);
    this.sprtIsolationManager = new SprtIsolationManager(scope);
    this.sprtGrader = new SprtGrader(scope);
    this.skillMetadataExtractor = new SkillMetadataExtractor(scope, claudeCodeVersion);
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
        "Commands: extract-units, extract-model, extract-effort, extract-test-dir, detect-changes, " +
        "map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, " +
        "merge-results, create-isolation-branch, create-runner-worktrees, check-run-contamination, " +
        "remove-runner-worktrees, write-test-results, save-failed-run, remove-runner-worktree, " +
        "remove-isolation-branch, prepare-run, prepare-trial, get-json-field, get-tc-name, " +
        "get-worktree-field, run-sprt-batch, run-full-sprt, run-single-test");

    String command = args[0];
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    switch (command)
    {
      case "extract-units" -> out.println(extractUnits(rest));
      case "extract-model" -> out.println(extractModel(rest));
      case "extract-effort" -> out.println(extractEffort(rest));
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
        "Valid commands: extract-units, extract-model, extract-effort, extract-test-dir, detect-changes, " +
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
    return skillMetadataExtractor.extractUnits(args);
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
    return skillMetadataExtractor.extractModel(args);
  }

  /**
   * Implements the {@code extract-effort} command.
   * <p>
   * Reads the YAML frontmatter of the skill file and returns the {@code effort:} field value,
   * or an empty string if the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the effort level (e.g., {@code "high"}), or {@code ""} if not specified
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String extractEffort(String[] args) throws IOException
  {
    return skillMetadataExtractor.extractEffort(args);
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
    return skillMetadataExtractor.extractTestDir(args);
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
    String model = skillMetadataExtractor.extractStringField(absSkillPath, "model");
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
   *             next_model_id, session_id, (--prior-boost)?]}
   * @return compact JSON {@code {"ok":true}} after writing the initial state to {@code sprt_state_path}
   * @throws IllegalArgumentException if arguments are missing or the prior file is not found
   * @throws IOException              if the prior file cannot be read or the state file cannot be written
   */
  public String initSprt(String[] args) throws IOException
  {
    return sprtStateManager.initSprt(args);
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
    sprtStateManager.updateSprt(args);
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
    return sprtStateManager.checkBoundary(args);
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
    return sprtStateManager.smokeStatus(args);
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
   * Creates an orphan branch {@code ${issue_name}-isolation} containing stripped test case files.
   * Frontmatter, the {@code ## Assertions} section, and everything after it are removed from each test case file before
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
    return sprtIsolationManager.createIsolationBranch(args);
  }

  /**
   * Implements the {@code create-runner-worktrees} command.
   * <p>
   * Creates one git worktree per UNDECIDED (INCONCLUSIVE) test case in the SPRT state.
   *
   * @param args {@code [worktree_path, sprt_state_path, issue_name, session_id]}
   * @return compact JSON object with {@code output_dir} (path to the test-runs session directory,
   *   which is created by this command) and {@code worktrees} (array of worktree descriptors, each
   *   with {@code tc_id}, {@code runner_branch}, {@code runner_worktree}, and {@code trial_num})
   * @throws IllegalArgumentException if the argument count is wrong or the state file is not found
   * @throws IOException              if the state file cannot be read or a git operation fails
   */
  public String createRunnerWorktrees(String[] args) throws IOException
  {
    return sprtIsolationManager.createRunnerWorktrees(args);
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
    return sprtIsolationManager.checkRunContamination(args);
  }

  /**
   * Implements the {@code remove-runner-worktrees} command.
   * <p>
   * Bulk-removes all git worktrees and branches whose branch name starts with
   * {@code ${issue_name}-tc}. Also attempts to delete the {@code ${issue_name}-isolation} branch.
   *
   * @param args {@code [worktree_path, issue_name]}
   * @return {@code key=value} line: {@code removed_count=N}
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if a git operation fails
   */
  public String removeRunnerWorktrees(String[] args) throws IOException
  {
    return sprtIsolationManager.removeRunnerWorktrees(args);
  }

  /**
   * Implements the {@code remove-isolation-branch} command.
   * <p>
   * Deletes the isolation branch created by {@code create-isolation-branch}. This is the
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
  public String removeIsolationBranch(String[] args) throws IOException
  {
    return sprtIsolationManager.removeIsolationBranch(args);
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
    return sprtIsolationManager.removeRunnerWorktree(args);
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
   *             output_dir, trial_num]}
   * @return {@code key=value} lines: {@code prompt_file}, {@code jlink_bin}, {@code plugin_source},
   *   {@code output_json}
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if the turn file cannot be read from git
   */
  public String prepareTrial(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 7)
      throw new IllegalArgumentException(
        "InstructionTestRunner prepare-trial: expected 7 arguments " +
        "<worktree_path> <isolation_branch> <test_dir_rel> <tc_id> <runner_worktree> " +
        "<output_dir> <trial_num>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner prepare-trial <worktree_path> <isolation_branch> " +
        "<test_dir_rel> <tc_id> <runner_worktree> <output_dir> <trial_num>");
    String worktreePath = args[0];
    String isolationBranch = args[1];
    String testDirRel = args[2];
    String tcId = args[3];
    requireThat(tcId, "tcId").matches("tc\\d+");
    String runnerWorktree = args[4];
    String outputDir = args[5];
    String trialNum = args[6];

    String outputJson = outputDir + "/" + tcId + "_run" + trialNum + ".json";

    // Use pre-recorded fixture if present — skip live runner entirely
    ProcessRunner.Result fixtureResult = ProcessRunner.run(Path.of(worktreePath),
      "git", "show", isolationBranch + ":" + testDirRel + "/" + tcId + "_runner.json");
    if (fixtureResult.exitCode() == 0)
    {
      Files.createDirectories(Path.of(outputDir));
      Files.writeString(Path.of(outputJson), fixtureResult.stdout(), UTF_8);
      StringJoiner fixtureOutput = new StringJoiner("\n");
      fixtureOutput.add("runner_fixture=yes");
      fixtureOutput.add("output_json=" + outputJson);
      return fixtureOutput.toString();
    }

    // Read the turn file content from the isolation branch
    ProcessRunner.Result showResult = ProcessRunner.run(Path.of(worktreePath),
      "git", "show", isolationBranch + ":" + testDirRel + "/" + tcId + "_turn1.md");
    if (showResult.exitCode() != 0)
      throw new IOException(
        "InstructionTestRunner prepare-trial: git show failed for " +
        isolationBranch + ":" + testDirRel + "/" + tcId + "_turn1.md" +
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

    String jlinkBin = runnerWorktree + "/client/target/jlink/bin";
    if (!Files.isDirectory(Path.of(jlinkBin)))
      throw new IOException(
        "InstructionTestRunner prepare-trial: jlink directory not found in runner worktree: " +
        jlinkBin + ". Run 'mvn -f client/pom.xml package' before starting SPRT.");

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
   * @param args {@code [worktree_path, sprt_state_json, issue_name, test_dir_rel, session_id,
   *   model_id, batch_num, isolation_result_json]}
   * @return compact JSON: {@code {"decided_count": N, "inconclusive_tcs": ["tc1", ...]}}
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   * @throws InterruptedException     if waiting for a runner process is interrupted
   */
  public String runSprtBatch(String[] args) throws IOException, InterruptedException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 8)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-sprt-batch: expected 8 arguments, got " + args.length + ".\n" +
        "Usage: instruction-test-runner run-sprt-batch <worktree_path> <sprt_state_json> " +
        "<issue_name> <test_dir_rel> <session_id> <model_id> <batch_num> <isolation_result_json>");

    String worktreePathStr = args[0];
    String sprtStateJson = args[1];
    String issueName = args[2];
    String testDirRel = args[3];
    String sessionId = args[4];
    String modelId = args[5];
    int batchNum = Integer.parseInt(args[6]);
    String isolationResultJson = args[7];

    JsonMapper mapper = scope.getJsonMapper();
    Object sprtLock = new Object();

    // Step 1: Create runner worktrees
    String[] createArgs = {worktreePathStr, sprtStateJson, issueName, sessionId};
    String worktreesJson = createRunnerWorktrees(createArgs);
    JsonNode worktreesRoot = mapper.readTree(worktreesJson);
    String outputDir = worktreesRoot.path("output_dir").asString();
    ArrayNode worktreesArray = (ArrayNode) worktreesRoot.path("worktrees");

    // Read SPRT state to prioritize failed test cases
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

    int decidedCount = 0;
    ArrayNode inconclusiveTcs = mapper.createArrayNode();

    // Adaptive parallelism: start at 2, double on success, halve on failure, cap at core count
    int maxParallelism = Runtime.getRuntime().availableProcessors();
    int currentParallelism = Math.min(2, maxParallelism);
    int processedCount = 0;
    List<String> graderFailures = Collections.synchronizedList(new ArrayList<>());
    // Seed with existing failures from prior batches so cross-batch threshold works.
    // Only active within the early-detection window to avoid aborting legitimate long runs.
    int priorFailures = 0;
    if (batchNum <= EARLY_FAIL_WINDOW)
    {
      for (JsonNode wt : sortedWorktrees)
      {
        String tcId = wt.path("tc_id").asString();
        priorFailures += sprtStateData.path(tcId).path("fails").asInt(0);
      }
    }
    AtomicInteger cumulativeFailures = new AtomicInteger(priorFailures);

    // Step 2: Process test cases with full pipeline (run → grade → update SPRT)
    while (processedCount < sortedWorktrees.size())
    {
      if (batchNum <= EARLY_FAIL_WINDOW && cumulativeFailures.get() >= EARLY_FAIL_THRESHOLD)
        break;
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
          issueName + "-isolation",
          testDirRel,
          tcId,
          runnerWorktree,
          outputDir,
          String.valueOf(trialNum)
        };
        String prepareResult = prepareTrial(prepareArgs);
        String promptFile = null;
        String outputJson = null;
        boolean hasFixture = false;
        for (String line : prepareResult.split("\n"))
        {
          if (line.startsWith("prompt_file="))
            promptFile = line.substring("prompt_file=".length());
          else if (line.startsWith("output_json="))
            outputJson = line.substring("output_json=".length());
          else if (line.startsWith("runner_fixture="))
            hasFixture = true;
        }

        if (outputJson == null || (!hasFixture && promptFile == null))
          throw new IOException("prepare-trial did not return all required fields for " + tcId);

        // Launch full pipeline (run → grade → update SPRT) in one thread
        String finalPromptFile = promptFile;
        String finalOutputJson = outputJson;
        boolean finalHasFixture = hasFixture;
        Thread pipelineThread = Thread.ofVirtual().start(() ->
        {
          boolean failed = false;
          try
          {
            if (!finalHasFixture)
            {
              // Step 1: Run trial (config at worktree/.cat/config/)
              String[] runnerArgs = {
                runnerWorktree, finalPromptFile,
                "--model", modelId, "--output", finalOutputJson
              };
              int exitCode;
              try (ClaudeTool runnerScope = new MainClaudeTool();
                PrintStream nullOut = new PrintStream(OutputStream.nullOutputStream(), false, UTF_8))
              {
                exitCode = ClaudeRunner.run(runnerScope, runnerArgs, nullOut);
              }

              if (!Files.exists(Path.of(finalOutputJson)))
              {
                log.warn("TC{}: runner failed (exit={})", tcNum, exitCode);
                failed = true;
                cumulativeFailures.incrementAndGet();
                // Count runner failures as FAIL in SPRT so consistent failures trigger rejection
                synchronized (sprtLock)
                {
                  String[] updateArgs = {sprtStateJson, tcId, "false"};
                  updateSprt(updateArgs);
                  String[] boundaryArgs = {sprtStateJson, tcId};
                  String boundaryResult = checkBoundary(boundaryArgs);
                  JsonNode boundaryNode = mapper.readTree(boundaryResult);
                  String decision = boundaryNode.path("decision").asString();
                  log.info("TC{}: {} (trial={}, runner-failure)", tcNum, decision, trialNum);
                  if (decision.equals("ACCEPT") || decision.equals("REJECT"))
                    batchDecidedCount.incrementAndGet();
                  else
                    inconclusiveTcs.add(tcId);
                }
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
            }

            // Step 3: Grade
            String gradeFilePath = Path.of(outputDir, tcId + "_run" + trialNum + "_grade.json").
              toString();
            String verdict;
            boolean graderFailed = false;
            try
            {
              // Use worktreePathStr so the grader reads assertions from the issue worktree,
              // where test files still exist with their ## Assertions sections.
              verdict = sprtGrader.gradeTc(tcId, trialNum, finalOutputJson, modelId, runnerWorktree,
                Path.of(runnerWorktree, "client/target/jlink/bin"),
                Path.of(worktreePathStr, testDirRel).toString(),
                gradeFilePath, isolationResultJson);
            }
            catch (IOException e)
            {
              // Check if this is a grader failure (not infrastructure failure)
              String message = e.getMessage();
              if (message.contains("Grader for") || message.contains("Grader did not write") ||
                message.contains("Grader output missing") || message.contains("Grade file"))
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
            {
              failed = true;
              cumulativeFailures.incrementAndGet();
            }

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

    boolean earlyAbort = batchNum <= EARLY_FAIL_WINDOW &&
      cumulativeFailures.get() >= EARLY_FAIL_THRESHOLD;
    if (earlyAbort)
      log.info("Early failure detection: {} failures reached threshold, batch interrupted",
        cumulativeFailures.get());

    // Step 3: Cleanup
    String[] removeArgs = {worktreePathStr, issueName};
    removeRunnerWorktrees(removeArgs);

    ObjectNode result = mapper.createObjectNode();
    result.put("decided_count", decidedCount);
    result.put("early_abort", earlyAbort);
    result.put("cumulative_failures", cumulativeFailures.get());
    result.set("inconclusive_tcs", inconclusiveTcs);
    return compactJson(result);
  }

  /**
   * Implements the {@code run-full-sprt} command.
   * <p>
   * Orchestrates the complete SPRT workflow: prepare run, create isolation branch, initialize SPRT state,
   * run batches until all test cases reach decisions, write test results, and cleanup.
   *
   * @param args {@code [--worktree-bin] <worktree_path> <test_dir> <test_model> <session_id> [<effort>]}
   *             where {@code --worktree-bin} is an optional flag indicating the re-exec has already happened
   * @param out  the output stream for progress messages (goes to stderr in bash)
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if any I/O operation fails
   * @throws InterruptedException     if a batch run is interrupted
   */
  private void runFullSprt(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    requireThat(args, "args").isNotNull();

    // Strip optional --worktree-bin flag (present when re-execed from the worktree binary).
    boolean usingWorktreeBin = args.length > 0 && args[0].equals("--worktree-bin");
    if (usingWorktreeBin)
      args = Arrays.copyOfRange(args, 1, args.length);

    if (args.length < 4 || args.length > 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-full-sprt: expected 4 or 5 arguments " +
        "<worktree_path> <test_dir> <test_model> <session_id> [<effort>], got " + args.length + ".\n" +
        "Usage: instruction-test-runner run-full-sprt <worktree_path> <test_dir> <test_model> " +
        "<session_id> [<effort>]");

    String worktreePath = args[0];
    String testDir = args[1];
    String testModel = args[2];
    String sessionId = args[3];
    String testEffort = "";
    if (args.length > 4)
      testEffort = args[4];

    // Re-exec from the worktree's own jlink binary so the correct (rebuilt) version runs.
    // CLAUDE_PLUGIN_ROOT may point to a stale workspace binary; the worktree binary is always current.
    if (!usingWorktreeBin)
    {
      Path worktreeRunner = Path.of(worktreePath).
        resolve("client/target/jlink/bin/instruction-test-runner");
      if (!Files.isExecutable(worktreeRunner))
        throw new IOException(
          "InstructionTestRunner run-full-sprt: worktree binary not found at " + worktreeRunner +
          ". Run 'mvn -f client/pom.xml package' before starting SPRT.");
      List<String> cmd = new ArrayList<>();
      cmd.add(worktreeRunner.toString());
      cmd.add("run-full-sprt");
      cmd.add("--worktree-bin");
      cmd.addAll(List.of(args));
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.environment().put("CLAUDE_PLUGIN_ROOT",
        Path.of(worktreePath).resolve("plugin").toString());
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
      int exitCode = pb.start().waitFor();
      if (exitCode != 0)
        throw new IOException(
          "InstructionTestRunner run-full-sprt: re-exec from worktree binary failed with " +
          "exit code " + exitCode);
      return;
    }

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
    removeIsolationBranch(new String[]{worktreePath, issueName + "-isolation"});
    removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();

    // Step 3: Create isolation branch
    out.println("Step 3: Creating isolation branch...");
    String isolationResult = createIsolationBranch(new String[]{worktreePath, testDirAbs, issueName});
    JsonNode isolationNode = mapper.readTree(isolationResult);
    String isolationBranch = isolationNode.path("isolation_branch").asString();
    ArrayNode tcIdsArray = (ArrayNode) isolationNode.path("tc_ids_json");
    out.println("  Isolation branch: " + isolationBranch);
    out.println("  Test cases: " + tcIdsArray.size());
    out.println();

    // Read failed_test_ids from previous run BEFORE initializing SPRT state.
    // Prefer sprt-state.json (same-session); fall back to test-results.json (cross-session).
    Set<String> failedTestIds = new HashSet<>();
    Path stateFilePath = Path.of(sprtStatePath);
    JsonNode failedTestIdsSource = null;
    if (Files.exists(stateFilePath))
    {
      JsonNode priorStateRoot = mapper.readTree(stateFilePath.toFile());
      failedTestIdsSource = priorStateRoot.path("failed_test_ids");
    }
    if (failedTestIdsSource == null || !failedTestIdsSource.isArray() || failedTestIdsSource.isEmpty())
    {
      Path testResultsPath = Path.of(testDirAbs).resolve("test-results.json");
      if (Files.exists(testResultsPath))
      {
        JsonNode priorResults = mapper.readTree(testResultsPath.toFile());
        failedTestIdsSource = priorResults.path("failed_test_ids");
      }
    }
    if (failedTestIdsSource != null && failedTestIdsSource.isArray())
    {
      for (JsonNode idNode : failedTestIdsSource)
      {
        if (idNode.isString())
          failedTestIds.add(idNode.asString());
      }
    }

    // Step 4: Initialize SPRT
    out.println("Step 4: Initializing SPRT state...");
    initSprt(new String[]{sprtStatePath, mapper.writeValueAsString(tcIdsArray), "/dev/null", testModel,
      sessionId, "--effort", testEffort});
    out.println("  SPRT state initialized at: " + sprtStatePath);
    out.println();

    // Step 5: SPRT loop
    List<String> tcIds = new ArrayList<>();
    for (JsonNode tcIdNode : tcIdsArray)
      tcIds.add(tcIdNode.asString());

    // Sort test cases: failed tests first, then others
    if (!failedTestIds.isEmpty())
    {
      tcIds.sort((a, b) ->
      {
        boolean aFailed = failedTestIds.contains(a);
        boolean bFailed = failedTestIds.contains(b);
        if (aFailed && !bFailed)
          return -1;
        if (!aFailed && bFailed)
          return 1;
        return 0;
      });
      out.println("=== Test Prioritization ===");
      out.println("Prioritizing " + failedTestIds.size() + " previously-failed test(s)");
      out.println();
    }

    out.println("=== Starting SPRT Loop ===");
    out.println("Test cases: " + tcIds.size());
    out.println();

    int batchNum = 0;
    int trialsPerBatch = 1;
    List<String> undecided = new ArrayList<>(tcIds);
    Map<String, Integer> runCounts = new HashMap<>();
    Map<String, String> decisions = new HashMap<>();
    for (String tcId : tcIds)
      runCounts.put(tcId, 0);
    long loopStartMs = System.currentTimeMillis();
    List<Long> batchDurationsMs = new ArrayList<>();

    while (!undecided.isEmpty())
    {
      ++batchNum;
      out.printf("=== Batch %d (%d trial(s) per TC): %d test case(s) remaining ===%n",
        batchNum, trialsPerBatch, undecided.size());

      // Read cumulative fails before this batch group for adaptive sizing
      String preStateJson = Files.readString(Path.of(sprtStatePath));
      int failsBefore = 0;
      JsonNode preSprtNode = mapper.readTree(preStateJson).path("sprt_state");
      for (String tcId : undecided)
        failsBefore += preSprtNode.path(tcId).path("fails").asInt(0);

      boolean batchEarlyAbort = false;
      JsonNode batchResultNode = null;
      boolean anyReject = false;

      for (int trial = 0; trial < trialsPerBatch; ++trial)
      {
        // Run one trial for all currently undecided TCs
        long batchStartMs = System.currentTimeMillis();
        String batchResult = runSprtBatch(new String[]{
          worktreePath, sprtStatePath, issueName, testDirRel,
          sessionId, testModel, String.valueOf(batchNum), isolationResult
        });
        batchDurationsMs.add(System.currentTimeMillis() - batchStartMs);
        batchResultNode = mapper.readTree(batchResult);
        batchEarlyAbort = batchResultNode.path("early_abort").asBoolean(false);

        // Check decisions after this trial
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

        if (batchEarlyAbort || anyReject || undecided.isEmpty())
          break;
      }
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

      // ETA line (only when undecided TCs remain)
      if (!undecided.isEmpty())
      {
        long avgBatchMs = batchDurationsMs.stream().mapToLong(Long::longValue).sum() /
          batchDurationsMs.size();
        int maxRunsToAccept = 0;
        for (String tcId : undecided)
        {
          double logRatio = sprtNode.path(tcId).path("log_ratio").asDouble(0.0);
          int runsToAccept = (int) Math.ceil((2.944 - logRatio) / 0.1112);
          if (runsToAccept > maxRunsToAccept)
            maxRunsToAccept = runsToAccept;
        }
        long elapsedMs = System.currentTimeMillis() - loopStartMs;
        long etaMs = maxRunsToAccept * avgBatchMs;
        out.printf("Elapsed: %s | Avg batch: %s | ETA to ACCEPT: ~%s (%d batch(es) @ %s each)%n",
          formatDuration(elapsedMs), formatDuration(avgBatchMs),
          formatDuration(etaMs), maxRunsToAccept, formatDuration(avgBatchMs));
        out.println();
      }

      // Update adaptive trials-per-batch: double on all-pass, reset to 1 on any failure
      {
        String postStateJson = Files.readString(Path.of(sprtStatePath));
        JsonNode postSprtNode = mapper.readTree(postStateJson).path("sprt_state");
        int failsAfter = 0;
        for (String tcId : tcIds)
          failsAfter += postSprtNode.path(tcId).path("fails").asInt(0);
        if (!batchEarlyAbort && !anyReject && failsAfter == failsBefore)
          trialsPerBatch = Math.min(trialsPerBatch * 2, 4);
        else
          trialsPerBatch = 1;
      }

      // Early failure detection: batch was interrupted mid-execution due to threshold
      if (batchEarlyAbort)
      {
        int totalFailures = batchResultNode.path("cumulative_failures").asInt(0);
        List<String> failedTcIds = new ArrayList<>();
        for (String tcId : tcIds)
        {
          if (sprtNode.path(tcId).path("fails").asInt(0) > 0)
            failedTcIds.add(tcId);
        }

        out.println("=== Early Failure Detection (Batch " + batchNum + ") ===");
        out.println("Detected " + totalFailures + " total failures across " +
          failedTcIds.size() + " test case(s). Batch interrupted mid-execution.");
        out.println("Stopping early to provide fast feedback.");
        out.println();

        // Update failed_test_ids in state file
        String freshStateJson = Files.readString(Path.of(sprtStatePath));
        ObjectNode mutableStateRoot = (ObjectNode) mapper.readTree(freshStateJson);
        ArrayNode failedIdsArray = mapper.createArrayNode();
        for (String tcId : failedTcIds)
          failedIdsArray.add(tcId);
        mutableStateRoot.set("failed_test_ids", failedIdsArray);
        Files.writeString(Path.of(sprtStatePath), mapper.writeValueAsString(mutableStateRoot), UTF_8);

        // Mark remaining undecided as INCONCLUSIVE
        for (String tcId : undecided)
        {
          decisions.put(tcId, "INCONCLUSIVE");
          out.println("  " + tcId + ": INCONCLUSIVE (early stop after " +
            runCounts.get(tcId) + " runs)");
        }
        out.println();
        break;
      }

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
    removeIsolationBranch(new String[]{worktreePath, isolationBranch});
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
   *             where {@code test_pattern} is a test name or glob pattern (e.g., "invoke_with_prompt_file"
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
    String sessionId = args[4];

    JsonMapper mapper = scope.getJsonMapper();
    SingleTestContext context = prepareSingleTestRun(worktreePath, testDir, testPattern, testModel,
      sessionId, "", mapper, out);

    Map<String, Integer> runCounts = runSprtLoop(worktreePath, sessionId, testModel,
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
    if (args.length != 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner run-single-test: expected 5 arguments " +
        "<worktree_path> <test_dir> <test_pattern> <test_model> <session_id>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner run-single-test <worktree_path> <test_dir> <test_pattern> " +
        "<test_model> <session_id>");
  }

  /**
   * Prepares a single test run by cleaning up prior runs, creating an isolation branch,
   * filtering test cases by pattern, and initializing SPRT state.
   *
   * @param worktreePath the worktree path
   * @param testDir      the test directory
   * @param testPattern  the test pattern (exact name or glob)
   * @param testModel    the test model
   * @param sessionId    the Claude session ID for SPRT state initialization
   * @param testEffort   the effort level for SPRT state initialization, or empty string if none
   * @param mapper       the JSON mapper
   * @param out          the output stream
   * @return context containing issue name, SPRT state path, isolation result, and filtered test case IDs
   * @throws IOException if an I/O error occurs
   */
  private SingleTestContext prepareSingleTestRun(String worktreePath, String testDir,
    String testPattern, String testModel, String sessionId, String testEffort, JsonMapper mapper,
    PrintStream out)
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
    removeIsolationBranch(new String[]{worktreePath, issueName + "-isolation"});
    removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();

    // Step 3: Create isolation branch
    out.println("Step 3: Creating isolation branch...");
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
      testModel, sessionId, "--effort", testEffort});
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
   * @param sessionId the Claude session ID
   * @param testModel the test model
   * @param context the test context
   * @param mapper the JSON mapper
   * @param out the output stream
   * @return a map of test case IDs to run counts
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if waiting is interrupted
   */
  private Map<String, Integer> runSprtLoop(String worktreePath,
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

      String batchResult = runSprtBatch(new String[]{
        worktreePath, context.sprtStatePath(), context.issueName(), context.issueName(),
        sessionId, testModel, String.valueOf(batchNum), context.isolationResult()
      });
      JsonNode batchResultNode = mapper.readTree(batchResult);
      boolean batchEarlyAbort = batchResultNode.path("early_abort").asBoolean(false);

      boolean anyReject = processBatchResults(undecided, runCounts, context, mapper, out);

      printBatchSummary(batchNum, context, runCounts, mapper, out);

      if (batchEarlyAbort)
      {
        int totalFailures = batchResultNode.path("cumulative_failures").asInt(0);
        out.println("=== Early Failure Detection (Batch " + batchNum + ") ===");
        out.println("Detected " + totalFailures + " total failures. Batch interrupted mid-execution.");
        out.println("Stopping early to provide fast feedback.");
        out.println();
        handleEarlyAbort(undecided, runCounts, context, out);
        break;
      }

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
    removeIsolationBranch(new String[]{worktreePath, context.isolationBranch()});
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
   * Formats a duration in milliseconds as a human-readable string.
   *
   * @param ms the duration in milliseconds
   * @return a formatted string like "3s", "2m 15s", or "1h 30m"
   */
  private static String formatDuration(long ms)
  {
    long seconds = ms / 1_000;
    if (seconds < 60)
      return seconds + "s";
    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (minutes < 60)
      return minutes + "m " + remainingSeconds + "s";
    long hours = minutes / 60;
    long remainingMinutes = minutes % 60;
    return hours + "h " + remainingMinutes + "m";
  }

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
    // Persist model_id, effort, and failed_test_ids so subsequent runs can validate model consistency
    // and prioritize previously-failed test cases across sessions.
    String modelId = stateRoot.path("model_id").asString("");
    if (modelId.isBlank())
      throw new IllegalStateException(
        "InstructionTestRunner write-test-results: sprt state is missing required field model_id");
    output.put("model_id", modelId);
    output.put("effort", stateRoot.path("effort").asString(""));
    JsonNode failedIdsNode = stateRoot.path("failed_test_ids");
    if (!failedIdsNode.isArray())
      throw new IllegalStateException(
        "InstructionTestRunner write-test-results: sprt state is missing required field failed_test_ids");
    output.set("failed_test_ids", failedIdsNode);
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
   * @param modelId          the model ID to use for grading
   * @param runnerWorktree   the runner worktree path (contains .cat/config/)
   * @param jlinkBin         the jlink binary directory path
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
