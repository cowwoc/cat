/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;
import io.github.cowwoc.cat.claude.hook.util.FileUtils;
import io.github.cowwoc.cat.claude.hook.util.ProcessRunner;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Manages isolation branch and runner worktree lifecycle for SPRT test runs.
 * <p>
 * Handles creation and removal of isolation branches and runner worktrees, as well
 * as contamination detection in runner outputs.
 */
final class SprtIsolationManager
{
  private final ClaudePluginScope scope;

  /**
   * Creates a new SprtIsolationManager.
   *
   * @param scope the Claude plugin scope providing JSON mapper and other services
   * @throws NullPointerException if {@code scope} is null
   */
  SprtIsolationManager(ClaudePluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
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
  String createIsolationBranch(String[] args) throws IOException
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

    String isolationBranch = issueName + "-isolation";

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
        // extract-turns expects a file path (e.g., /path/tc1.md), not a directory.
        // It creates files like /path/tc1_turn1.md, /path/tc1_turn2.md
        Path outputBase = testDir.resolve("tc" + opaqueId + ".md");
        ProcessRunner.Result extractResult = ProcessRunner.run(worktreePath,
          extractTurnsBin.toString(), mdFile.toString(), outputBase.toString());
        if (extractResult.exitCode() != 0)
          throw new IOException(
            "InstructionTestRunner create-isolation-branch: extract-turns failed for " + mdFile +
            " with exit code " + extractResult.exitCode() + ": " + extractResult.stdout());

        // Delete the original .md file after extraction
        Files.delete(mdFile);

        // Copy runner fixture if present (fixtures/[stem]_runner.json → tc[N]_runner.json)
        Path fixtureSource = testDir.resolve("fixtures").resolve(stem + "_runner.json");
        if (Files.exists(fixtureSource))
        {
          Path fixtureDest = testDir.resolve("tc" + opaqueId + "_runner.json");
          Files.copy(fixtureSource, fixtureDest, StandardCopyOption.REPLACE_EXISTING);
          Files.delete(fixtureSource);
        }
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
   * @param args {@code [worktree_path, sprt_state_path, issue_name, session_id]}
   * @return compact JSON object with {@code output_dir} (path to the test-runs session directory,
   *   which is created by this command) and {@code worktrees} (array of worktree descriptors, each
   *   with {@code tc_id}, {@code runner_branch}, {@code runner_worktree}, and {@code trial_num})
   * @throws IllegalArgumentException if the argument count is wrong or the state file is not found
   * @throws IOException              if the state file cannot be read or a git operation fails
   */
  String createRunnerWorktrees(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 4)
      throw new IllegalArgumentException(
        "InstructionTestRunner create-runner-worktrees: expected 4 arguments " +
        "<worktree_path> <sprt_state_path> <issue_name> <session_id>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner create-runner-worktrees " +
        "<worktree_path> <sprt_state_path> <issue_name> <session_id>");

    Path worktreePath = Path.of(args[0]);
    Path sprtStatePath = Path.of(args[1]);
    String issueName = args[2];
    String sessionId = args[3];

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
        String runnerWorktree = scope.getCatWorkPath().resolve("worktrees").resolve(runnerBranch).toString();

        ProcessRunner.Result worktreeResult = ProcessRunner.run(worktreePath,
          "git", "-C", worktreePath.toString(), "worktree", "add", "-b", runnerBranch,
          runnerWorktree, issueName + "-isolation");
        if (worktreeResult.exitCode() != 0)
          throw new IOException(
            "InstructionTestRunner create-runner-worktrees: git worktree add failed for " +
            tcId + " with exit code " + worktreeResult.exitCode() + ": " + worktreeResult.stdout());

        // Copy jlink directory from the issue worktree to the runner worktree so that
        // prepareTrial and claude-runner can use it without rebuilding.
        Path sourceJlink = worktreePath.resolve("client/target/jlink");
        Path targetJlink = Path.of(runnerWorktree, "client/target/jlink");
        if (Files.isDirectory(sourceJlink))
        {
          Files.createDirectories(targetJlink.getParent());
          FileUtils.copyDirectoryRecursively(sourceJlink, targetJlink);
        }

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
  String removeRunnerWorktrees(String[] args) throws IOException
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
  String removeIsolationBranch(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner remove-isolation-branch: expected 2 arguments " +
        "<worktree_path> <isolation_branch>, got " + args.length + ".\n" +
        "Usage: instruction-test-runner remove-isolation-branch <worktree_path> <isolation_branch>");

    Path worktreePath = Path.of(args[0]);
    String isolationBranch = args[1];

    // Ignore failure — branch may already be deleted by remove-runner-worktrees
    ProcessRunner.run(worktreePath, "git", "branch", "-D", isolationBranch);

    return "ok";
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
  String removeRunnerWorktree(String[] args) throws IOException
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
  String checkRunContamination(String[] args) throws IOException
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
   * Strips frontmatter and the {@code ## Assertions} section (and everything after it) from a test case file.
   *
   * @param filePath the test case file to strip
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
}
