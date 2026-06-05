/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.FrontmatterUtils;
import io.github.cowwoc.cat.agent.ProcessRunner;
import io.github.cowwoc.cat.tool.CliTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Incremental instruction-test driver for instruction-builder.
 * <p>
 * Dispatches subcommands: extract-units, extract-model, extract-test-dir, detect-changes,
 * map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results,
 * create-runner-worktrees, check-run-contamination, remove-runner-worktrees, remove-runner-worktree,
 * prepare-trial, get-json-field, run-sprt.
 * <p>
 * {@code prepare-trial} outputs {@code key=value} lines (all scalar paths); the combined prompt
 * is written to a file so {@code claude-runner} receives a path via {@code --prompt}.
 * <p>
 * Scalar-valued commands output {@code key=value} lines (one per line). Commands that return arrays
 * or nested objects output compact JSON. Expected errors are reported as a block response on stdout
 * with exit code 0. Unexpected errors are logged to stderr and also reported as a block response on
 * stdout with exit code 0.
 */
public final class SprtRunner
{
  /**
   * Minimum total failures across all test cases within the early-detection window to trigger early stop.
   */
  private static final int EARLY_FAIL_THRESHOLD = 2;
  /**
   * Maximum number of batches during which early-failure-detection is active.
   */
  private static final int EARLY_FAIL_WINDOW = 5;
  private static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofMinutes(10);
  private static final String GRADER_AGENT = "instruction-grader-agent";
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();
  private static final List<String> CLAUDE_EFFORT_LEVELS =
    List.of("low", "medium", "high", "xhigh", "max");
  private static final List<String> CODEX_EFFORT_LEVELS =
    List.of("low", "medium", "high", "xhigh");
  private static final Duration WAIT_POLL = Duration.ofMillis(50);

