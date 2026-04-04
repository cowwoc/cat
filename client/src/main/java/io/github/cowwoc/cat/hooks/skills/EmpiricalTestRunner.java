/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.SharedSecrets;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.skills.ClaudeRunner.ParsedOutput;
import io.github.cowwoc.cat.hooks.skills.ClaudeRunner.ProcessResult;
import io.github.cowwoc.cat.hooks.skills.ClaudeRunner.TurnOutput;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import tools.jackson.core.type.TypeReference;
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
  /**
   * Maximum number of trials allowed per configuration.
   */
  private static final int MAX_TRIALS = 1000;
  /**
   * Maximum characters in an output preview string.
   */
  private static final int MAX_PREVIEW_CHARS = 200;
  /**
   * Maximum number of tool uses to retain per trial.
   */
  private static final int MAX_TOOL_USES_DISPLAYED = 5;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };
  /**
   * Comparator that orders {@link Severity} values HIGH first, then MEDIUM, then LOW.
   */
  private static final Comparator<Severity> SEVERITY_COMPARATOR =
    Comparator.comparingInt((Severity s) -> switch (s)
    {
      case HIGH -> 0;
      case MEDIUM -> 1;
      case LOW -> 2;
    });

  private final ClaudeTool scope;
  private final Path sessionsPath;
  private final ClaudeRunner claudeRunner;

  static
  {
    SharedSecrets.setEmpiricalTestRunnerAccess(new SharedSecrets.EmpiricalTestRunnerAccess()
    {
      @Override
      public Path createTestWorktree(Path baseRepo) throws IOException
      {
        return EmpiricalTestRunner.createTestWorktree(baseRepo);
      }

      @Override
      public void removeTestWorktree(Path baseRepo, Path worktreePath)
      {
        EmpiricalTestRunner.removeTestWorktree(baseRepo, worktreePath);
      }
    });
  }

  /**
   * Creates a new empirical test runner.
   *
   * @param scope the Claude tool scope providing JSON mapper and display utilities
   * @throws NullPointerException if {@code scope} is null
   */
  public EmpiricalTestRunner(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.sessionsPath = scope.getClaudeSessionsPath();
    this.claudeRunner = new ClaudeRunner(scope);
  }

  /**
   * Runs all tests defined in the config file.
   *
   * @param configPath path to the test config JSON file
   * @param trials number of trials per configuration
   * @param model the model to test with (haiku, sonnet, opus)
   * @param cwd working directory for claude CLI
   * @param outputPath optional path to write full JSON results
   * @return the overall exit code (1 if any config achieved less than 100% pass rate, 0 otherwise)
   * @throws NullPointerException if {@code configPath}, {@code model}, or {@code cwd} are null
   * @throws IOException if config cannot be read or output cannot be written
   */
  public int runTests(Path configPath, int trials, String model, Path cwd, Path outputPath)
    throws IOException
  {
    requireThat(configPath, "configPath").isNotNull();
    requireThat(model, "model").isNotBlank();
    if (!ClaudeRunner.ALLOWED_MODELS.contains(model))
    {
      throw new IllegalArgumentException("Invalid model '" + model +
        "'. Valid values: " + ClaudeRunner.ALLOWED_MODELS);
    }
    requireThat(cwd, "cwd").isNotNull();
    requireThat(trials, "trials").isBetween(1, true, MAX_TRIALS, true);

    TestConfig tc = loadTestConfig(configPath);

    if (tc.configs().isEmpty())
      throw new IllegalArgumentException("Config file '" + configPath + "' has no entries in 'configs'.");

    System.out.println("Empirical Compliance Test: " + tc.targetDescription());
    System.out.println("Model: " + model + ", Trials: " + trials + ", Configs: " + tc.configs().size());
    System.out.println("=".repeat(90));

    Map<String, ConfigResult> allResults = new HashMap<>();
    for (Map.Entry<String, Object> entry : tc.configs().entrySet())
    {
      String configName = entry.getKey();
      Object configValue = entry.getValue();

      System.out.println();
      System.out.println(configName + ":");
      System.out.flush();

      if (!(configValue instanceof Map<?, ?>))
      {
        throw new IllegalArgumentException("Config '" + configName +
          "' has unsupported value type: " + configValue.getClass().getName() +
          ". Expected Map with 'messages' key.");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> configMap = (Map<String, Object>) configValue;
      ConfigResult result = runMultiMessageConfig(configName, configMap, tc.primingMessages(),
        tc.systemReminders(), trials, model, tc.systemPrompt(), cwd);
      allResults.put(configName, result);

      System.out.println("  RESULT: " + result.passes() + "/" + result.trials() + " (" +
        result.rate() + "%)");
      System.out.flush();
    }

    printSummaryTable(allResults);

    if (outputPath != null)
    {
      ObjectNode output = scope.getJsonMapper().createObjectNode();
      output.put("target", tc.targetDescription());
      output.put("model", model);
      output.put("trials", trials);
      output.set("results", scope.getJsonMapper().valueToTree(allResults));

      try
      {
        Files.writeString(outputPath, scope.getJsonMapper().writeValueAsString(output),
          StandardCharsets.UTF_8);
      }
      catch (IOException e)
      {
        throw new IOException("Failed to write JSON results to: " + outputPath, e);
      }
      System.out.println();
      System.out.println("Full results written to: " + outputPath);
    }

    boolean anyFailed = allResults.values().stream().
      anyMatch(r -> r.rate() < 100);
    if (anyFailed)
      return 1;

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
   * Formats the trial output preview for a failed trial result.
   * <p>
   * Returns a non-empty string with details about which messages and checks failed, or an error
   * description if the trial encountered an error. Returns an empty string for passing trials.
   *
   * @param result the trial result to format
   * @return the formatted preview string, possibly empty
   */
  private static String formatTrialPreview(TrialResult result)
  {
    if (!result.pass() && !result.messageEvaluations().isEmpty())
    {
      StringJoiner failedMsgs = new StringJoiner(", ");
      for (MessageEvaluation eval : result.messageEvaluations())
      {
        if (!eval.pass())
        {
          StringJoiner failedChecks = new StringJoiner(", ");
          for (Map.Entry<String, Boolean> check : eval.checks().entrySet())
          {
            if (!check.getValue())
              failedChecks.add(check.getKey());
          }
          failedMsgs.add("msg" + eval.messageIndex() + ": " + failedChecks);
        }
      }
      return " [" + failedMsgs + "]";
    }
    if (!result.error().isEmpty())
      return " [ERROR: " + result.error() + "]";
    return "";
  }

  /**
   * Runs all trials for a multi-message configuration in parallel with fail-fast behavior.
   * <p>
   * Trials are executed concurrently using virtual threads. If any trial fails, remaining
   * trials are cancelled immediately (fail-fast). The configuration is considered successful only
   * when all trials pass.
   *
   * @param name the configuration name
   * @param configMap the configuration map containing a "messages" array
   * @param primingMessages list of priming messages to send before the test messages
   * @param systemReminders list of system reminder strings to inject into each test message
   * @param trials number of trials to run
   * @param model the model name
   * @param systemPrompt the system prompt to append via CLI flag, or empty string for none
   * @param cwd the working directory
   * @return the configuration result
   */
  private ConfigResult runMultiMessageConfig(String name, Map<String, Object> configMap,
    List<PrimingMessage> primingMessages, List<String> systemReminders, int trials,
    String model, String systemPrompt, Path cwd)
  {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rawMessages =
      (List<Map<String, Object>>) configMap.get("messages");
    if (rawMessages == null)
    {
      throw new IllegalArgumentException("Config '" + name +
        "' is a Map but missing required 'messages' key.");
    }

    List<TestMessage> messages = new ArrayList<>();
    for (Map<String, Object> rawMsg : rawMessages)
    {
      String prompt = (String) rawMsg.get("prompt");
      if (prompt == null)
      {
        throw new IllegalArgumentException("Config '" + name +
          "': message is missing required 'prompt' field.");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> msgCriteria = (Map<String, Object>) rawMsg.getOrDefault(
        "success_criteria", new HashMap<>());
      messages.add(new TestMessage(prompt, msgCriteria));
    }

    List<TrialResult> results = Collections.synchronizedList(new ArrayList<>());
    AtomicBoolean failFast = new AtomicBoolean(false);

    // A new executor is created per config so that its lifecycle matches exactly the config's trial
    // batch. This keeps shutdown and failure isolation clean: one config timing out does not cancel
    // trials from another config, and the try-with-resources guarantees the executor is shut down
    // before results are aggregated.
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
    {
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      for (int i = 0; i < trials; ++i)
      {
        int trialIndex = i;
        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
        {
          if (failFast.get())
            return;

          Path trialCwd;
          try
          {
            trialCwd = createTestWorktree(cwd);
          }
          catch (IOException e)
          {
            TrialResult errorResult = new TrialResult(false, new HashMap<>(), 0,
              "", new ArrayList<>(), "worktree: " + e.getMessage(),
              List.of(), List.of());
            results.add(errorResult);
            failFast.set(true);
            System.out.println("  t" + (trialIndex + 1) + " FAIL [ERROR: worktree: " +
              e.getMessage() + "]");
            return;
          }

          try
          {
            TrialResult result = runMultiMessageTrial(primingMessages, systemReminders, messages,
              model, systemPrompt, trialCwd);
            results.add(result);

            if (!result.pass())
              failFast.set(true);

            String status;
            if (result.pass())
              status = "PASS";
            else
              status = "FAIL";

            String preview = formatTrialPreview(result);
            System.out.println("  t" + (trialIndex + 1) + " " + status + " (" +
              result.elapsed() + "s)" + preview);
          }
          finally
          {
            removeTestWorktree(cwd, trialCwd);
          }
        }, executor);
        futures.add(future);
      }

      for (CompletableFuture<Void> future : futures)
        future.join();
    }

    int passes = (int) results.stream().filter(TrialResult::pass).count();
    int actualTrials = results.size();
    int rate = calculateRate(passes, actualTrials);
    return new ConfigResult(name, actualTrials, passes, rate, List.copyOf(results));
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
   * Collects all session files (main + subagent) for a given session ID.
   * <p>
   * Session files follow this structure:
   * <pre>
   * sessionsPath/
   *   {sessionId}.jsonl              — main agent session
   *   {sessionId}/subagents/
   *     agent-{agentId}.jsonl        — subagent sessions
   * </pre>
   *
   * @param sessionsPath the path to the sessions directory
   * @param sessionId the session ID extracted from stream-json output
   * @return the list of session file paths (main first, then subagents), or empty list if
   *         sessionId is empty
   * @throws IOException if the subagents directory exists but cannot be read
   */
  private static List<Path> collectSessionFiles(Path sessionsPath, String sessionId) throws IOException
  {
    if (sessionId.isEmpty())
      return List.of();

    List<Path> files = new ArrayList<>();
    Path mainFile = sessionsPath.resolve(sessionId + ".jsonl");
    if (Files.isRegularFile(mainFile))
      files.add(mainFile);

    Path subagentsDir = sessionsPath.resolve(sessionId).resolve("subagents");
    if (Files.isDirectory(subagentsDir))
    {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(subagentsDir, "*.jsonl"))
      {
        for (Path path : stream)
          files.add(path);
      }
    }
    return files;
  }

  /**
   * Runs a single multi-message trial and evaluates each turn against its corresponding
   * message's criteria.
   *
   * @param primingMessages list of priming messages
   * @param systemReminders list of system reminder strings to inject into each test message
   * @param messages the test messages with per-message criteria
   * @param model the model name
   * @param systemPrompt the system prompt to append via CLI flag, or empty string for none
   * @param cwd the working directory
   * @return the trial result
   */
  private TrialResult runMultiMessageTrial(List<PrimingMessage> primingMessages,
    List<String> systemReminders, List<TestMessage> messages, String model,
    String systemPrompt, Path cwd)
  {
    List<String> command = claudeRunner.buildCommand(model, systemPrompt);

    List<String> prompts = new ArrayList<>();
    for (TestMessage msg : messages)
      prompts.add(msg.prompt());
    String input = claudeRunner.buildInput(primingMessages, prompts, systemReminders);

    ProcessResult processResult = claudeRunner.executeProcess(command, input, cwd);

    if (!processResult.error().isEmpty())
      return new TrialResult(false, new HashMap<>(), processResult.elapsed(), "",
        new ArrayList<>(), processResult.error(), List.of(), List.of());

    ParsedOutput parsed = processResult.parsed();

    // Calculate number of priming turns to skip.
    // Each UserMessage produces 1 turn, each ToolUse produces 1 turn.
    int primingTurnCount = primingMessages.size();

    List<TurnOutput> testTurns;
    if (parsed.turns().size() > primingTurnCount)
      testTurns = parsed.turns().subList(primingTurnCount, parsed.turns().size());
    else
      testTurns = List.of();

    boolean allPass = true;
    Map<String, Boolean> combinedChecks = new HashMap<>();
    List<MessageEvaluation> evaluations = new ArrayList<>();

    for (int i = 0; i < messages.size(); ++i)
    {
      TestMessage msg = messages.get(i);

      // Aggregate all turns that belong to this message.
      // For single-message configs, all test turns belong to message 0.
      // For multi-message configs, turns are split evenly across messages.
      List<String> aggregatedTexts = new ArrayList<>();
      List<String> aggregatedToolUses = new ArrayList<>();
      List<String> aggregatedWriteContents = new ArrayList<>();
      if (messages.size() == 1)
      {
        // Single message: all turns belong to it
        for (TurnOutput turn : testTurns)
        {
          aggregatedTexts.addAll(turn.texts());
          aggregatedToolUses.addAll(turn.toolUses());
          aggregatedWriteContents.addAll(turn.writeContents());
        }
      }
      else if (i < testTurns.size())
      {
        // Multi-message: map turn i to message i (original behavior)
        TurnOutput turn = testTurns.get(i);
        aggregatedTexts.addAll(turn.texts());
        aggregatedToolUses.addAll(turn.toolUses());
        aggregatedWriteContents.addAll(turn.writeContents());
      }

      EvaluationResult evaluation = evaluateOutput(aggregatedTexts, aggregatedToolUses,
        aggregatedWriteContents, msg.criteria());

      String turnPreview = String.join("\n", aggregatedTexts);
      String truncatedTurnPreview = truncatePreview(turnPreview, MAX_PREVIEW_CHARS).replace("\n", "\\n");

      evaluations.add(new MessageEvaluation(i, evaluation.pass(), evaluation.checks(),
        truncatedTurnPreview));

      if (!evaluation.pass())
        allPass = false;

      for (Map.Entry<String, Boolean> check : evaluation.checks().entrySet())
        combinedChecks.put("msg" + i + ":" + check.getKey(), check.getValue());
    }

    List<String> toolsUsed;
    if (parsed.toolUses().size() > MAX_TOOL_USES_DISPLAYED)
      toolsUsed = parsed.toolUses().subList(0, MAX_TOOL_USES_DISPLAYED);
    else
      toolsUsed = parsed.toolUses();

    String overallPreview;
    if (!evaluations.isEmpty())
      overallPreview = evaluations.getFirst().outputPreview();
    else
      overallPreview = "";

    List<Path> sessionFiles;
    try
    {
      sessionFiles = collectSessionFiles(sessionsPath, parsed.sessionId());
    }
    catch (IOException _)
    {
      sessionFiles = List.of();
    }

    return new TrialResult(allPass, combinedChecks, processResult.elapsed(),
      overallPreview, toolsUsed, "", evaluations, sessionFiles);
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
   * @param texts         the text outputs
   * @param toolUses      the tool uses
   * @param writeContents the content strings passed to Write tool calls
   * @param criteria      the success criteria map
   * @return the evaluation result
   * @throws NullPointerException if {@code texts}, {@code toolUses}, {@code writeContents}, or
   *                              {@code criteria} are null
   */
  public EvaluationResult evaluateOutput(List<String> texts, List<String> toolUses,
    List<String> writeContents, Map<String, Object> criteria)
  {
    requireThat(texts, "texts").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();
    requireThat(writeContents, "writeContents").isNotNull();
    requireThat(criteria, "criteria").isNotNull();
    String fullText = String.join("\n", texts);
    String lowerText = fullText.toLowerCase(Locale.ROOT);
    String fullWriteText = String.join("\n", writeContents);
    String lowerWriteText = fullWriteText.toLowerCase(Locale.ROOT);

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

    @SuppressWarnings("unchecked")
    List<String> writeMustContain = (List<String>) criteria.get("write_must_contain");
    if (writeMustContain != null)
      evaluateCriteria(writeMustContain, "write_contains", true,
        term -> lowerWriteText.contains(term.toLowerCase(Locale.ROOT)), checks);

    @SuppressWarnings("unchecked")
    List<String> writeMustNotContain = (List<String>) criteria.get("write_must_not_contain");
    if (writeMustNotContain != null)
      evaluateCriteria(writeMustNotContain, "write_not_contains", false,
        term -> lowerWriteText.contains(term.toLowerCase(Locale.ROOT)), checks);

    boolean allPass = checks.isEmpty() || checks.values().stream().allMatch(v -> v);
    return new EvaluationResult(allPass, checks);
  }

  /**
   * Evaluates a list of typed assertions against agent output.
   * <p>
   * Supports the following assertion types:
   * <ul>
   *   <li>{@code deterministic} — evaluates string-match assertions against the text output</li>
   *   <li>{@code semantic} — skipped (evaluated by Claude as a judge, not programmatically)</li>
   *   <li>{@code tool_use} — checks whether the named tool was invoked</li>
   * </ul>
   *
   * @param assertions list of assertion maps, each containing at minimum {@code assertion_id},
   *                   {@code type}, and {@code expected}
   * @param texts      the text outputs from the agent
   * @param toolUses   the tool use names from the agent
   * @return the evaluation result
   * @throws NullPointerException     if {@code assertions}, {@code texts}, or {@code toolUses} are null
   * @throws IllegalArgumentException if an assertion has an unknown {@code type} or an unknown
   *                                  {@code method} within a {@code deterministic} assertion
   */
  public EvaluationResult evaluateAssertions(List<Map<String, Object>> assertions,
    List<String> texts, List<String> toolUses)
  {
    requireThat(assertions, "assertions").isNotNull();
    requireThat(texts, "texts").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();
    String fullText = String.join("\n", texts);
    String lowerText = fullText.toLowerCase(Locale.ROOT);

    Map<String, Boolean> checks = new HashMap<>();
    for (Map<String, Object> assertion : assertions)
    {
      String assertionId = (String) assertion.get("assertion_id");
      String type = (String) assertion.get("type");
      boolean expected = Boolean.TRUE.equals(assertion.get("expected"));

      switch (type)
      {
        case "deterministic" ->
        {
          String method = (String) assertion.get("method");
          if (method == null || !method.equals("string_match"))
          {
            throw new IllegalArgumentException(
              "assertion '" + assertionId + "': unknown deterministic method: '" + method +
              "'. Supported methods: [string_match]");
          }
          String pattern = (String) assertion.get("pattern");
          boolean found = lowerText.contains(pattern.toLowerCase(Locale.ROOT));
          checks.put(assertionId, found == expected);
        }
        case "semantic" ->
        {
          // Semantic assertions are evaluated by Claude as a judge, not programmatically.
        }
        case "tool_use" ->
        {
          String tool = (String) assertion.get("tool");
          boolean found = toolUses.contains(tool);
          checks.put(assertionId, found == expected);
        }
        default ->
          throw new IllegalArgumentException(
            "assertion '" + assertionId + "': unknown type: '" + type +
            "'. Supported types: [deterministic, semantic, tool_use]");
      }
    }

    boolean allPass = checks.isEmpty() || checks.values().stream().allMatch(v -> v);
    return new EvaluationResult(allPass, checks);
  }

  /**
   * Produces a structured grading report for a single message evaluation.
   * <p>
   * For each criterion in the success criteria map, extracts a {@link CriterionGrade} containing
   * the pass/fail result and a relevant quote from the output. Criteria metadata ({@code description},
   * {@code reason}, {@code severity}) is read from an optional {@code _metadata} sub-map in the
   * criteria map. Grades are sorted by severity (HIGH first) then by criterion key.
   *
   * @param messageIndex the 0-based message index
   * @param texts        the text outputs for this message
   * @param toolUses     the tool uses for this message
   * @param criteria     the success criteria map (may contain a {@code _metadata} entry)
   * @return the grading report
   * @throws NullPointerException if {@code texts}, {@code toolUses}, or {@code criteria} are null
   */
  public GradingReport gradeOutput(int messageIndex, List<String> texts, List<String> toolUses,
    Map<String, Object> criteria)
  {
    requireThat(texts, "texts").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();
    requireThat(criteria, "criteria").isNotNull();

    String fullText = String.join("\n", texts);
    String lowerText = fullText.toLowerCase(Locale.ROOT);

    @SuppressWarnings("unchecked")
    Map<String, Object> metadataMap =
      (Map<String, Object>) criteria.getOrDefault("_metadata", new HashMap<>());

    List<CriterionGrade> grades = new ArrayList<>();

    @SuppressWarnings("unchecked")
    List<String> mustContain = (List<String>) criteria.get("must_contain");
    if (mustContain != null)
    {
      for (String term : mustContain)
      {
        String key = "contains:" + term;
        CriterionMetadata meta = CriterionMetadata.fromRaw(key, metadataMap.get(key));
        boolean found = lowerText.contains(term.toLowerCase(Locale.ROOT));
        String quote;
        if (found)
          quote = extractQuote(fullText, term, MAX_PREVIEW_CHARS);
        else
          quote = "";
        String expected = "Output contains: \"" + term + "\"";
        String actual;
        if (found)
          actual = "Term found in output";
        else
          actual = "Term not found in output";
        grades.add(new CriterionGrade(key, meta, found, quote, expected, actual));
      }
    }

    @SuppressWarnings("unchecked")
    List<String> mustNotContain = (List<String>) criteria.get("must_not_contain");
    if (mustNotContain != null)
    {
      for (String term : mustNotContain)
      {
        String key = "not_contains:" + term;
        CriterionMetadata meta = CriterionMetadata.fromRaw(key, metadataMap.get(key));
        boolean found = lowerText.contains(term.toLowerCase(Locale.ROOT));
        boolean pass = !found;
        String quote;
        if (found)
          quote = extractQuote(fullText, term, MAX_PREVIEW_CHARS);
        else
          quote = "";
        String expected = "Output does not contain: \"" + term + "\"";
        String actual;
        if (found)
          actual = "Term found in output";
        else
          actual = "Term not found in output";
        grades.add(new CriterionGrade(key, meta, pass, quote, expected, actual));
      }
    }

    @SuppressWarnings("unchecked")
    List<String> mustUseTools = (List<String>) criteria.get("must_use_tools");
    if (mustUseTools != null)
    {
      for (String tool : mustUseTools)
      {
        String key = "uses_tool:" + tool;
        CriterionMetadata meta = CriterionMetadata.fromRaw(key, metadataMap.get(key));
        boolean found = toolUses.contains(tool);
        String expected = "Tool used: " + tool;
        String actual;
        if (found)
          actual = "Tool was invoked";
        else
          actual = "Tool not invoked";
        grades.add(new CriterionGrade(key, meta, found, "", expected, actual));
      }
    }

    @SuppressWarnings("unchecked")
    List<String> mustNotUseTools = (List<String>) criteria.get("must_not_use_tools");
    if (mustNotUseTools != null)
    {
      for (String tool : mustNotUseTools)
      {
        String key = "not_uses_tool:" + tool;
        CriterionMetadata meta = CriterionMetadata.fromRaw(key, metadataMap.get(key));
        boolean found = toolUses.contains(tool);
        boolean pass = !found;
        String expected = "Tool not used: " + tool;
        String actual;
        if (found)
          actual = "Tool was invoked: " + tool;
        else
          actual = "Tool not invoked";
        grades.add(new CriterionGrade(key, meta, pass, "", expected, actual));
      }
    }

    // Sort by severity (HIGH first, then MEDIUM, then LOW), then by key for determinism
    grades.sort(Comparator.comparing((CriterionGrade g) -> g.metadata().severity(), SEVERITY_COMPARATOR).
      thenComparing(CriterionGrade::criterionKey));

    boolean allPass = grades.isEmpty() || grades.stream().allMatch(CriterionGrade::pass);
    return new GradingReport(messageIndex, List.copyOf(grades), allPass);
  }

  /**
   * Extracts a short quote around the first occurrence of {@code term} in {@code text}.
   *
   * @param text      the full text to search in
   * @param term      the term to find (case-insensitive)
   * @param maxLength the maximum length of the returned quote
   * @return the excerpt, or empty string if not found
   */
  public static String extractQuote(String text, String term, int maxLength)
  {
    int idx = text.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
    if (idx < 0)
      return "";
    int start = Math.max(0, idx - 40);
    int end = Math.min(text.length(), idx + term.length() + 40);
    String excerpt = text.substring(start, end);
    if (excerpt.length() > maxLength)
      excerpt = excerpt.substring(0, maxLength);
    return excerpt;
  }

  /**
   * Performs post-hoc analysis on a failed trial to identify instruction violations.
   * <p>
   * Analyzes the output texts against the criteria to find specific violations, assigns an
   * overall adherence score, categorizes issues, and generates improvement suggestions sorted by
   * severity.
   *
   * @param messageIndex the 0-based index of the message being analyzed
   * @param texts        the text outputs from the failed trial
   * @param toolUses     the tool uses from the failed trial
   * @param criteria     the success criteria map
   * @return the post-hoc analysis report
   * @throws NullPointerException if {@code texts}, {@code toolUses}, or {@code criteria} are null
   */
  public PostHocAnalysis analyzeFailedTrial(int messageIndex, List<String> texts,
    List<String> toolUses, Map<String, Object> criteria)
  {
    requireThat(texts, "texts").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();
    requireThat(criteria, "criteria").isNotNull();

    GradingReport report = gradeOutput(messageIndex, texts, toolUses, criteria);
    List<InstructionViolation> violations = new ArrayList<>();

    for (CriterionGrade grade : report.grades())
    {
      if (grade.pass())
        continue;

      String key = grade.criterionKey();
      String category = inferCategory(key);
      String expected;
      String actual;
      String quote = grade.quote();

      if (key.startsWith("contains:"))
      {
        String term = key.substring("contains:".length());
        expected = "Output contains: \"" + term + "\"";
        actual = "Term not found in output";
      }
      else if (key.startsWith("not_contains:"))
      {
        String term = key.substring("not_contains:".length());
        expected = "Output does not contain: \"" + term + "\"";
        if (quote.isEmpty())
          actual = "Term found in output";
        else
          actual = "Term found in output: \"" + quote + "\"";
      }
      else if (key.startsWith("uses_tool:"))
      {
        String tool = key.substring("uses_tool:".length());
        expected = "Tool used: " + tool;
        String toolList;
        if (toolUses.isEmpty())
          toolList = "none";
        else
          toolList = String.join(", ", toolUses);
        actual = "Tool not invoked. Tools used: " + toolList;
      }
      else if (key.startsWith("not_uses_tool:"))
      {
        String tool = key.substring("not_uses_tool:".length());
        expected = "Tool not used: " + tool;
        actual = "Tool was invoked: " + tool;
      }
      else
      {
        expected = "Criterion satisfied: " + key;
        actual = "Criterion not satisfied";
      }

      violations.add(new InstructionViolation(category, quote, expected, actual,
        grade.metadata().severity()));
    }

    // Sort violations by severity (HIGH first)
    violations.sort(Comparator.comparing(InstructionViolation::severity, SEVERITY_COMPARATOR));

    // Compute adherence score (1-10 scale)
    int totalCriteria = report.grades().size();
    int failedCriteria = (int) report.grades().stream().filter(g -> !g.pass()).count();
    int adherenceScore;
    if (totalCriteria == 0)
      adherenceScore = 10;
    else
    {
      double passRate = (double) (totalCriteria - failedCriteria) / totalCriteria;
      adherenceScore = Math.max(1, (int) Math.round(passRate * 10));
    }

    // Generate improvement suggestions sorted by severity
    List<String> suggestions = new ArrayList<>();
    for (InstructionViolation v : violations)
    {
      String prefix = "[" + v.severity() + "] ";
      suggestions.add(prefix + "Fix: " + v.expected() + " — " + v.actual());
    }

    return new PostHocAnalysis(adherenceScore, List.copyOf(violations), List.copyOf(suggestions));
  }

  /**
   * Infers the violation category from the criterion key.
   *
   * @param key the criterion key
   * @return the category string
   */
  private static String inferCategory(String key)
  {
    if (key.startsWith("uses_tool:") || key.startsWith("not_uses_tool:"))
      return "tool_usage";
    if (key.startsWith("not_contains:") && key.toLowerCase(Locale.ROOT).contains("error"))
      return "error_handling";
    return "instructions";
  }

  /**
   * Runs a blind comparison between two system prompts (candidate vs. baseline) using the same
   * test configuration.
   * <p>
   * Both prompts are run for the specified number of trials. The winner is determined by assertion
   * pass rate (primary) then total rubric score (secondary).
   *
   * @param configPath      path to the test config JSON file
   * @param trials          number of trials per configuration
   * @param model           the model to test with
   * @param cwd             working directory for claude CLI
   * @param candidatePrompt the candidate system prompt being evaluated
   * @param baselinePrompt  the baseline system prompt to compare against
   * @return the comparison result
   * @throws NullPointerException if any parameter is null
   * @throws IOException          if the config cannot be read
   */
  public ComparisonResult runBlindComparison(Path configPath, int trials, String model, Path cwd,
    String candidatePrompt, String baselinePrompt) throws IOException
  {
    requireThat(configPath, "configPath").isNotNull();
    requireThat(model, "model").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();
    requireThat(candidatePrompt, "candidatePrompt").isNotNull();
    requireThat(baselinePrompt, "baselinePrompt").isNotNull();
    requireThat(trials, "trials").isBetween(1, true, MAX_TRIALS, true);

    TestConfig tc = loadTestConfig(configPath);

    if (tc.configs().isEmpty())
      throw new IllegalArgumentException("Config file '" + configPath + "' has no entries in 'configs'.");

    // Use the first config entry as the benchmark
    Map.Entry<String, Object> firstEntry = tc.configs().entrySet().iterator().next();
    @SuppressWarnings("unchecked")
    Map<String, Object> configMap = (Map<String, Object>) firstEntry.getValue();

    // Run candidate and baseline in parallel
    final Map<String, Object> finalConfigMap = configMap;
    CompletableFuture<ConfigResult> candidateFuture = CompletableFuture.supplyAsync(() ->
      runMultiMessageConfig("candidate", finalConfigMap, tc.primingMessages(),
        tc.systemReminders(), trials, model, candidatePrompt, cwd));
    CompletableFuture<ConfigResult> baselineFuture = CompletableFuture.supplyAsync(() ->
      runMultiMessageConfig("baseline", finalConfigMap, tc.primingMessages(),
        tc.systemReminders(), trials, model, baselinePrompt, cwd));

    ConfigResult candidateResult = candidateFuture.join();
    ConfigResult baselineResult = baselineFuture.join();

    RubricScore candidateRubric = computeRubricScore(candidateResult);
    RubricScore baselineRubric = computeRubricScore(baselineResult);

    String winner;
    String winnerReason;
    if (candidateResult.rate() > baselineResult.rate())
    {
      winner = "candidate";
      winnerReason = "Higher assertion pass rate: " + candidateResult.rate() + "% vs " +
        baselineResult.rate() + "%";
    }
    else if (baselineResult.rate() > candidateResult.rate())
    {
      winner = "baseline";
      winnerReason = "Higher assertion pass rate: " + baselineResult.rate() + "% vs " +
        candidateResult.rate() + "%";
    }
    else if (candidateRubric.total() > baselineRubric.total())
    {
      winner = "candidate";
      winnerReason = "Equal pass rate (" + candidateResult.rate() + "%), higher rubric score: " +
        candidateRubric.total() + " vs " + baselineRubric.total();
    }
    else if (baselineRubric.total() > candidateRubric.total())
    {
      winner = "baseline";
      winnerReason = "Equal pass rate (" + baselineResult.rate() + "%), higher rubric score: " +
        baselineRubric.total() + " vs " + candidateRubric.total();
    }
    else
    {
      winner = "tie";
      winnerReason = "Equal pass rate (" + candidateResult.rate() + "%) and equal rubric score (" +
        candidateRubric.total() + ")";
    }

    return new ComparisonResult(candidateResult, baselineResult, candidateRubric, baselineRubric,
      winner, winnerReason);
  }

  /**
   * Loads and parses the test configuration from a JSON file.
   *
   * @param configPath path to the test config JSON file
   * @return the parsed test configuration
   * @throws NullPointerException if {@code configPath} is null
   * @throws IOException          if the config cannot be read
   */
  private TestConfig loadTestConfig(Path configPath) throws IOException
  {
    requireThat(configPath, "configPath").isNotNull();
    String configJson = Files.readString(configPath);
    Map<String, Object> config = scope.getJsonMapper().readValue(configJson, MAP_TYPE);

    String targetDescription = (String) config.getOrDefault("target_description", "");
    String systemPrompt = (String) config.getOrDefault("system_prompt", "");
    @SuppressWarnings("unchecked")
    List<PrimingMessage> primingMessages = PrimingMessage.fromRawList(
      (List<Object>) config.getOrDefault("priming_messages", new ArrayList<>()));
    @SuppressWarnings("unchecked")
    List<String> systemReminders = (List<String>) config.getOrDefault("system_reminders",
      new ArrayList<>());
    @SuppressWarnings("unchecked")
    Map<String, Object> configs = (Map<String, Object>) config.getOrDefault("configs",
      new HashMap<>());
    return new TestConfig(targetDescription, systemPrompt, primingMessages, systemReminders, configs);
  }

  /**
   * Computes a multi-dimensional rubric score for a configuration result.
   * <p>
   * Scores are derived from trial outcomes:
   * <ul>
   *   <li>Instruction adherence: based on overall pass rate</li>
   *   <li>Output quality: based on pass rate of text-based criteria</li>
   *   <li>Tool usage correctness: based on pass rate of tool-related criteria</li>
   *   <li>Error handling: based on pass rate of not_contains error criteria</li>
   * </ul>
   * Each dimension is scored 1-5. In the absence of relevant criteria, the dimension defaults to 3.
   *
   * @param result the configuration result to score
   * @return the rubric score
   */
  public RubricScore computeRubricScore(ConfigResult result)
  {
    requireThat(result, "result").isNotNull();
    if (result.results().isEmpty())
      return new RubricScore(3, 3, 3, 3);

    // Instruction adherence: overall pass rate scaled to 1-5
    int instructionAdherence = rateToScore(result.rate());

    // Output quality: checks that use text criteria (contains/not_contains)
    int outputQuality = computeDimensionScore(result, key ->
      key.startsWith("contains:") || key.startsWith("not_contains:"));

    // Tool usage correctness: checks involving tool criteria
    int toolUsage = computeDimensionScore(result, key ->
      key.startsWith("uses_tool:") || key.startsWith("not_uses_tool:"));

    // Error handling: checks involving error-related not_contains
    int errorHandling = computeDimensionScore(result, key ->
      key.startsWith("not_contains:") && key.toLowerCase(Locale.ROOT).contains("error"));

    return new RubricScore(instructionAdherence, outputQuality, toolUsage, errorHandling);
  }

  /**
   * Computes a dimension score (1-5) from trials by filtering checks matching the predicate.
   *
   * @param result    the configuration result
   * @param keyFilter predicate to select relevant check keys
   * @return a score from 1 (all fail) to 5 (all pass), or 3 if no relevant checks exist
   */
  private static int computeDimensionScore(ConfigResult result, Predicate<String> keyFilter)
  {
    int totalChecks = 0;
    int passedChecks = 0;
    for (TrialResult trial : result.results())
    {
      for (Map.Entry<String, Boolean> entry : trial.checks().entrySet())
      {
        String checkKey = entry.getKey();
        // Strip the "msgN:" prefix that was added during combined check generation
        String bareKey;
        if (checkKey.contains(":"))
          bareKey = checkKey.substring(checkKey.indexOf(':') + 1);
        else
          bareKey = checkKey;
        if (keyFilter.test(bareKey))
        {
          totalChecks += 1;
          if (entry.getValue())
            passedChecks += 1;
        }
      }
    }
    if (totalChecks == 0)
      return 3;
    int rate = calculateRate(passedChecks, totalChecks);
    return rateToScore(rate);
  }

  /**
   * Converts a pass rate (0-100) to a rubric score (1-5).
   *
   * @param rate the pass rate percentage
   * @return the score from 1 to 5
   */
  public static int rateToScore(int rate)
  {
    if (rate >= 90)
      return 5;
    if (rate >= 70)
      return 4;
    if (rate >= 50)
      return 3;
    if (rate >= 25)
      return 2;
    return 1;
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
   * Creates an isolated git worktree for a single test run.
   * <p>
   * Each test run gets its own isolated working tree so that files written by one run are not
   * visible to parallel runs executing in the same batch.
   *
   * @param baseRepo the base git repository to branch from
   * @return the path of the newly created worktree
   * @throws IOException if the temporary directory cannot be created or the git command fails
   */
  private static Path createTestWorktree(Path baseRepo) throws IOException
  {
    Path worktreePath = Files.createTempDirectory("empirical-trial-");
    // git worktree add requires the destination to not exist yet
    Files.delete(worktreePath);
    ProcessBuilder pb = new ProcessBuilder("git", "-C", baseRepo.toString(),
      "worktree", "add", "--detach", worktreePath.toString());
    pb.redirectErrorStream(true);
    try (Process process = pb.start())
    {
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      try
      {
        int exitCode = process.waitFor();
        if (exitCode != 0)
          throw new IOException("git worktree add failed (exit " + exitCode + "): " + output.strip());
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while creating trial worktree at: " + worktreePath, e);
      }
    }
    return worktreePath;
  }

  /**
   * Removes a test worktree created by {@link #createTestWorktree(Path)}.
   * <p>
   * Failures are logged as warnings rather than propagated because the trial result has already
   * been captured at this point.
   *
   * @param baseRepo     the base git repository
   * @param worktreePath the worktree path to remove
   */
  private static void removeTestWorktree(Path baseRepo, Path worktreePath)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "-C", baseRepo.toString(),
        "worktree", "remove", "--force", worktreePath.toString());
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectErrorStream(true);
      try (Process process = pb.start())
      {
        process.waitFor();
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      Logger log = LoggerFactory.getLogger(EmpiricalTestRunner.class);
      log.warn("Failed to remove trial worktree: {}", worktreePath, e);
    }
    catch (IOException e)
    {
      Logger log = LoggerFactory.getLogger(EmpiricalTestRunner.class);
      log.warn("Failed to remove trial worktree: {}", worktreePath, e);
    }
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        int exitCode = run(scope, args, System.out);
        System.exit(exitCode);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(EmpiricalTestRunner.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the empirical test runner logic with a caller-provided output stream.
   *
   * @param scope the scope providing access to session paths and shared services
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @return the exit code (0 for success, non-zero for failure)
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   */
  public static int run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      out.println("""
        Usage: empirical-test-runner --config <config.json> [OPTIONS]

        Options:
          --config <path>     Path to test config JSON file (required)
          --trials <N>        Number of trials per config (default: 10)
          --model <name>      Model to test with: haiku|sonnet|opus (default: haiku)
          --cwd <path>        Working directory for claude CLI (default: current directory)
          --output <path>     Path to write JSON results (optional)
          --baseline <prompt> Baseline system prompt for blind comparison mode

        Config JSON fields:
          target_description  Description of expected behavior
          priming_messages    Array of messages to send before test prompt (simulates prior turns)
          system_prompt       String passed as --append-system-prompt to claude CLI (optional)
          system_reminders    Array of strings, each injected as <system-reminder> tags in test messages (optional)
          configs             Object mapping config names to multi-message objects

        Config value format:
          Each config value is an object with a "messages" array. Each message has a "prompt" and
          optional "success_criteria" with must_contain, must_not_contain, must_use_tools,
          must_not_use_tools.

          Criteria may include optional "_metadata" maps for structured grading:
            "success_criteria": {
              "must_contain": ["expected"],
              "_metadata": {
                "contains:expected": { "description": "Checks for expected text", "severity": "HIGH" }
              }
            }

            "A_test": {
              "messages": [
                { "prompt": "First prompt", "success_criteria": { "must_contain": ["expected"] } },
                { "prompt": "Second prompt", "success_criteria": { "must_contain": ["other"] } }
              ]
            }

        Examples:
          empirical-test-runner --config /tmp/test.json --trials 10 --model sonnet
          empirical-test-runner --config test.json --output results.json
          empirical-test-runner --config test.json --baseline "baseline prompt" --output compare.json""");
      return 0;
    }

    Path configPath = null;
    int trials = 10;
    String model = "haiku";
    Path cwd = Path.of(".");
    Path outputPath = null;
    String baselinePrompt = null;

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
        case "--baseline" ->
        {
          baselinePrompt = args[i + 1];
          ++i;
        }
        default -> throw new IllegalArgumentException(
          "Unknown argument: " + args[i] + ". Valid arguments: --config, --trials, --model, --cwd, " +
            "--output, --baseline");
      }
    }

    if (configPath == null)
      throw new IllegalArgumentException("--config argument is required");

    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    if (baselinePrompt != null)
    {
      // Blind comparison mode: read system_prompt from config as candidate
      String configJson = Files.readString(configPath);
      Map<String, Object> config = scope.getJsonMapper().readValue(configJson, MAP_TYPE);
      String candidatePrompt = (String) config.getOrDefault("system_prompt", "");
      ComparisonResult comparison = runner.runBlindComparison(configPath, trials, model, cwd,
        candidatePrompt, baselinePrompt);
      out.println("Winner: " + comparison.winner());
      out.println("Reason: " + comparison.winnerReason());
      if (outputPath != null)
      {
        Files.writeString(outputPath,
          scope.getJsonMapper().writeValueAsString(comparison), StandardCharsets.UTF_8);
        out.println("Comparison results written to: " + outputPath);
      }
      return 0;
    }
    return runner.runTests(configPath, trials, model, cwd, outputPath);
  }

  /**
   * A single message in a multi-message test conversation.
   *
   * @param prompt the prompt text to send
   * @param criteria the success criteria for evaluating the response
   */
  public record TestMessage(String prompt, Map<String, Object> criteria)
  {
    /**
     * Creates a new test message.
     *
     * @param prompt the prompt text to send
     * @param criteria the success criteria for evaluating the response
     * @throws NullPointerException if {@code prompt} or {@code criteria} are null
     */
    public TestMessage
    {
      requireThat(prompt, "prompt").isNotNull();
      requireThat(criteria, "criteria").isNotNull();
    }
  }

  /**
   * Evaluation result for a single message in a multi-message trial.
   *
   * @param messageIndex the 0-based index of the message
   * @param pass whether this message's criteria were satisfied
   * @param checks the map of check results for this message
   * @param outputPreview a preview of the response
   */
  public record MessageEvaluation(int messageIndex, boolean pass, Map<String, Boolean> checks,
    String outputPreview)
  {
    /**
     * Creates a new message evaluation.
     *
     * @param messageIndex  the 0-based index of the message
     * @param pass          whether this message's criteria were satisfied
     * @param checks        the map of check results for this message
     * @param outputPreview a preview of the response
     * @throws NullPointerException if {@code checks} or {@code outputPreview} are null
     */
    public MessageEvaluation
    {
      requireThat(checks, "checks").isNotNull();
      requireThat(outputPreview, "outputPreview").isNotNull();
    }
  }

  /**
   * Result of evaluating output against success criteria.
   *
   * @param pass whether all checks passed
   * @param checks the map of check name to result
   */
  public record EvaluationResult(boolean pass, Map<String, Boolean> checks)
  {
    /**
     * Creates a new evaluation result.
     *
     * @param pass   whether all checks passed
     * @param checks the map of check name to result
     * @throws NullPointerException if {@code checks} is null
     */
    public EvaluationResult
    {
      requireThat(checks, "checks").isNotNull();
    }
  }

  /**
   * Result of a single trial.
   *
   * @param pass whether the trial passed
   * @param checks the map of check results
   * @param elapsed the elapsed time in seconds
   * @param outputPreview a preview of the output
   * @param toolsUsed the list of tools used
   * @param error the error message if any, or empty string if none
   * @param messageEvaluations per-message evaluation results for multi-message trials, empty for
   *                           single-message trials
   * @param sessionFiles paths to session .jsonl files created during this trial (main agent first,
   *                     then subagents), or empty list if the session ID was not found in the output
   */
  public record TrialResult(boolean pass, Map<String, Boolean> checks, long elapsed,
    String outputPreview, List<String> toolsUsed, String error,
    List<MessageEvaluation> messageEvaluations, List<Path> sessionFiles)
  {
    /**
     * Creates a new trial result.
     *
     * @param pass               whether the trial passed
     * @param checks             the map of check results
     * @param elapsed            the elapsed time in seconds
     * @param outputPreview      a preview of the output
     * @param toolsUsed          the list of tools used
     * @param error              the error message if any, or empty string if none
     * @param messageEvaluations per-message evaluation results for multi-message trials, empty for
     *                           single-message trials
     * @param sessionFiles       paths to session .jsonl files created during this trial (main agent
     *                           first, then subagents), or empty list if the session ID was not found
     *                           in the output
     * @throws NullPointerException if {@code checks}, {@code outputPreview}, {@code toolsUsed},
     *                              {@code error}, {@code messageEvaluations}, or {@code sessionFiles}
     *                              are null
     */
    public TrialResult
    {
      requireThat(checks, "checks").isNotNull();
      requireThat(outputPreview, "outputPreview").isNotNull();
      requireThat(toolsUsed, "toolsUsed").isNotNull();
      requireThat(error, "error").isNotNull();
      requireThat(messageEvaluations, "messageEvaluations").isNotNull();
      requireThat(sessionFiles, "sessionFiles").isNotNull();
    }
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
  public record ConfigResult(String name, int trials, int passes, int rate,
    List<TrialResult> results)
  {
    /**
     * Creates a new configuration result.
     *
     * @param name    the configuration name
     * @param trials  the number of trials
     * @param passes  the number of passing trials
     * @param rate    the pass rate as a percentage
     * @param results the list of trial results
     * @throws NullPointerException     if {@code name} or {@code results} are null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public ConfigResult
    {
      requireThat(name, "name").isNotBlank();
      requireThat(results, "results").isNotNull();
    }
  }

  /**
   * Severity level for a criterion.
   */
  public enum Severity
  {
    HIGH, MEDIUM, LOW
  }

  /**
   * Metadata for a single success criterion providing human-readable context.
   *
   * @param description what the criterion tests (defaults to the criterion key name)
   * @param reason why the criterion matters (may be empty)
   * @param severity the impact classification (defaults to MEDIUM)
   */
  public record CriterionMetadata(String description, String reason, Severity severity)
  {
    /**
     * Creates a new criterion metadata.
     *
     * @param description what the criterion tests
     * @param reason      why the criterion matters
     * @param severity    the impact classification
     * @throws NullPointerException if {@code description}, {@code reason}, or {@code severity} are null
     */
    public CriterionMetadata
    {
      requireThat(description, "description").isNotNull();
      requireThat(reason, "reason").isNotNull();
      requireThat(severity, "severity").isNotNull();
    }

    /**
     * Parses criterion metadata from a raw config value.
     * <p>
     * If the value is a string, it is treated as an inlined description with MEDIUM severity.
     * If the value is a map, the fields {@code description}, {@code reason}, and {@code severity}
     * are extracted. Missing fields fall back to defaults.
     *
     * @param criterionKey the criterion key, used as the default description
     * @param rawValue     the raw config value (String or Map)
     * @return the parsed metadata
     */
    public static CriterionMetadata fromRaw(String criterionKey, Object rawValue)
    {
      requireThat(criterionKey, "criterionKey").isNotNull();
      if (rawValue instanceof Map<?, ?> rawMap)
      {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        String description = (String) map.getOrDefault("description", criterionKey);
        String reason = (String) map.getOrDefault("reason", "");
        String severityStr = (String) map.getOrDefault("severity", "MEDIUM");
        String severityUpper = severityStr.toUpperCase(Locale.ROOT);
        Severity severity = switch (severityUpper)
        {
          case "HIGH" -> Severity.HIGH;
          case "MEDIUM" -> Severity.MEDIUM;
          case "LOW" -> Severity.LOW;
          default -> throw new IllegalArgumentException("Config key '" + criterionKey +
            "': invalid severity '" + severityStr + "'. Valid values: HIGH, MEDIUM, LOW");
        };
        return new CriterionMetadata(description, reason, severity);
      }
      return new CriterionMetadata(criterionKey, "", Severity.MEDIUM);
    }
  }

  /**
   * Grading result for a single criterion including evidence from the output.
   *
   * @param criterionKey the key identifying the criterion
   * @param metadata     the criterion metadata
   * @param pass         whether the criterion passed
   * @param quote        a relevant excerpt from the output (up to 200 chars), or empty string
   * @param expected     the expected value or behavior described by the criterion
   * @param actual       the actual value or behavior observed in the output
   */
  public record CriterionGrade(String criterionKey, CriterionMetadata metadata, boolean pass,
    String quote, String expected, String actual)
  {
    /**
     * Creates a new criterion grade.
     *
     * @param criterionKey the key identifying the criterion
     * @param metadata     the criterion metadata
     * @param pass         whether the criterion passed
     * @param quote        a relevant excerpt from the output
     * @param expected     the expected value or behavior described by the criterion
     * @param actual       the actual value or behavior observed in the output
     * @throws NullPointerException if {@code criterionKey}, {@code metadata}, {@code quote},
     *                              {@code expected}, or {@code actual} are null
     */
    public CriterionGrade
    {
      requireThat(criterionKey, "criterionKey").isNotNull();
      requireThat(metadata, "metadata").isNotNull();
      requireThat(quote, "quote").isNotNull();
      requireThat(expected, "expected").isNotNull();
      requireThat(actual, "actual").isNotNull();
    }
  }

  /**
   * Structured grading report for a single message evaluation.
   *
   * @param messageIndex the 0-based index of the message
   * @param grades       the per-criterion grades, ordered by severity (HIGH first) then by key
   * @param pass         whether all criteria passed
   */
  public record GradingReport(int messageIndex, List<CriterionGrade> grades, boolean pass)
  {
    /**
     * Creates a new grading report.
     *
     * @param messageIndex the 0-based index of the message
     * @param grades       the per-criterion grades
     * @param pass         whether all criteria passed
     * @throws NullPointerException if {@code grades} is null
     */
    public GradingReport
    {
      requireThat(grades, "grades").isNotNull();
    }
  }

  /**
   * A single violation identified during post-hoc analysis.
   *
   * @param category  the category of violation (instructions, tool_usage, error_handling, logic)
   * @param quote     the excerpt from the output that illustrates the violation
   * @param expected  the expected behavior
   * @param actual    the actual behavior observed
   * @param severity  the severity of the violation
   */
  public record InstructionViolation(String category, String quote, String expected, String actual,
    Severity severity)
  {
    /**
     * Creates a new instruction violation.
     *
     * @param category the category of violation
     * @param quote    the excerpt from the output
     * @param expected the expected behavior
     * @param actual   the actual behavior observed
     * @param severity the severity of the violation
     * @throws NullPointerException if {@code category}, {@code quote}, {@code expected},
     *                              {@code actual}, or {@code severity} are null
     */
    public InstructionViolation
    {
      requireThat(category, "category").isNotNull();
      requireThat(quote, "quote").isNotNull();
      requireThat(expected, "expected").isNotNull();
      requireThat(actual, "actual").isNotNull();
      requireThat(severity, "severity").isNotNull();
    }
  }

  /**
   * Post-hoc analysis report for a failed trial.
   *
   * @param adherenceScore     instruction adherence score from 1 (poor) to 10 (perfect)
   * @param violations         the list of identified violations, ordered by severity
   * @param suggestions        prioritized improvement suggestions
   */
  public record PostHocAnalysis(int adherenceScore, List<InstructionViolation> violations,
    List<String> suggestions)
  {
    /**
     * Creates a new post-hoc analysis.
     *
     * @param adherenceScore the instruction adherence score (1-10)
     * @param violations     the identified violations
     * @param suggestions    improvement suggestions
     * @throws NullPointerException     if {@code violations} or {@code suggestions} are null
     * @throws IllegalArgumentException if {@code adherenceScore} is not in range [1, 10]
     */
    public PostHocAnalysis
    {
      requireThat(adherenceScore, "adherenceScore").isBetween(1, true, 10, true);
      requireThat(violations, "violations").isNotNull();
      requireThat(suggestions, "suggestions").isNotNull();
    }
  }

  /**
   * Multi-dimensional rubric score for blind comparison.
   *
   * @param instructionAdherence instruction adherence score (1-5)
   * @param outputQuality        output quality score (1-5)
   * @param toolUsageCorrectness tool usage correctness score (1-5)
   * @param errorHandling        error handling score (1-5)
   */
  public record RubricScore(int instructionAdherence, int outputQuality,
    int toolUsageCorrectness, int errorHandling)
  {
    /**
     * Creates a new rubric score.
     *
     * @param instructionAdherence instruction adherence score (1-5)
     * @param outputQuality        output quality score (1-5)
     * @param toolUsageCorrectness tool usage correctness score (1-5)
     * @param errorHandling        error handling score (1-5)
     * @throws IllegalArgumentException if any score is not in range [1, 5]
     */
    public RubricScore
    {
      requireThat(instructionAdherence, "instructionAdherence").isBetween(1, true, 5, true);
      requireThat(outputQuality, "outputQuality").isBetween(1, true, 5, true);
      requireThat(toolUsageCorrectness, "toolUsageCorrectness").isBetween(1, true, 5, true);
      requireThat(errorHandling, "errorHandling").isBetween(1, true, 5, true);
    }

    /**
     * Returns the total rubric score as the sum of all dimensions.
     *
     * @return the total score (4-20)
     */
    public int total()
    {
      return instructionAdherence + outputQuality + toolUsageCorrectness + errorHandling;
    }
  }

  /**
   * Parsed content of a test configuration file.
   *
   * @param targetDescription human-readable description of what is being tested
   * @param systemPrompt      system prompt to append to claude CLI, or empty string for none
   * @param primingMessages   priming messages to send before the test messages
   * @param systemReminders   system reminder strings to inject into each test message
   * @param configs           map of config name to config map with "messages" key
   */
  private record TestConfig(String targetDescription, String systemPrompt,
    List<PrimingMessage> primingMessages, List<String> systemReminders,
    Map<String, Object> configs)
  {
    TestConfig
    {
      requireThat(targetDescription, "targetDescription").isNotNull();
      requireThat(systemPrompt, "systemPrompt").isNotNull();
      requireThat(primingMessages, "primingMessages").isNotNull();
      requireThat(systemReminders, "systemReminders").isNotNull();
      requireThat(configs, "configs").isNotNull();
    }
  }

  /**
   * Result of a blind comparison between a candidate and baseline system prompt.
   *
   * @param candidateResult    the results for the candidate system prompt
   * @param baselineResult     the results for the baseline system prompt
   * @param candidateRubric    the multi-dimensional rubric score for the candidate
   * @param baselineRubric     the multi-dimensional rubric score for the baseline
   * @param winner             which configuration won: "candidate", "baseline", or "tie"
   * @param winnerReason       explanation of how the winner was determined
   */
  public record ComparisonResult(ConfigResult candidateResult, ConfigResult baselineResult,
    RubricScore candidateRubric, RubricScore baselineRubric, String winner, String winnerReason)
  {
    /**
     * Creates a new comparison result.
     *
     * @param candidateResult the results for the candidate system prompt
     * @param baselineResult  the results for the baseline system prompt
     * @param candidateRubric the multi-dimensional rubric score for the candidate
     * @param baselineRubric  the multi-dimensional rubric score for the baseline
     * @param winner          which configuration won
     * @param winnerReason    explanation of the winner determination
     * @throws NullPointerException if any parameter is null
     */
    public ComparisonResult
    {
      requireThat(candidateResult, "candidateResult").isNotNull();
      requireThat(baselineResult, "baselineResult").isNotNull();
      requireThat(candidateRubric, "candidateRubric").isNotNull();
      requireThat(baselineRubric, "baselineRubric").isNotNull();
      requireThat(winner, "winner").isNotNull();
      requireThat(winnerReason, "winnerReason").isNotNull();
    }
  }
}
