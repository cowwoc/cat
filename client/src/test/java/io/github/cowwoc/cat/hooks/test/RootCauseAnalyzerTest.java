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
  @Test
  public void constructorRejectsNullProjectRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        new RootCauseAnalyzer(null, scope);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("projectRoot");
      }
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
  @Test
  public void constructorRejectsNullScope() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try
    {
      new RootCauseAnalyzer(tempDir, null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("scope");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that groups mistakes by rca_method and computes statistics.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void groupsByRcaMethodAndComputesStatistics() throws IOException
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
            {"id": "M001", "rca_method": "A"},
            {"id": "M002", "rca_method": "A"},
            {"id": "M003", "rca_method": "B"},
            {"id": "M004", "rca_method": "A", "recurrence_of": "M001"},
            {"id": "M005", "rca_method": "B", "recurrence_of": "M003"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "entriesCount").isEqualTo(2);

      // Find entries by method
      JsonNode methodA = null;
      JsonNode methodB = null;
      for (JsonNode entry : entries)
      {
        String method = entry.get("method").asString();
        if ("A".equals(method))
          methodA = entry;
        else if ("B".equals(method))
          methodB = entry;
      }

      requireThat(methodA, "methodAEntry").isNotNull();
      requireThat(methodA.get("count").asInt(), "ACount").isEqualTo(3);
      requireThat(methodA.get("recurrences").asInt(), "ARecurrences").isEqualTo(1);
      requireThat(methodA.get("recurrence_rate").asInt(), "ARecurrenceRate").isEqualTo(33);

      requireThat(methodB, "methodBEntry").isNotNull();
      requireThat(methodB.get("count").asInt(), "BCount").isEqualTo(2);
      requireThat(methodB.get("recurrences").asInt(), "BRecurrences").isEqualTo(1);
      requireThat(methodB.get("recurrence_rate").asInt(), "BRecurrenceRate").isEqualTo(50);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that filters mistakes by START_ID (numeric part).
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
            {"id": "M001", "rca_method": "A"},
            {"id": "M100", "rca_method": "B"},
            {"id": "M101", "rca_method": "B"},
            {"id": "M085", "rca_method": "A"},
            {"id": "M086", "rca_method": "A"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(86);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "entriesCount").isEqualTo(2);

      // Find entries
      JsonNode methodA = null;
      JsonNode methodB = null;
      for (JsonNode entry : entries)
      {
        String method = entry.get("method").asString();
        if ("A".equals(method))
          methodA = entry;
        else if ("B".equals(method))
          methodB = entry;
      }

      // M001 and M085 are < 86, so excluded
      // M086 is >= 86 (method A), M100 and M101 are >= 86 (method B)
      requireThat(methodA, "methodAEntry").isNotNull();
      requireThat(methodA.get("count").asInt(), "ACountAfterFiltering").isEqualTo(1);

      requireThat(methodB, "methodBEntry").isNotNull();
      requireThat(methodB.get("count").asInt(), "BCountAfterFiltering").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing rca_method field shows as "unassigned".
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
            {"id": "M001", "rca_method": "A"},
            {"id": "M002"},
            {"id": "M003"},
            {"id": "M004", "rca_method": "A", "recurrence_of": "M001"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "entriesCount").isEqualTo(2);

      // Find unassigned entry
      JsonNode unassigned = null;
      for (JsonNode entry : entries)
      {
        String method = entry.get("method").asString();
        if ("unassigned".equals(method))
          unassigned = entry;
      }

      requireThat(unassigned, "unassignedEntry").isNotNull();
      requireThat(unassigned.get("count").asInt(), "unassignedCount").isEqualTo(2);
      requireThat(unassigned.get("recurrences").asInt(), "unassignedRecurrences").isEqualTo(0);
      requireThat(unassigned.get("recurrence_rate").asInt(), "unassignedRecurrenceRate").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty input returns empty array.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void emptyInputReturnsEmptyArray() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-rca-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retrospectivesDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retrospectivesDir);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "emptyResult").isEqualTo(0);
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
            {"id": "M001", "rca_method": "A"},
            {"id": "M002", "rca_method": "A"}
          ]
        }
        """);

      Files.writeString(retrospectivesDir.resolve("mistakes-2026-02.json"), """
        {
          "period": "2026-02",
          "mistakes": [
            {"id": "M101", "rca_method": "B"},
            {"id": "M102", "rca_method": "A", "recurrence_of": "M001"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "entriesCountFrom2Files").isEqualTo(2);

      // Find entries
      JsonNode methodA = null;
      JsonNode methodB = null;
      for (JsonNode entry : entries)
      {
        String method = entry.get("method").asString();
        if ("A".equals(method))
          methodA = entry;
        else if ("B".equals(method))
          methodB = entry;
      }

      requireThat(methodA, "methodAFromMergedFiles").isNotNull();
      requireThat(methodA.get("count").asInt(), "ACount").isEqualTo(3);
      requireThat(methodA.get("recurrences").asInt(), "ARecurrences").isEqualTo(1);

      requireThat(methodB, "methodBFromMergedFiles").isNotNull();
      requireThat(methodB.get("count").asInt(), "BCount").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that results are sorted by method name alphabetically.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void sortsByMethodNameAlphabetically() throws IOException
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
            {"id": "M001", "rca_method": "C"},
            {"id": "M002", "rca_method": "A"},
            {"id": "M003", "rca_method": "B"}
          ]
        }
        """);

      RootCauseAnalyzer analyzer = new RootCauseAnalyzer(tempDir, scope);
      String result = analyzer.analyze(0);

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "entriesCount").isEqualTo(3);
      requireThat(entries[0].get("method").asString(), "firstMethod").isEqualTo("A");
      requireThat(entries[1].get("method").asString(), "secondMethod").isEqualTo("B");
      requireThat(entries[2].get("method").asString(), "thirdMethod").isEqualTo("C");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that returns empty JSON array when retrospectives directory doesn't exist.
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

      JsonNode[] entries = scope.getJsonMapper().readValue(result, JsonNode[].class);

      requireThat(entries.length, "emptyArrayWhenDirMissing").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseStartId rejects invalid --start-id value.
   */
  @Test
  public void parseStartIdRejectsInvalidValue()
  {
    try
    {
      RootCauseAnalyzer.parseStartId(new String[]{"--start-id", "not-a-number"});
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("Invalid start-id value");
    }
  }

  /**
   * Verifies that parseStartId rejects missing --start-id value.
   */
  @Test
  public void parseStartIdRejectsMissingValue()
  {
    try
    {
      RootCauseAnalyzer.parseStartId(new String[]{"--start-id"});
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("--start-id requires a value");
    }
  }
}
