/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.ClaudeEnv;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.skills.JsonHelper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Computes RCA statistics from mistakes-YYYY-MM.json files.
 *
 * <p>
 * Analyzes mistakes data to compute count, recurrences, and recurrence_rate.
 * </p>
 */
public final class RootCauseAnalyzer
{
  private final Path retrospectivesDir;
  private final JvmScope scope;

  /**
   * Creates a new root cause analyzer.
   *
   * @param projectRoot the project root directory
   * @param scope the JVM scope for accessing shared services
   */
  public RootCauseAnalyzer(Path projectRoot, JvmScope scope)
  {
    requireThat(projectRoot, "projectRoot").isNotNull();
    requireThat(scope, "scope").isNotNull();
    this.retrospectivesDir = scope.getCatDir().resolve("retrospectives");
    this.scope = scope;
  }

  /**
   * Analyzes mistakes and returns RCA statistics as JSON.
   *
   * @param startId minimum mistake ID (numeric part) to include in analysis
   * @return JSON object with count, recurrences, and recurrence_rate; or null when no data exists
   * @throws IOException if reading mistakes files fails
   */
  public String analyze(int startId) throws IOException
  {
    requireThat(startId, "startId").isGreaterThanOrEqualTo(0);

    // Read all mistakes-YYYY-MM.json files
    if (!Files.exists(retrospectivesDir))
      return null;

    List<Path> mistakesFiles;
    try (Stream<Path> files = Files.list(retrospectivesDir))
    {
      mistakesFiles = files.
        filter(path -> path.getFileName().toString().matches("mistakes-\\d{4}-\\d{2}\\.json")).
        sorted().
        toList();
    }

    if (mistakesFiles.isEmpty())
      return null;

    RcaStats stats = new RcaStats();
    for (Path path : mistakesFiles)
      processMistakesFile(path, startId, stats);

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.put("count", stats.count);
    result.put("recurrences", stats.recurrences);
    result.put("recurrence_rate", stats.getRecurrenceRate());

    return scope.getJsonMapper().writeValueAsString(result);
  }

  /**
   * Processes a single mistakes file and updates statistics.
   *
   * @param path the file path
   * @param startId minimum mistake ID to include
   * @param stats accumulator for statistics
   * @throws IOException if file reading fails
   */
  private void processMistakesFile(Path path, int startId, RcaStats stats)
    throws IOException
  {
    String content = Files.readString(path);
    JsonNode root = scope.getJsonMapper().readTree(content);
    JsonNode mistakesArray = root.get("mistakes");

    if (mistakesArray == null || !mistakesArray.isArray())
      throw new IOException("Expected 'mistakes' array in file: " + path);

    for (JsonNode mistake : mistakesArray)
    {
      // Extract ID and check if it meets the filter
      String id = JsonHelper.getStringOrDefault(mistake, "id", "");
      int numericId = extractNumericId(id);
      if (numericId < startId)
        continue;

      // Check if this is a recurrence
      String recurrenceText = JsonHelper.getStringOrDefault(mistake, "recurrence_of", "");
      boolean isRecurrence = !recurrenceText.isEmpty();

      ++stats.count;
      if (isRecurrence)
        ++stats.recurrences;
    }
  }

  /**
   * Extracts the numeric part from a mistake ID (e.g., "M123" -> 123).
   *
   * @param id the mistake ID
   * @return the numeric part, or 0 if not found
   */
  private int extractNumericId(String id)
  {
    if (id == null || id.isEmpty())
      return 0;

    StringBuilder numericPart = new StringBuilder();
    for (char c : id.toCharArray())
    {
      if (Character.isDigit(c))
        numericPart.append(c);
    }

    if (numericPart.isEmpty())
      return 0;

    try
    {
      return Integer.parseInt(numericPart.toString());
    }
    catch (NumberFormatException _)
    {
      return 0;
    }
  }

  /**
   * Parses command-line arguments to extract the start ID.
   *
   * @param args the command-line arguments
   * @return the parsed start ID
   * @throws IllegalArgumentException if --start-id is missing a value or has an invalid value
   */
  public static int parseStartId(String[] args)
  {
    int startId = 0;

    for (int i = 0; i < args.length; ++i)
    {
      if ("--start-id".equals(args[i]))
      {
        if (i + 1 < args.length)
        {
          try
          {
            startId = Integer.parseInt(args[i + 1]);
            ++i;
          }
          catch (NumberFormatException _)
          {
            throw new IllegalArgumentException("Invalid start-id value: " + args[i + 1]);
          }
        }
        else
        {
          throw new IllegalArgumentException("--start-id requires a value");
        }
      }
    }

    return startId;
  }

  /**
   * Container for RCA method statistics.
   */
  private static final class RcaStats
  {
    int count;
    int recurrences;

    /**
     * Computes recurrence rate as a percentage.
     *
     * @return recurrence rate (0-100)
     */
    int getRecurrenceRate()
    {
      if (count == 0)
        return 0;
      return (int) (((double) recurrences / count) * 100);
    }
  }

  /**
   * CLI entry point for root cause analyzer.
   *
   * @param args command-line arguments: [--start-id N]
   */
  public static void main(String[] args)
  {
    try (MainJvmScope scope = new MainJvmScope())
    {
      int startId;
      try
      {
        startId = parseStartId(args);
      }
      catch (IllegalArgumentException e)
      {
        System.err.println(e.getMessage());
        System.exit(1);
        return;
      }

      ClaudeEnv env = new ClaudeEnv();
      Path projectRoot = env.getClaudeProjectDir();
      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(projectRoot, scope);
      String result = analyzer.analyze(startId);
      System.out.println(result);
    }
    catch (IOException e)
    {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }
}
