/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.GetRetrospectiveOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetRetrospectiveOutput.
 */
public final class GetRetrospectiveOutputTest
{
  /**
   * Verifies that missing retrospectives directory produces an error.
   */
  @Test
  public void missingRetrospectivesDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ERROR:");
      requireThat(output, "output").contains("Retrospectives directory not found");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing index.json produces an error.
   */
  @Test
  public void missingIndexFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ERROR:");
      requireThat(output, "output").contains("Index file not found");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed index.json throws an exception.
   */
  @Test(expectedExceptions = Exception.class)
  public void malformedIndexFile() throws Exception
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, "{ invalid json }");

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      handler.getOutput();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that trigger not met produces status output.
   */
  @Test
  public void triggerNotMet() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 5,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(yesterday);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE STATUS:");
      requireThat(output, "output").contains("Retrospective not triggered");
      requireThat(output, "output").contains("Days since last retrospective: 1/7");
      requireThat(output, "output").contains("Mistakes accumulated: 5/10");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that time-based trigger produces analysis.
   */
  @Test
  public void timeBasedTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ANALYSIS:");
      requireThat(output, "output").contains("Trigger: 8 days since last retrospective (threshold: 7)");
      requireThat(output, "output").contains("Period analyzed:");
      requireThat(output, "output").contains("Mistakes analyzed: 0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that count-based trigger produces analysis.
   */
  @Test
  public void countBasedTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 12,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(yesterday);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ANALYSIS:");
      requireThat(output, "output").contains("Trigger: 12 mistakes accumulated (threshold: 10)");
      requireThat(output, "output").contains("Mistakes analyzed: 0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that first retrospective trigger works when no last_retrospective exists.
   */
  @Test
  public void firstRetrospectiveTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      String mistakesContent = """
        {
          "period": "2026-01",
          "mistakes": [
            {
              "id": "M001",
              "timestamp": "2026-01-15T10:00:00Z",
              "category": "test_failure",
              "description": "Test failed"
            }
          ]
        }
        """;

      Path mistakeFile = retroDir.resolve("mistakes-2026-01.json");
      Files.writeString(mistakeFile, mistakesContent);

      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": null,
          "mistake_count_since_last": 0,
          "files": {
            "mistakes": ["mistakes-2026-01.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """;

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ANALYSIS:");
      requireThat(output, "output").contains("Trigger: First retrospective with 1 logged mistakes");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies effectiveness reporting from index.json.
   */
  @Test
  public void effectivenessReporting() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": [
            {
              "id": "A001",
              "priority": "high",
              "description": "Fix something",
              "status": "implemented",
              "effectiveness_check": {
                "verdict": "effective"
              }
            },
            {
              "id": "A002",
              "priority": "medium",
              "description": "Fix something else",
              "status": "implemented",
              "effectiveness_check": {
                "verdict": "ineffective"
              }
            }
          ]
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Action item effectiveness:");
      requireThat(output, "output").contains("A001: effective");
      requireThat(output, "output").contains("A002: ineffective");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies pattern status with empty patterns array.
   */
  @Test
  public void emptyPatternsArray() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Pattern status:");
      requireThat(output, "output").contains("(no patterns)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies open action items with empty action_items array.
   */
  @Test
  public void emptyActionItemsArray() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Open action items:");
      requireThat(output, "output").contains("(no action items)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing mistakes file referenced in index.json throws IOException.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Mistakes file listed in index.json not found.*")
  public void missingMistakesFileThrowsIOException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": ["mistakes-nonexistent.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      handler.getOutput();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unparseable mistakes file propagates the error.
   */
  @Test(expectedExceptions = Exception.class)
  public void unparseableMistakesFilePropagatesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Path mistakeFile = retroDir.resolve("mistakes-bad.json");
      Files.writeString(mistakeFile, "not valid json {{{");

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": ["mistakes-bad.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      handler.getOutput();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that pattern status shows active patterns and filters out addressed ones.
   */
  @Test
  public void patternStatusFiltersAddressed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [
            {
              "pattern_id": "P001",
              "status": "active",
              "occurrences_total": 5,
              "occurrences_after_fix": 2
            },
            {
              "pattern_id": "P002",
              "status": "addressed",
              "occurrences_total": 3,
              "occurrences_after_fix": 0
            },
            {
              "pattern_id": "P003",
              "status": "monitoring",
              "occurrences_total": 7,
              "occurrences_after_fix": 1
            }
          ],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("P001: active (occurrences: 5/2)");
      requireThat(output, "output").contains("P003: monitoring (occurrences: 7/1)");
      requireThat(output, "output").doesNotContain("P002");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that open action items are sorted by priority (high first, then medium, then low).
   */
  @Test
  public void openActionItemsSortedByPriority() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": [
            {
              "id": "A001",
              "priority": "low",
              "description": "Low priority task",
              "status": "open"
            },
            {
              "id": "A002",
              "priority": "high",
              "description": "High priority task",
              "status": "open"
            },
            {
              "id": "A003",
              "priority": "medium",
              "description": "Medium priority task",
              "status": "open"
            },
            {
              "id": "A004",
              "priority": "high",
              "description": "Another high priority task",
              "status": "escalated"
            },
            {
              "id": "A005",
              "priority": "medium",
              "description": "Closed task",
              "status": "closed"
            }
          ]
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Open action items:");
      requireThat(output, "output").doesNotContain("A005");

      // Verify ordering: high items first, then medium, then low
      int posA002 = output.indexOf("A002 (high)");
      int posA004 = output.indexOf("A004 (high)");
      int posA003 = output.indexOf("A003 (medium)");
      int posA001 = output.indexOf("A001 (low)");
      requireThat(posA002, "posA002").isGreaterThan(-1);
      requireThat(posA004, "posA004").isGreaterThan(-1);
      requireThat(posA003, "posA003").isGreaterThan(-1);
      requireThat(posA001, "posA001").isGreaterThan(-1);
      requireThat(posA002, "posA002BeforePosA003").isLessThan(posA003);
      requireThat(posA004, "posA004BeforePosA003").isLessThan(posA003);
      requireThat(posA003, "posA003BeforePosA001").isLessThan(posA001);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies category breakdown with mistakes.
   */
  @Test
  public void categoryBreakdownWithMistakes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String mistakesContent = """
        {
          "period": "2026-02",
          "mistakes": [
            {
              "id": "M001",
              "timestamp": "%s",
              "category": "protocol_violation",
              "description": "First mistake"
            },
            {
              "id": "M002",
              "timestamp": "%s",
              "category": "test_failure",
              "description": "Second mistake"
            },
            {
              "id": "M003",
              "timestamp": "%s",
              "category": "protocol_violation",
              "description": "Third mistake"
            }
          ]
        }
        """.formatted(
        Instant.now().minus(1, ChronoUnit.DAYS),
        Instant.now().minus(1, ChronoUnit.DAYS),
        Instant.now().minus(1, ChronoUnit.DAYS));

      Path mistakeFile = retroDir.resolve("mistakes-2026-02.json");
      Files.writeString(mistakeFile, mistakesContent);

      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": ["mistakes-2026-02.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Category breakdown:");
      requireThat(output, "output").contains("protocol_violation: 2");
      requireThat(output, "output").contains("test_failure: 1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a mistake with an invalid timestamp is skipped while other valid mistakes are still
   * counted.
   */
  @Test
  public void invalidTimestampInMistakeSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String mistakesContent = """
        {
          "period": "2026-02",
          "mistakes": [
            {
              "id": "M001",
              "timestamp": "not-a-date",
              "category": "bad_timestamp",
              "description": "Invalid timestamp mistake"
            },
            {
              "id": "M002",
              "timestamp": "%s",
              "category": "valid_category",
              "description": "Valid mistake"
            }
          ]
        }
        """.formatted(Instant.now().minus(1, ChronoUnit.DAYS));

      Path mistakeFile = retroDir.resolve("mistakes-2026-02.json");
      Files.writeString(mistakeFile, mistakesContent);

      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": ["mistakes-2026-02.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ANALYSIS:");
      requireThat(output, "output").contains("Mistakes analyzed: 1");
      requireThat(output, "output").contains("valid_category: 1");
      requireThat(output, "output").doesNotContain("bad_timestamp");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an action item with an invalid priority value defaults to medium in the output.
   */
  @Test
  public void invalidPriorityDefaultsToMedium() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": [
            {
              "id": "A001",
              "priority": "urgent",
              "description": "Task with invalid priority",
              "status": "open"
            }
          ]
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("A001 (medium)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an index.json without a "config" key uses default trigger_interval_days=7 and
   * mistake_count_threshold=10.
   */
  @Test
  public void missingConfigObjectUsesDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "last_retrospective": "%s",
          "mistake_count_since_last": 5,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(yesterday);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE STATUS:");
      requireThat(output, "output").contains("Days since last retrospective: 1/7");
      requireThat(output, "output").contains("Mistakes accumulated: 5/10");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an action item missing the "status" field is excluded from open items.
   */
  @Test
  public void actionItemWithNullStatusIsExcluded() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": [
            {
              "id": "A001",
              "priority": "high",
              "description": "Item without status"
            }
          ]
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("(no open action items)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a mistakes file with no "mistakes" array results in zero mistakes counted.
   */
  @Test
  public void mistakesFileWithoutMistakesArray() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      Path mistakeFile = retroDir.resolve("mistakes-2026-02.json");
      Files.writeString(mistakeFile, """
        {
          "period": "2026-02"
        }
        """);

      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": ["mistakes-2026-02.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Mistakes analyzed: 0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an index.json without a "files" node results in zero mistakes analyzed.
   */
  @Test
  public void missingFilesNodeResultsInZeroMistakes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "patterns": [],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("Mistakes analyzed: 0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a pattern without occurrences_total or occurrences_after_fix fields shows 0/0.
   */
  @Test
  public void patternWithMissingOccurrencesDefaultsToZero() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "last_retrospective": "%s",
          "mistake_count_since_last": 3,
          "files": {
            "mistakes": [],
            "retrospectives": []
          },
          "patterns": [
            {
              "pattern_id": "P001",
              "status": "active"
            }
          ],
          "action_items": []
        }
        """.formatted(eightDaysAgo);

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").contains("P001: active (occurrences: 0/0)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an index.json where the last_retrospective field is omitted entirely is treated
   * as having no previous retrospective.
   */
  @Test
  public void missingLastRetrospectiveFieldTreatedAsNull() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);

      Path mistakeFile = retroDir.resolve("mistakes-2026-02.json");
      Files.writeString(mistakeFile, """
        {
          "period": "2026-02",
          "mistakes": [
            {
              "id": "M001",
              "timestamp": "2026-02-10T10:00:00Z",
              "category": "test_failure",
              "description": "A mistake"
            }
          ]
        }
        """);

      String indexContent = """
        {
          "version": "2.0",
          "config": {
            "trigger_interval_days": 7,
            "mistake_count_threshold": 10
          },
          "mistake_count_since_last": 0,
          "files": {
            "mistakes": ["mistakes-2026-02.json"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """;

      Path indexFile = retroDir.resolve("index.json");
      Files.writeString(indexFile, indexContent);

      GetRetrospectiveOutput handler = new GetRetrospectiveOutput(scope);
      String output = handler.getOutput();
      requireThat(output, "output").startsWith("SKILL OUTPUT RETROSPECTIVE ANALYSIS:");
      requireThat(output, "output").contains("First retrospective with 1 logged mistakes");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
