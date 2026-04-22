/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.ConversationLogUtils;
import io.github.cowwoc.cat.claude.hook.util.TurnSegment;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ConversationLogUtils}.
 */
public final class ConversationLogUtilsTest
{
  /**
   * Verifies that a pure string content message returns its text directly.
   */
  @Test
  public void stringContentReturnsText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = "{\"role\":\"assistant\",\"content\":\"I will implement the solution.\"}";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEqualTo("I will implement the solution.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a pure-text array message (no tool_use blocks) returns concatenated text.
   */
  @Test
  public void pureTextArrayReturnsText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[{"type":"text","text":"First part."},\
        {"type":"text","text":"Second part."}]}""";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEqualTo("First part. Second part.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a compound message (text + tool_use) returns empty string.
   * <p>
   * When an assistant message contains both text blocks and tool_use blocks, the text describes
   * the upcoming tool invocation rather than analytical reasoning. Scanning it would cause false
   * positives in DetectAssistantGivingUp (e.g., "Let me remove X" → CODE_REMOVAL false positive).
   */
  @Test
  public void compoundMessageWithTextAndToolUseReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Let me remove the stale worktrees."},\
        {"type":"tool_use","name":"Bash","input":{"command":"rm -rf old/"}}]}""";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a message with only a tool_use block returns empty string.
   * <p>
   * Tool invocations have no text content to extract.
   */
  @Test
  public void toolUseOnlyReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[{"type":"tool_use","name":"Skill",\
        "input":{"args":"given token usage let me fix this"}}]}""";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing content field returns empty string.
   */
  @Test
  public void missingContentReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = "{\"role\":\"user\",\"other\":\"value\"}";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an invalid JSON line returns empty string.
   */
  @Test
  public void invalidJsonReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String result = ConversationLogUtils.extractTextContent("not valid json", mapper);
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a wrapped message (content under "message" key) is extracted correctly.
   */
  @Test
  public void wrappedMessageFormatExtractsText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = "{\"message\":{\"role\":\"assistant\",\"content\":\"Wrapped text here.\"}}";
      String result = ConversationLogUtils.extractTextContent(line, mapper);
      requireThat(result, "result").isEqualTo("Wrapped text here.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ── extractSegments() ────────────────────────────────────────────────────

  /**
   * Verifies that string content produces a single segment with both paths null.
   */
  @Test
  public void extractSegmentsStringContentReturnsSingleSegment() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = "{\"role\":\"assistant\",\"content\":\"Plain text.\"}";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).text(), "text").isEqualTo("Plain text.");
      requireThat(segments.get(0).aboveFilePath(), "aboveFilePath").isNull();
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a pure-text array (no tool_use blocks) produces segments with both paths null.
   */
  @Test
  public void extractSegmentsPureTextArrayReturnsBothPathsNull() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[{"type":"text","text":"First."},\
        {"type":"text","text":"Second."}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(2);
      requireThat(segments.get(0).aboveFilePath(), "aboveFilePath").isNull();
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isNull();
      requireThat(segments.get(1).aboveFilePath(), "aboveFilePath").isNull();
      requireThat(segments.get(1).belowFilePath(), "belowFilePath").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit tool_use block immediately before a text block sets aboveFilePath.
   */
  @Test
  public void extractSegmentsEditToolBeforeTextSetsAboveFilePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"tool_use","name":"Edit","input":{"file_path":"/workspace/Foo.java"}},\
        {"type":"text","text":"Fixed the bug."}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).aboveFilePath(), "aboveFilePath").isEqualTo("/workspace/Foo.java");
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Bash tool_use block immediately after a text block sets belowFilePath when
   * the command contains a code file with a directory separator.
   */
  @Test
  public void extractSegmentsBashWithCodeFileAfterTextSetsBelowFilePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Compiling."},\
        {"type":"tool_use","name":"Bash","input":{"command":"javac /workspace/src/Main.java"}}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).aboveFilePath(), "aboveFilePath").isNull();
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isEqualTo("/workspace/src/Main.java");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Bash command containing only a bare filename (no directory separator) still sets
   * belowFilePath, because CODE_PATH_PATTERN does not require a slash.
   */
  @Test
  public void extractSegmentsBashWithBareFilenameSetsBelowFilePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Compiling."},\
        {"type":"tool_use","name":"Bash","input":{"command":"javac Main.java"}}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isEqualTo("Main.java");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a NotebookEdit tool_use block extracts the notebook_path field.
   */
  @Test
  public void extractSegmentsNotebookEditExtractsNotebookPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Updating notebook."},\
        {"type":"tool_use","name":"NotebookEdit","input":{"notebook_path":"/workspace/analysis.ipynb"}}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isEqualTo("/workspace/analysis.ipynb");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Bash command touching only a non-code file produces a null belowFilePath.
   */
  @Test
  public void extractSegmentsBashWithNonCodeFileProducesNullBelowFilePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Cleaning up."},\
        {"type":"tool_use","name":"Bash","input":{"command":"rm /workspace/.lock"}}]}""";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments.size(), "size").isEqualTo(1);
      requireThat(segments.get(0).belowFilePath(), "belowFilePath").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing content field returns an empty segment list.
   */
  @Test
  public void extractSegmentsMissingContentReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String line = "{\"role\":\"user\",\"other\":\"value\"}";
      List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
      requireThat(segments, "segments").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an invalid JSON line returns an empty segment list.
   */
  @Test
  public void extractSegmentsInvalidJsonReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      List<TurnSegment> segments = ConversationLogUtils.extractSegments("not valid json", mapper);
      requireThat(segments, "segments").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