  static
  {
    SharedSecrets.setSprtRunnerAccess(new SharedSecrets.SprtRunnerAccess()
    {
      @Override
      public String sha256Bytes(byte[] bytes)
      {
        return SprtRunner.sha256Bytes(bytes);
      }

      @Override
      public String[] parseRunSprtArgs(String[] args)
      {
        RunSprtArguments parsed = SprtRunner.parseRunSprtArgs(args);
        return new String[]{parsed.worktreePath(), parsed.testDir(), parsed.testModel(),
          parsed.testEffort(), parsed.sessionId()};
      }

      @Override
      public String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path jlinkBin)
      {
        validateClaudeModelAndEffort(modelId, effort);
        return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
          jlinkBin, true, null);
      }

      @Override
      public String[] buildClaudeSessionTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path jlinkBin, Path sessionFile)
      {
        validateClaudeModelAndEffort(modelId, effort);
        return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
          jlinkBin, true, sessionFile);
      }

      @Override
      public String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson)
      {
        validateCodexModelAndEffort(modelId, effort);
        return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
          null, false, null);
      }

      @Override
      public String[] buildCodexSessionTrialArgs(Path promptFile, String modelId, String effort,
        String runnerWorktree, String outputJson, Path sessionFile)
      {
        validateCodexModelAndEffort(modelId, effort);
        return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
          null, false, sessionFile);
      }

      @Override
      public int runTrial(SprtRunner runner, List<Path> promptFiles, String modelId, String effort,
        String runnerWorktree, String outputJson, PrintStream logStream) throws IOException
      {
        return runner.runTrial(promptFiles, modelId, effort, runnerWorktree, outputJson,
          logStream);
      }

      @Override
      public int runEngineCommand(SprtRunner runner, String[] args, String runnerWorktree,
        PrintStream out) throws IOException
      {
        return runner.runEngineCommand(args, runnerWorktree, out);
      }

      @Override
      public String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree, Path jlinkBin)
      {
        validateClaudeModelAndEffort(modelId, effort);
        return buildGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree, null,
          jlinkBin, true, true);
      }

      @Override
      public String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
        String runnerWorktree)
      {
        validateCodexModelAndEffort(modelId, effort);
        return buildGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree,
          null, null, false, false);
      }

      @Override
      public String engineIdForDescriptor(Path descriptor)
      {
        return runtimeIdFromDescriptor(descriptor);
      }

      @Override
      public SharedSecrets.ModelEffort resolveGraderModelEffort(Path pluginRoot, Path descriptor,
        String claudeCodeVersion)
        throws IOException
      {
        GraderModelEffort resolved = SprtRunner.resolveGraderModelEffort(
          pluginRoot, descriptor, claudeCodeVersion);
        return new SharedSecrets.ModelEffort(resolved.modelId(), resolved.effort());
      }

      @Override
      public String[] buildTrialArgsForDescriptor(Path descriptor, Path promptFile,
        String modelId, String effort, String runnerWorktree, String outputJson)
      {
        String runtimeId = runtimeIdFromDescriptor(descriptor);
        validateModelAndEffort(runtimeId, modelId, effort);
        boolean hasClaudeFlags = runtimeId.equals("claude");
        Path jlinkBin = null;
        if (hasClaudeFlags)
          jlinkBin = jlinkBin(runnerWorktree, runtimeId);
        return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
          jlinkBin, hasClaudeFlags, null);
      }

      @Override
      public String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile,
        String modelId, String effort, String runnerWorktree)
      {
        String runtimeId = runtimeIdFromDescriptor(descriptor);
        validateModelAndEffort(runtimeId, modelId, effort);
        boolean hasClaudeFlags = runtimeId.equals("claude");
        Path jlinkBin = null;
        if (hasClaudeFlags)
          jlinkBin = jlinkBin(runnerWorktree, runtimeId);
        return buildGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree,
          null, jlinkBin, hasClaudeFlags, hasClaudeFlags);
      }
    });
  }

  private final Logger log = LoggerFactory.getLogger(SprtRunner.class);
  private final Map<String, Set<String>> launcherFlagsByPath = new ConcurrentHashMap<>();
  private final CliTool scope;
  private final String runtimeId;
  private final String claudeCodeVersion;
  private final Duration processTimeout;
  private final SprtStateManager sprtStateManager;
  private final SprtIsolationManager sprtIsolationManager;
  private final SprtGrader sprtGrader;
  private final SprtResultsManager sprtResultsManager;
  private final SprtRunWorkflow sprtRunWorkflow;
  private final SprtCommandSupport sprtCommandSupport;
  private final SkillMetadataExtractor skillMetadataExtractor;

  /**
   * Creates a new SprtRunner.
   *
   * @param scope             the JVM scope providing shared services
   * @param claudeCodeVersion the Claude Code version string (e.g., {@code "2.1.87"})
   * @throws NullPointerException     if {@code scope} or {@code claudeCodeVersion} are null
   * @throws IllegalArgumentException if {@code claudeCodeVersion} is blank
   */
  public SprtRunner(CliTool scope, String claudeCodeVersion)
  {
    this(scope, claudeCodeVersion, DEFAULT_PROCESS_TIMEOUT);
  }

  /**
   * Creates a new SprtRunner.
   *
   * @param scope             the JVM scope providing shared services
   * @param claudeCodeVersion the Claude Code version string (e.g., {@code "2.1.87"})
   * @param processTimeout    the launcher process timeout
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if {@code claudeCodeVersion} is blank or
   *                                  {@code processTimeout} is not positive
   */
  public SprtRunner(CliTool scope, String claudeCodeVersion, Duration processTimeout)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    requireThat(processTimeout, "processTimeout").isNotNull();
    if (!processTimeout.isPositive())
      throw new IllegalArgumentException("processTimeout must be positive");
    this.scope = scope;
    this.runtimeId = runtimeIdFromDescriptor(scope.getPluginDescriptor());
    this.claudeCodeVersion = claudeCodeVersion;
    this.processTimeout = processTimeout;
    this.sprtStateManager = new SprtStateManager(scope);
    this.sprtIsolationManager = new SprtIsolationManager(scope, runtimeId);
    this.sprtGrader = new SprtGrader(scope, this);
    this.sprtResultsManager = new SprtResultsManager(scope);
    this.sprtRunWorkflow = new SprtRunWorkflow(this, scope, log, sprtGrader, sprtResultsManager,
      EARLY_FAIL_THRESHOLD, EARLY_FAIL_WINDOW);
    this.skillMetadataExtractor = new SkillMetadataExtractor(scope, claudeCodeVersion);
    this.sprtCommandSupport = new SprtCommandSupport(scope, runtimeId, claudeCodeVersion,
      skillMetadataExtractor, sprtResultsManager, log);
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
      throw new IllegalArgumentException(noCommandSpecifiedMessage());

    String command = args[0];
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    switch (command)
    {
      case "extract-units" -> out.println(extractUnits(rest));
      case "extract-model" -> out.println(extractModel(rest));
      case "extract-effort" -> out.println(extractEffort(rest));
      case "extract-test-dir" -> out.println(extractTestDir(rest));
      case "extract-config-source" -> out.println(extractConfigSource(rest));
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
      case "run-sprt" -> runSprt(rest, out);
      default -> throw new IllegalArgumentException(
        "SprtRunner: unknown command: " + command + "\n" +
        "Valid commands: extract-units, extract-model, extract-effort, extract-config-source, " +
        "extract-test-dir, detect-changes, " +
        "map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, " +
        "merge-results, create-runner-worktrees, check-run-contamination, remove-runner-worktrees, " +
        "remove-runner-worktree, prepare-trial, get-json-field, run-sprt");
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
   * Falls back to the active engine's default model when the field is absent.
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
   * Implements the {@code extract-config-source} command.
   *
   * @param args {@code [skill_path]}
   * @return {@code owner}, {@code default}, or {@code frontmatter}
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException if the file cannot be read
   */
  public String extractConfigSource(String[] args) throws IOException
  {
    return skillMetadataExtractor.extractConfigSource(args);
  }

  /**
   * Implements the {@code extract-test-dir} command.
   * <p>
   * Computes the test directory path for a given instruction file path. Maps plugin-relative paths by
   * stripping the {@code client/plugin/} prefix, then prefixes with {@code {projectDir}/client/plugin/tests/}.
   * <p>
   * Examples:
   * <ul>
   * <li>{@code client/plugin/skills/common/foo/first-use.md} →
   *   {@code {projectDir}/client/plugin/tests/skills/common/foo/first-use}</li>
   * <li>{@code CLAUDE.md} → {@code {projectDir}/client/plugin/tests/CLAUDE}</li>
   * <li>{@code .claude/rules/common.md} → {@code {projectDir}/client/plugin/tests/.claude/rules/common}</li>
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
    return sprtCommandSupport.detectChanges(args);
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
    return sprtCommandSupport.mapUnits(args);
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
    sprtCommandSupport.persistArtifacts(args, out);
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
    return sprtResultsManager.mergeResults(args);
  }

  /**
   * Implements the {@code create-isolation-branch} command.
   * <p>
   * Creates an orphan branch {@code ${issue_name}-isolation} containing stripped test case files.
   * Frontmatter, the {@code ## Assertions} section, and everything after it are removed from each test case file before
   * committing. The original branch is restored even if an error occurs.
   *
   * @param args {@code [worktree_path, test_dir, issue_name]}
   * @return compact JSON {@code {"isolation_branch":"...","tc_id_map":{"stem":"stem",...},
   *   "tc_name_map":{"stem":"stem",...},"tc_ids_json":["stem",...]}}
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
    return sprtCommandSupport.saveFailedRun(args);
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
    return sprtCommandSupport.prepareRun(args);
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
    return sprtCommandSupport.prepareTrial(args);
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
    return sprtCommandSupport.getJsonField(args);
  }

  /**
   * Implements the {@code get-tc-name} command.
   * <p>
   * Looks up the original filename stem for a test-case ID using the
   * {@code tc_name_map} field of the JSON returned by {@code create-isolation-branch}.
   *
   * @param args {@code [isolation_result_json, tc_id]}
   * @return the original filename stem (e.g., {@code "creates-hello-file"})
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the argument count is wrong, the JSON is invalid, or the ID is not
   *                                  found in the map
   * @throws IOException              if the JSON cannot be parsed
   */
  public String getTcName(String[] args) throws IOException
  {
    return sprtCommandSupport.getTcName(args);
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
    return sprtCommandSupport.getWorktreeField(args);
  }

  /**
   * Implements the {@code run-sprt} command.
   * <p>
   * Orchestrates the complete SPRT workflow: prepare run, create isolation branch, initialize SPRT state,
   * run batches until all test cases reach decisions, write test results, and cleanup.
   *
   * @param args {@code <worktree_path> <test_dir> <test_model> <effort> <session_id>}
   * @param out  the output stream for progress messages (goes to stderr in bash)
   * @throws IllegalArgumentException if the argument count is wrong
   * @throws IOException              if any I/O operation fails
   * @throws InterruptedException     if a batch run is interrupted
   */
  private void runSprt(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    sprtRunWorkflow.runSprt(args, out);
  }

  /**
   * Parsed arguments for the {@code run-sprt} command.
   *
   * @param worktreePath the trial worktree path
   * @param testDir the test-case directory
   * @param testModel the model under test
   * @param testEffort the effort level under test
   * @param sessionId the test-run session id
   */
  record RunSprtArguments(String worktreePath, String testDir, String testModel,
                          String testEffort, String sessionId)
  {
  }

  /**
   * Fixed grader model configuration.
   *
   * @param modelId the model ID to use for grading
   * @param effort  the reasoning effort to use for grading
   */
  private record GraderModelEffort(String modelId, String effort)
  {
  }

  /**
   * Parses arguments for the {@code run-sprt} command.
   *
   * @param args the raw command-line arguments
   * @return the parsed arguments
   */
  static RunSprtArguments parseRunSprtArgs(String[] args)
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 5)
      throw new IllegalArgumentException(
        "SprtRunner run-sprt: expected 5 arguments " +
        "<worktree_path> <test_dir> <test_model> <effort> <session_id>, got " + args.length + ".\n" +
        "Usage: sprt-runner run-sprt <worktree_path> <test_dir> <test_model> " +
        "<effort> <session_id>");
    requireThat(args[0], "worktree_path").isNotBlank();
    requireThat(args[1], "test_dir").isNotBlank();
    requireThat(args[2], "test_model").isNotBlank();
    requireThat(args[3], "effort").isNotBlank();
    String sessionId = SprtCommandSupport.validateSessionIdSegment(args[4]);
    return new RunSprtArguments(args[0], args[1], args[2], args[3], sessionId);
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
    return sprtResultsManager.writeTestResults(args);
  }


  /**
   * Computes the SHA-256 hex digest of the given bytes.
   *
   * @param bytes the bytes to hash
   * @return lowercase hex SHA-256 digest
   */
  static String sha256Bytes(byte[] bytes)
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

  void validateConfiguration(String modelId, String effort)
  {
    validateModelAndEffort(runtimeId, modelId, effort);
  }

  /**
   * Runs a single-turn trial.
   * <p>
   * Equivalent to
   * {@code runTrial(List.of(promptFile), modelId, effort, runnerWorktree, outputJson, logStream)}.
   *
   * @param promptFile the prompt file for the trial turn
   * @param modelId the model id under test
   * @param effort the effort level under test
   * @param runnerWorktree the runner worktree
   * @param outputJson the final output path
   * @param logStream the output sink for launcher logs
   * @return the nested runner exit code
   * @throws IOException if trial execution fails
   */
  int runTrial(Path promptFile, String modelId, String effort, String runnerWorktree,
    String outputJson, PrintStream logStream)
    throws IOException
  {
    return runTrial(List.of(promptFile), modelId, effort, runnerWorktree, outputJson, logStream);
  }

  int runTrial(List<Path> promptFiles, String modelId, String effort, String runnerWorktree,
    String outputJson, PrintStream logStream) throws IOException
  {
    requireThat(promptFiles, "promptFiles").isNotNull().isNotEmpty();
    requireThat(logStream, "logStream").isNotNull();
    Path outputPath = Path.of(outputJson);
    if (promptFiles.size() == 1)
    {
      Files.deleteIfExists(outputPath);
      String[] args = buildTrialArgs(promptFiles.getFirst(), modelId, effort, runnerWorktree,
        outputJson);
      return runEngineCommand(args, runnerWorktree, logStream);
    }
    Path sessionFile = Path.of(runnerWorktree).resolve(".cat/work").resolve(
      outputPath.getFileName().toString().replace(".json", "") + "-session.json");
    Files.deleteIfExists(outputPath);
    Files.deleteIfExists(sessionFile);
    int exitCode = 0;
    try
    {
      for (int i = 0; i < promptFiles.size(); ++i)
      {
        Path promptFile = promptFiles.get(i);
        Files.deleteIfExists(outputPath);
        String outputForTurn = null;
        if (i == promptFiles.size() - 1)
          outputForTurn = outputJson;
        String[] args = buildTrialArgs(promptFile, modelId, effort, runnerWorktree, outputForTurn,
          sessionFile);
        exitCode = runEngineCommand(args, runnerWorktree, logStream);
        if (exitCode != 0)
        {
          Files.deleteIfExists(outputPath);
          return exitCode;
        }
        if (i < promptFiles.size() - 1 && Files.notExists(sessionFile))
        {
          throw new IOException("Multi-turn runner did not persist session state: " + sessionFile);
        }
      }
      return exitCode;
    }
    finally
    {
      Files.deleteIfExists(sessionFile);
    }
  }

  int runGrader(Path graderPromptFile, String modelId, String effort, String runnerWorktree,
    String gradeOutputPath, PrintStream out)
    throws IOException
  {
    requireThat(out, "out").isNotNull();
    GraderModelEffort graderConfig = resolveGraderModelEffort(runnerWorktree);
    String[] args = buildGraderArgs(graderPromptFile, graderConfig.modelId(),
      graderConfig.effort(), runnerWorktree);
    return runEngineCommand(args, runnerWorktree, out);
  }

  /**
   * Runs a nested engine launcher and streams its output to the supplied print stream.
   *
   * @param args the launcher arguments
   * @param runnerWorktree the runner worktree
   * @param out the destination for launcher output
   * @return the nested process exit code
   * @throws IOException if process startup, output draining, or timeout cleanup fails
   */
  private int runEngineCommand(String[] args, String runnerWorktree, PrintStream out) throws IOException
  {
    Path launcher = launcherPath(runnerWorktree);
    AtomicReference<Exception> readerFailure = new AtomicReference<>();
    Thread stdoutReader = null;
    try (Process process = startEngineProcess(args, launcher))
    {
      stdoutReader = startStdoutReader(process, out, readerFailure);
      long deadlineNanos = System.nanoTime() + processTimeout.toNanos();
      waitForProcessCompletion(process, stdoutReader, readerFailure, deadlineNanos, launcher);
      return process.exitValue();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      if (stdoutReader != null)
        stopReaderThread(stdoutReader);
      throw new IOException("Interrupted while waiting for " + launcher.getFileName(), e);
    }
  }

  /**
   * Starts the nested engine process for a launcher invocation.
   *
   * @param args the launcher arguments
   * @param launcher the launcher executable path
   * @return the started process
   * @throws IOException if process startup fails
   */
  private Process startEngineProcess(String[] args, Path launcher) throws IOException
  {
    List<String> command = new ArrayList<>(args.length + 1);
    command.add(launcher.toString());
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    return builder.start();
  }

  /**
   * Starts the stdout reader thread that mirrors launcher output to the supplied stream.
   *
   * @param process the nested engine process
   * @param out the output sink
   * @param readerFailure captures reader-thread failures
   * @return the started reader thread
   */
  private Thread startStdoutReader(Process process, PrintStream out, AtomicReference<Exception> readerFailure)
  {
    return Thread.ofVirtual().start(() ->
    {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8)))
      {
        while (true)
        {
          String line = reader.readLine();
          if (line == null)
            break;
          out.println(line);
        }
      }
      catch (IOException | RuntimeException e)
      {
        readerFailure.set(e);
      }
    });
  }

  /**
   * Waits for process completion and drains stdout within the shared timeout budget.
   *
   * @param process the nested engine process
   * @param stdoutReader the stdout reader thread
   * @param readerFailure captures reader-thread failures
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param launcher the launcher executable path
   * @throws IOException if the process, reader, or timeout path fails
   * @throws InterruptedException if interrupted while waiting
   */
  private void waitForProcessCompletion(Process process, Thread stdoutReader,
    AtomicReference<Exception> readerFailure, long deadlineNanos, Path launcher)
    throws IOException, InterruptedException
  {
    boolean completed = waitForProcessOrReaderFailure(process, readerFailure, deadlineNanos);
    rethrowProcessReaderFailure(process, stdoutReader, readerFailure.get());
    if (!completed)
    {
      abortEngineProcess(process, stdoutReader);
      throw new IOException("Timeout while waiting for " + launcher.getFileName());
    }
    joinStdoutReader(process, stdoutReader, deadlineNanos, launcher);
    rethrowReaderFailure(readerFailure.get());
  }

  /**
   * Rethrows a reader failure after aborting the process and stopping the reader thread.
   *
   * @param process the nested engine process
   * @param stdoutReader the stdout reader thread
   * @param readerFailure the captured reader failure
   * @throws IOException if the failure should surface as checked I/O
   */
  private void rethrowProcessReaderFailure(Process process, Thread stdoutReader,
    Exception readerFailure) throws IOException
  {
    if (readerFailure == null)
      return;
    abortEngineProcess(process, stdoutReader);
    rethrowReaderFailure(readerFailure);
  }

  /**
   * Joins the stdout reader within the remaining timeout budget.
   *
   * @param process the nested engine process
   * @param stdoutReader the stdout reader thread
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @param launcher the launcher executable path
   * @throws IOException if draining stdout times out
   * @throws InterruptedException if interrupted while joining
   */
  private void joinStdoutReader(Process process, Thread stdoutReader, long deadlineNanos, Path launcher)
    throws IOException, InterruptedException
  {
    long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
    stdoutReader.join(Duration.ofNanos(remainingNanos).toMillis());
    if (stdoutReader.isAlive())
    {
      abortEngineProcess(process, stdoutReader);
      throw new IOException("Timeout while draining stdout for " + launcher.getFileName());
    }
  }

  /**
   * Forcibly stops the process and asks the stdout reader to exit.
   *
   * @param process the nested engine process
   * @param stdoutReader the stdout reader thread; may be {@code null}
   */
  private void abortEngineProcess(Process process, Thread stdoutReader)
  {
    process.destroyForcibly();
    if (stdoutReader != null)
      stopReaderThread(stdoutReader);
  }

  /**
   * Waits until the nested process exits, the stdout reader fails, or the deadline expires.
   *
   * @param process the nested process
   * @param readerFailure captures reader-thread failures
   * @param deadlineNanos the absolute timeout deadline from {@link System#nanoTime()}
   * @return {@code true} if the process exited before the deadline; otherwise {@code false}
   * @throws InterruptedException if interrupted while waiting
   */
  private static boolean waitForProcessOrReaderFailure(Process process,
    AtomicReference<Exception> readerFailure, long deadlineNanos) throws InterruptedException
  {
    while (true)
    {
      if (readerFailure.get() != null)
        return process.waitFor(0, TimeUnit.MILLISECONDS);
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0)
        return false;
      long waitMillis = Math.min(Duration.ofNanos(remainingNanos).toMillis(), WAIT_POLL.toMillis());
      if (waitMillis <= 0)
        waitMillis = 1;
      if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS))
        return true;
    }
  }

  /**
   * Rethrows a reader-thread failure using the closest checked-exception shape.
   *
   * @param readerFailure the captured reader failure
   * @throws IOException if the failure should surface as checked I/O
   */
  private static void rethrowReaderFailure(Exception readerFailure) throws IOException
  {
    if (readerFailure == null)
      return;
    if (readerFailure instanceof IOException ioException)
      throw ioException;
    if (readerFailure instanceof RuntimeException runtimeException)
      throw runtimeException;
    throw new IOException(readerFailure.getMessage(), readerFailure);
  }

  /**
   * Interrupts and briefly joins the stdout reader thread during cleanup.
   *
   * @param stdoutReader the reader thread to stop
   */
  private void stopReaderThread(Thread stdoutReader)
  {
    stdoutReader.interrupt();
    try
    {
      stdoutReader.join(100);
    }
    catch (InterruptedException _)
    {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Builds one-shot trial arguments without managed-session persistence.
   * <p>
   * Equivalent to
   * {@code buildTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson, null)}.
   *
   * @param promptFile the prompt file for the trial turn
   * @param modelId the model id under test
   * @param effort the effort level under test
   * @param runnerWorktree the runner worktree
   * @param outputJson the final output path
   * @return the launcher argument vector
   */
  private String[] buildTrialArgs(Path promptFile, String modelId,
    String effort, String runnerWorktree, String outputJson)
  {
    return buildTrialArgs(promptFile, modelId, effort, runnerWorktree, outputJson, null);
  }

  /**
   * Builds trial arguments for one-shot or managed-session execution.
   *
   * @param promptFile the prompt file for the trial turn
   * @param modelId the model id under test
   * @param effort the effort level under test
   * @param runnerWorktree the runner worktree
   * @param outputJson the optional final output path
   * @param sessionFile the optional managed-session file
   * @return the launcher argument vector
   */
  private String[] buildTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path sessionFile)
  {
    validateModelAndEffort(runtimeId, modelId, effort);
    Path jlinkBin = jlinkBin(runnerWorktree, runtimeId);
    Set<String> flags = supportedFlags(runnerWorktree);
    if (sessionFile != null && !flags.contains("--session-file"))
    {
      throw new IllegalArgumentException(launcherPath(runnerWorktree).getFileName() +
        " does not support --session-file; update the runner/plugin cache before multi-turn SPRT");
    }
    return buildTrialArgsInternal(promptFile, modelId, effort, runnerWorktree, outputJson,
      jlinkBin, flags.contains("--plugin-source") && flags.contains("--jlink-bin"), sessionFile);
  }

  /**
   * Builds grader-runner arguments for the current engine/runtime.
   *
   * @param graderPromptFile the grader prompt file
   * @param modelId the grader model id
   * @param effort the grader effort level
   * @param runnerWorktree the runner worktree
   * @return the launcher argument vector
   */
  private String[] buildGraderArgs(Path graderPromptFile, String modelId,
    String effort, String runnerWorktree)
  {
    validateModelAndEffort(runtimeId, modelId, effort);
    Path jlinkBin = jlinkBin(runnerWorktree, runtimeId);
    Set<String> flags = supportedFlags(runnerWorktree);
    return buildGraderArgsInternal(graderPromptFile, modelId, effort, runnerWorktree, null,
      jlinkBin, flags.contains("--plugin-source") && flags.contains("--jlink-bin"),
      flags.contains("--agent"));
  }

  /**
   * Resolves the fixed grader model/effort pair for the current runner worktree.
   *
   * @param runnerWorktree the runner worktree
   * @return the grader model configuration
   * @throws IOException if the grader agent descriptor cannot be read
   */
  private GraderModelEffort resolveGraderModelEffort(String runnerWorktree)
    throws IOException
  {
    Path candidatePluginRoot = Path.of(runnerWorktree, "client/plugin");
    return resolveGraderModelEffort(List.of(candidatePluginRoot, scope.getPluginRoot()),
      scope.getPluginDescriptor(), claudeCodeVersion);
  }

  /**
   * Builds trial arguments once engine-specific capability checks are complete.
   *
   * @param promptFile the prompt file for the trial turn
   * @param modelId the model id under test
   * @param effort the effort level under test
   * @param runnerWorktree the runner worktree
   * @param outputJson the optional final output path
   * @param jlinkBin the runner jlink bin directory
   * @param includeClaudeFlags whether Claude-specific runner flags should be emitted
   * @param sessionFile the optional managed-session file
   * @return the launcher argument vector
   */
  private static String[] buildTrialArgsInternal(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path jlinkBin, boolean includeClaudeFlags,
    Path sessionFile)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    if (includeClaudeFlags)
      requireThat(jlinkBin, "jlinkBin").isNotNull();
    List<String> args = new ArrayList<>();
    args.add("--prompt-file");
    args.add(promptFile.toString());
    args.add("--model");
    args.add(modelId);
    args.add("--effort");
    args.add(effort);
    if (includeClaudeFlags)
    {
      args.add("--plugin-source");
      args.add(Path.of(runnerWorktree, "client/plugin").toString());
      args.add("--jlink-bin");
      args.add(jlinkBin.toString());
    }
    args.add("--cwd");
    args.add(runnerWorktree);
    if (outputJson != null && !outputJson.isBlank())
    {
      args.add("--output");
      args.add(outputJson);
    }
    if (sessionFile != null)
    {
      args.add("--session-file");
      args.add(sessionFile.toString());
    }
    return args.toArray(String[]::new);
  }

  /**
   * Builds grader arguments once engine-specific capability checks are complete.
   *
   * @param graderPromptFile the grader prompt file
   * @param modelId the grader model id
   * @param effort the grader effort level
   * @param runnerWorktree the runner worktree
   * @param gradeOutputPath the optional grader output path
   * @param jlinkBin the runner jlink bin directory
   * @param includeClaudeFlags whether Claude-specific runner flags should be emitted
   * @param includeAgentFlag whether the runner supports explicit agent selection
   * @return the launcher argument vector
   */
  private static String[] buildGraderArgsInternal(Path graderPromptFile, String modelId,
    String effort, String runnerWorktree, String gradeOutputPath, Path jlinkBin,
    boolean includeClaudeFlags, boolean includeAgentFlag)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    if (includeClaudeFlags)
      requireThat(jlinkBin, "jlinkBin").isNotNull();
    List<String> args = new ArrayList<>();
    args.add("--prompt-file");
    args.add(graderPromptFile.toString());
    args.add("--model");
    args.add(modelId);
    args.add("--effort");
    args.add(effort);
    if (includeAgentFlag)
    {
      args.add("--agent");
      args.add(GRADER_AGENT);
    }
    if (includeClaudeFlags)
    {
      args.add("--plugin-source");
      args.add(Path.of(runnerWorktree, "client/plugin").toString());
      args.add("--jlink-bin");
      args.add(jlinkBin.toString());
    }
    args.add("--cwd");
    args.add(runnerWorktree);
    if (gradeOutputPath != null)
    {
      args.add("--output");
      args.add(gradeOutputPath);
    }
    return args.toArray(String[]::new);
  }

  /**
   * Resolves the grader model configuration from a single plugin root.
   * <p>
   * Equivalent to
   * {@code resolveGraderModelEffort(List.of(pluginRoot), descriptor, claudeCodeVersion)}.
   *
   * @param pluginRoot the plugin root to inspect
   * @param descriptor the active plugin descriptor
   * @param claudeCodeVersion the Claude Code version for model alias resolution
   * @return the grader model configuration
   * @throws IOException if descriptor inspection fails
   */
  private static GraderModelEffort resolveGraderModelEffort(Path pluginRoot,
    Path descriptor, String claudeCodeVersion) throws IOException
  {
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    return resolveGraderModelEffort(List.of(pluginRoot), descriptor, claudeCodeVersion);
  }

  /**
   * Resolves the grader model configuration from one or more candidate plugin roots.
   *
   * @param pluginRoots the plugin roots to inspect
   * @param descriptor the active plugin descriptor
   * @param claudeCodeVersion the Claude Code version for model alias resolution
   * @return the grader model configuration
   * @throws IOException if descriptor inspection fails
   */
  private static GraderModelEffort resolveGraderModelEffort(List<Path> pluginRoots,
    Path descriptor, String claudeCodeVersion) throws IOException
  {
    requireThat(pluginRoots, "pluginRoots").isNotNull();
    requireThat(descriptor, "descriptor").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    String graderRuntimeId = runtimeIdFromDescriptor(descriptor);
    Path agentPath = findGraderAgentDescriptorPath(pluginRoots, graderRuntimeId);

    String modelId;
    String effort;
    if (graderRuntimeId.equals("claude"))
    {
      String model = extractYamlFrontmatterField(agentPath, "model");
      if (model.isBlank())
        throw new IllegalStateException("Instruction grader agent is missing model frontmatter: " + agentPath);
      modelId = ModelIdResolver.resolve(claudeCodeVersion, model);
      effort = extractYamlFrontmatterField(agentPath, "effort");
    }
    else if (graderRuntimeId.equals("codex"))
    {
      modelId = extractTomlStringField(agentPath, "model");
      effort = extractTomlStringField(agentPath, "model_reasoning_effort");
    }
    else
      throw new IllegalStateException("Unsupported CAT engine descriptor: " + descriptor);

    if (effort.isBlank())
      throw new IllegalStateException("Instruction grader agent is missing effort: " + agentPath);
    validateModelAndEffort(graderRuntimeId, modelId, effort);
    return new GraderModelEffort(modelId, effort);
  }

  /**
   * Finds the first existing grader-agent descriptor among the candidate plugin roots.
   *
   * @param pluginRoots the plugin roots to inspect
   * @param graderRuntimeId the grader runtime id
   * @return the first matching descriptor path
   */
  private static Path findGraderAgentDescriptorPath(List<Path> pluginRoots,
    String graderRuntimeId)
  {
    List<Path> candidates = new ArrayList<>();
    for (Path pluginRoot: pluginRoots)
    {
      candidates.addAll(graderAgentDescriptorPaths(pluginRoot, graderRuntimeId));
    }
    for (Path candidate: candidates)
    {
      if (Files.exists(candidate))
        return candidate;
    }
    throw new IllegalStateException(
      "Instruction grader agent descriptor not found. Searched: " + candidates);
  }

  /**
   * Returns the candidate grader-agent descriptor paths for a runtime.
   *
   * @param pluginRoot the plugin root to inspect
   * @param graderRuntimeId the grader runtime id
   * @return the candidate descriptor paths
   */
  private static List<Path> graderAgentDescriptorPaths(Path pluginRoot,
    String graderRuntimeId)
  {
    return switch (graderRuntimeId)
    {
      case "claude" -> List.of(
        pluginRoot.resolve("agents/claude/instruction-grader-agent.md"),
        pluginRoot.resolve("agents/instruction-grader-agent.md"));
      case "codex" -> List.of(
        pluginRoot.resolve("agents/codex/instruction-grader-agent.toml"),
        pluginRoot.resolve("agents/instruction-grader-agent.toml"));
      default -> throw new IllegalStateException("Unsupported CAT engine: " + graderRuntimeId);
    };
  }

  /**
   * Extracts a YAML frontmatter field from an agent markdown file.
   *
   * @param agentPath the agent descriptor path
   * @param fieldName the field name to extract
   * @return the extracted value, or an empty string
   * @throws IOException if the agent file cannot be read
   */
  private static String extractYamlFrontmatterField(Path agentPath, String fieldName)
    throws IOException
  {
    String content = Files.readString(agentPath, UTF_8);
    String frontmatter = FrontmatterUtils.extractFrontmatter(content);
    if (frontmatter == null || frontmatter.isBlank())
      return "";
    JsonNode parsed = YAML_MAPPER.readTree(frontmatter);
    JsonNode node = parsed.get(fieldName);
    if (node == null || node.isNull() || node.isMissingNode())
      return "";
    return node.asString("");
  }

  /**
   * Extracts a TOML string field from an agent descriptor.
   *
   * @param agentPath the agent descriptor path
   * @param fieldName the field name to extract
   * @return the extracted value, or an empty string
   * @throws IOException if the agent file cannot be read
   */
  private static String extractTomlStringField(Path agentPath, String fieldName)
    throws IOException
  {
    String content = Files.readString(agentPath, UTF_8);
    for (String line: content.split("\\R"))
    {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#"))
        continue;
      int equalsIndex = trimmed.indexOf('=');
      if (equalsIndex < 0)
        continue;
      String key = trimmed.substring(0, equalsIndex).strip();
      if (!key.equals(fieldName))
        continue;
      return parseTomlStringValue(trimmed.substring(equalsIndex + 1).strip());
    }
    return "";
  }

  /**
   * Parses a TOML string literal or bare value from the right-hand side of an assignment.
   *
   * @param rawValue the raw TOML value expression
   * @return the parsed scalar value
   */
  private static String parseTomlStringValue(String rawValue)
  {
    if (rawValue.isEmpty())
      return "";
    char quote = rawValue.charAt(0);
    if (quote == '"' || quote == '\'')
    {
      int closingQuote = rawValue.indexOf(quote, 1);
      if (closingQuote < 0)
        return "";
      return rawValue.substring(1, closingQuote);
    }
    int commentIndex = rawValue.indexOf('#');
    String withoutComment = rawValue;
    if (commentIndex >= 0)
      withoutComment = rawValue.substring(0, commentIndex);
    return withoutComment.strip();
  }

  /**
   * Dispatches runtime-specific model/effort validation.
   *
   * @param runtimeId the engine runtime id
   * @param modelId the model id to validate
   * @param effort the effort level to validate
   */
  private static void validateModelAndEffort(String runtimeId, String modelId, String effort)
  {
    requireThat(runtimeId, "runtimeId").isNotBlank();
    switch (runtimeId)
    {
      case "claude" -> validateClaudeModelAndEffort(modelId, effort);
      case "codex" -> validateCodexModelAndEffort(modelId, effort);
      default -> throw new IllegalStateException("Unsupported CAT engine: " + runtimeId);
    }
  }

  /**
   * Validates a Claude model/effort pair.
   *
   * @param modelId the model id to validate
   * @param effort the effort level to validate
   */
  private static void validateClaudeModelAndEffort(String modelId, String effort)
  {
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    if (!modelId.startsWith("claude-") && !ModelIdResolver.knownModels().contains(modelId))
      throw new IllegalArgumentException("Invalid Claude model ID '" + modelId + "'.");
    validateEffort(effort, CLAUDE_EFFORT_LEVELS);
  }

  /**
   * Validates a Codex model/effort pair.
   *
   * @param modelId the model id to validate
   * @param effort the effort level to validate
   */
  private static void validateCodexModelAndEffort(String modelId, String effort)
  {
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    if (modelId.startsWith("claude-") || ModelIdResolver.knownModels().contains(modelId))
      throw new IllegalArgumentException("Invalid Codex model ID '" + modelId + "'.");
    validateEffort(effort, CODEX_EFFORT_LEVELS);
  }

  /**
   * Validates that an effort value belongs to the allowed set.
   *
   * @param effort the effort value to validate
   * @param allowedEffort the allowed effort values
   */
  private static void validateEffort(String effort, List<String> allowedEffort)
  {
    if (!allowedEffort.contains(effort))
      throw new IllegalArgumentException("Invalid effort '" + effort + "'. Valid values: " + allowedEffort);
  }

  /**
   * Resolves the jlink binary directory for an engine in a runner worktree.
   *
   * @param runnerWorktree runner worktree path
   * @param runtimeId the runtime identifier
   * @return the jlink bin directory
   */
  public static Path jlinkBin(String runnerWorktree, String runtimeId)
  {
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(runtimeId, "runtimeId").isNotBlank();
    Path result = Path.of(runnerWorktree, "client/distribution/target/jlink", runtimeId, "bin");
    if (!Files.isDirectory(result))
      throw new IllegalArgumentException(
        "jlink directory not found in runner worktree for runtime '" + runtimeId + "': " +
          result);
    return result;
  }

  static String runtimeIdFromDescriptor(Path descriptor)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    Path parent = descriptor.getParent();
    if (parent == null)
      throw new IllegalStateException("Unsupported CAT engine descriptor: " + descriptor);
    String name = parent.getFileName().toString();
    if (!name.startsWith(".") || !name.endsWith("-plugin"))
      throw new IllegalStateException("Unsupported CAT engine descriptor: " + descriptor);
    return name.substring(1, name.length() - "-plugin".length());
  }

  /**
   * Returns the runner launcher path inside a runner worktree.
   *
   * @param runnerWorktree the runner worktree
   * @return the launcher path
   */
  private Path launcherPath(String runnerWorktree)
  {
    return jlinkBin(runnerWorktree, runtimeId).resolve(runtimeId + "-runner");
  }

  /**
   * Returns the supported CLI flags for the nested runner launcher.
   *
   * @param runnerWorktree the runner worktree
   * @return the supported flag set
   */
  private Set<String> supportedFlags(String runnerWorktree)
  {
    Path launcher = launcherPath(runnerWorktree);
    String launcherPath = launcher.toString();
    return launcherFlagsByPath.computeIfAbsent(launcherPath, _ ->
    {
      ProcessRunner.Result result = ProcessRunner.run(Path.of(runnerWorktree), launcherPath, "--help");
      String help = result.output();
      Set<String> flags = new HashSet<>();
      for (String token: List.of("--plugin-source", "--jlink-bin", "--agent", "--output",
        "--session-file"))
      {
        if (help.contains(token))
          flags.add(token);
      }
      return Set.copyOf(flags);
    });
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
  public static String toErrorJson(CliTool scope, String message) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(message, "message").isNotNull();
    String escapedMessage = scope.getJsonMapper().writeValueAsString(message);
    return "{\"status\":\"ERROR\",\"message\":" + escapedMessage + "}";
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
  public static void run(CliTool scope, String[] args, PrintStream out)
    throws IOException, InterruptedException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length == 0)
      throw new IllegalArgumentException(noCommandSpecifiedMessage());
    String claudeCodeVersion = ModelIdResolver.detectClaudeCodeVersionOrLatestMapping();
    new SprtRunner(scope, claudeCodeVersion).run(args, out);
  }

  /**
   * Returns the error message for missing CLI commands.
   *
   * @return the no-command usage message
   */
  private static String noCommandSpecifiedMessage()
  {
    return "SprtRunner: no command specified.\n" +
      "Usage: skill-test-runner <command> [args...]\n" +
      "Commands: extract-units, extract-model, extract-effort, extract-config-source, " +
      "extract-test-dir, detect-changes, " +
      "map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, " +
      "merge-results, create-isolation-branch, create-runner-worktrees, check-run-contamination, " +
      "remove-runner-worktrees, write-test-results, save-failed-run, remove-runner-worktree, " +
      "remove-isolation-branch, prepare-run, prepare-trial, get-json-field, get-tc-name, " +
      "get-worktree-field, run-sprt";
  }
}
