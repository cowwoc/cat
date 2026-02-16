/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.EvaluationResult;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.ParsedOutput;
import io.github.cowwoc.cat.hooks.skills.PrimingMessage;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link EmpiricalTestRunner}.
 */
public final class EmpiricalTestRunnerTest
{
  /**
   * Verifies that buildInput with empty priming produces only the test prompt message.
   */
  @Test
  public void buildInputWithEmptyPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      String result = runner.buildInput(new ArrayList<PrimingMessage>(), "hello world");

      // Should be a single line (one user message)
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(1);

      JsonNode parsed = mapper.readTree(lines[0]);
      requireThat(parsed.path("type").asString(""), "type").isEqualTo("user");
      requireThat(parsed.path("message").path("role").asString(""), "role").isEqualTo("user");
      requireThat(parsed.path("message").path("content").get(0).path("type").asString(""),
        "contentType").isEqualTo("text");
      requireThat(parsed.path("message").path("content").get(0).path("text").asString(""),
        "text").isEqualTo("hello world");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with string-only priming produces user messages for each string
   * plus the test prompt.
   */
  @Test
  public void buildInputWithStringOnlyPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("first message"),
        new PrimingMessage.UserMessage("second message"));

      String result = runner.buildInput(priming, "test prompt");

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);

      // Verify first priming message
      JsonNode first = mapper.readTree(lines[0]);
      requireThat(first.path("message").path("content").get(0).path("text").asString(""),
        "firstText").isEqualTo("first message");

      // Verify second priming message
      JsonNode second = mapper.readTree(lines[1]);
      requireThat(second.path("message").path("content").get(0).path("text").asString(""),
        "secondText").isEqualTo("second message");

      // Verify test prompt
      JsonNode third = mapper.readTree(lines[2]);
      requireThat(third.path("message").path("content").get(0).path("text").asString(""),
        "testPrompt").isEqualTo("test prompt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with tool_use-only priming produces assistant + user message pairs
   * followed by the test prompt.
   */
  @Test
  public void buildInputWithToolUseOnlyPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", Map.of("command", "ls"), "file1.txt\nfile2.txt"));

      String result = runner.buildInput(priming, "test prompt");

      // tool_use generates 2 messages (assistant + user tool_result), plus test prompt = 3 lines
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);

      // Verify assistant tool_use message
      JsonNode assistantMsg = mapper.readTree(lines[0]);
      requireThat(assistantMsg.path("type").asString(""), "type").isEqualTo("assistant");
      String role = assistantMsg.path("message").path("role").asString("");
      requireThat(role, "role").isEqualTo("assistant");
      JsonNode toolUseContent = assistantMsg.path("message").path("content").get(0);
      String contentType = toolUseContent.path("type").asString("");
      requireThat(contentType, "contentType").isEqualTo("tool_use");
      requireThat(toolUseContent.path("name").asString(""), "toolName").isEqualTo("Bash");
      String command = toolUseContent.path("input").path("command").asString("");
      requireThat(command, "command").isEqualTo("ls");

      // Verify user tool_result message
      JsonNode resultMsg = mapper.readTree(lines[1]);
      requireThat(resultMsg.path("type").asString(""), "type").isEqualTo("user");
      JsonNode resultContent = resultMsg.path("message").path("content").get(0);
      String resultContentType = resultContent.path("type").asString("");
      requireThat(resultContentType, "contentType").isEqualTo("tool_result");
      String toolOutput = resultContent.path("content").asString("");
      requireThat(toolOutput, "toolOutput").isEqualTo("file1.txt\nfile2.txt");

      // Verify tool_use_id matches between the pair
      String toolUseId = toolUseContent.path("id").asString("");
      String toolResultId = resultContent.path("tool_use_id").asString("");
      requireThat(toolResultId, "toolResultId").isEqualTo(toolUseId);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with mixed priming (strings and tool_use) produces messages in the
   * correct order.
   */
  @Test
  public void buildInputWithMixedPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("user prompt"),
        new PrimingMessage.ToolUse("Read", Map.of("file_path", "/tmp/test.txt"), "contents"));

      String result = runner.buildInput(priming, "final prompt");

      // 1 user message + 2 tool messages + 1 test prompt = 4 lines
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(4);

      // First: user message
      JsonNode firstMsg = mapper.readTree(lines[0]);
      requireThat(firstMsg.path("type").asString(""), "firstType").isEqualTo("user");

      // Second: assistant tool_use
      JsonNode secondMsg = mapper.readTree(lines[1]);
      requireThat(secondMsg.path("type").asString(""), "secondType").isEqualTo("assistant");

      // Third: user tool_result
      JsonNode thirdMsg = mapper.readTree(lines[2]);
      requireThat(thirdMsg.path("message").path("content").get(0).path("type").asString(""),
        "thirdContentType").isEqualTo("tool_result");

      // Fourth: test prompt
      JsonNode fourthMsg = mapper.readTree(lines[3]);
      requireThat(fourthMsg.path("message").path("content").get(0).path("text").asString(""),
        "testPrompt").isEqualTo("final prompt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that fromRawList rejects tool_use messages missing the 'tool' field.
   */
  @Test
  public void fromRawListRejectsMissingToolField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("input", Map.of("command", "ls"));
    toolUseMsg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("tool");
      requireThat(e.getMessage(), "message").contains("priming_messages[0]");
    }
  }

  /**
   * Verifies that fromRawList rejects tool_use messages missing the 'input' field.
   */
  @Test
  public void fromRawListRejectsMissingInputField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("tool", "Bash");
    toolUseMsg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("input");
      requireThat(e.getMessage(), "message").contains("priming_messages[0]");
    }
  }

  /**
   * Verifies that fromRawList rejects tool_use messages missing the 'output' field.
   */
  @Test
  public void fromRawListRejectsMissingOutputField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("tool", "Bash");
    toolUseMsg.put("input", Map.of("command", "ls"));

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("output");
      requireThat(e.getMessage(), "message").contains("priming_messages[0]");
    }
  }

  /**
   * Verifies that fromRawList rejects null rawMessages parameter.
   */
  @Test
  public void fromRawListRejectsNullRawMessages()
  {
    try
    {
      PrimingMessage.fromRawList(null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("rawMessages");
    }
  }

  /**
   * Verifies that fromRawList rejects unsupported message types like Integer.
   */
  @Test
  public void fromRawListRejectsUnsupportedMessageType()
  {
    List<Object> raw = new ArrayList<>();
    raw.add(42);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("unsupported message type");
      requireThat(e.getMessage(), "message").contains("priming_messages[0]");
    }
  }

  /**
   * Verifies that fromRawList rejects a Map with a type value other than "tool_use".
   */
  @Test
  public void fromRawListRejectsWrongTypeValue()
  {
    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "invalid");
    msg.put("tool", "Bash");
    msg.put("input", Map.of("command", "ls"));
    msg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(msg);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("unsupported type");
      requireThat(e.getMessage(), "message").contains("'invalid'");
    }
  }

  /**
   * Verifies that each line of buildInput output is valid single-line JSONL (no embedded newlines).
   */
  @Test
  public void buildInputProducesValidJsonl() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("prompt with\nnewline"),
        new PrimingMessage.ToolUse("Bash", Map.of("command", "echo 'hello\nworld'"),
          "hello\nworld"));

      String result = runner.buildInput(priming, "test");

      // Each line should be parseable as JSON individually
      for (String line : result.split("\n"))
      {
        JsonNode node = mapper.readTree(line);
        requireThat(node.isObject(), "isObject").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput extracts text blocks from assistant messages.
   */
  @Test
  public void parseOutputExtractsText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Hello world"}]}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.texts().size(), "textCount").isEqualTo(1);
      requireThat(parsed.texts().get(0), "text").isEqualTo("Hello world");
      requireThat(parsed.toolUses().isEmpty(), "noToolUses").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput extracts tool_use names from assistant messages.
   */
  @Test
  public void parseOutputExtractsToolUse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
        "\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\"," +
        "\"id\":\"123\",\"input\":{\"command\":\"ls\"}}]}}\n";

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.toolUses().size(), "toolUseCount").isEqualTo(1);
      requireThat(parsed.toolUses().get(0), "toolName").isEqualTo("Bash");
      requireThat(parsed.texts().isEmpty(), "noTexts").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput extracts text from result events.
   */
  @Test
  public void parseOutputExtractsResultText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"result","result":"Final answer"}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.texts().size(), "textCount").isEqualTo(1);
      requireThat(parsed.texts().get(0), "text").isEqualTo("Final answer");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput gracefully handles malformed JSON lines.
   */
  @Test
  public void parseOutputHandlesMalformedJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        not valid json
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"valid"}]}}
        also not json {{{
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.texts().size(), "textCount").isEqualTo(1);
      requireThat(parsed.texts().get(0), "text").isEqualTo("valid");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles assistant messages with null type field gracefully.
   */
  @Test
  public void parseOutputHandlesMissingTypeField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"message":{"role":"assistant","content":[{"type":"text","text":"no envelope type"}]}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.texts().isEmpty(), "noTexts").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles content blocks with missing type field.
   */
  @Test
  public void parseOutputHandlesContentBlockMissingType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"text":"no block type"}]}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      // Block without type should be skipped
      requireThat(parsed.texts().isEmpty(), "noTexts").isTrue();
      requireThat(parsed.toolUses().isEmpty(), "noToolUses").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles content blocks with non-string type field.
   */
  @Test
  public void parseOutputHandlesNonStringTypeField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"type":123,"text":"numeric type"}]}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      // Non-string type should not match "text" or "tool_use"
      requireThat(parsed.texts().isEmpty(), "noTexts").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles empty output string.
   */
  @Test
  public void parseOutputHandlesEmptyOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      ParsedOutput parsed = runner.parseOutput("");
      requireThat(parsed.texts().isEmpty(), "noTexts").isTrue();
      requireThat(parsed.toolUses().isEmpty(), "noToolUses").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_contain checks case insensitively.
   */
  @Test
  public void evaluateOutputMustContainIsCaseInsensitive() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("Hello World");
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_contain", List.of("hello world"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("contains:hello world"), "check").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_contain fails when term is absent.
   */
  @Test
  public void evaluateOutputMustContainFailsWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("something else");
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_contain", List.of("hello"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isFalse();
      requireThat(result.checks().get("contains:hello"), "check").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_not_contain passes when term is absent.
   */
  @Test
  public void evaluateOutputMustNotContainPassesWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("clean output");
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_not_contain", List.of("error"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("not_contains:error"), "check").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_not_contain fails when term is present.
   */
  @Test
  public void evaluateOutputMustNotContainFailsWhenPresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("an error occurred");
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_not_contain", List.of("error"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_use_tools passes when tool is present.
   */
  @Test
  public void evaluateOutputMustUseToolsPasses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("output");
      List<String> toolUses = List.of("Bash", "Read");
      Map<String, Object> criteria = Map.of("must_use_tools", List.of("Bash"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("uses_tool:Bash"), "check").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_use_tools fails when tool is absent.
   */
  @Test
  public void evaluateOutputMustUseToolsFailsWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("output");
      List<String> toolUses = List.of("Read");
      Map<String, Object> criteria = Map.of("must_use_tools", List.of("Bash"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_not_use_tools passes when tool is absent.
   */
  @Test
  public void evaluateOutputMustNotUseToolsPasses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("output");
      List<String> toolUses = List.of("Read");
      Map<String, Object> criteria = Map.of("must_not_use_tools", List.of("Bash"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("not_uses_tool:Bash"), "check").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with must_not_use_tools fails when tool is present.
   */
  @Test
  public void evaluateOutputMustNotUseToolsFailsWhenPresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("output");
      List<String> toolUses = List.of("Bash");
      Map<String, Object> criteria = Map.of("must_not_use_tools", List.of("Bash"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with empty criteria passes.
   */
  @Test
  public void evaluateOutputWithEmptyCriteriaPasses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("any output");
      List<String> toolUses = List.of("Bash");
      Map<String, Object> criteria = new HashMap<>();

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().isEmpty(), "noChecks").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput preserves the full term in check keys.
   */
  @Test
  public void evaluateOutputPreservesFullTermInKey() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String longTerm = "a".repeat(100);
      List<String> texts = List.of(longTerm);
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_contain", List.of(longTerm));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isTrue();

      String expectedKey = "contains:" + longTerm;
      requireThat(result.checks().containsKey(expectedKey), "hasKey").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that makeToolUseMessage produces valid JSON with required fields including
   * complex nested inputs.
   */
  @Test
  public void buildInputWithToolUseComplexInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      Map<String, Object> complexInput = new HashMap<>();
      complexInput.put("command", "git log --oneline");
      complexInput.put("timeout", 30_000);
      complexInput.put("description", "List recent commits");

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", complexInput, "abc123 Initial commit"));

      String result = runner.buildInput(priming, "test");

      String[] lines = result.split("\n");
      JsonNode assistantMsg = mapper.readTree(lines[0]);
      JsonNode input = assistantMsg.path("message").path("content").get(0).path("input");
      String commandValue = input.path("command").asString("");
      requireThat(commandValue, "command").isEqualTo("git log --oneline");
      requireThat(input.path("timeout").asInt(0), "timeout").isEqualTo(30_000);
      String descValue = input.path("description").asString("");
      requireThat(descValue, "description").isEqualTo("List recent commits");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles tool_use content blocks with null name field.
   */
  @Test
  public void parseOutputHandlesToolUseWithNullFields() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"123","input":{}}]}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      // Should still extract a tool use, with empty name
      requireThat(parsed.toolUses().size(), "toolUseCount").isEqualTo(1);
      requireThat(parsed.toolUses().get(0), "toolName").isEqualTo("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that calculateRate handles 0 passes / 5 trials correctly.
   */
  @Test
  public void calculateRateZeroPassesReturnsZero()
  {
    int rate = EmpiricalTestRunner.calculateRate(0, 5);
    requireThat(rate, "rate").isEqualTo(0);
  }

  /**
   * Verifies that calculateRate handles 5 passes / 5 trials correctly.
   */
  @Test
  public void calculateRateFivePassesReturnsOneHundred()
  {
    int rate = EmpiricalTestRunner.calculateRate(5, 5);
    requireThat(rate, "rate").isEqualTo(100);
  }

  /**
   * Verifies that calculateRate rounds 1 pass / 3 trials to 33%.
   */
  @Test
  public void calculateRateOneOfThreeRoundsToThirtyThree()
  {
    int rate = EmpiricalTestRunner.calculateRate(1, 3);
    requireThat(rate, "rate").isEqualTo(33);
  }

  /**
   * Verifies that calculateRate rounds 2 passes / 3 trials to 67%.
   */
  @Test
  public void calculateRateTwoOfThreeRoundsToSixtySeven()
  {
    int rate = EmpiricalTestRunner.calculateRate(2, 3);
    requireThat(rate, "rate").isEqualTo(67);
  }

  /**
   * Verifies that buildInput with multiple sequential tool_use messages generates sequential IDs.
   */
  @Test
  public void buildInputWithMultipleToolUsesGeneratesSequentialIds() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", Map.of("command", "ls"), "output1"),
        new PrimingMessage.ToolUse("Read", Map.of("file_path", "/tmp/test"), "output2"),
        new PrimingMessage.ToolUse("Write", Map.of("file_path", "/tmp/out"), "output3"));

      String result = runner.buildInput(priming, "test");

      String[] lines = result.split("\n");
      // 3 tool uses = 6 messages (assistant + user for each) + 1 test prompt = 7 lines
      requireThat(lines.length, "lineCount").isEqualTo(7);

      // Verify first tool_use has ID toolu_priming_0
      JsonNode firstToolUse = mapper.readTree(lines[0]);
      String firstId = firstToolUse.path("message").path("content").get(0).path("id").asString("");
      requireThat(firstId, "firstId").isEqualTo("toolu_priming_0");

      // Verify first tool_result references toolu_priming_0
      JsonNode firstResult = mapper.readTree(lines[1]);
      String firstResultId = firstResult.path("message").path("content").get(0).
        path("tool_use_id").asString("");
      requireThat(firstResultId, "firstResultId").isEqualTo("toolu_priming_0");

      // Verify second tool_use has ID toolu_priming_1
      JsonNode secondToolUse = mapper.readTree(lines[2]);
      String secondId = secondToolUse.path("message").path("content").get(0).path("id").asString("");
      requireThat(secondId, "secondId").isEqualTo("toolu_priming_1");

      // Verify second tool_result references toolu_priming_1
      JsonNode secondResult = mapper.readTree(lines[3]);
      String secondResultId = secondResult.path("message").path("content").get(0).
        path("tool_use_id").asString("");
      requireThat(secondResultId, "secondResultId").isEqualTo("toolu_priming_1");

      // Verify third tool_use has ID toolu_priming_2
      JsonNode thirdToolUse = mapper.readTree(lines[4]);
      String thirdId = thirdToolUse.path("message").path("content").get(0).path("id").asString("");
      requireThat(thirdId, "thirdId").isEqualTo("toolu_priming_2");

      // Verify third tool_result references toolu_priming_2
      JsonNode thirdResult = mapper.readTree(lines[5]);
      String thirdResultId = thirdResult.path("message").path("content").get(0).
        path("tool_use_id").asString("");
      requireThat(thirdResultId, "thirdResultId").isEqualTo("toolu_priming_2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput with multiple criteria handles mixed pass/fail results.
   */
  @Test
  public void evaluateOutputWithMultipleCriteriaMixedResults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> texts = List.of("hello world");
      List<String> toolUses = new ArrayList<>();
      Map<String, Object> criteria = Map.of("must_contain", List.of("hello", "missing"));

      EvaluationResult result = runner.evaluateOutput(texts, toolUses, criteria);
      requireThat(result.pass(), "pass").isFalse();
      requireThat(result.checks().get("contains:hello"), "helloCheck").isTrue();
      requireThat(result.checks().get("contains:missing"), "missingCheck").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput handles mixed content with text, tool_use, and result events.
   */
  @Test
  public void parseOutputWithMixedContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"thinking"}]}}
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","name":"Bash","id":"123","input":{}}]}}
        {"type":"result","result":"done"}
        """;

      ParsedOutput parsed = runner.parseOutput(output);
      requireThat(parsed.texts().size(), "textCount").isEqualTo(2);
      requireThat(parsed.texts().get(0), "firstText").isEqualTo("thinking");
      requireThat(parsed.texts().get(1), "secondText").isEqualTo("done");
      requireThat(parsed.toolUses().size(), "toolUseCount").isEqualTo(1);
      requireThat(parsed.toolUses().get(0), "toolName").isEqualTo("Bash");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the constructor rejects null scope.
   */
  @Test
  public void constructorRejectsNullScope()
  {
    try
    {
      new EmpiricalTestRunner(null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("scope");
    }
  }

  /**
   * Verifies that buildInput rejects null primingMessages.
   */
  @Test
  public void buildInputRejectsNullPrimingMessages() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.buildInput(null, "prompt");
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("primingMessages");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput rejects null testPrompt.
   */
  @Test
  public void buildInputRejectsNullTestPrompt() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.buildInput(List.of(), null);
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("testPrompt");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput rejects null output.
   */
  @Test
  public void parseOutputRejectsNullOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.parseOutput(null);
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("output");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null texts.
   */
  @Test
  public void evaluateOutputRejectsNullTexts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.evaluateOutput(null, List.of(), Map.of());
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("texts");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null toolUses.
   */
  @Test
  public void evaluateOutputRejectsNullToolUses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.evaluateOutput(List.of(), null, Map.of());
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("toolUses");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null criteria.
   */
  @Test
  public void evaluateOutputRejectsNullCriteria() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path envFile = Files.createTempFile(tempDir, "env", ".sh");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir, "test-session",
      envFile, TerminalType.WINDOWS_TERMINAL))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      try
      {
        runner.evaluateOutput(List.of(), List.of(), null);
        requireThat(false, "shouldThrow").isTrue();
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("criteria");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that fromRawList rejects a list containing null elements.
   */
  @Test
  public void fromRawListRejectsNullElements()
  {
    List<Object> raw = new ArrayList<>();
    raw.add("valid message");
    raw.add(null);

    try
    {
      PrimingMessage.fromRawList(raw);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("rawMessages");
    }
  }

  /**
   * Verifies that UserMessage rejects null text.
   */
  @Test
  public void userMessageRejectsNullText()
  {
    try
    {
      new PrimingMessage.UserMessage(null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("text");
    }
  }

  /**
   * Verifies that ToolUse rejects null tool.
   */
  @Test
  public void toolUseRejectsNullTool()
  {
    try
    {
      new PrimingMessage.ToolUse(null, Map.of(), "out");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("tool");
    }
  }

  /**
   * Verifies that ToolUse rejects null input.
   */
  @Test
  public void toolUseRejectsNullInput()
  {
    try
    {
      new PrimingMessage.ToolUse("Bash", null, "out");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("input");
    }
  }

  /**
   * Verifies that ToolUse rejects null output.
   */
  @Test
  public void toolUseRejectsNullOutput()
  {
    try
    {
      new PrimingMessage.ToolUse("Bash", Map.of(), null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("output");
    }
  }
}
