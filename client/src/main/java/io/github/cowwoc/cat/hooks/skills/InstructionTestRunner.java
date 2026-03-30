/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import static io.github.cowwoc.cat.hooks.Strings.block;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Incremental instruction-test driver for instruction-builder-agent.
 * <p>
 * Dispatches 10 subcommands: extract-units, detect-changes, map-units, extract-model,
 * persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results.
 * <p>
 * All output is written to stdout as JSON. Expected errors are reported as a block response
 * on stdout with exit code 0. Unexpected errors are logged
 * to stderr and also reported as a block response on stdout with exit code 0.
 */
public final class SkillTestRunner
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
   * Pattern matching unified diff hunk headers: {@code @@ -old +new,count @@}.
   */
  private static final Pattern HUNK_PATTERN =
    Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");
  /**
   * ISO-8601 UTC timestamp formatter.
   */
  private static final DateTimeFormatter ISO_UTC =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private final Logger log = LoggerFactory.getLogger(SkillTestRunner.class);
  private final JvmScope scope;

  /**
   * Creates a new SkillTestRunner.
   *
   * @param scope the JVM scope providing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public SkillTestRunner(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
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
        "SkillTestRunner: no command specified.\n" +
        "Usage: skill-test-runner <command> [args...]\n" +
        "Commands: extract-units, extract-model, detect-changes, map-units, " +
        "persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results");

    String command = args[0];
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    switch (command)
    {
      case "extract-units" -> out.println(extractUnits(rest));
      case "extract-model" -> out.println(extractModel(rest));
      case "detect-changes" -> out.println(detectChanges(rest));
      case "map-units" -> out.println(mapUnits(rest));
      case "persist-artifacts" -> persistArtifacts(rest, out);
      case "init-sprt" -> out.println(initSprt(rest));
      case "update-sprt" -> out.println(updateSprt(rest));
      case "check-boundary" -> out.println(checkBoundary(rest));
      case "smoke-status" -> out.println(smokeStatus(rest));
      case "merge-results" -> out.println(mergeResults(rest));
      default -> throw new IllegalArgumentException(
        "SkillTestRunner: unknown command: " + command + "\n" +
        "Valid commands: extract-units, extract-model, detect-changes, map-units, " +
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
        "SkillTestRunner extract-units: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-units <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SkillTestRunner extract-units: file not found: " + skillPath);
    return bodyWithLineNumbers(skillPath);
  }

  /**
   * Implements the {@code extract-model} command.
   * <p>
   * Reads the YAML frontmatter of the skill and returns the value of the {@code model:} field,
   * falling back to {@code "haiku"} when the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the model name
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String extractModel(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "SkillTestRunner extract-model: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-model <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SkillTestRunner extract-model: file not found: " + skillPath);

    ParsedSkill parsed = parseSkill(skillPath);
    String model = SkillDiscovery.extractField(parsed.frontmatter(), "model");
    if (model.isBlank())
    {
      log.warn("SkillTestRunner extract-model: no 'model:' field in frontmatter of {}; falling back to 'haiku'",
        skillPath);
      return "haiku";
    }
    return model;
  }

  /**
   * Implements the {@code detect-changes} command.
   * <p>
   * Compares the old skill (at the given git SHA) against the current skill file and identifies
   * which test cases need re-running based on changed line ranges.
   *
   * @param args {@code [old_skill_sha, new_skill_path, test_cases_path]}
   * @return a JSON object describing changed ranges and test case partitioning
   * @throws IllegalArgumentException if arguments are missing or files are not found
   * @throws IOException              if files cannot be read or git commands fail
   */
  public String detectChanges(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "SkillTestRunner detect-changes: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner detect-changes <old_skill_sha> <new_skill_path> <test_cases_path>");

    String oldSha = args[0];
    Path newSkillPath = Path.of(args[1]);
    Path testCasesPath = Path.of(args[2]);

    if (!oldSha.matches("[0-9a-f]{7,40}"))
      throw new IllegalArgumentException(
        "SkillTestRunner detect-changes: invalid git SHA format: " + oldSha);

    if (Files.notExists(newSkillPath))
      throw new IllegalArgumentException(
        "SkillTestRunner detect-changes: new skill file not found: " + newSkillPath);
    if (Files.notExists(testCasesPath))
      throw new IllegalArgumentException(
        "SkillTestRunner detect-changes: test cases file not found: " + testCasesPath);

    // Determine git repo root from the skill file's directory
    ProcessRunner.Result rootResult =
      ProcessRunner.run(newSkillPath.toAbsolutePath().getParent(),
        "git", "rev-parse", "--show-toplevel");
    if (rootResult.exitCode() != 0 || rootResult.stdout().isBlank())
      throw new IOException(
        "SkillTestRunner detect-changes: cannot determine git repo root from: " + newSkillPath);
    Path repoRoot = Path.of(rootResult.stdout().strip());

    // Derive repo-relative path for git show (normalize path separators for git)
    String relPath = repoRoot.relativize(newSkillPath.toAbsolutePath()).toString().
      replace(java.io.File.separatorChar, '/');

    // Retrieve old skill content via git show
    ProcessRunner.Result showResult =
      ProcessRunner.run(repoRoot, "git", "show", oldSha + ":" + relPath);
    if (showResult.exitCode() != 0)
      throw new IOException(
        "SkillTestRunner detect-changes: git show " + oldSha + ":" + relPath + " failed.\n" +
        "Verify that the SHA '" + oldSha + "' exists and the path '" + relPath + "' was tracked at that commit.");

    // Write old content to temp file, parse both
    Path oldTempFile = Files.createTempFile("instruction-test-old-", ".md");
    Path newTempFile = Files.createTempFile("instruction-test-new-", ".md");
    try
    {
      Files.writeString(oldTempFile, showResult.stdout(), StandardCharsets.UTF_8);
      Files.copy(newSkillPath, newTempFile, StandardCopyOption.REPLACE_EXISTING);

      ParsedSkill oldSkill = parseSkill(oldTempFile);
      ParsedSkill newSkill = parseSkill(newTempFile);

      boolean frontmatterChanged =
        !sha256String(oldSkill.frontmatter()).equals(sha256String(newSkill.frontmatter()));

      // Write body lines to temp files for diff
      Path oldBodyFile = Files.createTempFile("instruction-test-old-body-", ".txt");
      Path newBodyFile = Files.createTempFile("instruction-test-new-body-", ".txt");
      try
      {
        Files.write(oldBodyFile, oldSkill.bodyLines(), StandardCharsets.UTF_8);
        Files.write(newBodyFile, newSkill.bodyLines(), StandardCharsets.UTF_8);

        List<int[]> changedRanges = diffBodies(oldBodyFile, newBodyFile);
        boolean bodyChanged = !changedRanges.isEmpty();
        boolean skillChanged = frontmatterChanged || bodyChanged;

        // Collect all test case IDs
        List<String> allTestCaseIds = readAllTestCaseIds(testCasesPath);

        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("skill_changed", skillChanged);
        result.put("frontmatter_changed", frontmatterChanged);
        result.put("body_changed", bodyChanged);

        ArrayNode rangesArray = mapper.createArrayNode();
        for (int[] range : changedRanges)
        {
          ObjectNode rangeNode = mapper.createObjectNode();
          rangeNode.put("start", range[0]);
          rangeNode.put("end", range[1]);
          rangesArray.add(rangeNode);
        }
        result.set("changed_ranges", rangesArray);

        ArrayNode allIdsArray = mapper.createArrayNode();
        for (String id : allTestCaseIds)
          allIdsArray.add(id);
        result.set("all_test_case_ids", allIdsArray);

        if (!skillChanged)
        {
          // No changes: all test cases carry forward
          result.set("rerun_test_case_ids", mapper.createArrayNode());
          result.set("carryforward_test_case_ids", allIdsArray.deepCopy());
          result.put("semantic_units_path_hint",
            "Run: skill-test-runner extract-units " + args[1]);
        }
        else if (frontmatterChanged)
        {
          // Frontmatter changed: all test cases must re-run
          result.set("rerun_test_case_ids", allIdsArray.deepCopy());
          result.set("carryforward_test_case_ids", mapper.createArrayNode());
          result.put("semantic_units_path_hint",
            "Run: skill-test-runner extract-units " + args[1]);
        }
        else
        {
          // Body changed only: agent must apply semantic unit location filtering
          result.set("rerun_test_case_ids", mapper.createArrayNode());
          result.set("carryforward_test_case_ids", mapper.createArrayNode());
          result.put("requires_unit_mapping", true);
          result.put("semantic_units_path_hint",
            "Run: skill-test-runner extract-units " + args[1] +
            " to get line-numbered body, then apply semantic unit extraction, then run: " +
            "skill-test-runner map-units " + args[2] + " <changed_unit_ids_json>");
        }

        // Produce compact single-line JSON to match Bash output style
        return compactJson(result);
      }
      finally
      {
        Files.deleteIfExists(oldBodyFile);
        Files.deleteIfExists(newBodyFile);
      }
    }
    finally
    {
      Files.deleteIfExists(oldTempFile);
      Files.deleteIfExists(newTempFile);
    }
  }

  /**
   * Implements the {@code map-units} command.
   * <p>
   * Given test-cases.json and a JSON array of changed semantic unit IDs, determines which test
   * cases must re-run and which carry forward.
   *
   * @param args {@code [test_cases_path, changed_units_json]}
   * @return a JSON object with rerun and carryforward test case ID lists
   * @throws IllegalArgumentException if arguments are missing or the file is not found
   * @throws IOException              if the file cannot be read
   */
  public String mapUnits(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SkillTestRunner map-units: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner map-units <test_cases_path> <changed_units_json>");

    Path testCasesPath = Path.of(args[0]);
    String changedUnitsJson = args[1];

    if (Files.notExists(testCasesPath))
      throw new IllegalArgumentException(
        "SkillTestRunner map-units: test cases file not found: " + testCasesPath);

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

    // Read test cases file and partition by whether semantic_unit_id is in changed set
    JsonNode root = mapper.readTree(testCasesPath.toFile());
    JsonNode testCasesArray = root.path("test_cases");

    List<String> allIds = new ArrayList<>();
    List<String> rerunIds = new ArrayList<>();
    List<String> carryforwardIds = new ArrayList<>();

    if (testCasesArray.isArray())
    {
      for (JsonNode tc : testCasesArray)
      {
        String testCaseId = tc.path("test_case_id").asString("");
        if (testCaseId.isBlank())
          continue;
        allIds.add(testCaseId);
        String semanticUnitId = tc.path("semantic_unit_id").asString("");
        if (changedUnits.isEmpty() || !changedUnits.contains(semanticUnitId))
          carryforwardIds.add(testCaseId);
        else
          rerunIds.add(testCaseId);
      }
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
   * test-cases.json into the skill's instruction-test directory, and commits via git.
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
        "SkillTestRunner persist-artifacts: expected 5 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner persist-artifacts <skill_path> <artifacts_dir> " +
        "<session_id> <worktree_root> <phase>");

    String skillPathArg = args[0];
    Path artifactsDir = Path.of(args[1]);
    String sessionId = args[2];
    Path worktreeRoot = Path.of(args[3]);
    String phase = args[4];

    if (Files.notExists(worktreeRoot))
      throw new IllegalArgumentException(
        "SkillTestRunner persist-artifacts: worktree root not found: " + worktreeRoot);
    if (Files.notExists(artifactsDir))
      throw new IllegalArgumentException(
        "SkillTestRunner persist-artifacts: artifacts directory not found: " + artifactsDir);

    Path absSkillPath = worktreeRoot.resolve(skillPathArg).normalize();
    validatePathWithinBoundary(worktreeRoot, absSkillPath);
    if (Files.notExists(absSkillPath))
      throw new IllegalArgumentException(
        "SkillTestRunner persist-artifacts: skill file not found: " + absSkillPath);

    Path testCasesSrc = artifactsDir.resolve("test-cases.json");
    if (Files.notExists(testCasesSrc))
      throw new IllegalArgumentException(
        "SkillTestRunner persist-artifacts: test-cases.json not found: " + testCasesSrc);

    // Compute skill directory and instruction-test subdirectory
    Path skillDir = absSkillPath.getParent();
    Path instructionTestDir = skillDir.resolve("instruction-test");
    Files.createDirectories(instructionTestDir);

    // Copy test-cases.json
    Path testCasesDest = instructionTestDir.resolve("test-cases.json");
    validatePathWithinBoundary(skillDir, testCasesDest);
    Files.copy(testCasesSrc, testCasesDest, StandardCopyOption.REPLACE_EXISTING);

    // Compute hashes
    String skillHash = sha256File(absSkillPath);
    String testCasesHash = sha256File(testCasesDest);

    // Compute worktree-relative paths
    Path skillParent = Path.of(skillPathArg).getParent();
    Path skillParentOrDot;
    if (skillParent != null)
      skillParentOrDot = skillParent;
    else
      skillParentOrDot = Path.of(".");
    String relTestCasesPath = skillParentOrDot.resolve("instruction-test").resolve("test-cases.json").toString();
    String relInstructionTestJson = skillParentOrDot.resolve("instruction-test").
      resolve("instruction-test.json").toString();

    String timestamp = ISO_UTC.format(Instant.now());

    // Write instruction-test.json using Jackson to ensure proper escaping and formatting
    Path instructionTestJsonPath = instructionTestDir.resolve("instruction-test.json");
    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("session_id", sessionId);
    root.put("phase", phase);
    root.put("timestamp", timestamp);
    ObjectNode skillNode = root.putObject("skill");
    skillNode.put("path", skillPathArg);
    skillNode.put("sha256", skillHash);
    ObjectNode testCasesNode = root.putObject("test_cases");
    testCasesNode.put("path", relTestCasesPath);
    testCasesNode.put("sha256", testCasesHash);
    String instructionTestContent = mapper.writeValueAsString(root);
    Files.writeString(instructionTestJsonPath, instructionTestContent, StandardCharsets.UTF_8);

    // Stage files
    ProcessRunner.Result addTestCases = ProcessRunner.run(worktreeRoot, "git", "add", relTestCasesPath);
    if (addTestCases.exitCode() != 0)
      throw new IOException("git add failed for " + relTestCasesPath +
        ": exit code " + addTestCases.exitCode() + ", output: " + addTestCases.stdout());
    ProcessRunner.Result addInstructionTest = ProcessRunner.run(worktreeRoot, "git", "add", relInstructionTestJson);
    if (addInstructionTest.exitCode() != 0)
      throw new IOException("git add failed for " + relInstructionTestJson +
        ": exit code " + addInstructionTest.exitCode() + ", output: " + addInstructionTest.stdout());

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
        log.warn("SkillTestRunner: git commit failed (attempt {}/{}), retrying in {}s...",
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
        "SkillTestRunner persist-artifacts: git commit failed after " + maxRetries + " attempts");

    out.println(
      "skill-test-runner: artifacts committed for phase=" + phase + ", session=" + sessionId);
  }

  /**
   * Implements the {@code init-sprt} command.
   * <p>
   * Initialises per-test-case SPRT state: fresh state for re-run cases, and carry-forward state
   * from the prior instruction-test for unchanged cases.
   *
   * @param args {@code [rerun_tc_ids_json, prior_instruction_test_json_path, (--prior-boost)?]}
   * @return a JSON object containing the {@code sprt_state} map
   * @throws IllegalArgumentException if arguments are missing or the prior file is not found
   * @throws IOException              if the prior file cannot be read
   */
  public String initSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 2)
      throw new IllegalArgumentException(
        "SkillTestRunner init-sprt: expected at least 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner init-sprt <rerun_tc_ids_json> <prior_instruction_test_json_path> [--prior-boost]");

    String rerunJson = args[0];
    String priorPath = args[1];
    boolean usePriorBoost = false;
    for (int i = 2; i < args.length; ++i)
    {
      if (args[i].equals("--prior-boost"))
        usePriorBoost = true;
    }

    boolean hasPrior = !priorPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorPath)))
      throw new IllegalArgumentException(
        "SkillTestRunner init-sprt: prior instruction-test file not found: " + priorPath);

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
    Map<String, JsonNode> priorByTestCaseId = new HashMap<>();
    if (hasPrior)
    {
      JsonNode priorRoot = mapper.readTree(Path.of(priorPath).toFile());
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
        "SkillTestRunner update-sprt: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner update-sprt <sprt_state_path> <tc_id> <passed>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];
    String passedStr = args[2];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "SkillTestRunner update-sprt: state file not found: " + statePath);
    if (!passedStr.equals("true") && !passedStr.equals("false"))
      throw new IllegalArgumentException(
        "SkillTestRunner update-sprt: <passed> must be 'true' or 'false', got: " + passedStr);

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
        "SkillTestRunner check-boundary: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner check-boundary <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "SkillTestRunner check-boundary: state file not found: " + statePath);

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
        "SkillTestRunner smoke-status: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner smoke-status <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "SkillTestRunner smoke-status: state file not found: " + statePath);

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
   * summary ready for committing.
   *
   * @param args {@code [new_sprt_state_path, prior_instruction_test_json_path, carryforward_ids_json]}
   * @return a JSON object with overall_decision, timestamp, incremental flag, and test_cases
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if files cannot be read
   */
  public String mergeResults(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "SkillTestRunner merge-results: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner merge-results <new_sprt_state_path> " +
        "<prior_instruction_test_json_path> <carryforward_ids_json>");

    Path statePath = Path.of(args[0]);
    String priorInstructionTestPath = args[1];
    String carryforwardIdsJson = args[2];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "SkillTestRunner merge-results: state file not found: " + statePath);

    boolean hasPrior = !priorInstructionTestPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorInstructionTestPath)))
      throw new IllegalArgumentException(
        "SkillTestRunner merge-results: prior instruction-test file not found: " + priorInstructionTestPath);

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
  private String sha256Bytes(byte[] bytes)
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
   * Computes the SHA-256 hex digest of a string.
   *
   * @param text the text to hash
   * @return lowercase hex SHA-256 digest
   */
  private String sha256String(String text)
  {
    return sha256Bytes(text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Runs a unified diff between two body files and returns the changed line ranges in the new file.
   *
   * @param oldBodyFile path to the old body file
   * @param newBodyFile path to the new body file
   * @return list of {@code [start, end]} pairs (1-based, inclusive)
   * @throws IOException if the diff command fails
   */
  private List<int[]> diffBodies(Path oldBodyFile, Path newBodyFile) throws IOException
  {
    ProcessRunner.Result diffResult =
      ProcessRunner.run("diff", "-u", oldBodyFile.toString(), newBodyFile.toString());
    // diff exits 1 when files differ — that's expected
    if (diffResult.exitCode() > 1)
      throw new IOException("diff command failed unexpectedly");

    String diffOutput = diffResult.stdout();
    if (diffOutput.isBlank())
      return List.of();

    List<int[]> ranges = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(diffOutput)))
    {
      String line = reader.readLine();
      while (line != null)
      {
        Matcher matcher = HUNK_PATTERN.matcher(line);
        if (!matcher.find())
        {
          line = reader.readLine();
          continue;
        }
        int newStart = Integer.parseInt(matcher.group(1));
        int count;
        if (matcher.group(2) != null)
          count = Integer.parseInt(matcher.group(2));
        else
          count = 1;
        int newEnd;
        if (count == 0)
          newEnd = newStart;
        else
          newEnd = newStart + count - 1;
        ranges.add(new int[]{newStart, newEnd});
        line = reader.readLine();
      }
    }
    return ranges;
  }

  /**
   * Reads all test case IDs from a test-cases.json file.
   *
   * @param testCasesPath path to test-cases.json
   * @return list of test case ID strings in document order
   * @throws IOException if the file cannot be read
   */
  private List<String> readAllTestCaseIds(Path testCasesPath) throws IOException
  {
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(testCasesPath.toFile());
    JsonNode testCasesArray = root.path("test_cases");
    List<String> ids = new ArrayList<>();
    if (testCasesArray.isArray())
    {
      for (JsonNode tc : testCasesArray)
      {
        String id = tc.path("test_case_id").asString("");
        if (!id.isBlank())
          ids.add(id);
      }
    }
    return ids;
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
        "SkillTestRunner: path traversal detected: '" + candidate + "' is outside '" + boundary + "'");
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
        Logger log = LoggerFactory.getLogger(SkillTestRunner.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the skill test runner logic with a caller-provided output stream.
   *
   * @param scope the JVM scope
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   */
  public static void run(JvmScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    new SkillTestRunner(scope).run(args, out);
  }
}
