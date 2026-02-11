package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetTokenReportOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetTokenReportOutput functionality.
 * <p>
 * Tests verify token report output generation. The token report handler reads session JSONL
 * files from the filesystem and parses them. Tests use synthetic session files to verify
 * parsing and formatting logic.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetTokenReportOutputTest
{
  /**
   * Verifies that getOutput returns null for a nonexistent session ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonexistentSessionReturnsNull() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetTokenReportOutput handler = new GetTokenReportOutput(scope);
      String result = handler.getOutput("nonexistent-session-id-12345");

      requireThat(result, "result").isNull();
    }
  }

  /**
   * Verifies that getOutput returns appropriate message for empty session file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptySessionFileReturnsNoSubagentMessage() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      // Create a temp session file in the expected location
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-empty-session-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        // Write an empty JSONL file (no subagent data)
        Files.writeString(sessionFile, "");

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        // Empty file has no subagent data
        requireThat(result, "result").isEqualTo("No subagent data found in session.");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }

  /**
   * Verifies that getOutput handles session file with non-Task entries gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sessionWithNoTaskEntriesReturnsNoSubagentMessage() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-no-tasks-session-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        // Write a JSONL file with a user entry but no Task tool_use
        String jsonl = "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"hello\"}}\n";
        Files.writeString(sessionFile, jsonl);

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        requireThat(result, "result").isEqualTo("No subagent data found in session.");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }

  /**
   * Verifies that getOutput produces summary and legend for valid Task data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sessionWithTaskDataProducesSummary() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-task-data-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        // Write a JSONL file with a Task tool_use and its result
        String assistantEntry = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
          "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"Task\"," +
          "\"input\":{\"prompt\":\"## Implement feature\\nDo the thing\"," +
          "\"description\":\"Implement feature\"}}]}}";
        String resultEntry = "{\"type\":\"user\",\"toolUseResult\":{\"tool_use_id\":\"toolu_01\"," +
          "\"totalTokens\":50000,\"durationMs\":30000}}";

        Files.writeString(sessionFile, assistantEntry + "\n" + resultEntry + "\n");

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        requireThat(result, "result").isNotNull().
          contains("Summary:").contains("1 subagents").contains("50000 total tokens").
          contains("Legend:");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }

  /**
   * Verifies that the token report table has proper table structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenReportTableHasProperStructure() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-table-structure-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        String assistantEntry = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
          "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_02\",\"name\":\"Task\"," +
          "\"input\":{\"prompt\":\"## Build tests\\nWrite tests\"," +
          "\"description\":\"Build tests\"}}]}}";
        String resultEntry = "{\"type\":\"user\",\"toolUseResult\":{\"tool_use_id\":\"toolu_02\"," +
          "\"totalTokens\":75000,\"durationMs\":45000}}";

        Files.writeString(sessionFile, assistantEntry + "\n" + resultEntry + "\n");

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        // Table should have top/bottom borders and column headers
        requireThat(result, "result").isNotNull().
          contains("╭").contains("╰").
          contains("Type").contains("Description").contains("Tokens").
          contains("Context").contains("Duration").
          contains("TOTAL");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }

  /**
   * Verifies that multiple subagents are counted and totaled correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleSubagentsAreTotaled() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-multi-subagents-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        String entry1 = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
          "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_a\",\"name\":\"Task\"," +
          "\"input\":{\"prompt\":\"## Task A\\nDo A\",\"description\":\"Task A\"}}]}}";
        String result1 = "{\"type\":\"user\",\"toolUseResult\":{\"tool_use_id\":\"toolu_a\"," +
          "\"totalTokens\":30000,\"durationMs\":20000}}";
        String entry2 = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
          "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_b\",\"name\":\"Task\"," +
          "\"input\":{\"prompt\":\"## Task B\\nDo B\",\"description\":\"Task B\"}}]}}";
        String result2 = "{\"type\":\"user\",\"toolUseResult\":{\"tool_use_id\":\"toolu_b\"," +
          "\"totalTokens\":20000,\"durationMs\":10000}}";

        Files.writeString(sessionFile, entry1 + "\n" + result1 + "\n" + entry2 + "\n" + result2 + "\n");

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        requireThat(result, "result").isNotNull().
          contains("2 subagents").contains("50000 total tokens");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }

  /**
   * Verifies that context percentage is included in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void contextPercentageIsDisplayed() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      Path sessionDir = Path.of(System.getProperty("user.home"),
        ".config", "claude", "projects", "-workspace");
      Files.createDirectories(sessionDir);

      String sessionId = "test-context-pct-" + Thread.currentThread().threadId();
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");

      try
      {
        // 50000 tokens out of 200000 limit = 25%
        String assistantEntry = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
          "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_c\",\"name\":\"Task\"," +
          "\"input\":{\"prompt\":\"## Check context\\nAnalyze\"," +
          "\"description\":\"Check context\"}}]}}";
        String resultEntry = "{\"type\":\"user\",\"toolUseResult\":{\"tool_use_id\":\"toolu_c\"," +
          "\"totalTokens\":50000,\"durationMs\":15000}}";

        Files.writeString(sessionFile, assistantEntry + "\n" + resultEntry + "\n");

        GetTokenReportOutput handler = new GetTokenReportOutput(scope);
        String result = handler.getOutput(sessionId);

        requireThat(result, "result").isNotNull().contains("25%");
      }
      finally
      {
        Files.deleteIfExists(sessionFile);
      }
    }
  }
}
