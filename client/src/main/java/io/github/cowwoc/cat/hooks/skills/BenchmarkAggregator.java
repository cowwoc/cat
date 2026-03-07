/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Aggregates benchmark run results into per-config statistics with mean, stddev, pass rate,
 * and delta vs. the baseline config.
 * <p>
 * Accepts one argument: a JSON array of run result objects. Each object must have:
 * <ul>
 *   <li>{@code config} (string) — the config name (e.g., "baseline", "with-skill")</li>
 *   <li>{@code assertions} (boolean array) — per-assertion pass/fail results</li>
 *   <li>{@code duration_ms} (non-negative number) — elapsed time in milliseconds for this run</li>
 *   <li>{@code total_tokens} (non-negative number) — total token count for this run</li>
 * </ul>
 * <p>
 * Outputs a JSON object with:
 * <ul>
 *   <li>{@code configs} — a map from config name to aggregate stats</li>
 *   <li>{@code delta} — delta of each non-baseline config vs. the baseline (only present when
 *       two or more distinct config names exist in the input)</li>
 * </ul>
 * <p>
 * Each stats object contains:
 * <ul>
 *   <li>{@code pass_rate} — fraction of assertions that passed across all runs (0.0–1.0)</li>
 *   <li>{@code mean_duration_ms} — mean duration in milliseconds</li>
 *   <li>{@code stddev_duration_ms} — population stddev of duration in milliseconds</li>
 *   <li>{@code mean_tokens} — mean token count</li>
 *   <li>{@code stddev_tokens} — population stddev of token count</li>
 *   <li>{@code run_count} — number of runs contributing to these stats</li>
 * </ul>
 * <p>
 * Each delta object contains:
 * <ul>
 *   <li>{@code baseline_config} — the config name used as the baseline</li>
 *   <li>{@code pass_rate} — delta pass_rate (non-baseline minus baseline)</li>
 *   <li>{@code mean_duration_ms} — delta mean_duration_ms (non-baseline minus baseline)</li>
 *   <li>{@code mean_tokens} — delta mean_tokens (non-baseline minus baseline)</li>
 * </ul>
 * <p>
 * The first distinct config name encountered in the input array is used as the baseline.
 * Stddev is computed as population stddev (divides by N, not N-1).
 */
public final class BenchmarkAggregator implements SkillOutput
{
  private final JvmScope scope;

