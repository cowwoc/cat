/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.InvestigationContextExtractor;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Tests for InvestigationContextExtractor.
 */
public final class InvestigationContextExtractorTest
{
  /**
   * Builds an assistant message JSONL line containing a single tool_use block.
   *
   * @param toolId    the tool use ID
   * @param toolName  the tool name
   * @param inputJson the JSON content of the input object (without outer braces)
   * @param timestamp the ISO timestamp string
   * @return a single JSONL line
   */
  private static String assistantLine(String toolId, String toolName, String inputJson,
    String timestamp)
  {
    return "{\"type\":\"assistant\",\"timestamp\":\"" + timestamp + "\"," +
      "\"message\":{\"content\":[{\"type\":\"tool_use\",\"id\":\"" + toolId +
      "\",\"name\":\"" + toolName +
      "\",\"input\":{" + inputJson + "}}]}}";
  }

  /**
   * Builds a tool result JSONL line.
   *
   * @param toolUseId  the tool use ID being correlated
   * @param resultText the result text content
   * @return a single JSONL line
   */
  private static String toolResultLine(String toolUseId, String resultText)
  {
    return "{\"type\":\"tool\",\"content\":[{\"type\":\"tool_result\"," +
      "\"tool_use_id\":\"" + toolUseId + "\"," +
      "\"content\":\"" + resultText + "\"}]}";
  }

