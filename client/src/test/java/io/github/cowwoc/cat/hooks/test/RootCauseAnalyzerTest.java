/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.RootCauseAnalyzer;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link RootCauseAnalyzer}.
 */
public final class RootCauseAnalyzerTest
{
  /**
   * Verifies that constructor rejects null projectRoot.
   *
   * @throws IOException if test setup fails
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*projectRoot.*")
  public void constructorRejectsNullProjectRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new RootCauseAnalyzer(null, scope);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null scope.
   *
   * @throws IOException if test setup fails
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try
    {
      new RootCauseAnalyzer(tempDir, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistakes are counted and statistics are computed correctly.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void computesStatistics() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M001"},
            {"id": "M002"},
            {"id": "M003"},
            {"id": "M004", "recurrence_of": "M001"},
            {"id": "M005", "recurrence_of": "M003"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode entry = scope.getJsonMapper().readTree(result);
      requireThat(entry.get("count").asInt(), "count").isEqualTo(5);
      requireThat(entry.get("recurrences").asInt(), "recurrences").isEqualTo(2);
      requireThat(entry.get("recurrence_rate").asInt(), "recurrence_rate").isEqualTo(40);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistakes are filtered by startId (numeric part of the ID).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void filtersHistoryByStartId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M001"},
            {"id": "M100"},
            {"id": "M101"},
            {"id": "M085"},
            {"id": "M086"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(86);

      JsonNode entry = scope.getJsonMapper().readTree(result);

      // M001 and M085 are < 86, so excluded
      // M086 >= 86 (1 item), M100 and M101 >= 86 (2 items)
      requireThat(entry.get("count").asInt(), "countAfterFiltering").isEqualTo(3);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistakes without rca_method field are processed correctly.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void handlesMissingRcaMethod() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M001"},
            {"id": "M002"},
            {"id": "M003"},
            {"id": "M004", "recurrence_of": "M001"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode entry = scope.getJsonMapper().readTree(result);
      requireThat(entry.get("count").asInt(), "count").isEqualTo(4);
      requireThat(entry.get("recurrences").asInt(), "recurrences").isEqualTo(1);
      requireThat(entry.get("recurrence_rate").asInt(), "recurrence_rate").isEqualTo(25);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that returns null when there are no mistakes files.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void emptyInputReturnsNull() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      requireThat(result, "result").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handles month-split file format (multiple mistakes-YYYY-MM.json files).
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void handlesMonthlySplitFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M001"},
            {"id": "M002"}
          ]
        }
        """);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-02.json"), """
        {
          "period": "2026-02",
          "mistakes": [
            {"id": "M101"},
            {"id": "M102", "recurrence_of": "M001"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode entry = scope.getJsonMapper().readTree(result);

      // M001, M002, M101, M102 = 4 total, with M102 being a recurrence
      requireThat(entry.get("count").asInt(), "count").isEqualTo(4);
      requireThat(entry.get("recurrences").asInt(), "recurrences").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistake records are processed correctly and recurrence rate is computed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void computesRecurrenceRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-03.json"), """
        {
          "period": "2026-03",
          "mistakes": [
            {"id": "M600"},
            {"id": "M601"},
            {"id": "M602", "recurrence_of": "M600"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode entry = scope.getJsonMapper().readTree(result);
      requireThat(entry.get("count").asInt(), "count").isEqualTo(3);
      requireThat(entry.get("recurrences").asInt(), "recurrences").isEqualTo(1);
      requireThat(entry.get("recurrence_rate").asInt(), "recurrence_rate").isEqualTo(33);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all mistakes are counted regardless of their original rca_method field value.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void countsAllMistakesRegardlessOfRcaMethod() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M100"},
            {"id": "M101"},
            {"id": "M102"},
            {"id": "M103", "recurrence_of": "M100"},
            {"id": "M104", "recurrence_of": "M102"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode entry = scope.getJsonMapper().readTree(result);
      requireThat(entry.get("count").asInt(), "count").isEqualTo(5);
      requireThat(entry.get("recurrences").asInt(), "recurrences").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that returns null when retrospectives directory doesn't exist.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void handlesMissingRetrospectivesDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Don't create the retrospectives directory
      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      requireThat(result, "result").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseStartId rejects invalid --start-id value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid start-id value.*")
  public void parseStartIdRejectsInvalidValue()
  {
    RootCauseAnalyzer.parseStartId(new String[]{"--start-id", "not-a-number"});
  }

  /**
   * Verifies that parseStartId rejects missing --start-id value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*--start-id requires a value.*")
  public void parseStartIdRejectsMissingValue()
  {
    RootCauseAnalyzer.parseStartId(new String[]{"--start-id"});
  }
}
