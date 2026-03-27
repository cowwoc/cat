/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;


import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.CriterionGrade;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.CriterionMetadata;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.EvaluationResult;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.GradingReport;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.ParsedOutput;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.PostHocAnalysis;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.RubricScore;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.Severity;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.TestMessage;
import io.github.cowwoc.cat.hooks.skills.EmpiricalTestRunner.TurnOutput;
import io.github.cowwoc.cat.hooks.skills.PrimingMessage;
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
   * Verifies that buildInput with empty priming produces only the test message.
   */
  @Test
  public void buildInputWithEmptyPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      String result = runner.buildInput(new ArrayList<>(),
        List.of(new TestMessage("hello world", Map.of())), List.of());

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
   * plus the test message.
   */
  @Test
  public void buildInputWithStringOnlyPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("first message"),
        new PrimingMessage.UserMessage("second message"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test prompt", Map.of())), List.of());

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

      // Verify test message
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", Map.of("command", "ls"), "file1.txt\nfile2.txt"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test prompt", Map.of())), List.of());

      // tool_use generates 2 messages (assistant + user tool_result), plus test message = 3 lines
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("user prompt"),
        new PrimingMessage.ToolUse("Read", Map.of("file_path", "/tmp/test.txt"), "contents"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("final prompt", Map.of())), List.of());

      // 1 user message + 2 tool messages + 1 test message = 4 lines
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

      // Fourth: test message
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
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*tool)(?=.*priming_messages\\[0\\]).*")
  public void fromRawListRejectsMissingToolField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("input", Map.of("command", "ls"));
    toolUseMsg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that fromRawList rejects tool_use messages missing the 'input' field.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*input)(?=.*priming_messages\\[0\\]).*")
  public void fromRawListRejectsMissingInputField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("tool", "Bash");
    toolUseMsg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that fromRawList rejects tool_use messages missing the 'output' field.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*output)(?=.*priming_messages\\[0\\]).*")
  public void fromRawListRejectsMissingOutputField()
  {
    Map<String, Object> toolUseMsg = new HashMap<>();
    toolUseMsg.put("type", "tool_use");
    toolUseMsg.put("tool", "Bash");
    toolUseMsg.put("input", Map.of("command", "ls"));

    List<Object> raw = new ArrayList<>();
    raw.add(toolUseMsg);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that fromRawList rejects null rawMessages parameter.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*rawMessages.*")
  public void fromRawListRejectsNullRawMessages()
  {
    PrimingMessage.fromRawList(null);
  }

  /**
   * Verifies that fromRawList rejects unsupported message types like Integer.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*unsupported message type)(?=.*priming_messages\\[0\\]).*")
  public void fromRawListRejectsUnsupportedMessageType()
  {
    List<Object> raw = new ArrayList<>();
    raw.add(42);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that fromRawList rejects a Map with a type value other than "tool_use".
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*unsupported type)(?=.*'invalid').*")
  public void fromRawListRejectsWrongTypeValue()
  {
    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "invalid");
    msg.put("tool", "Bash");
    msg.put("input", Map.of("command", "ls"));
    msg.put("output", "result");

    List<Object> raw = new ArrayList<>();
    raw.add(msg);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that each line of buildInput output is valid single-line JSONL (no embedded newlines).
   */
  @Test
  public void buildInputProducesValidJsonl() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("prompt with\nnewline"),
        new PrimingMessage.ToolUse("Bash", Map.of("command", "echo 'hello\nworld'"),
          "hello\nworld"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test", Map.of())), List.of());

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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      Map<String, Object> complexInput = new HashMap<>();
      complexInput.put("command", "git log --oneline");
      complexInput.put("timeout", 30_000);
      complexInput.put("description", "List recent commits");

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", complexInput, "abc123 Initial commit"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test", Map.of())), List.of());

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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.ToolUse("Bash", Map.of("command", "ls"), "output1"),
        new PrimingMessage.ToolUse("Read", Map.of("file_path", "/tmp/test"), "output2"),
        new PrimingMessage.ToolUse("Write", Map.of("file_path", "/tmp/out"), "output3"));

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test", Map.of())), List.of());

      String[] lines = result.split("\n");
      // 3 tool uses = 6 messages (assistant + user for each) + 1 test message = 7 lines
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
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
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String output = """
        {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"thinking"}]}}
        {"type":"assistant","message":{"role":"assistant","content":\
        [{"type":"tool_use","name":"Bash","id":"123","input":{}}]}}
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
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope()
  {
    new EmpiricalTestRunner(null);
  }

  /**
   * Verifies that buildInput rejects null primingMessages.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*primingMessages.*")
  public void buildInputRejectsNullPrimingMessages() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.buildInput(null, List.of(new TestMessage("prompt", Map.of())), List.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput rejects null messages list.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*messages.*")
  public void buildInputRejectsNullMessages() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.buildInput(List.of(), null, List.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput rejects null output.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*output.*")
  public void parseOutputRejectsNullOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.parseOutput(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null texts.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*texts.*")
  public void evaluateOutputRejectsNullTexts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.evaluateOutput(null, List.of(), Map.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null toolUses.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*toolUses.*")
  public void evaluateOutputRejectsNullToolUses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.evaluateOutput(List.of(), null, Map.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that evaluateOutput rejects null criteria.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*criteria.*")
  public void evaluateOutputRejectsNullCriteria() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.evaluateOutput(List.of(), List.of(), null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that fromRawList rejects a list containing null elements.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*rawMessages.*")
  public void fromRawListRejectsNullElements()
  {
    List<Object> raw = new ArrayList<>();
    raw.add("valid message");
    raw.add(null);

    PrimingMessage.fromRawList(raw);
  }

  /**
   * Verifies that UserMessage rejects null text.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*text.*")
  public void userMessageRejectsNullText()
  {
    new PrimingMessage.UserMessage(null);
  }

  /**
   * Verifies that ToolUse rejects null tool.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*tool.*")
  public void toolUseRejectsNullTool()
  {
    new PrimingMessage.ToolUse(null, Map.of(), "out");
  }

  /**
   * Verifies that ToolUse rejects null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*input.*")
  public void toolUseRejectsNullInput()
  {
    new PrimingMessage.ToolUse("Bash", null, "out");
  }

  /**
   * Verifies that ToolUse rejects null output.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*output.*")
  public void toolUseRejectsNullOutput()
  {
    new PrimingMessage.ToolUse("Bash", Map.of(), null);
  }

  /**
   * Verifies that ConfigResult serializes to valid JSON with all expected fields.
   */
  @Test
  public void configResultSerializesToJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      List<EmpiricalTestRunner.TrialResult> trialResults = List.of(
        new EmpiricalTestRunner.TrialResult(true, Map.of("contains:hello", true), 5L,
          "Hello world", List.of("Bash"), "", List.of(), List.of()),
        new EmpiricalTestRunner.TrialResult(false, Map.of("contains:hello", false), 3L,
          "no match", List.of(), "", List.of(), List.of()));
      EmpiricalTestRunner.ConfigResult configResult =
        new EmpiricalTestRunner.ConfigResult("test-config", 2, 1, 50, trialResults);

      String json = mapper.writeValueAsString(configResult);
      JsonNode node = mapper.readTree(json);

      requireThat(node.path("name").asString(""), "name").isEqualTo("test-config");
      requireThat(node.path("trials").asInt(0), "trials").isEqualTo(2);
      requireThat(node.path("passes").asInt(0), "passes").isEqualTo(1);
      requireThat(node.path("rate").asInt(0), "rate").isEqualTo(50);
      requireThat(node.path("results").isArray(), "resultsIsArray").isTrue();
      requireThat(node.path("results").size(), "resultsSize").isEqualTo(2);

      JsonNode firstTrial = node.path("results").get(0);
      requireThat(firstTrial.path("pass").asBoolean(false), "firstTrialPass").isTrue();
      requireThat(firstTrial.path("elapsed").asLong(0L), "firstTrialElapsed").isEqualTo(5L);
      requireThat(firstTrial.path("outputPreview").asString(""), "firstTrialOutputPreview").
        isEqualTo("Hello world");
      requireThat(firstTrial.path("toolsUsed").isArray(), "firstTrialToolsUsedIsArray").isTrue();
      requireThat(firstTrial.path("toolsUsed").size(), "firstTrialToolsUsedSize").isEqualTo(1);
      requireThat(firstTrial.path("toolsUsed").get(0).asString(""), "firstTrialTool").
        isEqualTo("Bash");
      requireThat(firstTrial.path("checks").isObject(), "firstTrialChecksIsObject").isTrue();
      requireThat(firstTrial.path("checks").path("contains:hello").asBoolean(false),
        "firstTrialContainsHello").isTrue();

      JsonNode secondTrial = node.path("results").get(1);
      requireThat(secondTrial.path("pass").asBoolean(true), "secondTrialPass").isFalse();
      requireThat(secondTrial.path("elapsed").asLong(0L), "secondTrialElapsed").isEqualTo(3L);
      requireThat(secondTrial.path("outputPreview").asString(""), "secondTrialOutputPreview").
        isEqualTo("no match");
      requireThat(secondTrial.path("toolsUsed").isArray(), "secondTrialToolsUsedIsArray").isTrue();
      requireThat(secondTrial.path("toolsUsed").size(), "secondTrialToolsUsedSize").isEqualTo(0);
      requireThat(secondTrial.path("checks").path("contains:hello").asBoolean(true),
        "secondTrialContainsHello").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with system reminders appends them to the test message user message
   * wrapped in system-reminder tags.
   */
  @Test
  public void buildInputWithSystemReminders() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<String> reminders = List.of("You are a helpful assistant.", "Always be concise.");

      String result = runner.buildInput(new ArrayList<>(),
        List.of(new TestMessage("hello world", Map.of())), reminders);

      // Should be a single line (one user message with reminders appended)
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(1);

      JsonNode parsed = mapper.readTree(lines[0]);
      String text = parsed.path("message").path("content").get(0).path("text").asString("");
      requireThat(text, "text").contains("hello world").contains("<system-reminder>").
        contains("You are a helpful assistant.").contains("Always be concise.").
        contains("</system-reminder>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with system reminders and priming messages produces the correct
   * message sequence with reminders appended to the test message.
   */
  @Test
  public void buildInputWithSystemRemindersAndPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<PrimingMessage> priming = List.of(
        new PrimingMessage.UserMessage("first message"));
      List<String> reminders = List.of("Reminder content here.");

      String result = runner.buildInput(priming,
        List.of(new TestMessage("test prompt", Map.of())), reminders);

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(2);

      // First line: priming message (should NOT contain reminder)
      JsonNode firstMsg = mapper.readTree(lines[0]);
      String firstText = firstMsg.path("message").path("content").get(0).path("text").asString("");
      requireThat(firstText, "firstText").isEqualTo("first message");

      // Second line: test message with system reminders appended
      JsonNode secondMsg = mapper.readTree(lines[1]);
      String secondText = secondMsg.path("message").path("content").get(0).path("text").asString("");
      requireThat(secondText, "secondText").contains("test prompt").contains("<system-reminder>").
        contains("Reminder content here.").contains("</system-reminder>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildCommand includes --append-system-prompt when system prompt is provided.
   */
  @Test
  public void buildCommandWithSystemPrompt() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> command = runner.buildCommand("haiku", "You are a cat expert.");

      requireThat(command, "command").contains("claude").contains("--append-system-prompt");
      // Verify the system prompt value follows the flag
      int flagIndex = command.indexOf("--append-system-prompt");
      requireThat(flagIndex, "flagIndex").isGreaterThanOrEqualTo(0);
      requireThat(command.get(flagIndex + 1), "systemPromptValue").isEqualTo(
        "You are a cat expert.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildCommand omits --append-system-prompt when system prompt is empty.
   */
  @Test
  public void buildCommandWithoutSystemPrompt() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      List<String> command = runner.buildCommand("haiku", "");

      requireThat(command, "command").contains("claude");
      requireThat(command.contains("--append-system-prompt"), "noSystemPromptFlag").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with empty system reminders list produces the same output as
   * without system reminders.
   */
  @Test
  public void buildInputWithEmptySystemReminders() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      String result = runner.buildInput(new ArrayList<>(),
        List.of(new TestMessage("hello world", Map.of())), List.of());

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(1);

      JsonNode parsed = mapper.readTree(lines[0]);
      String text = parsed.path("message").path("content").get(0).path("text").asString("");
      requireThat(text, "text").isEqualTo("hello world");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that TrialResult serializes to valid JSON with all expected fields.
   */
  @Test
  public void trialResultSerializesToJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      EmpiricalTestRunner.TrialResult trialResult = new EmpiricalTestRunner.TrialResult(
        true,
        Map.of("contains:expected", true, "not_contains:error", true),
        7L,
        "expected output text",
        List.of("Bash", "Read"),
        "",
        List.of(),
        List.of());

      String json = mapper.writeValueAsString(trialResult);
      JsonNode node = mapper.readTree(json);

      requireThat(node.path("pass").asBoolean(false), "pass").isTrue();
      requireThat(node.path("elapsed").asLong(0L), "elapsed").isEqualTo(7L);
      requireThat(node.path("outputPreview").asString(""), "outputPreview").
        isEqualTo("expected output text");
      requireThat(node.path("toolsUsed").isArray(), "toolsUsedIsArray").isTrue();
      requireThat(node.path("toolsUsed").size(), "toolsUsedSize").isEqualTo(2);
      requireThat(node.path("error").asString("x"), "error").isEqualTo("");
      requireThat(node.path("checks").isObject(), "checksIsObject").isTrue();
      requireThat(node.path("checks").path("contains:expected").asBoolean(false),
        "containsExpected").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseOutput groups output into turns correctly, with each assistant event
   * starting a new turn and result events adding to the last turn.
   */
  @Test
  public void parseOutputGroupsTurnsByAssistantEvents() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      String turn1Json = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
        "\"content\":[{\"type\":\"text\",\"text\":\"Turn 1 text\"}]}}";
      String turn2Json = "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\"," +
        "\"content\":[{\"type\":\"text\",\"text\":\"Turn 2 text\"}," +
        "{\"type\":\"tool_use\",\"name\":\"Bash\",\"id\":\"1\",\"input\":{}}]}}";
      String resultJson = "{\"type\":\"result\",\"result\":\"Final result\"}";
      String output = turn1Json + "\n" + turn2Json + "\n" + resultJson + "\n";

      ParsedOutput parsed = runner.parseOutput(output);

      // Flat lists should contain all items
      requireThat(parsed.texts().size(), "textCount").isEqualTo(3);
      requireThat(parsed.toolUses().size(), "toolUseCount").isEqualTo(1);

      // Should have 2 turns: first assistant event, second assistant event + result
      requireThat(parsed.turns().size(), "turnCount").isEqualTo(2);

      TurnOutput turn1 = parsed.turns().get(0);
      requireThat(turn1.texts().size(), "turn1TextCount").isEqualTo(1);
      requireThat(turn1.texts().get(0), "turn1Text").isEqualTo("Turn 1 text");
      requireThat(turn1.toolUses().isEmpty(), "turn1NoToolUses").isTrue();

      TurnOutput turn2 = parsed.turns().get(1);
      requireThat(turn2.texts().size(), "turn2TextCount").isEqualTo(2);
      requireThat(turn2.texts().get(0), "turn2Text1").isEqualTo("Turn 2 text");
      requireThat(turn2.texts().get(1), "turn2Text2").isEqualTo("Final result");
      requireThat(turn2.toolUses().size(), "turn2ToolUseCount").isEqualTo(1);
      requireThat(turn2.toolUses().get(0), "turn2ToolName").isEqualTo("Bash");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with multiple test messages generates correct stream-json
   * with one user message per test message.
   */
  @Test
  public void buildInputWithMultipleTestMessages() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<TestMessage> messages = List.of(
        new TestMessage("First prompt", Map.of("must_contain", List.of("a"))),
        new TestMessage("Second prompt", Map.of("must_contain", List.of("b"))),
        new TestMessage("Third prompt", Map.of("must_contain", List.of("c"))));

      String result = runner.buildInput(List.of(), messages, List.of());

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);

      JsonNode first = mapper.readTree(lines[0]);
      requireThat(first.path("message").path("content").get(0).path("text").asString(""),
        "firstText").isEqualTo("First prompt");

      JsonNode second = mapper.readTree(lines[1]);
      requireThat(second.path("message").path("content").get(0).path("text").asString(""),
        "secondText").isEqualTo("Second prompt");

      JsonNode third = mapper.readTree(lines[2]);
      requireThat(third.path("message").path("content").get(0).path("text").asString(""),
        "thirdText").isEqualTo("Third prompt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildInput with multiple test messages and system reminders appends
   * reminders to each test message.
   */
  @Test
  public void buildInputWithMultipleMessagesAndSystemReminders() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      JsonMapper mapper = scope.getJsonMapper();

      List<TestMessage> messages = List.of(
        new TestMessage("First prompt", Map.of()),
        new TestMessage("Second prompt", Map.of()));
      List<String> reminders = List.of("Always be helpful.");

      String result = runner.buildInput(List.of(), messages, reminders);

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(2);

      // Both messages should have system reminders appended
      JsonNode first = mapper.readTree(lines[0]);
      String firstText = first.path("message").path("content").get(0).path("text").asString("");
      requireThat(firstText, "firstText").contains("First prompt").
        contains("<system-reminder>").contains("Always be helpful.");

      JsonNode second = mapper.readTree(lines[1]);
      String secondText = second.path("message").path("content").get(0).path("text").asString("");
      requireThat(secondText, "secondText").contains("Second prompt").
        contains("<system-reminder>").contains("Always be helpful.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the exit code logic returns 0 when all configurations have 100% pass rate.
   * <p>
   * The {@code runTests()} method returns 0 when no config's rate falls below 100. This test
   * directly validates that stream logic by constructing ConfigResult objects with 100% pass rate.
   */
  @Test
  public void exitCodeIsZeroWhenAllConfigsPassAtHundredPercent()
  {
    List<EmpiricalTestRunner.ConfigResult> results = List.of(
      new EmpiricalTestRunner.ConfigResult("config-a", 5, 5, 100, List.of()),
      new EmpiricalTestRunner.ConfigResult("config-b", 3, 3, 100, List.of()),
      new EmpiricalTestRunner.ConfigResult("config-c", 10, 10, 100, List.of()));

    boolean anyFailed = results.stream().anyMatch(r -> r.rate() < 100);
    int exitCode;
    if (anyFailed)
      exitCode = 1;
    else
      exitCode = 0;

    requireThat(exitCode, "exitCode").isEqualTo(0);
  }

  /**
   * Verifies that the exit code logic returns 1 when any configuration has less than 100% pass rate.
   * <p>
   * The {@code runTests()} method returns 1 when at least one config's rate falls below 100. This
   * test directly validates that stream logic by constructing ConfigResult objects with mixed rates.
   */
  @Test
  public void exitCodeIsOneWhenAnyConfigHasLessThanHundredPercentPassRate()
  {
    List<EmpiricalTestRunner.ConfigResult> results = List.of(
      new EmpiricalTestRunner.ConfigResult("config-a", 5, 5, 100, List.of()),
      new EmpiricalTestRunner.ConfigResult("config-b", 5, 4, 80, List.of()),
      new EmpiricalTestRunner.ConfigResult("config-c", 5, 5, 100, List.of()));

    boolean anyFailed = results.stream().anyMatch(r -> r.rate() < 100);
    int exitCode;
    if (anyFailed)
      exitCode = 1;
    else
      exitCode = 0;

    requireThat(exitCode, "exitCode").isEqualTo(1);
  }

  /**
   * Verifies that the exit code logic returns 1 when only one configuration has less than 100% pass rate.
   * <p>
   * Even a single failing config should cause exit code 1.
   */
  @Test
  public void exitCodeIsOneWhenOnlyOneConfigFailsBelowHundred()
  {
    List<EmpiricalTestRunner.ConfigResult> results = List.of(
      new EmpiricalTestRunner.ConfigResult("config-a", 10, 9, 90, List.of()));

    boolean anyFailed = results.stream().anyMatch(r -> r.rate() < 100);
    int exitCode;
    if (anyFailed)
      exitCode = 1;
    else
      exitCode = 0;

    requireThat(exitCode, "exitCode").isEqualTo(1);
  }

  /**
   * Verifies that the exit code logic returns 1 when a config has 0% pass rate (all trials fail).
   */
  @Test
  public void exitCodeIsOneWhenConfigHasZeroPercentPassRate()
  {
    List<EmpiricalTestRunner.ConfigResult> results = List.of(
      new EmpiricalTestRunner.ConfigResult("config-a", 5, 0, 0, List.of()));

    boolean anyFailed = results.stream().anyMatch(r -> r.rate() < 100);
    int exitCode;
    if (anyFailed)
      exitCode = 1;
    else
      exitCode = 0;

    requireThat(exitCode, "exitCode").isEqualTo(1);
  }

  /**
   * Verifies that ConfigResult correctly reports reduced trial counts when fail-fast triggers.
   * <p>
   * When fail-fast stops execution after the first failure, {@code actualTrials} will be less than
   * the requested number of trials. ConfigResult stores the actual count, not the requested count.
   */
  @Test
  public void configResultReportsReducedTrialCountWhenFailFastTriggers()
  {
    // Simulate 10 trials requested but only 3 ran before fail-fast triggered
    int requestedTrials = 10;
    int actualTrials = 3;
    int passes = 2;
    int rate = EmpiricalTestRunner.calculateRate(passes, actualTrials);

    EmpiricalTestRunner.ConfigResult result =
      new EmpiricalTestRunner.ConfigResult("config-a", actualTrials, passes, rate, List.of());

    requireThat(result.trials(), "trials").isEqualTo(actualTrials);
    requireThat(result.trials(), "trials").isLessThan(requestedTrials);
    requireThat(result.passes(), "passes").isEqualTo(passes);
    requireThat(result.rate(), "rate").isEqualTo(67);
  }

  /**
   * Verifies that ConfigResult correctly reports 1 trial when fail-fast triggers on the first trial.
   * <p>
   * When the very first trial fails, fail-fast stops all remaining trials. ConfigResult stores
   * actualTrials = 1 even though many more were requested.
   */
  @Test
  public void configResultReportsOneTrialWhenFailFastTriggersImmediately()
  {
    // Only 1 trial ran before fail-fast triggered on first failure
    int actualTrials = 1;
    int passes = 0;
    int rate = EmpiricalTestRunner.calculateRate(passes, actualTrials);

    EmpiricalTestRunner.ConfigResult result =
      new EmpiricalTestRunner.ConfigResult("config-a", actualTrials, passes, rate, List.of());

    requireThat(result.trials(), "trials").isEqualTo(1);
    requireThat(result.passes(), "passes").isEqualTo(0);
    requireThat(result.rate(), "rate").isEqualTo(0);
    // Rate < 100 confirms this would trigger exit code 1
    requireThat(result.rate() < 100, "rateBelow100").isTrue();
  }

  /**
   * Verifies that calculateRate handles 0 trials (edge case for fail-fast with no completed trials).
   */
  @Test
  public void calculateRateWithZeroTrialsReturnsZero()
  {
    int rate = EmpiricalTestRunner.calculateRate(0, 0);
    requireThat(rate, "rate").isEqualTo(0);
  }

  /**
   * Verifies that calculateRate with 1 pass out of 1 trial returns 100%.
   */
  @Test
  public void calculateRateOnePassOneTrial()
  {
    int rate = EmpiricalTestRunner.calculateRate(1, 1);
    requireThat(rate, "rate").isEqualTo(100);
  }

  /**
   * Verifies that calculateRate rounds 1 pass out of 2 trials to 50%.
   */
  @Test
  public void calculateRateOneOfTwoReturnsHalf()
  {
    int rate = EmpiricalTestRunner.calculateRate(1, 2);
    requireThat(rate, "rate").isEqualTo(50);
  }

  // ─── Wave 1: Structured Grading ────────────────────────────────────────────

  /**
   * Verifies that gradeOutput with a passing must_contain criterion includes the criterion grade.
   */
  @Test
  public void gradeOutputMustContainPass() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("The answer is correct and complete.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("correct"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.pass(), "pass").isTrue();
      requireThat(report.messageIndex(), "messageIndex").isEqualTo(0);
      requireThat(report.grades().size(), "gradeCount").isEqualTo(1);

      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.criterionKey(), "key").isEqualTo("contains:correct");
      requireThat(grade.pass(), "gradePass").isTrue();
      requireThat(grade.quote().isEmpty(), "quoteNotEmpty").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with a failing must_contain criterion marks the grade as failed.
   */
  @Test
  public void gradeOutputMustContainFail() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("The answer is wrong.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("correct"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.pass(), "pass").isFalse();
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.pass(), "gradePass").isFalse();
      requireThat(grade.quote(), "quote").isEqualTo("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with rich _metadata extracts description and severity correctly.
   */
  @Test
  public void gradeOutputUsesRichMetadata() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("Output contains magic word.");

      Map<String, Object> metadataEntry = new HashMap<>();
      metadataEntry.put("description", "Checks for magic keyword");
      metadataEntry.put("reason", "Required for validation");
      metadataEntry.put("severity", "HIGH");

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("contains:magic", metadataEntry);

      Map<String, Object> criteria = new HashMap<>();
      criteria.put("must_contain", List.of("magic"));
      criteria.put("_metadata", metadata);

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.grades().size(), "gradeCount").isEqualTo(1);
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.metadata().description(), "description").isEqualTo("Checks for magic keyword");
      requireThat(grade.metadata().reason(), "reason").isEqualTo("Required for validation");
      requireThat(grade.metadata().severity(), "severity").isEqualTo(Severity.HIGH);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput defaults severity to MEDIUM and description to the criterion key
   * when no metadata is present.
   */
  @Test
  public void gradeOutputDefaultsMetadataWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Map<String, Object> criteria = Map.of("must_contain", List.of("hello"));

      GradingReport report = runner.gradeOutput(0, List.of("hello world"), List.of(), criteria);

      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.metadata().severity(), "severity").isEqualTo(Severity.MEDIUM);
      requireThat(grade.metadata().description(), "description").isEqualTo("contains:hello");
      requireThat(grade.metadata().reason(), "reason").isEqualTo("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput sorts grades by severity (HIGH before MEDIUM before LOW).
   */
  @Test
  public void gradeOutputSortsBySeverity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("alpha beta gamma");

      Map<String, Object> lowMeta = new HashMap<>();
      lowMeta.put("severity", "LOW");
      Map<String, Object> highMeta = new HashMap<>();
      highMeta.put("severity", "HIGH");
      Map<String, Object> medMeta = new HashMap<>();
      medMeta.put("severity", "MEDIUM");

      Map<String, Object> metadataMap = new HashMap<>();
      metadataMap.put("contains:alpha", lowMeta);
      metadataMap.put("contains:beta", highMeta);
      metadataMap.put("contains:gamma", medMeta);

      Map<String, Object> criteria = new HashMap<>();
      criteria.put("must_contain", List.of("alpha", "beta", "gamma"));
      criteria.put("_metadata", metadataMap);

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.grades().size(), "gradeCount").isEqualTo(3);
      requireThat(report.grades().get(0).metadata().severity(), "firstSeverity").isEqualTo(Severity.HIGH);
      requireThat(report.grades().get(1).metadata().severity(), "secondSeverity").isEqualTo(Severity.MEDIUM);
      requireThat(report.grades().get(2).metadata().severity(), "thirdSeverity").isEqualTo(Severity.LOW);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with empty criteria returns an empty grade list with pass=true.
   */
  @Test
  public void gradeOutputWithEmptyCriteriaPassesWithNoGrades() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      GradingReport report = runner.gradeOutput(2, List.of("any output"), List.of(), Map.of());

      requireThat(report.pass(), "pass").isTrue();
      requireThat(report.grades().isEmpty(), "noGrades").isTrue();
      requireThat(report.messageIndex(), "messageIndex").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractQuote returns the surrounding context around the matched term.
   */
  @Test
  public void extractQuoteReturnsContextAroundTerm()
  {
    String text = "The quick brown fox jumps over the lazy dog";
    String quote = EmpiricalTestRunner.extractQuote(text, "fox", 200);
    requireThat(quote.isEmpty(), "quoteNotEmpty").isFalse();
    requireThat(quote, "quote").contains("fox");
  }

  /**
   * Verifies that extractQuote returns empty string when term is not found.
   */
  @Test
  public void extractQuoteReturnsEmptyWhenTermAbsent()
  {
    String text = "The quick brown fox";
    String quote = EmpiricalTestRunner.extractQuote(text, "cat", 200);
    requireThat(quote, "quote").isEqualTo("");
  }

  /**
   * Verifies that CriterionMetadata.fromRaw defaults to MEDIUM severity for missing severity field.
   */
  @Test
  public void criterionMetadataFromRawDefaultsSeverityToMedium()
  {
    Map<String, Object> rawMap = Map.of("description", "Test criterion");
    CriterionMetadata meta = CriterionMetadata.fromRaw("myKey", rawMap);
    requireThat(meta.severity(), "severity").isEqualTo(Severity.MEDIUM);
    requireThat(meta.description(), "description").isEqualTo("Test criterion");
  }

  /**
   * Verifies that CriterionMetadata.fromRaw throws for an unknown severity value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*invalid severity 'CRITICAL'.*")
  public void criterionMetadataFromRawHandlesUnknownSeverity()
  {
    Map<String, Object> rawMap = Map.of("description", "desc", "severity", "CRITICAL");
    CriterionMetadata.fromRaw("key", rawMap);
  }

  /**
   * Verifies that CriterionMetadata.fromRaw with a non-Map rawValue uses the criterion key as description.
   */
  @Test
  public void criterionMetadataFromRawWithNullValueUsesKeyAsDescription()
  {
    CriterionMetadata meta = CriterionMetadata.fromRaw("contains:hello", null);
    requireThat(meta.description(), "description").isEqualTo("contains:hello");
    requireThat(meta.severity(), "severity").isEqualTo(Severity.MEDIUM);
    requireThat(meta.reason(), "reason").isEqualTo("");
  }

  // ─── Wave 2: Post-Hoc Analysis ─────────────────────────────────────────────

  /**
   * Verifies that analyzeFailedTrial on a trial with no failing criteria returns score=10
   * and no violations.
   */
  @Test
  public void analyzeFailedTrialWithNoFailuresReturnsMaxScore() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("Output is correct and helpful.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("correct"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.adherenceScore(), "score").isEqualTo(10);
      requireThat(analysis.violations().isEmpty(), "noViolations").isTrue();
      requireThat(analysis.suggestions().isEmpty(), "noSuggestions").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial on a trial with one missing must_contain generates a
   * violation in the instructions category.
   */
  @Test
  public void analyzeFailedTrialIdentifiesInstructionViolation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("The output is wrong.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("correct"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.violations().size(), "violationCount").isEqualTo(1);
      requireThat(analysis.violations().get(0).category(), "category").isEqualTo("instructions");
      requireThat(analysis.violations().get(0).expected(), "expected").contains("correct");
      requireThat(analysis.violations().get(0).severity(), "severity").isEqualTo(Severity.MEDIUM);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial assigns the tool_usage category for missing tool criteria.
   */
  @Test
  public void analyzeFailedTrialCategoriesToolUsageForMissingTool() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("Done without using any tools.");
      Map<String, Object> criteria = Map.of("must_use_tools", List.of("Bash"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.violations().size(), "violationCount").isEqualTo(1);
      requireThat(analysis.violations().get(0).category(), "category").isEqualTo("tool_usage");
      requireThat(analysis.violations().get(0).actual(), "actual").contains("none");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial generates improvement suggestions sorted by severity.
   */
  @Test
  public void analyzeFailedTrialSuggestionsAreSortedBySeverity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("Something output here.");

      Map<String, Object> lowMeta = new HashMap<>();
      lowMeta.put("severity", "LOW");
      Map<String, Object> highMeta = new HashMap<>();
      highMeta.put("severity", "HIGH");

      Map<String, Object> metadataMap = new HashMap<>();
      metadataMap.put("contains:alpha", lowMeta);
      metadataMap.put("contains:beta", highMeta);

      Map<String, Object> criteria = new HashMap<>();
      criteria.put("must_contain", List.of("alpha", "beta"));
      criteria.put("_metadata", metadataMap);

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.suggestions().size(), "suggestionCount").isEqualTo(2);
      requireThat(analysis.suggestions().get(0), "firstSuggestion").contains("[HIGH]");
      requireThat(analysis.suggestions().get(1), "secondSuggestion").contains("[LOW]");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial adherence score is 1 when all criteria fail.
   */
  @Test
  public void analyzeFailedTrialMinScoreWhenAllFail() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("completely wrong output with nothing right");
      Map<String, Object> criteria = Map.of(
        "must_contain", List.of("alpha", "beta", "gamma", "delta", "epsilon",
          "zeta", "eta", "theta", "iota", "kappa"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.adherenceScore(), "score").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial with empty criteria returns score=10 and no violations.
   */
  @Test
  public void analyzeFailedTrialWithEmptyCriteriaReturnsMaxScore() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, List.of("any"), List.of(), Map.of());

      requireThat(analysis.adherenceScore(), "score").isEqualTo(10);
      requireThat(analysis.violations().isEmpty(), "noViolations").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── Wave 3: Blind Comparison ───────────────────────────────────────────────

  /**
   * Verifies that rateToScore maps pass rates to the correct 1-5 score.
   */
  @Test
  public void rateToScoreMapsCorrectly()
  {
    requireThat(EmpiricalTestRunner.rateToScore(100), "score100").isEqualTo(5);
    requireThat(EmpiricalTestRunner.rateToScore(90), "score90").isEqualTo(5);
    requireThat(EmpiricalTestRunner.rateToScore(89), "score89").isEqualTo(4);
    requireThat(EmpiricalTestRunner.rateToScore(70), "score70").isEqualTo(4);
    requireThat(EmpiricalTestRunner.rateToScore(69), "score69").isEqualTo(3);
    requireThat(EmpiricalTestRunner.rateToScore(50), "score50").isEqualTo(3);
    requireThat(EmpiricalTestRunner.rateToScore(49), "score49").isEqualTo(2);
    requireThat(EmpiricalTestRunner.rateToScore(25), "score25").isEqualTo(2);
    requireThat(EmpiricalTestRunner.rateToScore(24), "score24").isEqualTo(1);
    requireThat(EmpiricalTestRunner.rateToScore(0), "score0").isEqualTo(1);
  }

  /**
   * Verifies that RubricScore.total returns the sum of all four dimensions.
   */
  @Test
  public void rubricScoreTotalIsSumOfDimensions()
  {
    RubricScore score = new RubricScore(5, 4, 3, 2);
    requireThat(score.total(), "total").isEqualTo(14);
  }

  /**
   * Verifies that RubricScore rejects values outside the 1-5 range.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void rubricScoreRejectsOutOfRangeValues()
  {
    new RubricScore(0, 3, 3, 3);
  }

  /**
   * Verifies that computeRubricScore returns default score of 3 per dimension when no trials exist.
   */
  @Test
  public void computeRubricScoreReturnsDefaultWhenNoTrials() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      EmpiricalTestRunner.ConfigResult result =
        new EmpiricalTestRunner.ConfigResult("empty", 0, 0, 0, List.of());

      RubricScore score = runner.computeRubricScore(result);

      requireThat(score.instructionAdherence(), "instructionAdherence").isEqualTo(3);
      requireThat(score.outputQuality(), "outputQuality").isEqualTo(3);
      requireThat(score.toolUsageCorrectness(), "toolUsageCorrectness").isEqualTo(3);
      requireThat(score.errorHandling(), "errorHandling").isEqualTo(3);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that computeRubricScore assigns a score of 5 for instruction adherence when all trials pass.
   */
  @Test
  public void computeRubricScoreAssignsFiveForPerfectPassRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<EmpiricalTestRunner.TrialResult> trials = List.of(
        new EmpiricalTestRunner.TrialResult(true, Map.of("msg0:contains:hello", true), 1L, "",
          List.of(), "", List.of(), List.of()),
        new EmpiricalTestRunner.TrialResult(true, Map.of("msg0:contains:hello", true), 1L, "",
          List.of(), "", List.of(), List.of()));
      EmpiricalTestRunner.ConfigResult result =
        new EmpiricalTestRunner.ConfigResult("perfect", 2, 2, 100, trials);

      RubricScore score = runner.computeRubricScore(result);

      requireThat(score.instructionAdherence(), "instructionAdherence").isEqualTo(5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that PostHocAnalysis rejects an adherence score below 1.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void postHocAnalysisRejectsScoreBelowOne()
  {
    new PostHocAnalysis(0, List.of(), List.of());
  }

  /**
   * Verifies that PostHocAnalysis rejects an adherence score above 10.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void postHocAnalysisRejectsScoreAboveTen()
  {
    new PostHocAnalysis(11, List.of(), List.of());
  }

  // ─── CRITICAL: Additional gradeOutput tests ─────────────────────────────────

  /**
   * Verifies that gradeOutput with must_not_contain and a present term fails with a quote.
   */
  @Test
  public void gradeOutputMustNotContainFailWithQuote() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("An error occurred during processing.");
      Map<String, Object> criteria = Map.of("must_not_contain", List.of("error"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.pass(), "pass").isFalse();
      requireThat(report.grades().size(), "gradeCount").isEqualTo(1);
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.criterionKey(), "key").isEqualTo("not_contains:error");
      requireThat(grade.pass(), "gradePass").isFalse();
      requireThat(grade.quote().isEmpty(), "quotePresent").isFalse();
      requireThat(grade.quote(), "quote").contains("error");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with must_not_contain and an absent term passes with empty quote.
   */
  @Test
  public void gradeOutputMustNotContainPassWithEmptyQuote() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("Operation completed successfully.");
      Map<String, Object> criteria = Map.of("must_not_contain", List.of("error"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);

      requireThat(report.pass(), "pass").isTrue();
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.pass(), "gradePass").isTrue();
      requireThat(grade.quote(), "quote").isEqualTo("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with must_use_tools passes when the tool was used.
   */
  @Test
  public void gradeOutputMustUseToolsPass() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> toolUses = List.of("Bash", "Read");
      Map<String, Object> criteria = Map.of("must_use_tools", List.of("Bash"));

      GradingReport report = runner.gradeOutput(0, List.of("output"), toolUses, criteria);

      requireThat(report.pass(), "pass").isTrue();
      requireThat(report.grades().size(), "gradeCount").isEqualTo(1);
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.criterionKey(), "key").isEqualTo("uses_tool:Bash");
      requireThat(grade.pass(), "gradePass").isTrue();
      requireThat(grade.expected(), "expected").contains("Bash");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with must_use_tools fails when the tool was not used.
   */
  @Test
  public void gradeOutputMustUseToolsFail() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> toolUses = List.of("Read");
      Map<String, Object> criteria = Map.of("must_use_tools", List.of("Bash"));

      GradingReport report = runner.gradeOutput(0, List.of("output"), toolUses, criteria);

      requireThat(report.pass(), "pass").isFalse();
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.pass(), "gradePass").isFalse();
      requireThat(grade.actual(), "actual").contains("not invoked");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with must_not_use_tools passes when tool was not used.
   */
  @Test
  public void gradeOutputMustNotUseToolsPass() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> toolUses = List.of("Read");
      Map<String, Object> criteria = Map.of("must_not_use_tools", List.of("Bash"));

      GradingReport report = runner.gradeOutput(0, List.of("output"), toolUses, criteria);

      requireThat(report.pass(), "pass").isTrue();
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.criterionKey(), "key").isEqualTo("not_uses_tool:Bash");
      requireThat(grade.pass(), "gradePass").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput with must_not_use_tools fails when tool was used.
   */
  @Test
  public void gradeOutputMustNotUseToolsFail() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> toolUses = List.of("Bash");
      Map<String, Object> criteria = Map.of("must_not_use_tools", List.of("Bash"));

      GradingReport report = runner.gradeOutput(0, List.of("output"), toolUses, criteria);

      requireThat(report.pass(), "pass").isFalse();
      CriterionGrade grade = report.grades().get(0);
      requireThat(grade.pass(), "gradePass").isFalse();
      requireThat(grade.actual(), "actual").contains("invoked");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that CriterionGrade.expected and actual fields are populated correctly for must_contain.
   */
  @Test
  public void criterionGradeHasExpectedAndActualFields() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("The answer is correct.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("correct"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);
      CriterionGrade grade = report.grades().get(0);

      requireThat(grade.expected(), "expected").isNotNull();
      requireThat(grade.actual(), "actual").isNotNull();
      requireThat(grade.expected(), "expected").contains("correct");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── CRITICAL: Additional analyzeFailedTrial tests ─────────────────────────

  /**
   * Verifies that analyzeFailedTrial categorizes not_contains error violations as error_handling.
   */
  @Test
  public void analyzeFailedTrialCategorizesNotContainsErrorAsErrorHandling() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("An error occurred in processing.");
      Map<String, Object> criteria = Map.of("must_not_contain", List.of("error"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, texts, List.of(), criteria);

      requireThat(analysis.violations().size(), "violationCount").isEqualTo(1);
      requireThat(analysis.violations().get(0).category(), "category").isEqualTo("error_handling");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial with not_uses_tool violation generates tool_usage category.
   */
  @Test
  public void analyzeFailedTrialCategorizesNotUsesToolAsToolUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> toolUses = List.of("Bash");
      Map<String, Object> criteria = Map.of("must_not_use_tools", List.of("Bash"));

      PostHocAnalysis analysis = runner.analyzeFailedTrial(0, List.of("output"), toolUses, criteria);

      requireThat(analysis.violations().size(), "violationCount").isEqualTo(1);
      requireThat(analysis.violations().get(0).category(), "category").isEqualTo("tool_usage");
      requireThat(analysis.violations().get(0).actual(), "actual").contains("Bash");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that analyzeFailedTrial uses the messageIndex when calling gradeOutput.
   */
  @Test
  public void analyzeFailedTrialUsesMessageIndex() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<String> texts = List.of("All criteria satisfied.");
      Map<String, Object> criteria = Map.of("must_contain", List.of("criteria"));

      // Different message indices should not affect the analysis result
      PostHocAnalysis analysis0 = runner.analyzeFailedTrial(0, texts, List.of(), criteria);
      PostHocAnalysis analysis3 = runner.analyzeFailedTrial(3, texts, List.of(), criteria);

      requireThat(analysis0.adherenceScore(), "score0").isEqualTo(analysis3.adherenceScore());
      requireThat(analysis0.violations().size(), "violations0").
        isEqualTo(analysis3.violations().size());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── CRITICAL: Additional computeRubricScore tests ──────────────────────────

  /**
   * Verifies that computeRubricScore correctly scores tool-related dimensions from trial checks.
   */
  @Test
  public void computeRubricScoreToolDimensionFromChecks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      // All tool checks pass → tool dimension should be 5
      List<EmpiricalTestRunner.TrialResult> trials = List.of(
        new EmpiricalTestRunner.TrialResult(true, Map.of("msg0:uses_tool:Bash", true), 1L, "",
          List.of("Bash"), "", List.of(), List.of()),
        new EmpiricalTestRunner.TrialResult(true, Map.of("msg0:uses_tool:Bash", true), 1L, "",
          List.of("Bash"), "", List.of(), List.of()));
      EmpiricalTestRunner.ConfigResult result =
        new EmpiricalTestRunner.ConfigResult("tool-test", 2, 2, 100, trials);

      RubricScore score = runner.computeRubricScore(result);

      requireThat(score.toolUsageCorrectness(), "toolUsage").isEqualTo(5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that computeRubricScore uses default 3 for dimensions with no relevant checks.
   */
  @Test
  public void computeRubricScoreDefaultsToThreeForMissingDimensions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      // Only text criteria — tool and error dimensions should default to 3
      List<EmpiricalTestRunner.TrialResult> trials = List.of(
        new EmpiricalTestRunner.TrialResult(true, Map.of("msg0:contains:hello", true), 1L, "",
          List.of(), "", List.of(), List.of()));
      EmpiricalTestRunner.ConfigResult result =
        new EmpiricalTestRunner.ConfigResult("text-only", 1, 1, 100, trials);

      RubricScore score = runner.computeRubricScore(result);

      requireThat(score.toolUsageCorrectness(), "toolUsage").isEqualTo(3);
      requireThat(score.errorHandling(), "errorHandling").isEqualTo(3);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that computeRubricScore total() returns sum of all four dimensions.
   */
  @Test
  public void computeRubricScoreTotalIsSumOfDimensions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      EmpiricalTestRunner.ConfigResult result =
        new EmpiricalTestRunner.ConfigResult("empty", 0, 0, 0, List.of());

      RubricScore score = runner.computeRubricScore(result);

      requireThat(score.total(), "total").isEqualTo(
        score.instructionAdherence() + score.outputQuality() +
        score.toolUsageCorrectness() + score.errorHandling());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── HIGH: Validation tests ──────────────────────────────────────────────────

  /**
   * Verifies that runTests rejects trials count above MAX_TRIALS.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void runTestsRejectsTrialsAboveMaxBound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path configFile = Files.createTempFile(tempDir, "config", ".json");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Files.writeString(configFile,
        "{\"configs\":{\"A\":{\"messages\":[{\"prompt\":\"test\"}]}}}");
      // 1001 > MAX_TRIALS (1000)
      runner.runTests(configFile, 1001, "haiku", tempDir, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that runTests rejects zero trials.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void runTestsRejectsZeroTrials() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path configFile = Files.createTempFile(tempDir, "config", ".json");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Files.writeString(configFile,
        "{\"configs\":{\"A\":{\"messages\":[{\"prompt\":\"test\"}]}}}");
      runner.runTests(configFile, 0, "haiku", tempDir, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that runTests rejects a config file with empty configs map.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no entries in 'configs'.*")
  public void runTestsRejectsEmptyConfigs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path configFile = Files.createTempFile(tempDir, "config", ".json");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Files.writeString(configFile, "{\"configs\":{}}");
      runner.runTests(configFile, 1, "haiku", tempDir, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that runBlindComparison rejects a config file with empty configs map.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no entries in 'configs'.*")
  public void runBlindComparisonRejectsEmptyConfigs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path configFile = Files.createTempFile(tempDir, "config", ".json");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Files.writeString(configFile, "{\"configs\":{}}");
      runner.runBlindComparison(configFile, 1, "haiku", tempDir, "candidate", "baseline");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that runBlindComparison rejects trials above MAX_TRIALS.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void runBlindComparisonRejectsTrialsAboveMaxBound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    Path configFile = Files.createTempFile(tempDir, "config", ".json");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Files.writeString(configFile,
        "{\"configs\":{\"A\":{\"messages\":[{\"prompt\":\"test\"}]}}}");
      runner.runBlindComparison(configFile, 1001, "haiku", tempDir, "candidate", "baseline");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── MEDIUM: CriterionGrade fields validation ────────────────────────────────

  /**
   * Verifies that CriterionGrade constructor rejects null expected.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*expected.*")
  public void criterionGradeRejectsNullExpected()
  {
    new CriterionGrade("key",
      new CriterionMetadata("desc", "", EmpiricalTestRunner.Severity.MEDIUM),
      true, "", null, "actual");
  }

  /**
   * Verifies that CriterionGrade constructor rejects null actual.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*actual.*")
  public void criterionGradeRejectsNullActual()
  {
    new CriterionGrade("key",
      new CriterionMetadata("desc", "", EmpiricalTestRunner.Severity.MEDIUM),
      true, "", "expected", null);
  }

  /**
   * Verifies that gradeOutput expected field for must_contain describes the term to find.
   */
  @Test
  public void gradeOutputMustContainExpectedDescribesTerm() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Map<String, Object> criteria = Map.of("must_contain", List.of("hello"));

      GradingReport report = runner.gradeOutput(0, List.of("hello world"), List.of(), criteria);
      CriterionGrade grade = report.grades().get(0);

      requireThat(grade.expected(), "expected").contains("hello");
      requireThat(grade.actual(), "actual").contains("found");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput actual field for must_contain when term absent says not found.
   */
  @Test
  public void gradeOutputMustContainActualWhenAbsentSaysNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Map<String, Object> criteria = Map.of("must_contain", List.of("missing"));

      GradingReport report = runner.gradeOutput(0, List.of("something else"), List.of(), criteria);
      CriterionGrade grade = report.grades().get(0);

      requireThat(grade.actual(), "actual").contains("not found");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ─── LOW: extractQuote edge cases ────────────────────────────────────────────

  /**
   * Verifies that extractQuote truncates to the specified maxLength.
   */
  @Test
  public void extractQuoteTruncatesToMaxLength()
  {
    String text = "The quick brown fox jumps over the lazy dog and then some more text here";
    String quote = EmpiricalTestRunner.extractQuote(text, "fox", 10);
    requireThat(quote.length(), "quoteLength").isLessThanOrEqualTo(10);
    requireThat(quote.isEmpty(), "quoteNotEmpty").isFalse();
  }

  /**
   * Verifies that extractQuote is case-insensitive when finding the term.
   */
  @Test
  public void extractQuoteIsCaseInsensitive()
  {
    String text = "The Quick Brown Fox jumps";
    String quote = EmpiricalTestRunner.extractQuote(text, "fox", 200);
    requireThat(quote.isEmpty(), "quoteNotEmpty").isFalse();
    requireThat(quote.toLowerCase(java.util.Locale.ROOT), "quoteLower").contains("fox");
  }

  /**
   * Verifies that extractQuote handles a term at the very beginning of the text.
   */
  @Test
  public void extractQuoteHandlesTermAtStart()
  {
    String text = "fox is at the start";
    String quote = EmpiricalTestRunner.extractQuote(text, "fox", 200);
    requireThat(quote.isEmpty(), "quoteNotEmpty").isFalse();
    requireThat(quote, "quote").contains("fox");
  }

  /**
   * Verifies that extractQuote handles a term at the very end of the text.
   */
  @Test
  public void extractQuoteHandlesTermAtEnd()
  {
    String text = "at the end is the fox";
    String quote = EmpiricalTestRunner.extractQuote(text, "fox", 200);
    requireThat(quote.isEmpty(), "quoteNotEmpty").isFalse();
    requireThat(quote, "quote").contains("fox");
  }

  // ─── LOW: rateToScore boundary tests ─────────────────────────────────────────

  /**
   * Verifies that rateToScore assigns 1 for a rate of 0.
   */
  @Test
  public void rateToScoreZeroRateReturnsOne()
  {
    requireThat(EmpiricalTestRunner.rateToScore(0), "score").isEqualTo(1);
  }

  /**
   * Verifies that rateToScore assigns 5 for a rate of 100.
   */
  @Test
  public void rateToScoreHundredPercentReturnsFive()
  {
    requireThat(EmpiricalTestRunner.rateToScore(100), "score").isEqualTo(5);
  }

  // ─── LOW: Magic constants accessible ────────────────────────────────────────

  /**
   * Verifies that truncatePreview limits output to MAX_PREVIEW_CHARS when text is long.
   */
  @Test
  public void gradeOutputQuoteDoesNotExceedMaxPreviewChars() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      // Create a very long text to ensure truncation happens
      String longText = "prefix ".repeat(100) + "target" + " suffix".repeat(100);
      List<String> texts = List.of(longText);
      Map<String, Object> criteria = Map.of("must_contain", List.of("target"));

      GradingReport report = runner.gradeOutput(0, texts, List.of(), criteria);
      CriterionGrade grade = report.grades().get(0);

      requireThat(grade.quote().length(), "quoteLength").isLessThanOrEqualTo(200);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildCommand rejects an invalid model name.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*model.*")
  public void buildCommandRejectsInvalidModel() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.buildCommand("not-a-real-model-xyz", "system prompt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that gradeOutput rejects null texts.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*texts.*")
  public void gradeOutputRejectsNullTexts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      runner.gradeOutput(0, null, List.of(), Map.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the sessions path is derived from the scope's sessions path, not hardcoded.
   * <p>
   * Guards against silent auto-merge regressions that replace the injectable sessionsPath design
   * with a static hardcoded path — a regression that git auto-merge cannot detect because there
   * is no textual conflict.
   */
  @Test
  public void sessionsPathMatchesScopeSessionsPath() throws IOException, NoSuchFieldException,
    IllegalAccessException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      Path expected = scope.getClaudeSessionsPath();
      java.lang.reflect.Field field = EmpiricalTestRunner.class.getDeclaredField("sessionsPath");
      field.setAccessible(true); // NOPMD - test-only reflection to verify injectable field
      Path actual = (Path) field.get(runner);
      requireThat(actual, "sessionsPath").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a tool_use assertion passes when the expected tool appears in toolUses.
   */
  @Test
  public void toolUseAssertionPassesWhenToolFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC10_tool_1", "type", "tool_use", "tool", "Skill", "expected", true));
      EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"),
        List.of("Skill", "Bash"));
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("TC10_tool_1"), "TC10_tool_1").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a tool_use assertion fails when the expected tool is absent from toolUses.
   */
  @Test
  public void toolUseAssertionFailsWhenToolNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC10_tool_1", "type", "tool_use", "tool", "Skill", "expected", true));
      EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"),
        List.of("Bash"));
      requireThat(result.pass(), "pass").isFalse();
      requireThat(result.checks().get("TC10_tool_1"), "TC10_tool_1").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a tool_use assertion passes when the tool is absent and expected is false.
   */
  @Test
  public void toolUseAssertionPassesWhenToolAbsentAndExpectedFalse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC10_tool_1", "type", "tool_use", "tool", "Skill", "expected", false));
      EvaluationResult result = runner.evaluateAssertions(assertions, List.of(), List.of("Bash"));
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("TC10_tool_1"), "TC10_tool_1").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a deterministic string_match assertion passes when pattern is found and expected is true.
   */
  @Test
  public void deterministicAssertionPassesWhenPatternFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC1_det_1", "type", "deterministic", "method", "string_match",
          "pattern", "hello world", "expected", true));
      EvaluationResult result = runner.evaluateAssertions(assertions, List.of("Hello World output"),
        List.of());
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().get("TC1_det_1"), "TC1_det_1").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that semantic assertions are skipped and do not affect the pass/fail result.
   */
  @Test
  public void semanticAssertionIsSkippedDoesNotAffectResult() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC1_sem_1", "type", "semantic",
          "instruction", "Check if the response is good", "expected", true));
      EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"), List.of());
      requireThat(result.pass(), "pass").isTrue();
      requireThat(result.checks().containsKey("TC1_sem_1"), "containsKey").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown assertion type throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown type.*'invalid_type'.*")
  public void evaluateAssertionsRejectsUnknownType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("empirical-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
      List<Map<String, Object>> assertions = List.of(
        Map.of("assertion_id", "TC_bad", "type", "invalid_type", "expected", true));
      runner.evaluateAssertions(assertions, List.of(), List.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
