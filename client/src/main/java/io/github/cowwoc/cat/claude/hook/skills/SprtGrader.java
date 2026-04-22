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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Handles per-trial grading for SPRT test runs.
 * <p>
 * Invokes the instruction-grader-agent to evaluate test case outputs and returns
 * PASS/FAIL verdicts.
 */
final class SprtGrader
{
  private final Logger log = LoggerFactory.getLogger(SprtGrader.class);
  private final ClaudePluginScope scope;

  /**
   * Creates a new SprtGrader.
   *
   * @param scope the Claude plugin scope providing JSON mapper and other services
   * @throws NullPointerException if {@code scope} is null
   */
  SprtGrader(ClaudePluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
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
   * @param modelId        the model ID to use for grading
   * @param runnerWorktree path to the runner worktree
   * @param jlinkBin       the jlink binary directory path
   * @param testDir        path to the test directory containing scenario MD files
   * @param gradeOutputPath path where grader should write grade.json
   * @param isolationResult JSON string from create-isolation-branch (contains tc_name_map)
   * @return {@code "PASS"} or {@code "FAIL"}
   * @throws IOException if grading fails
   */
  String gradeTc(String tcId, int trialNum, String outputJson, String modelId,
    String runnerWorktree, Path jlinkBin, String testDir,
    String gradeOutputPath, String isolationResult)
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
  String buildGraderPrompt(String outputJson, String testDir, String originalStem,
    String tcId, int trialNum, String gradeOutputPath, String runnerWorktree)
  {
    return String.format("""
      Grade the following test run:

      1. **Transcript**: %s (claude-runner JSON output file)
      2. **Scenario file path**: %s
      3. **Run ID**: %s_run_%d
      4. **Output path**: %s
      5. **Runner worktree**: %s

      **REQUIRED OUTPUT SCHEMA** — write exactly this structure to the temp file:
      ```json
      {
        "assertion_results": [
          {"assertion": "...", "verdict": "PASS", "evidence": "...", "explanation": "..."},
          {"assertion": "...", "verdict": "FAIL", "evidence": "...", "explanation": "..."}
        ]
      }
      ```
      Field names MANDATORY: `assertion_results` (NOT `assertions`), `verdict`
      (NOT `status`/`result`/`pass`), uppercase `"PASS"`/`"FAIL"` only.
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
   * @param jlinkBin the jlink binary directory path
   * @param gradeOutputPath the expected output path for the grade file
   * @param trialNum the trial number
   * @return the actual path where the grade file was written
   * @throws IOException if the grader fails or the grade file is not found
   */
  Path invokeGrader(String tcId, Path graderPromptFile, String modelId,
    String runnerWorktree, Path jlinkBin, String gradeOutputPath, int trialNum)
    throws IOException
  {
    int maxAttempts = 3;
    IOException lastException = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt += 1)
    {
      try (ClaudeTool graderScope = new MainClaudeTool())
      {
        String[] graderArgs = buildGraderArgs(graderPromptFile, modelId, runnerWorktree, jlinkBin);

        Path graderStdout = Files.createTempFile("grader-stdout-", ".txt");
        try (PrintStream graderOut = new PrintStream(graderStdout.toFile(), UTF_8))
        {
          int exitCode = ClaudeRunner.run(graderScope, graderArgs, graderOut);

          if (exitCode != 0)
          {
            String graderOutput = Files.readString(graderStdout, UTF_8);
            lastException = new IOException("Grader for " + tcId + " exited with code " + exitCode +
              "\nGrader output:\n" + graderOutput);
            if (graderOutput.contains("API Error") && attempt < maxAttempts)
            {
              log.warn("TC{}: grader hit API error on attempt {}/{}, retrying in {}s",
                tcId, attempt, maxAttempts, attempt * 5);
              try
              {
                Thread.sleep(attempt * 5000L);
              }
              catch (InterruptedException _)
              {
                Thread.currentThread().interrupt();
                throw lastException;
              }
              continue;
            }
            throw lastException;
          }

          return findGradeFile(tcId, gradeOutputPath, runnerWorktree, trialNum, graderStdout);
        }
        finally
        {
          Files.deleteIfExists(graderStdout);
        }
      }
    }
    throw lastException;
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
  Path findGradeFile(String tcId, String gradeOutputPath, String runnerWorktree,
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
  String extractVerdict(Path gradePath) throws IOException
  {
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode gradeNode = mapper.readTree(Files.readString(gradePath, UTF_8));

    JsonNode assertionResults = gradeNode.path("assertion_results");
    if (assertionResults.isMissingNode() || !assertionResults.isArray())
      throw new IOException(
        "Grade file missing required 'assertion_results' array: " + gradePath);

    ArrayNode results = (ArrayNode) assertionResults;
    if (results.isEmpty())
      throw new IOException("Grade file has empty assertion_results: " + gradePath);

    for (JsonNode result : results)
    {
      String verdict = result.path("verdict").asString();
      if (verdict.isEmpty())
      {
        StringJoiner foundFields = new StringJoiner(", ");
        for (Map.Entry<String, JsonNode> entry : ((ObjectNode) result).properties())
          foundFields.add(entry.getKey());
        throw new IOException("Grader output missing required 'verdict' field. " +
          "Found fields: [" + foundFields + "]. " +
          "Expected exactly {\"verdict\": \"PASS\"} or {\"verdict\": \"FAIL\"}. " +
          "Grade file: " + gradePath);
      }
      if (!verdict.equals("PASS") && !verdict.equals("FAIL"))
        throw new IOException("Invalid verdict value: '" + verdict + "'. " +
          "Must be exactly 'PASS' or 'FAIL'. Grade file: " + gradePath);

      if (!verdict.equals("PASS"))
        return "FAIL";
    }

    return "PASS";
  }

  /**
   * Looks up the original filename stem for an opaque test-case ID.
   *
   * @param args {@code [isolation_result_json, tc_id]}
   * @return the original filename stem (e.g., {@code "creates-hello-file"})
   * @throws IOException if the JSON cannot be parsed
   */
  private String getTcName(String[] args) throws IOException
  {
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
    String numericKey = tcId.substring(2);
    JsonNode stemNode = tcNameMapNode.path(numericKey);
    if (stemNode.isMissingNode())
      throw new IllegalArgumentException(
        "InstructionTestRunner get-tc-name: tc_id '" + tcId + "' (key '" + numericKey +
        "') not found in tc_name_map");
    return stemNode.asString();
  }

  /**
   * Builds the grader argument array for ClaudeRunner invocation.
   *
   * @param graderPromptFile the grader prompt file path
   * @param modelId          the model ID to use for grading
   * @param runnerWorktree   the runner worktree path (contains .cat/config/)
   * @param jlinkBin         the jlink binary directory path
   * @return the grader arguments array
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
}