  /**
   * Verifies that Read tool invocations are extracted as documents_read entries.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void extractsDocumentsReadFromReadTool() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl = assistantLine("t1", "Read",
        "\"file_path\":\"/workspace/foo.java\"", "2026-01-01T00:00:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode docs = result.path("documents_read");
      requireThat(docs.size(), "docsSize").isEqualTo(1);
      requireThat(docs.get(0).path("path").asString(),
        "path").isEqualTo("/workspace/foo.java");
      requireThat(docs.get(0).path("tool").asString(), "tool").isEqualTo("Read");
      requireThat(docs.get(0).path("timestamp").asString(),
        "timestamp").isEqualTo("2026-01-01T00:00:00Z");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Glob tool invocations are extracted as documents_read entries using the pattern field.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void extractsGlobPatternsAsDocumentsRead() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl = assistantLine("t1", "Glob",
        "\"pattern\":\"**/*.java\"", "2026-01-02T00:00:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode docs = result.path("documents_read");
      requireThat(docs.size(), "docsSize").isEqualTo(1);
      requireThat(docs.get(0).path("path").asString(), "path").isEqualTo("**/*.java");
      requireThat(docs.get(0).path("tool").asString(), "tool").isEqualTo("Glob");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Skill tool invocations are extracted as skill_invocations entries.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void extractsSkillInvocations() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl = assistantLine("t1", "Skill",
        "\"skill\":\"cat:git-squash\",\"args\":\"--dry-run\"", "2026-01-03T00:00:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode skills = result.path("skill_invocations");
      requireThat(skills.size(), "skillsSize").isEqualTo(1);
      requireThat(skills.get(0).path("skill").asString(), "skill").isEqualTo("cat:git-squash");
      requireThat(skills.get(0).path("args").asString(), "args").isEqualTo("--dry-run");
      requireThat(skills.get(0).path("timestamp").asString(),
        "timestamp").isEqualTo("2026-01-03T00:00:00Z");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Bash commands matching provided keywords are included while non-matching ones are excluded.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filtersBashCommandsByKeywords() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"git squash HEAD~3\"", "2026-01-04T00:00:00Z") +
        "\n" +
        assistantLine("t2", "Bash", "\"command\":\"ls -la\"", "2026-01-04T00:01:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of("squash"));

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("command").asString(),
        "command").contains("squash");
      requireThat(commands.get(0).path("matched_keywords").size(),
        "matchedKeywordsSize").isEqualTo(1);
      requireThat(commands.get(0).path("matched_keywords").get(0).asString(),
        "matchedKeyword").isEqualTo("squash");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that all Bash commands are included when no keywords are specified.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void includesAllBashCommandsWhenNoKeywords() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"git status\"", "2026-01-05T00:00:00Z") +
        "\n" +
        assistantLine("t2", "Bash", "\"command\":\"mvn verify\"", "2026-01-05T00:01:00Z") + "\n" +
        assistantLine("t3", "Bash", "\"command\":\"ls\"", "2026-01-05T00:02:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("bash_commands").size(), "bashCommandsSize").isEqualTo(3);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Bash command results are correlated by tool_use_id using O(1) HashMap lookup.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void correlatesBashResultsByToolUseId() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("tool-abc", "Bash", "\"command\":\"git log --oneline -5\"",
          "2026-01-06T00:00:00Z") + "\n" +
        toolResultLine("tool-abc", "abc123 commit message") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("result").asString(),
        "result").isEqualTo("abc123 commit message");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that malformed JSON lines are skipped and counted in parse_errors_skipped.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsMalformedJsonLinesAndCountsThem() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Read", "\"file_path\":\"/foo.txt\"", "2026-01-07T00:00:00Z") + "\n" +
        "{this is not valid json}\n" +
        "{also bad json\n" +
        assistantLine("t2", "Read", "\"file_path\":\"/bar.txt\"", "2026-01-07T00:01:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("parse_errors_skipped").asInt(),
        "parseErrorsSkipped").isEqualTo(2);
      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(2);

      JsonNode parseErrors = result.path("parse_errors");
      requireThat(parseErrors.size(), "parseErrorsSize").isEqualTo(2);

      // First error: "{this is not valid json}" on line 2
      requireThat(parseErrors.get(0).path("line_number").asInt(), "errorLine0").isEqualTo(2);
      requireThat(parseErrors.get(0).path("line_preview").asString(), "errorPreview0")
        .isEqualTo("{this is not valid json}");
      requireThat(parseErrors.get(0).has("error"), "hasError0").isTrue();

      // Second error: "{also bad json" on line 3
      requireThat(parseErrors.get(1).path("line_number").asInt(), "errorLine1").isEqualTo(3);
      requireThat(parseErrors.get(1).path("line_preview").asString(), "errorPreview1")
        .isEqualTo("{also bad json");
      requireThat(parseErrors.get(1).has("error"), "hasError1").isTrue();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Bash command results are truncated at 2000 characters.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void truncatesBashResultsAt2000Chars() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String longResult = "x".repeat(3000);
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"cat bigfile.txt\"", "2026-01-08T00:00:00Z") +
        "\n" +
        toolResultLine("t1", longResult) + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      String resultText = result.path("bash_commands").get(0).path("result").asString();
      requireThat(resultText.length(), "resultLength").isEqualTo(2000);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that timeline_events are capped at 200 entries and timeline_truncated is set to true.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void capsTimelineEventsAt200AndSetsTruncatedFlag() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      StringBuilder jsonl = new StringBuilder();
      for (int i = 0; i < 210; ++i)
      {
        jsonl.append(assistantLine("t" + i, "Read",
          "\"file_path\":\"/file" + i + ".txt\"", "2026-01-09T00:00:0" +
            String.format("%02d", i % 60) + "Z")).append('\n');
      }
      Files.writeString(tempFile, jsonl.toString());

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("timeline_events").size(), "timelineEventsSize").isEqualTo(200);
      requireThat(result.path("timeline_truncated").asBoolean(), "timelineTruncated").isTrue();
      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(210);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that an empty session file produces empty collections with zero counts.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handlesEmptySessionFile() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      Files.writeString(tempFile, "");

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(0);
      requireThat(result.path("skill_invocations").size(), "skillsSize").isEqualTo(0);
      requireThat(result.path("bash_commands").size(), "commandsSize").isEqualTo(0);
      requireThat(result.path("timeline_events").size(), "timelineSize").isEqualTo(0);
      requireThat(result.path("total_messages_scanned").asInt(), "totalMessages").isEqualTo(0);
      requireThat(result.path("parse_errors_skipped").asInt(), "parseErrors").isEqualTo(0);
      requireThat(result.path("parse_errors").size(), "parseErrorsSize").isEqualTo(0);
      requireThat(result.path("timeline_truncated").asBoolean(), "timelineTruncated").isFalse();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that the timezone_context field reflects the timezone parameter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void includesTimezoneContext() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      Files.writeString(tempFile, "");

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("timezone_context").asString(),
        "timezoneContext").startsWith("TZ=");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that an assistant entry whose message content is a string (not an array) is silently skipped.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsAssistantEntryWithNonArrayContent() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl = "{\"type\":\"assistant\",\"timestamp\":\"2026-01-10T00:00:00Z\"," +
        "\"message\":{\"content\":\"just a string\"}}\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(0);
      requireThat(result.path("skill_invocations").size(), "skillsSize").isEqualTo(0);
      requireThat(result.path("bash_commands").size(), "commandsSize").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that tool_use entries with an unknown tool name are silently skipped.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsUnknownToolTypes() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "UnknownTool", "\"foo\":\"bar\"", "2026-01-10T00:01:00Z") + "\n" +
        assistantLine("t2", "Read", "\"file_path\":\"/test.txt\"", "2026-01-10T00:02:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(1);
      requireThat(result.path("bash_commands").size(), "commandsSize").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that a Bash command with an empty tool ID is recorded but cannot be correlated with a result.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handlesBashCommandWithEmptyToolId() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl = assistantLine("", "Bash", "\"command\":\"echo hello\"",
        "2026-01-10T00:03:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("command").asString(), "command").isEqualTo("echo hello");
      requireThat(commands.get(0).path("result").isNull(), "resultIsNull").isTrue();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that a tool result for a non-Bash tool (e.g., Read) is silently skipped
   * and does not affect bash_commands.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsToolResultWithoutMatchingBashCommand() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Read", "\"file_path\":\"/test.txt\"", "2026-01-10T00:04:00Z") + "\n" +
        toolResultLine("t1", "file contents here") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      requireThat(result.path("documents_read").size(), "docsSize").isEqualTo(1);
      requireThat(result.path("bash_commands").size(), "commandsSize").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that result_truncated is set to true when a Bash result exceeds the 2000-character limit.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void setsResultTruncatedFlagWhenResultExceedsLimit() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String longResult = "x".repeat(3000);
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"cat big\"", "2026-01-10T00:05:00Z") + "\n" +
        toolResultLine("t1", longResult) + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("result").asString().length(), "resultLength").isEqualTo(2000);
      requireThat(commands.get(0).path("result_truncated").asBoolean(),
        "resultTruncated").isTrue();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that result_truncated is set to false when a Bash result is within the 2000-character limit.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void setsResultTruncatedFalseForShortResults() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"echo hi\"", "2026-01-10T00:06:00Z") + "\n" +
        toolResultLine("t1", "short result") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("result_truncated").asBoolean(),
        "resultTruncated").isFalse();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that bash_commands entries include the correct 1-based JSONL line_number.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void includesLineNumberInBashCommands() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Read", "\"file_path\":\"/a.txt\"", "2026-01-10T00:07:00Z") + "\n" +
        assistantLine("t2", "Read", "\"file_path\":\"/b.txt\"", "2026-01-10T00:08:00Z") + "\n" +
        assistantLine("t3", "Bash", "\"command\":\"ls\"", "2026-01-10T00:09:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode commands = result.path("bash_commands");
      requireThat(commands.size(), "commandsSize").isEqualTo(1);
      requireThat(commands.get(0).path("line_number").asInt(), "lineNumber").isEqualTo(3);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that the session_file and keywords fields are correctly included in the output.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void includesSessionFileAndKeywordsInOutput() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      Files.writeString(tempFile, "");

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of("git", "squash"));

      requireThat(result.path("session_file").asString(),
        "sessionFile").isEqualTo(tempFile.toString());
      JsonNode keywords = result.path("keywords");
      requireThat(keywords.size(), "keywordsSize").isEqualTo(2);
      requireThat(keywords.get(0).asString(), "keyword0").isEqualTo("git");
      requireThat(keywords.get(1).asString(), "keyword1").isEqualTo("squash");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that timeline events for long Bash commands include a "..." truncation indicator.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void appendsEllipsisToTruncatedTimelineEvents() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String longCommand = "x".repeat(200);
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"" + longCommand + "\"",
          "2026-01-10T00:10:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode timeline = result.path("timeline_events");
      requireThat(timeline.size(), "timelineSize").isEqualTo(1);
      String event = timeline.get(0).asString();
      requireThat(event, "event").contains("[truncated]");
      requireThat(event, "event").endsWith("[truncated]");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that timeline events for short Bash commands do not include "..." truncation.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void doesNotAppendEllipsisToShortTimelineEvents() throws IOException
  {
    Path tempFile = Files.createTempFile("extract-", ".jsonl");
    try
    {
      String jsonl =
        assistantLine("t1", "Bash", "\"command\":\"ls -la\"",
          "2026-01-10T00:11:00Z") + "\n";
      Files.writeString(tempFile, jsonl);

      InvestigationContextExtractor extractor =
        new InvestigationContextExtractor(new TestJvmScope());
      JsonNode result = extractor.extract(tempFile, List.of());

      JsonNode timeline = result.path("timeline_events");
      requireThat(timeline.size(), "timelineSize").isEqualTo(1);
      String event = timeline.get(0).asString();
      requireThat(event, "event").doesNotContain("[truncated]");
      requireThat(event, "event").endsWith("ls -la");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }
}
