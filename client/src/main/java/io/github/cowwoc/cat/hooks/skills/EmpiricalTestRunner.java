/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ObjectNode;

/**
 * CLI tool for running empirical compliance tests against Claude CLI.
 * <p>
 * Executes controlled experiments to measure agent compliance rates across different
 * prompt configurations. Each configuration is run N times and results are collected
 * as pass/fail statistics.
 */
public final class EmpiricalTestRunner
{
  private static final int DEFAULT_TIMEOUT_SECONDS = 180;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private final JvmScope scope;
  private final ObjectWriter compactWriter;

  /**
   * Creates a new empirical test runner.
   *
   * @param scope the JVM scope providing JSON mapper and display utilities
   * @throws NullPointerException if {@code scope} is null
   */
  public EmpiricalTestRunner(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.compactWriter = scope.getJsonMapper().writer().without(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Runs all tests defined in the config file.
   *
   * @param configPath path to the test config JSON file
   * @param trials number of trials per configuration
   * @param model the model to test with (haiku, sonnet, opus)
   * @param cwd working directory for claude CLI
   * @param outputPath optional path to write full JSON results
   * @return the overall exit code (1 if any config got 0% and there are multiple configs, 0 otherwise)
   * @throws NullPointerException if {@code configPath}, {@code model}, or {@code cwd} are null
   * @throws IOException if config cannot be read or output cannot be written
   */
  public int runTests(Path configPath, int trials, String model, Path cwd, Path outputPath)
    throws IOException
  {
    requireThat(configPath, "configPath").isNotNull();
    requireThat(model, "model").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(trials, "trials").isPositive();

    String configJson = Files.readString(configPath);
    Map<String, Object> config = scope.getJsonMapper().readValue(configJson, MAP_TYPE);

    String targetDescription = (String) config.getOrDefault("target_description", "");
    @SuppressWarnings("unchecked")
    Map<String, Object> criteria = (Map<String, Object>) config.getOrDefault("success_criteria",
      new HashMap<>());
    @SuppressWarnings("unchecked")
    List<PrimingMessage> primingMessages = PrimingMessage.fromRawList(
      (List<Object>) config.getOrDefault("priming_messages", new ArrayList<>()));
    @SuppressWarnings("unchecked")
    Map<String, String> configs = (Map<String, String>) config.getOrDefault("configs",
      new HashMap<>());

    System.out.println("Empirical Compliance Test: " + targetDescription);
    System.out.println("Model: " + model + ", Trials: " + trials + ", Configs: " + configs.size());
    System.out.println("=".repeat(90));

    Map<String, ConfigResult> allResults = new HashMap<>();
    for (Map.Entry<String, String> entry : configs.entrySet())
    {
      String configName = entry.getKey();
      String prompt = entry.getValue();

      System.out.println();
      System.out.println(configName + ":");
      System.out.flush();

      ConfigResult result = runConfig(configName, prompt, primingMessages, criteria, trials,
        model, cwd);
      allResults.put(configName, result);

      System.out.println("  RESULT: " + result.passes() + "/" + result.trials() + " (" +
        result.rate() + "%)");
      System.out.flush();
    }

    printSummaryTable(allResults);

    if (outputPath != null)
    {
      ObjectNode output = scope.getJsonMapper().createObjectNode();
      output.put("target", targetDescription);
      output.put("model", model);
      output.put("trials", trials);
      output.set("criteria", scope.getJsonMapper().valueToTree(criteria));
      output.set("results", scope.getJsonMapper().valueToTree(allResults));

      Files.writeString(outputPath, scope.getJsonMapper().writeValueAsString(output));
      System.out.println();
      System.out.println("Full results written to: " + outputPath);
    }

    if (allResults.size() > 1)
    {
      int worstRate = allResults.values().stream().
        mapToInt(ConfigResult::rate).
        min().
        orElse(0);
      if (worstRate == 0)
        return 1;
    }

    return 0;
  }

  /**
   * Calculates the pass rate as a percentage.
   *
   * @param passes the number of passing trials
   * @param trials the total number of trials
   * @return the pass rate as a percentage (0-100)
   */
  public static int calculateRate(int passes, int trials)
  {
    if (trials <= 0)
      return 0;
    return Math.round(passes * 100.0f / trials);
  }

  /**
   * Runs all trials for a single configuration.
   *
   * @param name the configuration name
   * @param prompt the prompt text
   * @param primingMessages list of priming messages to send before the test prompt
   * @param criteria the success criteria map
   * @param trials number of trials to run
   * @param model the model name
   * @param cwd the working directory
   * @return the configuration result
   */
  private ConfigResult runConfig(String name, String prompt, List<PrimingMessage> primingMessages,
    Map<String, Object> criteria, int trials, String model, Path cwd)
  {
    List<TrialResult> results = new ArrayList<>();
    int passes = 0;

    for (int i = 0; i < trials; ++i)
    {
      TrialResult result = runTrial(primingMessages, prompt, criteria, model, cwd);
      results.add(result);
      if (result.pass())
        ++passes;

      String status;
      if (result.pass())
        status = "PASS";
      else
        status = "FAIL";
      String preview = "";
      if (!result.pass() && !result.outputPreview().isEmpty())
        preview = " [" + truncatePreview(result.outputPreview(), 80) + "]";
      else if (result.error() != null && !result.error().isEmpty())
        preview = " [ERROR: " + result.error() + "]";

      System.out.println("  t" + (i + 1) + " " + status + " (" + result.elapsed() + "s)" + preview);
      System.out.flush();
    }

    int rate = calculateRate(passes, trials);
    return new ConfigResult(name, trials, passes, rate, results);
  }

  /**
   * Truncates a string to the specified maximum length.
   *
   * @param text the text to truncate
   * @param maxLength the maximum length
   * @return the truncated text
   */
  private static String truncatePreview(String text, int maxLength)
  {
    if (text.length() > maxLength)
      return text.substring(0, maxLength);
    return text;
  }

  /**
   * Executes the claude CLI process with the given input.
   *
   * @param command the command to execute
   * @param input the input to send to the process
   * @param cwd the working directory
   * @return the process result with output, elapsed time, and error
   */
  private ProcessResult executeClaudeProcess(List<String> command, String input, Path cwd)
  {
    long startTime = System.currentTimeMillis();
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command);
      Map<String, String> env = pb.environment();
      env.remove("CLAUDECODE");
      pb.directory(cwd.toFile());
      pb.redirectErrorStream(true);

      Process process = pb.start();
      process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().close();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          output.append(line).append('\n');
          line = reader.readLine();
        }
      }

      boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;

      if (!completed)
      {
        process.destroyForcibly();
        return new ProcessResult("", elapsed, "timeout");
      }

      return new ProcessResult(output.toString(), elapsed, "");
    }
    catch (IOException | InterruptedException e)
    {
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      return new ProcessResult("", elapsed, e.getMessage());
    }
  }

  /**
   * Runs a single trial and evaluates the result.
   *
   * @param primingMessages list of priming messages
   * @param testPrompt the test prompt
   * @param criteria the success criteria
   * @param model the model name
   * @param cwd the working directory
   * @return the trial result
   */
  private TrialResult runTrial(List<PrimingMessage> primingMessages, String testPrompt,
    Map<String, Object> criteria, String model, Path cwd)
  {
    List<String> command = new ArrayList<>();
    command.add("claude");
    command.add("-p");
    command.add("--model");
    command.add(model);
    command.add("--input-format");
    command.add("stream-json");
    command.add("--output-format");
    command.add("stream-json");
    command.add("--verbose");
    command.add("--no-session-persistence");
    command.add("--dangerously-skip-permissions");

    String input = buildInput(primingMessages, testPrompt);

    ProcessResult processResult = executeClaudeProcess(command, input, cwd);

    if (!processResult.error().isEmpty())
      return new TrialResult(false, new HashMap<>(), processResult.elapsed(), "",
        new ArrayList<>(), processResult.error());

    ParsedOutput parsed = parseOutput(processResult.output());
    EvaluationResult evaluation = evaluateOutput(parsed.texts(), parsed.toolUses(), criteria);

    String preview = String.join("\n", parsed.texts());
    String truncatedPreview = truncatePreview(preview, 300).replace("\n", "\\n");

    List<String> toolsUsed;
    if (parsed.toolUses().size() > 5)
      toolsUsed = parsed.toolUses().subList(0, 5);
    else
      toolsUsed = parsed.toolUses();

    return new TrialResult(evaluation.pass(), evaluation.checks(), processResult.elapsed(),
      truncatedPreview, toolsUsed, "");
  }

  /**
   * Builds stream-json input with priming messages followed by test prompt.
   *
   * @param primingMessages the priming messages
   * @param testPrompt the test prompt
   * @return the stream-json input string
   * @throws NullPointerException if {@code primingMessages} or {@code testPrompt} are null
   */
  public String buildInput(List<PrimingMessage> primingMessages, String testPrompt)
  {
    requireThat(primingMessages, "primingMessages").isNotNull();
    requireThat(testPrompt, "testPrompt").isNotNull();
    StringJoiner joiner = new StringJoiner("\n");
    int toolUseCounter = 0;
    for (PrimingMessage msg : primingMessages)
    {
      switch (msg)
      {
        case PrimingMessage.UserMessage userMsg ->
          joiner.add(makeUserMessage(userMsg.text()));
        case PrimingMessage.ToolUse toolUse ->
        {
          String toolUseId = "toolu_priming_" + toolUseCounter;
          ++toolUseCounter;
          joiner.add(makeToolUseMessage(toolUseId, toolUse.tool(), toolUse.input()));
          joiner.add(makeToolResultMessage(toolUseId, toolUse.output()));
        }
      }
    }
    joiner.add(makeUserMessage(testPrompt));
    return joiner.toString();
  }

  /**
   * Creates a stream-json message with the common envelope structure.
   *
   * @param envelopeType the type field for the outer envelope ("user" or "assistant")
   * @param role the role field for the inner message ("user" or "assistant")
   * @param contentBlock the content block to include in the message
   * @return the compact JSON string
   */
  private String buildMessage(String envelopeType, String role, ObjectNode contentBlock)
  {
    ObjectNode message = scope.getJsonMapper().createObjectNode();
    message.put("type", envelopeType);

    ObjectNode msg = scope.getJsonMapper().createObjectNode();
    msg.put("role", role);
    msg.set("content", scope.getJsonMapper().createArrayNode().add(contentBlock));
    message.set("message", msg);

    return compactWriter.writeValueAsString(message);
  }

  /**
   * Creates a stream-json user message.
   *
   * @param text the message text
   * @return the JSON message string
   */
  private String makeUserMessage(String text)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "text");
    content.put("text", text);
    return buildMessage("user", "user", content);
  }

  /**
   * Creates a stream-json assistant message with tool_use.
   *
   * @param toolUseId the unique ID for the tool use
   * @param toolName the name of the tool
   * @param toolInput the tool input as a map
   * @return the JSON message string
   */
  private String makeToolUseMessage(String toolUseId, String toolName,
    Map<String, Object> toolInput)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "tool_use");
    content.put("id", toolUseId);
    content.put("name", toolName);
    content.set("input", scope.getJsonMapper().valueToTree(toolInput));
    return buildMessage("assistant", "assistant", content);
  }

  /**
   * Creates a stream-json user message with tool_result.
   *
   * @param toolUseId the ID from the tool_use message
   * @param toolOutput the tool output content
   * @return the JSON message string
   */
  private String makeToolResultMessage(String toolUseId, String toolOutput)
  {
    ObjectNode content = scope.getJsonMapper().createObjectNode();
    content.put("type", "tool_result");
    content.put("tool_use_id", toolUseId);
    content.put("content", toolOutput);
    return buildMessage("user", "user", content);
  }

  /**
   * Parses stream-json output to extract assistant text blocks and tool uses.
   *
   * @param output the raw output from claude CLI
   * @return the parsed output
   * @throws NullPointerException if {@code output} is null
   */
  public ParsedOutput parseOutput(String output)
  {
    requireThat(output, "output").isNotNull();
    List<String> texts = new ArrayList<>();
    List<String> toolUses = new ArrayList<>();

    for (String line : output.split("\n"))
    {
      String trimmed = line.strip();
      if (trimmed.isEmpty())
        continue;

      try
      {
        JsonNode event = scope.getJsonMapper().readTree(trimmed);
        String type = event.path("type").asString("");

        if (type.equals("assistant"))
        {
          JsonNode content = event.path("message").path("content");
          if (content.isArray())
          {
            for (JsonNode block : content)
            {
              String blockType = block.path("type").asString("");
              if (blockType.equals("text"))
                texts.add(block.path("text").asString(""));
              else if (blockType.equals("tool_use"))
                toolUses.add(block.path("name").asString(""));
            }
          }
        }
        else if (type.equals("result"))
        {
          String result = event.path("result").asString("");
          if (!result.isEmpty())
            texts.add(result);
        }
      }
      catch (Exception _)
      {
        // Skip malformed JSON lines (e.g., error messages from the CLI)
      }
    }

    return new ParsedOutput(texts, toolUses);
  }

  /**
   * Evaluates criteria by checking whether items are present or absent in a searchable collection,
   * and adds results to the checks map.
   *
   * @param items the list of items to check
   * @param keyPrefix the prefix for check keys (e.g., "contains" or "uses_tool")
   * @param expectPresent true if items should be present, false if they should be absent
   * @param isPresent a predicate that tests whether an item is found
   * @param checks the map to add check results to
   */
  private static void evaluateCriteria(List<String> items, String keyPrefix,
    boolean expectPresent, Predicate<String> isPresent, Map<String, Boolean> checks)
  {
    for (String item : items)
    {
      String key = keyPrefix + ":" + item;
      boolean found = isPresent.test(item);
      if (expectPresent)
        checks.put(key, found);
      else
        checks.put(key, !found);
    }
  }

  /**
   * Evaluates output against success criteria.
   *
   * @param texts the text outputs
   * @param toolUses the tool uses
   * @param criteria the success criteria map
   * @return the evaluation result
   * @throws NullPointerException if {@code texts}, {@code toolUses}, or {@code criteria} are null
   */
  public EvaluationResult evaluateOutput(List<String> texts, List<String> toolUses,
    Map<String, Object> criteria)
  {
    requireThat(texts, "texts").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();
    requireThat(criteria, "criteria").isNotNull();
    String fullText = String.join("\n", texts);
    String lowerText = fullText.toLowerCase(Locale.ROOT);

    Map<String, Boolean> checks = new HashMap<>();

    @SuppressWarnings("unchecked")
    List<String> mustContain = (List<String>) criteria.get("must_contain");
    if (mustContain != null)
      evaluateCriteria(mustContain, "contains", true,
        term -> lowerText.contains(term.toLowerCase(Locale.ROOT)), checks);

    @SuppressWarnings("unchecked")
    List<String> mustNotContain = (List<String>) criteria.get("must_not_contain");
    if (mustNotContain != null)
      evaluateCriteria(mustNotContain, "not_contains", false,
        term -> lowerText.contains(term.toLowerCase(Locale.ROOT)), checks);

    @SuppressWarnings("unchecked")
    List<String> mustUseTools = (List<String>) criteria.get("must_use_tools");
    if (mustUseTools != null)
      evaluateCriteria(mustUseTools, "uses_tool", true, toolUses::contains, checks);

    @SuppressWarnings("unchecked")
    List<String> mustNotUseTools = (List<String>) criteria.get("must_not_use_tools");
    if (mustNotUseTools != null)
      evaluateCriteria(mustNotUseTools, "not_uses_tool", false, toolUses::contains, checks);

    boolean allPass = checks.isEmpty() || checks.values().stream().allMatch(v -> v);
    return new EvaluationResult(allPass, checks);
  }

  /**
   * Prints a summary table of all configuration results.
   *
   * @param allResults the map of configuration results
   */
  private void printSummaryTable(Map<String, ConfigResult> allResults)
  {
    System.out.println();
    System.out.println("=".repeat(90));
    System.out.printf("%-40s %6s %6s%n", "Config", "Pass", "Rate");
    System.out.println("-".repeat(54));

    for (Map.Entry<String, ConfigResult> entry : allResults.entrySet())
    {
      ConfigResult r = entry.getValue();
      System.out.printf("%-40s %3d/%-3d %4d%%%n", entry.getKey(), r.passes(), r.trials(),
        r.rate());
    }

    System.out.flush();
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   * @throws IOException if operations fail
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      System.out.println("""
        Usage: empirical-test-runner --config <config.json> [OPTIONS]

        Options:
          --config <path>     Path to test config JSON file (required)
          --trials <N>        Number of trials per config (default: 5)
          --model <name>      Model to test with: haiku|sonnet|opus (default: haiku)
          --cwd <path>        Working directory for claude CLI (default: /workspace)
          --output <path>     Path to write JSON results (optional)

        Config JSON fields:
          target_description  Description of expected behavior
          success_criteria    Object with must_contain, must_not_contain, must_use_tools, must_not_use_tools
          priming_messages    Array of messages to send before test prompt (simulates prior turns)
          configs             Object mapping config names to prompt text

        Examples:
          empirical-test-runner --config /tmp/test.json --trials 10 --model sonnet
          empirical-test-runner --config test.json --output results.json""");
      return;
    }

    Path configPath = null;
    int trials = 5;
    String model = "haiku";
    Path cwd = Path.of("/workspace");
    Path outputPath = null;

    for (int i = 0; i < args.length; ++i)
    {
      if (i + 1 >= args.length)
        continue;
      switch (args[i])
      {
        case "--config" ->
        {
          configPath = Path.of(args[i + 1]);
          ++i;
        }
        case "--trials" ->
        {
          trials = Integer.parseInt(args[i + 1]);
          ++i;
        }
        case "--model" ->
        {
          model = args[i + 1];
          ++i;
        }
        case "--cwd" ->
        {
          cwd = Path.of(args[i + 1]);
          ++i;
        }
        case "--output" ->
        {
          outputPath = Path.of(args[i + 1]);
          ++i;
        }
        default ->
        {
          // Ignore unknown arguments
        }
      }
    }

    if (configPath == null)
    {
      System.err.println("ERROR: --config argument is required");
      System.exit(1);
    }

    try (JvmScope scope = new MainJvmScope())
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      int exitCode = runner.runTests(configPath, trials, model, cwd, outputPath);
      System.exit(exitCode);
    }
  }

  /**
   * Result of executing the claude CLI process.
   *
   * @param output the process output
   * @param elapsed the elapsed time in seconds
   * @param error the error message, or empty string if none
   */
  private record ProcessResult(String output, long elapsed, String error)
  {
  }

  /**
   * Parsed output containing text blocks and tool uses.
   *
   * @param texts the list of text outputs
   * @param toolUses the list of tool use names
   */
  public record ParsedOutput(List<String> texts, List<String> toolUses)
  {
  }

  /**
   * Result of evaluating output against success criteria.
   *
   * @param pass whether all checks passed
   * @param checks the map of check name to result
   */
  public record EvaluationResult(boolean pass, Map<String, Boolean> checks)
  {
  }

  /**
   * Result of a single trial.
   *
   * @param pass whether the trial passed
   * @param checks the map of check results
   * @param elapsed the elapsed time in seconds
   * @param outputPreview a preview of the output
   * @param toolsUsed the list of tools used
   * @param error the error message if any
   */
  private record TrialResult(boolean pass, Map<String, Boolean> checks, long elapsed,
    String outputPreview, List<String> toolsUsed, String error)
  {
  }

  /**
   * Result of running all trials for a configuration.
   *
   * @param name the configuration name
   * @param trials the number of trials
   * @param passes the number of passing trials
   * @param rate the pass rate as a percentage
   * @param results the list of trial results
   */
  private record ConfigResult(String name, int trials, int passes, int rate,
    List<TrialResult> results)
  {
  }
}