  /**
   * Creates a BenchmarkAggregator instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public BenchmarkAggregator(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Aggregates run results and returns JSON statistics.
   *
   * @param args one argument: the JSON array of run result objects
   * @return a JSON string with per-config stats and optional delta
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the wrong number of arguments is provided, the JSON is
   *                                  malformed, or the result list is empty
   * @throws IOException              if a JSON serialization error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "BenchmarkAggregator requires 1 argument: [run-results-json]. " +
        "Got " + args.length + " argument(s).");

    String json = args[0];
    JsonNode root = scope.getJsonMapper().readTree(json);

    if (!root.isArray() || root.isEmpty())
      throw new IllegalArgumentException(
        "BenchmarkAggregator requires at least one run result in the input array. " +
        "Input must be a non-empty JSON array of run result objects.");

    // Group runs by config name, preserving insertion order for baseline detection
    Map<String, List<RunResult>> byConfig = new LinkedHashMap<>();
    for (JsonNode node : root)
    {
      String configName = node.path("config").asString("");
      if (configName.isBlank())
        throw new IllegalArgumentException(
          "BenchmarkAggregator: each run result must have a non-empty 'config' field. " +
          "Found: " + node);

      JsonNode assertionsNode = node.path("assertions");
      if (!assertionsNode.isArray())
        throw new IllegalArgumentException(
          "BenchmarkAggregator: 'assertions' must be a JSON boolean array in run result " +
          "for config '" + configName + "'. Found: " + assertionsNode);

      List<Boolean> assertions = new ArrayList<>();
      for (JsonNode a : assertionsNode)
        assertions.add(a.asBoolean());

      JsonNode durationNode = node.path("duration_ms");
      if (durationNode.isMissingNode())
        throw new IllegalArgumentException(
          "BenchmarkAggregator: 'duration_ms' field is missing in run result for config '" +
          configName + "'. Found: " + node);
      long durationMs = durationNode.asLong();
      if (durationMs < 0)
        throw new IllegalArgumentException(
          "BenchmarkAggregator: 'duration_ms' must be >= 0 in run result for config '" +
          configName + "'. Got: " + durationMs);

      JsonNode tokensNode = node.path("total_tokens");
      if (tokensNode.isMissingNode())
        throw new IllegalArgumentException(
          "BenchmarkAggregator: 'total_tokens' field is missing in run result for config '" +
          configName + "'. Found: " + node);
      long totalTokens = tokensNode.asLong();
      if (totalTokens < 0)
        throw new IllegalArgumentException(
          "BenchmarkAggregator: 'total_tokens' must be >= 0 in run result for config '" +
          configName + "'. Got: " + totalTokens);

      byConfig.computeIfAbsent(configName, k -> new ArrayList<>()).
        add(new RunResult(assertions, durationMs, totalTokens));
    }

    // Build per-config stats
    ObjectNode configsNode = scope.getJsonMapper().createObjectNode();
    Map<String, ConfigStats> statsMap = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunResult>> entry : byConfig.entrySet())
    {
      ConfigStats stats = computeStats(entry.getValue());
      statsMap.put(entry.getKey(), stats);
      configsNode.set(entry.getKey(), statsToJson(stats));
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("configs", configsNode);

    // Compute delta when there are at least two distinct configs
    if (statsMap.size() >= 2)
    {
      String baselineConfigName = statsMap.keySet().iterator().next();
      ConfigStats baseline = statsMap.get(baselineConfigName);

      ObjectNode deltaContainerNode = scope.getJsonMapper().createObjectNode();
      for (Map.Entry<String, ConfigStats> entry : statsMap.entrySet())
      {
        if (entry.getKey().equals(baselineConfigName))
          continue;

        ConfigStats other = entry.getValue();
        ObjectNode deltaNode = scope.getJsonMapper().createObjectNode();
        deltaNode.put("baseline_config", baselineConfigName);
        deltaNode.put("pass_rate", round3(other.passRate - baseline.passRate));
        deltaNode.put("mean_duration_ms", round3(other.meanDurationMs - baseline.meanDurationMs));
        deltaNode.put("mean_tokens", round3(other.meanTokens - baseline.meanTokens));
        deltaContainerNode.set(entry.getKey(), deltaNode);
      }
      result.set("delta", deltaContainerNode);
    }

    return scope.getJsonMapper().writer().
      withFeatures(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT).
      writeValueAsString(result);
  }

  /**
   * Computes aggregate statistics for a list of run results belonging to the same config.
   *
   * @param runs the run results for a single config
   * @return the computed statistics
   */
  private ConfigStats computeStats(List<RunResult> runs)
  {
    int totalAssertions = 0;
    int passedAssertions = 0;
    double sumDuration = 0;
    double sumTokens = 0;

    for (RunResult run : runs)
    {
      for (boolean passed : run.assertions)
      {
        totalAssertions += 1;
        if (passed)
        {
          passedAssertions += 1;
        }
      }
      sumDuration += run.durationMs;
      sumTokens += run.totalTokens;
    }

    double passRate;
    if (totalAssertions == 0)
    {
      passRate = 0.0;
    }
    else
    {
      passRate = (double) passedAssertions / totalAssertions;
    }
    double meanDuration = sumDuration / runs.size();
    double meanTokens = sumTokens / runs.size();

    // Population stddev (divide by N)
    double varianceDuration = 0;
    double varianceTokens = 0;
    for (RunResult run : runs)
    {
      double diffDuration = run.durationMs - meanDuration;
      double diffTokens = run.totalTokens - meanTokens;
      varianceDuration += diffDuration * diffDuration;
      varianceTokens += diffTokens * diffTokens;
    }
    double stddevDuration = Math.sqrt(varianceDuration / runs.size());
    double stddevTokens = Math.sqrt(varianceTokens / runs.size());

    return new ConfigStats(passRate, meanDuration, stddevDuration, meanTokens, stddevTokens,
      runs.size());
  }

  /**
   * Converts a {@link ConfigStats} to a JSON {@link ObjectNode}.
   *
   * @param stats the stats to convert
   * @return an ObjectNode with all stat fields
   */
  private ObjectNode statsToJson(ConfigStats stats)
  {
    ObjectNode node = scope.getJsonMapper().createObjectNode();
    node.put("pass_rate", round3(stats.passRate));
    node.put("mean_duration_ms", round3(stats.meanDurationMs));
    node.put("stddev_duration_ms", round3(stats.stddevDurationMs));
    node.put("mean_tokens", round3(stats.meanTokens));
    node.put("stddev_tokens", round3(stats.stddevTokens));
    node.put("run_count", stats.runCount);
    return node;
  }

  /**
   * Rounds a double to 3 decimal places.
   *
   * @param value the value to round
   * @return the rounded value
   */
  private double round3(double value)
  {
    return Math.round(value * 1000.0) / 1000.0;
  }

  /**
   * Holds the raw data for a single benchmark run.
   *
   * @param assertions  per-assertion pass/fail results
   * @param durationMs  elapsed time in milliseconds
   * @param totalTokens total token count
   */
  private record RunResult(List<Boolean> assertions, long durationMs, long totalTokens)
  {
  }

  /**
   * Holds aggregate statistics for a single config across all its runs.
   *
   * @param passRate        fraction of assertions that passed (0.0–1.0)
   * @param meanDurationMs  mean duration in milliseconds
   * @param stddevDurationMs population stddev of duration in milliseconds
   * @param meanTokens      mean token count
   * @param stddevTokens    population stddev of token count
   * @param runCount        number of runs
   */
  private record ConfigStats(
    double passRate,
    double meanDurationMs,
    double stddevDurationMs,
    double meanTokens,
    double stddevTokens,
    int runCount)
  {
  }
}
